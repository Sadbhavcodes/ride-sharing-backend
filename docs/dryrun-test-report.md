# Dry-Run Test Report — Ride Sharing Backend
> Type: Thought Experiment / Pre-Test Code Trace  
> Date: 2026-07-08 | Scenarios: 8

---

## How to Read This Report

Each scenario traces the **exact code path** that would execute at runtime, service by service.  
**State** columns show what the database row/event payload looks like at that point.  
Outcomes are marked: ✅ Works correctly | ⚠️ Issue found | 🔴 Bug

---

## Scenario 1 — Full Happy Path (Trip Created → Matched → Completed → Paid)

### Setup preconditions
| Step | HTTP | Service | Expected Result |
|---|---|---|---|
| 1a | `POST /auth/register` body: `{name, email, pass, role:"RIDER"}` | user-service | User row created, riderId = 1 |
| 1b | `POST /auth/register` body: `{name, email, pass, role:"DRIVER"}` | user-service | User row created, driverUserId = 2 |
| 1c | `POST /auth/login` (driver creds) | user-service | JWT returned |
| 1d | `POST /drivers` body: `{userId:2, vehicleId:99}` | driver-service → Feign → `GET /users/2` → userservice | Driver row: `{id:1, userId:2, status:PENDING, availability:OFFLINE}` |
| 1e | `PUT /drivers/status` body: `{id:1, status:ACTIVE}` | driver-service | Driver row: `status:ACTIVE` |
| 1f | `PUT /drivers/availability` body: `{id:1, availability:ONLINE}` | driver-service | Driver row: `availability:ONLINE` |
| 1g | `POST /locations/ping` body: `{driverId:1, lat:28.6139, lng:77.2090}` | location-service | `driver_locations` row upserted, PostGIS Point stored as SRID 4326 |

### Main flow
**Step 2 — Create Trip**

```
POST /trips  body: { riderId:1, pickup:{lat:28.6, lng:77.2}, destination:{lat:28.7, lng:77.3} }
```

```
TripController.createTrip()
  → TripService.createTrip()
      → userFeignClient.getUserById(1)           // GET /users/1 → userservice ✅
      → trip = new Trip(status:REQUESTED, ...)
      → tripRepository.save(trip)                // trip.id = 1, persisted ✅
      → matchingFeignClient.findMatch(matchReq)  // POST /matching/match → matching-service
          → MatchingService.matchDriver()
              → locationFeignClient.findNearbyDrivers({lng:77.2, lat:28.6, radius:3000})
                  // POST /locations/drivers/nearby → location-service ✅ (path fixed)
                  → ST_DWithin query runs on PostGIS → returns [{driverId:1, lat:28.6139, lng:77.2090}]
              → driverFeignClient.getAvailableDrivers([1])
                  // POST /drivers/available → driver-service
                  → findByIdInAndStatusAndAvailability([1], ACTIVE, ONLINE) → returns [Driver{id:1}] ✅
              → driverFeignClient.claimDriver(1)
                  // POST /drivers/1/claim → driver-service
                  → DriverService.claimDriver() @Transactional
                  → driver.availability = BUSY, @Version incremented ✅
                  → returns MatchResponse{tripId:1, driverId:1}
      → trip.driverId = 1, trip.status = MATCHED
      → tripRepository.save(trip)                // persisted first ✅
      → tripEventPublisher.publishTripMatched(1, 1, 1)
          → RabbitMQ: exchange=trip.events, routingKey=trip.matched
          → notification-service receives on notification.trip.matched queue
          → logs: "[NOTIFICATION] Trip matched — tripId=1, riderId=1, driverId=1"
      → returns Trip{id:1, status:MATCHED, driverId:1}
```

**Result DB state:** Trip `{id:1, status:MATCHED, driverId:1}`, Driver `{availability:BUSY}`  
**Result:** ✅ 200 OK, trip returned to caller.

---

**Step 3 — Start Trip (IN_PROGRESS)**

```
PUT /trips/1/status  body: { status: IN_PROGRESS }
```

```
TripService.updateTripStatus(1, {IN_PROGRESS})
  → validateTransition(MATCHED → IN_PROGRESS) ✅
  → trip.status = IN_PROGRESS
  → trip.tripStartTime = LocalDateTime.now()     // captured ✅
  → tripRepository.save(trip)
  → status is IN_PROGRESS, no event published (correct — no event defined for this transition)
  → returns Trip{id:1, status:IN_PROGRESS}
```

**Result:** ✅

---

**Step 4 — Complete Trip**

```
PUT /trips/1/status  body: { status: COMPLETED }
```

```
TripService.updateTripStatus(1, {COMPLETED})
  → validateTransition(IN_PROGRESS → COMPLETED) ✅
  → trip.status = COMPLETED
  → trip.tripEndTime = LocalDateTime.now()       // captured ✅
  → driverFeignClient.releaseDriver(1)           // BUSY → ONLINE ✅
  → fetchDistanceKm(trip)
      → locationFeignClient.calculateDistance({fromLat:28.6, fromLng:77.2, toLat:28.7, toLng:77.3})
          // POST /locations/distance → location-service ✅ (path fixed, lowercase name fixed)
          → Haversine formula → ~14.7 km
          → returns DistanceResponse{distanceKm: 14.7}
  → trip.distanceKm = 14.7
  → tripRepository.save(trip)                   // all fields persisted first ✅
  → tripEventPublisher.publishTripCompleted(1, 1, 1, 14.7, startTime, endTime)
      → RabbitMQ: exchange=trip.events, routingKey=trip.completed
      → TWO queues receive it:
          [A] notification.trip.completed → notification-service
               → logs "[NOTIFICATION] Trip completed" ✅
          [B] payment.trip.completed → payment-service
               → TripCompletedEventConsumer.consumeTripCompletedEvent()
               → PaymentService.processPayment(event)
                   → createPendingPayment()
                       → existsByTripId(1) → false (first time) ✅
                       → calculateFair({distanceKm:14.7, tripStartTime, tripEndTime})
                           → duration = e.g. 10 minutes
                           → km = 14.7 (not null ✅ — null guard is there anyway)
                           → fare = 30 + (12 × 14.7) + (1 × 10) = 30 + 176.4 + 10 = ₹216.40
                       → Payment{tripId:1, amount:216.40, status:PENDING} saved ✅
                   → paymentGateway.chargeRider("trip-1", 216.40, 1)
                       → MockPaymentGateway: 90% chance COMPLETED
                       → returns GatewayResponse{txnId:"MOCK-TXN-...", status:COMPLETED}
                   → finalizePayment()
                       → payment.status = COMPLETED, transactionId set, saved ✅
                       → publishPaymentCompleted()
                           → RabbitMQ: exchange=payment.events, routingKey=payment.completed
                           → notification.payment.completed queue → notification-service
                               → logs "Sending payment receipt to riderId:1, amount:216.40" ✅
```

**Final DB state:**  
- Trip: `{status:COMPLETED, distanceKm:14.7, tripStartTime:T, tripEndTime:T+10m}`  
- Driver: `{availability:ONLINE}`  
- Payment: `{status:COMPLETED, amount:216.40, transactionId:"MOCK-TXN-..."}`  

**Result:** ✅ Full happy path completes end-to-end with no issues.

---

## Scenario 2 — Cancel Trip Before Driver Assigned (REQUESTED state)

```
POST /trips  → Trip{id:2, status:REQUESTED}
  (matching fails intentionally — no driver available)
  → MatchingService throws NoDriverAvailableException
  → trip-service catches it in the catch block
  → trip.status = CANCELLED
  → tripRepository.save(trip)
  → publishTripCancelled(2, null, riderId)   ← driverId is null here
      → RabbitMQ routingKey=trip.cancelled
      → notification-service: handleTripCancelled({tripId:2, driverId:null, riderId:1})
          → logs "Trip cancelled — driverId=null" ✅ (null is fine, just logged)

Alternative — explicit cancel via API:
DELETE /trips/2/cancel
  → TripService.cancelTrip(2)
  → trip.status == REQUESTED → no driver to release
  → trip.status = CANCELLED, saved
  → publishTripCancelled(2, null, riderId) ✅
```

**Result:** ✅ Works correctly. `driverId=null` flows through publisher and notification consumer without NPE.

---

## Scenario 3 — Cancel Trip After Driver Assigned (MATCHED state)

```
Trip{id:3, status:MATCHED, driverId:1}
Driver{id:1, availability:BUSY}

DELETE /trips/3/cancel
  → TripService.cancelTrip(3)
  → validateTransition(MATCHED → CANCELLED) ✅
  → trip.status == MATCHED → driverId is NOT null → driverFeignClient.releaseDriver(1) called
      → DriverService.releaseDriver(1) @Transactional
      → driver.availability = ONLINE ✅
  → trip.status = CANCELLED, tripRepository.save()
  → publishTripCancelled(3, 1, riderId)
      → notification-service receives and logs ✅
```

**Result:** ✅ Driver correctly released back to ONLINE. No ghost-BUSY driver left.

---

## Scenario 4 — Race Condition: Two Trips Claiming Same Driver Simultaneously

```
Driver{id:1, availability:ONLINE, version:3}

Thread A (Trip 4): MatchingService calls driverFeignClient.claimDriver(1)
Thread B (Trip 5): MatchingService calls driverFeignClient.claimDriver(1) [same instant]

Both arrive at DriverService.claimDriver(1):
  Thread A reads driver (version:3), sets BUSY, saves → version becomes 4 ✅
  Thread B reads driver (version:3), sets BUSY, saves →
      @Version mismatch → ObjectOptimisticLockingFailureException thrown

DriverController catches it:
  → return ResponseEntity.status(HttpStatus.CONFLICT).build()  // 409

matching-service MatchingService:
  → FeignException.Conflict caught in the for-loop:
  → continue; → tries next candidate driver or next radius

trip-service TripService.createTrip():
  → If matching-service eventually finds another driver → Trip 5 gets a different driver ✅
  → If no other driver exists → NoDriverAvailableException propagated
      → trip-service catch block → Trip 5.status = CANCELLED ✅
```

**Result:** ✅ Race condition handled correctly via optimistic locking at DB level + 409 handling in Feign client.

---

## Scenario 5 — Location Service Down During Trip Completion

```
Trip{id:6, status:IN_PROGRESS, driverId:1}

PUT /trips/6/status body:{COMPLETED}
  → driverFeignClient.releaseDriver(1) ✅
  → fetchDistanceKm(trip)
      → locationFeignClient.calculateDistance(...)
          → location-service is DOWN → Feign retries 3 times (100ms, ~200ms, ~400ms wait)
          → All 3 fail → FeignException thrown
      → catch(Exception e) in fetchDistanceKm()
          → log.warn("Could not fetch distance...") ✅
          → return null
  → trip.distanceKm = null
  → tripRepository.save(trip)  ← null distanceKm persisted (column is nullable ✅)
  → publishTripCompleted(6, 1, riderId, null, startTime, endTime)

payment-service receives TripCompletedEvent{distanceKm:null}:
  → calculateFair():
      → km = event.distanceKm() != null ? event.distanceKm() : 0.0  → km = 0.0 ✅ (null guard)
      → fare = 30 + (12 × 0.0) + (1 × durationMinutes) = base + time only
  → Payment created and processed ✅

notification-service: logs trip completed ✅
```

**Result:** ✅ System degrades gracefully. Rider still gets charged base + time fare. No crash. No DLQ.

---

## Scenario 6 — Duplicate TripCompletedEvent (RabbitMQ Re-delivery / At-Least-Once)

```
Event: TripCompletedEvent{tripId:7}

First delivery:
  → TripCompletedEventConsumer.consumeTripCompletedEvent()
  → PaymentService.createPendingPayment()
      → existsByTripId(7) → false → Payment created, processed, COMPLETED ✅

RabbitMQ re-delivers same event (e.g., consumer crashed after processing but before ACK):
  → TripCompletedEventConsumer.consumeTripCompletedEvent() again
  → PaymentService.createPendingPayment()
      → existsByTripId(7) → TRUE
      → throw new RuntimeException("Payment already exists")

⚠️ ISSUE FOUND:
RuntimeException propagates out of the @RabbitListener method.
With AcknowledgeMode.AUTO, Spring AMQP NACKs the message → goes to DLQ.

This is CORRECT — no double payment. But the DLQ entry looks like a system error
even though it is actually an expected idempotency guard firing.

RECOMMENDED FIX (apply after first successful test run — not blocking):
Catch the duplicate-payment case in the consumer, log as WARN, and return
normally so Spring ACKs the message cleanly instead of routing it to the DLQ.
```

**Result:** ⚠️ Functionally correct (no double payment), but duplicate events pollute the DLQ with false-positive error entries. Non-blocking for local testing.

---

## Scenario 7 — Invalid State Transition (Complete an Already-Cancelled Trip)

```
Trip{id:8, status:CANCELLED}

PUT /trips/8/status body:{COMPLETED}
  → TripService.updateTripStatus(8, {COMPLETED})
  → validateTransition(CANCELLED → COMPLETED)
      → case CANCELLED → throw new IllegalStateException("Trip is already finished") ✅

GlobalExceptionHandler.handleIllegalStateException()
  → returns 409 CONFLICT with ErrorResponse{message:"Trip is already finished"} ✅
```

**Result:** ✅ State machine protection works correctly.

---

## Scenario 8 — Payment Gateway Returns FAILED (10% failure rate)

```
TripCompletedEvent{tripId:9} consumed by payment-service:
  → createPendingPayment() → Payment{status:PENDING} saved
  → paymentGateway.chargeRider("trip-9", amount, riderId)
      → Math.random() ≥ 0.9 (10% case)
      → returns GatewayResponse{txnId:"MOCK-TXN-FAILED-...", status:FAILED}
  → finalizePayment():
      → payment.status = FAILED, transactionId saved ✅
      → response.status == FAILED, NOT COMPLETED
      → publishPaymentCompleted() is NOT called ✅
          → notification-service will NOT receive a PaymentCompleted event
          → No false "payment successful" notification sent to rider ✅
```

**DB state:** Payment `{status:FAILED, tripId:9}`  
**Notification:** None sent (correct behaviour)  
**Result:** ✅ Failure path handled cleanly.

---

## Summary Table

| # | Scenario | Verdict | Action Required |
|---|---|---|---|
| 1 | Full happy path (request → match → start → complete → pay → notify) | ✅ Pass | None |
| 2 | Cancel trip in REQUESTED state (no driver assigned) | ✅ Pass | None |
| 3 | Cancel trip in MATCHED state (driver must be released) | ✅ Pass | None |
| 4 | Race condition — two trips claiming same driver | ✅ Pass | None |
| 5 | Location service DOWN during trip completion | ✅ Pass | None |
| 6 | Duplicate RabbitMQ event (idempotency test) | ⚠️ Noisy DLQ | Fix after first test run |
| 7 | Invalid state transition (complete a CANCELLED trip) | ✅ Pass | None |
| 8 | Mock gateway payment failure (10% rate) | ✅ Pass | None |

---

## Pre-Test Startup Sequence

When ready, start in this order (or just run `docker compose up --build` — `depends_on` enforces the order automatically):

```bash
# Option A — controlled step-by-step
docker compose up postgres rabbitmq          # wait until healthy (~20s)
docker compose up config-server              # wait until healthy (~30s)
docker compose up eureka-server              # wait until healthy (~30s)
docker compose up gateway-server             # wait until healthy (~30s)
docker compose up user-service driver-service location-service
docker compose up matching-service trip-service
docker compose up payment-service notification-service

# Option B — all at once
docker compose up --build
```

RabbitMQ management UI → http://localhost:15672 (guest/guest) — use this to watch queues during testing.  
Eureka dashboard → http://localhost:8761 — confirm all services are registered before sending requests.
