# Transactions, Race Conditions & Atomic Operations — The Complete Mental Model

> **Purpose:** Explain @Transactional, atomic claims, race conditions, and every failure scenario from trip request to payment completion.
> **Stack:** Spring Boot 3 · PostgreSQL · JPA · RabbitMQ · Optimistic Locking

---

## Table of Contents

1. [The Mental Model: What Is a Transaction?](#1-the-mental-model-what-is-a-transaction)
2. [What Problem Does @Transactional Solve?](#2-what-problem-does-transactional-solve)
3. [The Atomic Claim Pattern — Matching Service](#3-the-atomic-claim-pattern--matching-service)
4. [Race Condition: The Double-Assignment Nightmare](#4-race-condition-the-double-assignment-nightmare)
5. [Why Multiple Payment Threads on Same TripId?](#5-why-multiple-payment-threads-on-same-tripid)
6. [Every Bad Path: Trip Request → Payment Complete](#6-every-bad-path-trip-request--payment-complete)
7. [How Each Bad Path Is Resolved](#7-how-each-bad-path-is-resolved)
8. [The Complete Flow with All Failure Points](#8-the-complete-flow-with-all-failure-points)

---

## 1. The Mental Model: What Is a Transaction?

### The Simplest Possible Explanation

Imagine you're transferring ₹500 from your bank account to your friend's account. The bank does two things:

1. **Subtract ₹500 from your account**
2. **Add ₹500 to your friend's account**

**Question:** What happens if the computer crashes after step 1 but before step 2?

- Your account: -₹500 ✅
- Your friend's account: no change ❌
- **Result:** ₹500 disappeared into the void. Money vanished.

**A transaction prevents this.** It means: **both steps succeed, or both steps fail — never just one.**

```
START TRANSACTION
  UPDATE accounts SET balance = balance - 500 WHERE user_id = 'you'
  UPDATE accounts SET balance = balance + 500 WHERE user_id = 'friend'
COMMIT
```

If the crash happens anywhere inside, the database **rolls back both updates**. Your account is never touched. It's as if nothing happened.

---

### The Core Properties (ACID)

| Property | What It Means | Real Example |
|---|---|---|
| **Atomicity** | All or nothing — no partial success | Transfer ₹500: either both accounts update or neither updates |
| **Consistency** | DB stays in a valid state | Total money in the system before = total money after |
| **Isolation** | Concurrent transactions don't see each other's partial work | If two transfers happen at once, they don't interfere |
| **Durability** | Once committed, it survives crashes | After COMMIT, even if the server explodes, the transfer is recorded |

---

### In Spring Boot: @Transactional

```java
@Transactional
public void transferMoney(Long fromUserId, Long toUserId, BigDecimal amount) {
    accountRepository.deduct(fromUserId, amount);   // Step 1
    accountRepository.add(toUserId, amount);        // Step 2
    // If ANY exception is thrown, Spring rolls back BOTH steps
}
```

**Without @Transactional:**
- Step 1 commits immediately to the DB
- If step 2 throws an exception, step 1 is already permanent
- Inconsistent state

**With @Transactional:**
- Spring opens a transaction at the start of the method
- Step 1 and step 2 happen inside the transaction
- If step 2 throws an exception, Spring rolls back step 1
- **All or nothing**

---

## 2. What Problem Does @Transactional Solve?

### Problem 1: Partial Updates

**Scenario:** Creating a trip and assigning it to a driver

```java
// WITHOUT @Transactional — BROKEN
public Trip createTrip(CreateTripRequest request) {
    Trip trip = new Trip();
    trip.setRiderId(request.riderId());
    trip.setStatus(TripStatus.REQUESTED);
    tripRepository.save(trip);          // ← DB write happens NOW

    Driver driver = findDriver();       // ← This takes 2 seconds
    trip.setDriverId(driver.getId());
    trip.setStatus(TripStatus.MATCHED);
    tripRepository.save(trip);          // ← Another DB write

    return trip;
}
```

**What's wrong?**

If `findDriver()` throws an exception (no drivers available), the trip is already saved in the DB with status REQUESTED. Now you have an orphaned trip that will never be matched.

**With @Transactional:**

```java
@Transactional
public Trip createTrip(CreateTripRequest request) {
    Trip trip = new Trip();
    trip.setRiderId(request.riderId());
    trip.setStatus(TripStatus.REQUESTED);
    tripRepository.save(trip);          // ← Staged, not committed yet

    Driver driver = findDriver();       // ← Exception here
    trip.setDriverId(driver.getId());
    trip.setStatus(TripStatus.MATCHED);
    tripRepository.save(trip);

    // COMMIT happens here (end of method)
    return trip;
}
```

If `findDriver()` throws an exception, **the first `save()` is rolled back**. The trip never enters the DB. Clean failure.

---

### Problem 2: Read-Then-Write Race Condition

**Scenario:** Two requests try to book the last available driver at the same time.

```java
// WITHOUT @Transactional — RACE CONDITION
public void claimDriver(Long driverId) {
    Driver driver = driverRepository.findById(driverId).orElseThrow();

    if (driver.getAvailability() != Availability.ONLINE) {
        throw new DriverNotAvailableException();
    }

    driver.setAvailability(Availability.BUSY);
    driverRepository.save(driver);
}
```

**Timeline:**

```
Thread A (Trip 1)                     Thread B (Trip 2)
─────────────────                     ─────────────────
Read driver 7 → ONLINE
                                      Read driver 7 → ONLINE
Check: ONLINE ✅
                                      Check: ONLINE ✅
Set to BUSY
Save → DB                             Set to BUSY
                                      Save → DB

Result: Driver 7 is BUSY
        But TWO trips think they own driver 7
        DOUBLE ASSIGNMENT ❌
```

Both threads read ONLINE, both checked "is it ONLINE?", both said yes, both claimed the driver. **This is the race condition.**

---

## 3. The Atomic Claim Pattern — Matching Service

### The Problem

Multiple trips are trying to claim the same driver at the exact same moment. Only ONE should succeed. The others must fail immediately and try the next driver.

### The Solution: Optimistic Locking with @Version

```java
@Entity
@Table(name = "drivers")
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private Availability availability;

    @Version  // ← This is the magic
    private Long version;
}
```

**How @Version works:**

Every time you update a Driver, JPA increments the `version` field. When saving, JPA generates:

```sql
UPDATE drivers
SET availability = 'BUSY',
    version = version + 1
WHERE id = 7
  AND version = 3;  -- ← Must match the version you read
```

If the version changed (someone else updated the driver), **the WHERE clause fails, 0 rows updated, JPA throws `OptimisticLockingFailureException`.**

---

### The Atomic Claim Implementation

```java
@Service
public class DriverService {

    @Transactional
    public void claimDriver(Long driverId) {
        Driver driver = driverRepository.findById(driverId)
            .orElseThrow(() -> new DriverNotFoundException(driverId));

        if (driver.getAvailability() != Availability.ONLINE) {
            throw new DriverNotAvailableException("Driver is " + driver.getAvailability());
        }

        driver.setAvailability(Availability.BUSY);
        driverRepository.save(driver);
        // If another thread claimed this driver, JPA throws OptimisticLockingFailureException
    }
}
```

**Timeline with @Version:**

```
Driver 7 initially: { id: 7, availability: ONLINE, version: 5 }

Thread A (Trip 1)                          Thread B (Trip 2)
─────────────────                          ─────────────────
Read driver 7 → { ONLINE, version: 5 }
                                           Read driver 7 → { ONLINE, version: 5 }
Check: ONLINE ✅
Set to BUSY
Save:
  UPDATE drivers
  SET availability = 'BUSY', version = 6
  WHERE id = 7 AND version = 5
  → 1 row updated ✅
  → COMMIT
                                           Check: ONLINE ✅
                                           Set to BUSY
                                           Save:
                                             UPDATE drivers
                                             SET availability = 'BUSY', version = 6
                                             WHERE id = 7 AND version = 5
                                             → 0 rows updated ❌
                                             → OptimisticLockingFailureException

Thread A: Success, driver claimed
Thread B: Exception, try next driver
```

**Only one thread's UPDATE actually modifies a row.** The other gets 0 rows updated and fails. This is **atomic** — the claim either fully succeeds or fully fails.

---

### In the Matching Service

```java
@Service
public class MatchingService {

    public MatchResponse matchDriver(MatchRequest request) {
        double[] radii = {3000, 5000, 8000, 12000, 20000};

        for (double radius : radii) {
            List<DriverLocationResponse> nearby = locationService.findNearby(request.pickup(), radius);
            List<Long> nearbyIds = nearby.stream().map(DriverLocationResponse::driverId).toList();
            List<DriverDto> available = driverService.getAvailableDrivers(nearbyIds);

            Set<Long> availableIds = available.stream().map(DriverDto::id).collect(Collectors.toSet());

            // Try each driver in order (nearest first)
            for (DriverLocationResponse candidate : nearby) {
                if (!availableIds.contains(candidate.driverId())) continue;

                try {
                    driverService.claimDriver(candidate.driverId());
                    // Success! Return this driver
                    return new MatchResponse(request.tripId(), candidate.driverId());

                } catch (FeignException.Conflict e) {
                    // Another trip claimed this driver — try next one
                    log.info("Driver {} already claimed, trying next", candidate.driverId());
                    continue;
                }
            }
        }
        throw new NoDriverAvailableException();
    }
}
```

**Key insight:** When `claimDriver()` throws `OptimisticLockingFailureException`, the Driver Service maps it to `409 Conflict`. Feign sees that and throws `FeignException.Conflict`. Matching Service catches it and **tries the next nearest driver**. No crash, clean fallback.

---

## 4. Race Condition: The Double-Assignment Nightmare

### What Is a Race Condition?

**Definition:** When the correctness of your code depends on the **timing** of events, and different timings produce different results.

### The Classic Example: Double Assignment

**Scenario:** Two riders request trips at the same time. There's only one driver nearby.

```
Rider A requests trip → Matching starts
Rider B requests trip → Matching starts

Both matching processes find the same driver (Driver 7) as nearest
Both check: Driver 7 is ONLINE ✅
Both try to assign Driver 7

WITHOUT OPTIMISTIC LOCKING:
  → Both succeed
  → Driver 7 is assigned to BOTH Trip 101 and Trip 102
  → Driver shows up to pick up Rider A
  → Rider B waits forever
  → DISASTER

WITH OPTIMISTIC LOCKING:
  → Thread A: claimDriver(7) → SUCCESS (version 5 → 6)
  → Thread B: claimDriver(7) → CONFLICT (version still 5, but DB is now 6)
  → Thread B catches the exception, tries Driver 8 instead
  → Trip 101 gets Driver 7
  → Trip 102 gets Driver 8
  → CORRECT
```

---

### Visual Timeline

```
Time →
────────────────────────────────────────────────────────────────────

10:00:00.000   Rider A requests trip (Trip 101)
10:00:00.005   Rider B requests trip (Trip 102)

10:00:00.100   Matching Service A finds nearest driver: Driver 7 (ONLINE, version 5)
10:00:00.110   Matching Service B finds nearest driver: Driver 7 (ONLINE, version 5)

10:00:00.200   Thread A calls claimDriver(7)
10:00:00.210   Thread B calls claimDriver(7)

10:00:00.250   Thread A: UPDATE drivers SET availability='BUSY', version=6 WHERE id=7 AND version=5
               → 1 row updated ✅
               → Driver 7 is now { BUSY, version 6 }

10:00:00.260   Thread B: UPDATE drivers SET availability='BUSY', version=6 WHERE id=7 AND version=5
               → 0 rows updated ❌ (version is now 6, not 5)
               → OptimisticLockingFailureException

10:00:00.270   Thread B catches exception, tries next driver (Driver 8)

RESULT:
  Trip 101 → Driver 7 ✅
  Trip 102 → Driver 8 ✅
  No double assignment
```

**The race condition still EXISTS** (two threads racing), but **optimistic locking makes it SAFE**. Only one wins, the other automatically retries.

---

## 5. Why Multiple Payment Threads on Same TripId?

You asked: "Why the fuck would there be two payment threads on the same tripId?"

**Answer: Because distributed systems deliver messages more than once.**

### Scenario 1: RabbitMQ Redelivery

```
1. Trip Service publishes TripCompletedEvent { tripId: 101 }
2. RabbitMQ delivers it to Payment Service
3. Payment Service starts processing
4. Payment Service successfully charges the gateway
5. Payment Service is about to ACK the message
6. CRASH — Payment Service dies before ACKing
7. RabbitMQ thinks: "Message not ACKed = not processed"
8. Payment Service restarts
9. RabbitMQ RE-DELIVERS the same message
10. Payment Service processes it AGAIN
```

**Without idempotency, Priya gets charged twice.**

---

### Scenario 2: Horizontal Scaling (Multiple Instances)

You deployed 2 instances of Payment Service for high availability:

```
                    RabbitMQ
                       │
         ┌─────────────┴─────────────┐
         ↓                           ↓
Payment Service A           Payment Service B
(EC2 instance 1)            (EC2 instance 2)
```

RabbitMQ can deliver the same message to **both instances** if:
- Prefetch settings allow it
- Network glitch causes double delivery
- Message is being reprocessed

**Timeline:**

```
10:00:00.000   TripCompletedEvent { tripId: 101 } published

10:00:00.100   Payment Service A receives message
10:00:00.105   Payment Service B receives message (network glitch caused duplicate)

10:00:00.200   Service A: SELECT * FROM payments WHERE trip_id = 101 → NOT FOUND
10:00:00.205   Service B: SELECT * FROM payments WHERE trip_id = 101 → NOT FOUND

10:00:00.300   Service A: INSERT INTO payments (trip_id=101, ...) → SUCCESS
10:00:00.310   Service B: INSERT INTO payments (trip_id=101, ...) → UNIQUE CONSTRAINT VIOLATION

Service A: Processes payment, charges ₹398.30 ✅
Service B: Catches DataIntegrityViolationException, logs "already processed", ACKs message ✅

Result: Only ONE payment created
```

**The unique constraint on `trip_id` prevents the duplicate insert.** Service B's exception is **expected and handled**.

---

### Scenario 3: Manual Replay from Dead Letter Queue

A payment failed because the gateway was down. You fixed the gateway and manually replayed the message from the DLQ. But the original message HAD partially processed (created PENDING record) before failing. Now you have:

- Original attempt: Payment record exists (status: PENDING or FAILED)
- Replay attempt: Tries to process the same tripId again

**Without idempotency:** Duplicate payment or crash
**With idempotency:** Second attempt sees existing record, skips or updates it

---

## 6. Every Bad Path: Trip Request → Payment Complete

I'll map out **every single thing that can go wrong** at each step.

### 6.1 Trip Creation Phase

```
Rider App → API Gateway → Trip Service
```

| # | Failure Point | What Happens | How It's Handled |
|---|---|---|---|
| 1 | Invalid JWT | Gateway returns 401 | Trip Service never called, clean failure |
| 2 | Rider ID doesn't exist | Feign call to User Service returns 404 | `FeignClientErrorDecoder` throws `RiderNotFoundException` → 404 to client |
| 3 | Trip DB is down | `save()` throws `DataAccessException` | @Transactional rolls back, 500 to client |
| 4 | Matching Service is down | Feign call times out or throws exception | Trip stays REQUESTED (not MATCHED), 500 to client or retry logic |

---

### 6.2 Matching Phase

```
Trip Service → Matching Service → Location Service + Driver Service
```

| # | Failure Point | What Happens | How It's Handled |
|---|---|---|---|
| 5 | No drivers nearby | Location Service returns empty list for all radii | Matching Service throws `NoDriverAvailableException` → Trip stays REQUESTED |
| 6 | All nearby drivers are OFFLINE/BUSY | Driver Service filters them out | Matching Service throws `NoDriverAvailableException` |
| 7 | Two trips claim same driver | Optimistic locking | One succeeds, one gets 409 Conflict, retries with next driver |
| 8 | Driver's `@Version` field missing | Race condition NOT prevented | **BUG**: Double assignment possible (this is why @Version is critical) |
| 9 | Location Service DB (PostGIS) is down | Feign call throws exception | Matching fails, Trip stays REQUESTED |

---

### 6.3 Trip IN_PROGRESS Phase

```
Driver App → API Gateway → Trip Service (status update)
```

| # | Failure Point | What Happens | How It's Handled |
|---|---|---|---|
| 10 | Invalid state transition (e.g., REQUESTED → IN_PROGRESS) | `IllegalStateException` | 409 Conflict to client |
| 11 | Trip DB down during status update | `DataAccessException` | 500 to client, trip status unchanged |
| 12 | RabbitMQ is down when publishing `trip.started` event | Event publish fails | **PROBLEM**: Notification not sent (need retry or fallback) |

---

### 6.4 Trip COMPLETED Phase

```
Driver App → Trip Service → RabbitMQ → Payment Service
```

| # | Failure Point | What Happens | How It's Handled |
|---|---|---|---|
| 13 | Invalid transition (e.g., REQUESTED → COMPLETED) | `IllegalStateException` | 409 Conflict, trip status unchanged |
| 14 | Driver release fails (Feign call to Driver Service) | Driver stays BUSY forever | **PROBLEM**: Driver starvation (needs retry or compensation) |
| 15 | RabbitMQ down when publishing `trip.completed` | Event not published | Payment never triggered, trip completed but not charged |
| 16 | Event published but Payment Service is down | RabbitMQ holds message in queue | When Payment Service restarts, it processes backlog ✅ |

---

### 6.5 Payment Processing Phase

```
RabbitMQ → Payment Service → Mock Gateway → DB
```

| # | Failure Point | What Happens | How It's Handled |
|---|---|---|---|
| 17 | Duplicate event (message redelivery) | Two threads try to insert same `trip_id` | Unique constraint: one succeeds, one catches `DataIntegrityViolationException` |
| 18 | Payment DB down | `save()` throws exception | Message NOT ACKed, RabbitMQ redelivers later ✅ |
| 19 | Gateway times out (real Stripe, not mock) | `charge()` throws timeout exception | Payment stays PENDING, retry logic attempts again |
| 20 | Gateway succeeds but Payment Service crashes before saving | Gateway charged, DB not updated | **PROBLEM**: Money taken, no record (needs idempotency key reconciliation) |
| 21 | Gateway returns FAILED (insufficient funds) | Payment updated to FAILED status | `PaymentFailedEvent` published → Notification sends "Payment failed" |
| 22 | Fare calculation throws exception (divide by zero, etc.) | Exception before DB write | Message NOT ACKed, redelivered → needs bug fix |

---

### 6.6 Notification Phase

```
RabbitMQ → Notification Service
```

| # | Failure Point | What Happens | How It's Handled |
|---|---|---|---|
| 23 | Notification Service down | RabbitMQ holds messages | When it restarts, processes backlog ✅ |
| 24 | Push notification provider (FCM) fails | Notification not delivered | **Graceful degradation**: log failure, don't crash, maybe retry |

---

## 7. How Each Bad Path Is Resolved

### Failure Categories and Solutions

| Failure Type | Detection | Resolution Strategy | Implementation |
|---|---|---|---|
| **Network timeout** | Feign timeout exception | Retry with backoff | Spring Retry or manual retry loop |
| **Service unavailable** | 503 from downstream | Circuit breaker | Resilience4j (Phase 11) |
| **Duplicate message** | Second insert on same `trip_id` | Unique constraint + idempotency check | DB constraint + `existsByTripId()` |
| **Race condition (claim)** | Two threads modify same entity | Optimistic locking | `@Version` field |
| **Partial transaction** | Crash mid-processing | `@Transactional` rollback | Spring transaction management |
| **Lost event** | RabbitMQ down | Persistent queues + redelivery | RabbitMQ durable queues |
| **Gateway charge but no DB record** | Idempotency key mismatch | Reconciliation job | Cron job checks gateway vs DB |
| **Poison message** | Message format changed, always crashes | Dead letter queue | RabbitMQ DLQ configuration |



---

## 8. The Complete Flow with All Failure Points

### Visual Flow: Happy Path + All Bad Paths

```
┌─────────────────────────────────────────────────────────────────────┐
│                        TRIP CREATION PHASE                           │
└─────────────────────────────────────────────────────────────────────┘

Rider App
  │ POST /trips
  ↓
[F1] Gateway → JWT invalid → 401 ❌
  │ JWT valid ✅
  ↓
Trip Service
  │ Feign → User Service (validate riderId)
  ↓
[F2] User Service → 404 (riderId doesn't exist) → 404 to client ❌
  │ Rider exists ✅
  ↓
Trip Service
  │ @Transactional START
  │ INSERT trip { status: REQUESTED }
  ↓
[F3] DB down → DataAccessException → ROLLBACK → 500 ❌
  │ DB write staged ✅
  ↓
Trip Service
  │ Feign → Matching Service
  ↓
[F4] Matching Service down → FeignException → ROLLBACK trip insert → 500 ❌
  │ Matching Service up ✅
  ↓


┌─────────────────────────────────────────────────────────────────────┐
│                        MATCHING PHASE                                │
└─────────────────────────────────────────────────────────────────────┘

Matching Service
  │ Feign → Location Service (find nearby drivers)
  ↓
[F5] No drivers in 20km radius → NoDriverAvailableException → Trip CANCELLED ❌
  │ Drivers found: [Driver 7, Driver 8] ✅
  ↓
Matching Service
  │ Feign → Driver Service (filter ONLINE)
  ↓
[F6] All drivers OFFLINE/BUSY → NoDriverAvailableException → Trip CANCELLED ❌
  │ Available: [Driver 7] ✅
  ↓
Matching Service
  │ Try to claim Driver 7
  │ Feign → Driver Service: POST /drivers/7/claim
  ↓

Driver Service (claimDriver method)
  │ @Transactional START
  │ findById(7) → Driver { availability: ONLINE, version: 5 }
  │ Check: ONLINE ✅
  │ Set availability = BUSY
  │ save()
  ↓

[F7] RACE CONDITION: Another trip also calls claimDriver(7) at same time
  │
  ├── Thread A (Trip 101)
  │     UPDATE drivers SET availability='BUSY', version=6
  │     WHERE id=7 AND version=5
  │     → 1 row updated ✅
  │     → COMMIT
  │     → Returns 200 OK to Matching Service
  │
  └── Thread B (Trip 102)
        UPDATE drivers SET availability='BUSY', version=6
        WHERE id=7 AND version=5
        → 0 rows updated ❌ (version is now 6)
        → OptimisticLockingFailureException
        → Mapped to 409 Conflict
        → FeignException.Conflict thrown
        → Matching Service catches it
        → Tries next driver (Driver 8) ✅

Matching Service
  │ Driver 7 claimed successfully
  │ Return MatchResponse { tripId: 101, driverId: 7 }
  ↓

Trip Service
  │ Update trip { status: MATCHED, driverId: 7 }
  │ COMMIT transaction
  │ Publish TripMatchedEvent to RabbitMQ
  ↓

[F12] RabbitMQ down → Event not published → Notification not sent ⚠️
  │ (Trip is MATCHED, but notifications fail — needs monitoring)
  │ RabbitMQ up ✅
  ↓

RabbitMQ
  │ Route to notification.trip.matched queue
  ↓

Notification Service
  │ Consume event
  │ Log: "Driver 7 matched to Trip 101"
  ✅


┌─────────────────────────────────────────────────────────────────────┐
│                    TRIP IN_PROGRESS PHASE                            │
└─────────────────────────────────────────────────────────────────────┘

Driver App (Raj picks up Priya)
  │ PATCH /trips/101/status { status: IN_PROGRESS }
  ↓
Trip Service
  │ Validate transition: MATCHED → IN_PROGRESS ✅
  │ Update trip { status: IN_PROGRESS }
  ↓
[F10] Invalid transition (e.g., REQUESTED → IN_PROGRESS) → 409 ❌
  │ Valid ✅
  ↓
[F11] DB down → 500 ❌
  │ DB up ✅
  │ Publish TripInProgressEvent (optional)
  ✅


┌─────────────────────────────────────────────────────────────────────┐
│                    TRIP COMPLETED PHASE                              │
└─────────────────────────────────────────────────────────────────────┘

Driver App (Arrived at destination)
  │ PATCH /trips/101/status { status: COMPLETED }
  ↓
Trip Service
  │ Validate transition: IN_PROGRESS → COMPLETED ✅
  │ Update trip { status: COMPLETED, endTime: now }
  │ Calculate distance (Feign → Location Service)
  │ Feign → Driver Service: POST /drivers/7/release
  ↓
[F14] Driver Service down → Driver stays BUSY forever ⚠️
  │ (Need compensating transaction or retry)
  │ Driver Service up ✅
  ↓
Driver Service
  │ Set Driver 7 { availability: ONLINE }
  ✅
  ↓
Trip Service
  │ Publish TripCompletedEvent {
  │   tripId: 101,
  │   riderId: 42,
  │   driverId: 7,
  │   distanceKm: 23.4,
  │   startTime: "2026-07-05T10:00:00",
  │   endTime: "2026-07-05T10:45:00"
  │ }
  ↓
[F15] RabbitMQ down → Event not published → Payment never triggered ❌
  │ RabbitMQ up ✅
  ↓

RabbitMQ (exchange: trip.events, routing key: trip.completed)
  │ Route to:
  │   1. notification.trip.completed
  │   2. payment.trip.completed
  ↓

[F16] Payment Service down → RabbitMQ holds message → redelivers when service restarts ✅


┌─────────────────────────────────────────────────────────────────────┐
│                    PAYMENT PROCESSING PHASE                          │
└─────────────────────────────────────────────────────────────────────┘

Payment Service
  │ @RabbitListener(queues = "payment.trip.completed")
  │ onTripCompleted(TripCompletedEvent)
  ↓

[F17] DUPLICATE EVENT (message redelivery or horizontal scaling)
  │
  ├── Instance A receives event at 10:00:00.100
  └── Instance B receives event at 10:00:00.105 (redelivery)
  │
  ├── Instance A: SELECT * FROM payments WHERE trip_id=101 → NOT FOUND
  └── Instance B: SELECT * FROM payments WHERE trip_id=101 → NOT FOUND
  │
  ├── Instance A: @Transactional START
  │     calculateFare() → ₹398.30
  │     INSERT payments { trip_id: 101, status: PENDING, amount: 398.30 }
  │     → SUCCESS ✅
  │
  └── Instance B: @Transactional START
        calculateFare() → ₹398.30
        INSERT payments { trip_id: 101, status: PENDING, amount: 398.30 }
        → DataIntegrityViolationException (unique constraint on trip_id) ❌
        → Catch exception, log "already processing", ACK message ✅

Payment Service (Instance A continues)
  │ Payment record created { id: 55, trip_id: 101, status: PENDING }
  │ COMMIT transaction 1
  │
  │ Call Mock Gateway (outside transaction)
  │ gateway.charge(idempotencyKey: "trip-101", riderId: 42, amount: 398.30)
  ↓

[F19] Gateway timeout → Exception → Payment stays PENDING → retry logic ⚠️
  │ Gateway responds ✅
  ↓

Mock Gateway
  │ Check: Have I seen idempotencyKey "trip-101" before?
  ↓

[F20] CRASH after gateway succeeds but before DB update
  │ Gateway charged → Money taken
  │ DB not updated → Payment still PENDING
  │ Resolution: Idempotency key reconciliation job
  │             (cron job queries gateway by idempotencyKey, updates DB)

Gateway returns { txnId: "MOCK-TXN-1720171234567", status: "SUCCESS" }
  ↓

Payment Service
  │ @Transactional START (transaction 2)
  │ findById(55) → Payment { status: PENDING, version: 0 }
  │ Set status = COMPLETED
  │ Set transactionId = "MOCK-TXN-1720171234567"
  │ save()
  ↓

[F21] Gateway returns FAILED (insufficient funds)
  │ Set status = FAILED
  │ Publish PaymentFailedEvent → Notification: "Payment failed" ⚠️

Payment Service
  │ UPDATE payments SET status='COMPLETED', transaction_id='...', version=1
  │ WHERE id=55 AND version=0
  │ COMMIT transaction 2
  │
  │ Publish PaymentCompletedEvent {
  │   tripId: 101,
  │   riderId: 42,
  │   amount: 398.30,
  │   transactionId: "MOCK-TXN-1720171234567"
  │ }
  │ ACK RabbitMQ message
  ✅

[F22] Fare calculation exception (bug in code)
  │ → Exception before any DB write
  │ → Message NOT ACKed
  │ → RabbitMQ redelivers
  │ → Keeps crashing (poison message)
  │ → After max retries → sent to Dead Letter Queue
  │ → Manual inspection required


┌─────────────────────────────────────────────────────────────────────┐
│                    NOTIFICATION PHASE                                │
└─────────────────────────────────────────────────────────────────────┘

RabbitMQ
  │ Route PaymentCompletedEvent to notification.payment.completed
  ↓

[F23] Notification Service down → RabbitMQ holds message → redelivers ✅

Notification Service
  │ @RabbitListener(queues = "notification.payment.completed")
  │ onPaymentCompleted(PaymentCompletedEvent)
  │ Log: "Trip 101: ₹398.30 charged. TXN: MOCK-TXN-..."
  │ (Future: Push notification to Priya's phone via FCM)
  ↓

[F24] FCM push fails → Log failure, don't crash ⚠️
  │ (Graceful degradation — notification not critical to payment)

✅ FLOW COMPLETE
```

---

## 9. The @Transactional Split Pattern (Payment Service)

### Why Split Transactions?

**Bad pattern (single long transaction):**

```java
@Transactional
public void processPayment(TripCompletedEvent event) {
    // Transaction START
    Payment payment = new Payment(...);
    payment.setStatus(PaymentStatus.PENDING);
    paymentRepository.save(payment);        // DB write 1 (locks row)

    // Gateway call — takes 500ms
    GatewayResponse response = gateway.charge(...);

    // DB locks held for 500ms while waiting for external HTTP call ❌
    payment.setStatus(PaymentStatus.COMPLETED);
    payment.setTransactionId(response.getTransactionId());
    paymentRepository.save(payment);        // DB write 2
    // COMMIT
}
```

**Problem:** The database row is locked for the entire duration of the gateway call. Under high load, this creates contention — other transactions wait.

---

### Good pattern (split transactions):**

```java
public void processPayment(TripCompletedEvent event) {
    // Transaction 1: Create PENDING record
    Payment payment = createPendingPayment(event);
    // COMMIT (locks released immediately)

    // Gateway call — outside any transaction
    GatewayResponse response = gateway.charge(
        "trip-" + event.tripId(),
        event.riderId(),
        calculateFare(event)
    );

    // Transaction 2: Update to final state
    finalizePayment(payment.getId(), response);
    // COMMIT
}

@Transactional
private Payment createPendingPayment(TripCompletedEvent event) {
    if (paymentRepository.existsByTripId(event.tripId())) {
        throw new PaymentAlreadyProcessedException();
    }
    Payment payment = new Payment();
    payment.setTripId(event.tripId());
    payment.setRiderId(event.riderId());
    payment.setAmount(calculateFare(event));
    payment.setStatus(PaymentStatus.PENDING);
    payment.setIdempotencyKey("trip-" + event.tripId());
    return paymentRepository.save(payment);
}

@Transactional
private void finalizePayment(Long paymentId, GatewayResponse response) {
    Payment payment = paymentRepository.findById(paymentId).orElseThrow();
    if (response.isSuccess()) {
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setTransactionId(response.getTransactionId());
    } else {
        payment.setStatus(PaymentStatus.FAILED);
    }
    paymentRepository.save(payment);
}
```

**Benefits:**
1. DB locks held for ~5ms (INSERT) + ~5ms (UPDATE) = 10ms total
2. Gateway's 500ms happens between transactions
3. Better throughput under load
4. If crash happens during gateway call, PENDING record exists — you can reconcile it

---

## 10. Key Insights: The Ah-Ha Moments

### Insight 1: @Transactional Is Insurance

Without it, your code is **optimistic** — it assumes every line succeeds. @Transactional is **pessimistic** — it plans for failure and ensures you can undo.

```
Without @Transactional:
  You're skydiving without a parachute hoping you don't fall

With @Transactional:
  You have a parachute — if something goes wrong mid-air, you pull the cord (rollback)
```

---

### Insight 2: Optimistic Locking Is Not About Locking

The name is misleading. It's not a "lock" like a mutex. It's a **version-based conflict detection** system.

```
Pessimistic Locking (traditional):
  "Lock the driver row so only I can read/write it"
  Other threads WAIT

Optimistic Locking (@Version):
  "Let everyone read the driver row"
  "When saving, check: is the version still what I read?"
  "If not, someone else changed it — fail fast"
  Other threads DON'T wait, they fail and retry
```

Optimistic locking is faster in **low-contention** scenarios (most claims succeed). Pessimistic locking is better in **high-contention** (many threads fighting for same resource).

For driver matching, optimistic is correct because:
- Multiple trips try to claim different drivers (low contention)
- When contention happens, we have fallback (try next driver)

---

### Insight 3: Race Conditions Are Inevitable, Not Preventable

You **cannot** prevent two threads from running at the same time. That's the point of concurrency. What you CAN do:

1. **Detect** the race (optimistic locking, unique constraints)
2. **Make one winner, one loser** (not both winners or both losers)
3. **Handle the loser gracefully** (retry, fallback, log)

The double-assignment race condition will **always exist** in the timeline. Optimistic locking just ensures only one thread's UPDATE actually modifies the database.

---

### Insight 4: Idempotency Is Your Safety Net in Distributed Systems

In a distributed system:
- Networks are unreliable (messages get duplicated)
- Services crash mid-process (partial work happens)
- Clocks are not synchronized (can't rely on timestamps)

**Idempotency says:** "I don't care how many times you call me, the outcome is the same."

```
charge(tripId: 101, amount: 398.30) called 1 time  → Priya charged ₹398.30
charge(tripId: 101, amount: 398.30) called 5 times → Priya charged ₹398.30

Non-idempotent (broken):
  5 calls → ₹398.30 × 5 = ₹1,991.50 charged ❌

Idempotent (correct):
  5 calls → ₹398.30 charged once ✅
```

How you achieve it:
1. Unique constraint (DB level)
2. Check-before-insert (application level)
3. Idempotency key (gateway level)

All three layers together = bulletproof.

---

### Insight 5: The PENDING State Is Your Audit Trail

Without PENDING:

```
Payment record doesn't exist → We haven't processed it yet
Payment record exists with COMPLETED → We processed it successfully

What about: "We started processing but crashed"?
  → No way to tell
```

With PENDING:

```
No record → Not started
Record with PENDING → Started but not finished (investigate)
Record with COMPLETED → Done
Record with FAILED → Attempted and failed (don't retry)
```

PENDING is **observability**. It tells you: "A process began but the outcome is unknown — reconcile this."

---

## 11. Summary: Mental Model Checklist

When designing any service method, ask:

| Question | Tool/Pattern |
|---|---|
| Can this method be partially executed? | `@Transactional` |
| Can two threads execute this on the same entity simultaneously? | `@Version` (optimistic locking) |
| Can this be called multiple times for the same input? | Unique constraint + idempotency check |
| Does this call an external system that might time out? | Split transactions (short DB, long external call, short DB) |
| Can a message be delivered multiple times? | Idempotency + unique constraint |
| What if the DB is down? | Exception → rollback → message NOT ACKed → redelivery |
| What if RabbitMQ is down? | Event not published → **operational problem** (monitor) |
| What if I crash mid-process? | PENDING state + reconciliation job |

---

## 12. Recommended Next Steps

1. **Read the Driver Service claim code** — see `@Version` in action
2. **Trace one trip request through the logs** — watch the Feign calls
3. **Simulate a race condition** — use JMeter to send 10 concurrent trip requests with 1 nearby driver
4. **Deploy 2 Payment Service instances** — publish the same event twice, confirm only 1 payment created
5. **Kill Payment Service mid-process** — confirm RabbitMQ redelivers the message

Once you've done these, the mental model will be **concrete**, not abstract.

---

**End of Document**
