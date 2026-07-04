# Trip Creation Workflow & RabbitMQ Integration Plan
> **Ride-Sharing Backend — Full System Analysis**  
> Stack: Java 21 · Spring Boot 3 · Spring Cloud · PostgreSQL · Feign · RabbitMQ (Phase 7)

---

## Part 1 — Full Trip Creation Workflow (High + Low Level)

### High-Level Overview

When a rider requests a trip, **5 microservices** collaborate synchronously to either match a driver or cancel the trip. Here's the full journey:

```
Rider App
   ↓ POST /trips (via API Gateway on port 8080)
API Gateway  ──→  JWT Validation (GlobalFilter)
   ↓ lb://TRIPSERVICE  (Eureka resolves)
Trip Service (8083)
   ↓ Feign → userservice  ──→  User Service (8081)  [validate riderId]
   ↓ Save trip (status = REQUESTED) → rideshare_trips DB
   ↓ Feign → matchingservice  ──→  Matching Service (port TBD)
                                        ↓ Feign → locationservice
                                        Location Service (8085) → PostGIS DB
                                        ↓ "nearest drivers" list
                                        ↓ Feign → driverservice
                                        Driver Service (8082) [filter ONLINE + claim atomically]
                                        ↓ return driverId
   ↓ Update trip (status = MATCHED, driverId = X) → rideshare_trips DB
   ↓ Return Trip JSON to Gateway → Rider App
```

---

### Low-Level Step-by-Step Code Trace

#### Step 1 — Request enters API Gateway

**Entry point:** `POST http://localhost:8080/trips`  
**Component:** `JwtAuthenticationFilter` (reactive `GlobalFilter` in `gateway-server`)

- JWT is extracted from `Authorization: Bearer <token>`
- Signature verified using shared secret from Config Server (`myVeryStrongSecretKeyForRideShareApplication2025`)
- If valid → request forwarded to `lb://TRIPSERVICE` (Eureka load-balances)
- If invalid → `401 Unauthorized` returned immediately, trip-service never touched

---

#### Step 2 — TripController receives the request

**File:** `trip-service/.../controller/TripController.java`

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public Trip createTrip(@Valid @RequestBody CreateTripRequest request) {
    return tripService.createTrip(request);
}
```

**DTO `CreateTripRequest`** carries:
- `riderId` (Long)
- `pickup` (Coordinate: latitude, longitude)
- `destination` (Coordinate: latitude, longitude)

---

#### Step 3 — TripService.createTrip() — the orchestration brain

**File:** `trip-service/.../service/TripService.java`

**3a. Validate the rider exists — synchronous Feign call to User Service**

```java
UserDto rider = userFeignClient.getUserById(request.riderId());
// Feign: GET http://USERSERVICE/users/{id}  (Eureka-resolved)
// If 404: FeignClientErrorDecoder throws RiderNotFoundException → 404 returned
```

**Component:** `UserFeignClient` → `@FeignClient(name = "userservice")`  
**What User Service does:** `GET /users/{id}` → queries `rideshare_users` DB → returns `UserDto`

---

**3b. Create and persist the Trip entity**

```java
Trip trip = new Trip();
trip.setRiderId(rider.getId());
trip.setPickupLatitude(request.pickup().latitude());
trip.setPickupLongitude(request.pickup().longitude());
trip.setDestinationLatitude(request.destination().latitude());
trip.setDestinationLongitude(request.destination().longitude());
trip.setStatus(TripStatus.REQUESTED);  // ← starts here

Trip savedTrip = tripRepository.save(trip);
// INSERT into rideshare_trips DB
// @PrePersist sets createdAt/updatedAt automatically
```

**DB:** `rideshare_trips` PostgreSQL database  
**State Machine Entry:** Trip is now in `REQUESTED` state

---

**3c. Request driver matching — synchronous Feign call to Matching Service**

```java
MatchRequest matchRequest = new MatchRequest(
    savedTrip.getId(), savedTrip.getRiderId(), request.pickup(), request.destination()
);

try {
    MatchResponse response = matchingFeignClient.findMatch(matchRequest);
    // Feign: POST http://MATCHINGSERVICE/matching/match
    savedTrip.setDriverId(response.driverId());
    savedTrip.setStatus(TripStatus.MATCHED);         // ← happy path
    return tripRepository.save(savedTrip);
} catch (Exception e) {
    savedTrip.setStatus(TripStatus.CANCELLED);       // ← no drivers / timeout
    return tripRepository.save(savedTrip);
}
```

**Component:** `MatchingFeignClient` → `@FeignClient(name = "matchingservice")`

---

#### Step 4 — MatchingService.matchDriver() — the real intelligence

**File:** `matching-service/.../service/MatchingService.java`

This is where the distributed logic lives. It does an **expanding radius search**:

```java
double[] radii = {3000, 5000, 8000, 12000, 20000};  // meters

for (double radius : radii) {
    // 4a. Ask Location Service: "who is nearby?"
    NearestDriverRequest nearestDriverRequest = new NearestDriverRequest(
        request.pickup().longitude(), request.pickup().latitude(), radius
    );
    List<DriverLocationResponse> nearbyDrivers = locationFeignClient.findNearbyDrivers(nearestDriverRequest);
    // Feign: POST http://LOCATIONSERVICE/drivers/nearby

    if (nearbyDrivers.isEmpty()) continue;  // expand radius

    // 4b. Ask Driver Service: "which of these are ONLINE?"
    List<Long> nearbyDriverIds = nearbyDrivers.stream().map(DriverLocationResponse::driverId).toList();
    List<DriverDto> availableDrivers = driverFeignClient.getAvailableDrivers(nearbyDriverIds);
    // Feign: POST http://DRIVERSERVICE/drivers/available

    if (availableDrivers.isEmpty()) continue;

    // 4c. Try to atomically claim a driver (handles race conditions)
    Set<Long> availableDriverIds = availableDrivers.stream().map(DriverDto::id).collect(Collectors.toSet());

    for (DriverLocationResponse candidate : nearbyDrivers) {
        if (!availableDriverIds.contains(candidate.driverId())) continue;

        try {
            driverFeignClient.claimDriver(candidateId);
            // Feign: POST http://DRIVERSERVICE/drivers/{id}/claim
            // Driver Service atomically sets status → BUSY (409 if already claimed)
            return new MatchResponse(request.tripId(), candidateId);
        } catch (FeignException.Conflict e) {
            continue;  // another trip grabbed this driver, try next
        }
    }
}
throw new NoDriverAvailableException("No drivers available even in expanded radius");
```

---

#### Step 5 — LocationService handles the spatial query

**File:** `location-service/.../controller/DriverLocationController.java`

```java
@PostMapping("/drivers/nearby")
public List<DriverLocationResponse> findNearbyDrivers(@RequestBody NearestDriverRequest request) {
    return driverLocationService.findNearbyDrivers(
        request.latitude(), request.longitude(),
        request.radius() != null ? request.radius() : 5000.0  // 5km default
    );
}
```

**Under the hood:** `DriverLocationService` calls `DriverLocationRepository` which runs a **PostGIS `ST_DWithin()` spatial query** on the `rideshare_locations` PostgreSQL database. It uses a `GIST` spatial index for performance. Returns a list of `DriverLocationResponse` objects sorted by distance.

---

#### Step 6 — Driver Service handles availability + claiming

**File:** `driver-service/.../controller/...`  
Two endpoints are used by Matching Service:

| Endpoint | What it does |
|---|---|
| `POST /drivers/available` | Batch-filter given driver IDs → return only ONLINE ones |
| `POST /drivers/{id}/claim` | Atomically flip driver status ONLINE → BUSY, return 409 if already BUSY |

The 409 conflict on `/claim` is the **race-condition guard**: if two trips race to claim the same driver, only one wins. The other catches `FeignException.Conflict` and moves to the next candidate.

---

#### Step 7 — Trip state is finalized

Back in `TripService.createTrip()`:
- On success: `trip.status = MATCHED`, `trip.driverId = X` → saved to `rideshare_trips`
- On failure (no driver / timeout): `trip.status = CANCELLED` → saved

**The response returns the full `Trip` entity** up through the chain:  
`TripService` → `TripController` → `Gateway` → `Rider App`

---

### Trip State Machine (Full Picture)

```
         POST /trips
              ↓
         REQUESTED ──────────────────────────────→ CANCELLED
              │          (no driver found /           (POST /{id}/cancel)
              │           matching exception)
              ↓
           MATCHED ──────────────────────────────→ CANCELLED
              │          (PATCH /{id}/status         (POST /{id}/cancel)
              │           or POST /{id}/cancel)      + driver.releaseDriver()
              ↓
         IN_PROGRESS ────────────────────────────→ CANCELLED
              │                                      + driver.releaseDriver()
              ↓
          COMPLETED
       + driver.releaseDriver()
```

**Key behavior:** Any transition to `COMPLETED` or `CANCELLED` when a driver is assigned calls `driverFeignClient.releaseDriver(driverId)` → sets driver back to `ONLINE` in Driver Service.

---

### Full Feign Call Graph (Current System — All Synchronous)

```
API Gateway
    └─→ Trip Service (Feign orchestrator)
            ├─→ User Service          [validate rider]
            ├─→ Matching Service      [find + claim driver]
            │       ├─→ Location Service   [spatial: nearby drivers]
            │       └─→ Driver Service     [filter + claim driver]
            └─→ Driver Service        [release driver on completion/cancel]
```

> ⚠️ **Critical observation:** The entire `createTrip()` call is ONE synchronous HTTP chain. If **any** service in this chain is slow or down, the rider waits (or gets an error). This is exactly the problem RabbitMQ solves for the right flows.

---

## Part 2 — Where & How to Implement RabbitMQ

### First: The Cardinal Rule from Your Execution Plan

From `execution-system.md` Phase 7:
> "Trip Service publishes **'trip matched/completed'** events; Notification Service consumes and logs them"

From the onboarding blueprint (Section 3 — Trip Service):
> "Making Trip Service synchronously call Notification Service directly. If Notification is slow or down, trip creation **shouldn't block** — that's exactly what the message broker is for."

This tells us **exactly** what the boundary is.

---

### The Feign vs RabbitMQ Decision Framework

The answer to "remove Feign or use both?" is **USE BOTH**. Here is the rule:

| Communication Pattern | Tool | Why |
|---|---|---|
| **Need a response to continue** (validation, data lookup, result required) | **Feign (keep it)** | Synchronous — caller waits for answer |
| **Fire and forget** (notify, audit, update another service's state as a side-effect) | **RabbitMQ (add it)** | Async — caller doesn't need to wait |
| **Downstream failure must NOT block upstream** | **RabbitMQ (add it)** | If consumer is down, broker holds message |

---

### What Stays Feign (Do NOT Replace)

| Call | Why it MUST stay synchronous |
|---|---|
| `trip-service → user-service` (validate riderId) | Trip cannot be created if rider doesn't exist — **need the result now** |
| `trip-service → matching-service` (find driver) | Trip needs a `driverId` in the response — **need the result now** |
| `matching-service → location-service` (nearby drivers) | Matching cannot proceed without the spatial data — **need the result now** |
| `matching-service → driver-service` (filter available + claim) | Race condition guard requires synchronous atomic claim — **need the result + 409 response now** |
| `trip-service → driver-service` (release driver) | Strong candidate for async later, keep Feign for now |

---

### What Gets RabbitMQ (New Async Flows)

#### 🟢 Flow 1 — `trip.matched` / `trip.completed` / `trip.cancelled` → Notification Service

**Problem it solves:** Notification Service being slow or down must NOT block trip creation or status updates.

**Publisher:** `Trip Service` (after saving trip state)  
**Exchange:** `trip.events` (Topic Exchange)  
**Routing Keys:**
- `trip.matched` → Notification Service
- `trip.completed` → Notification Service + Payment Service (Phase 8)
- `trip.cancelled` → Notification Service

**Consumer:** `Notification Service` (new service, Phase 7)

**What Notification Service does with each event:**
- `trip.matched`: Push notification to rider ("Driver X is on the way") and driver ("New trip assigned")
- `trip.completed`: Push notification ("Trip completed, fare = ₹X")
- `trip.cancelled`: Push notification ("Trip cancelled")

---

#### 🟡 Flow 2 — `trip.completed` → Payment Service (Phase 8, plan now)

**Publisher:** `Trip Service` publishes `trip.completed` event  
**Consumer:** `Payment Service` calculates fare, initiates charge

This means `Payment Service` does NOT need Feign from Trip Service — it listens to the event and acts.

---

#### 🔵 Flow 3 (Optional/Future) — `driver.released` async

Currently `trip-service → driver-service releaseDriver()` is Feign. This could become async:
- Trip publishes `trip.completed` or `trip.cancelled` event
- Driver Service consumes it and releases the driver
- **However:** Keep it Feign for now — async release creates a window where the driver is incorrectly BUSY. Only move it async when you have idempotency controls.

---

### RabbitMQ Architecture Design

#### Exchange & Queue Setup

```
Exchange: trip.events  (type: topic)
   │
   ├── routing key: trip.matched
   │       └── Queue: notification.trip.matched  → Notification Service
   │
   ├── routing key: trip.completed
   │       ├── Queue: notification.trip.completed → Notification Service
   │       └── Queue: payment.trip.completed      → Payment Service (Phase 8)
   │
   └── routing key: trip.cancelled
           └── Queue: notification.trip.cancelled → Notification Service
```

**Dead Letter Queue (DLQ) — configure from day 1:**
```
DLX Exchange: trip.events.dlx
   └── Queue: trip.events.dead-letter  (inspect + replay failed messages)
```

---

### Where Exactly to Add Code in Trip Service

#### Step 1 — Add dependency to `trip-service/pom.xml`

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

#### Step 2 — Create `RabbitMQConfig.java` in trip-service

```java
@Configuration
public class RabbitMQConfig {

    public static final String TRIP_EVENTS_EXCHANGE = "trip.events";

    @Bean
    public TopicExchange tripEventsExchange() {
        return new TopicExchange(TRIP_EVENTS_EXCHANGE);
    }
    // Note: Queues are declared in each CONSUMER service, not here.
    // Trip Service only needs the exchange to publish to.
}
```

#### Step 3 — Create `TripEventPublisher.java` in trip-service

```java
@Component
public class TripEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public TripEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishTripMatched(Long tripId, Long driverId, Long riderId) {
        TripMatchedEvent event = new TripMatchedEvent(tripId, driverId, riderId);
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.TRIP_EVENTS_EXCHANGE,
            "trip.matched",        // routing key
            event
        );
    }

    public void publishTripCompleted(Long tripId, Long driverId, Long riderId) {
        TripCompletedEvent event = new TripCompletedEvent(tripId, driverId, riderId);
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.TRIP_EVENTS_EXCHANGE,
            "trip.completed",
            event
        );
    }

    public void publishTripCancelled(Long tripId, Long riderId) {
        TripCancelledEvent event = new TripCancelledEvent(tripId, riderId);
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.TRIP_EVENTS_EXCHANGE,
            "trip.cancelled",
            event
        );
    }
}
```

#### Step 4 — Inject and call publisher in TripService.java

In `createTrip()`:
```java
// After: savedTrip.setStatus(TripStatus.MATCHED); tripRepository.save(savedTrip);
tripEventPublisher.publishTripMatched(savedTrip.getId(), response.driverId(), savedTrip.getRiderId());
// ↑ This is fire-and-forget — if Notification is down, RabbitMQ holds the message
```

In `updateTripStatus()`:
```java
// After: tripRepository.save(trip);
if (request.status() == TripStatus.COMPLETED) {
    tripEventPublisher.publishTripCompleted(trip.getId(), trip.getDriverId(), trip.getRiderId());
} else if (request.status() == TripStatus.CANCELLED) {
    tripEventPublisher.publishTripCancelled(trip.getId(), trip.getRiderId());
}
```

In `cancelTrip()`:
```java
// After: tripRepository.save(trip);
tripEventPublisher.publishTripCancelled(trip.getId(), trip.getRiderId());
```

---

### Create Notification Service (New — Phase 7)

**This is a brand new service.** It has NO database (Phase 7 scope), just logs.

#### Structure
```
notification-service/
├── config/
│   └── RabbitMQConfig.java        ← declare queues + bindings here
├── consumer/
│   └── TripEventConsumer.java     ← @RabbitListener methods
├── event/
│   ├── TripMatchedEvent.java
│   ├── TripCompletedEvent.java
│   └── TripCancelledEvent.java
└── NotificationServiceApplication.java
```

#### `RabbitMQConfig.java` in notification-service

```java
@Configuration
public class RabbitMQConfig {

    public static final String TRIP_EVENTS_EXCHANGE = "trip.events";
    public static final String NOTIFICATION_MATCHED_QUEUE = "notification.trip.matched";
    public static final String NOTIFICATION_COMPLETED_QUEUE = "notification.trip.completed";
    public static final String NOTIFICATION_CANCELLED_QUEUE = "notification.trip.cancelled";

    @Bean
    public TopicExchange tripEventsExchange() {
        return new TopicExchange(TRIP_EVENTS_EXCHANGE);
    }

    @Bean
    public Queue matchedQueue() {
        return QueueBuilder.durable(NOTIFICATION_MATCHED_QUEUE)
            .withArgument("x-dead-letter-exchange", "trip.events.dlx")
            .build();
    }

    @Bean
    public Queue completedQueue() {
        return QueueBuilder.durable(NOTIFICATION_COMPLETED_QUEUE)
            .withArgument("x-dead-letter-exchange", "trip.events.dlx")
            .build();
    }

    @Bean
    public Queue cancelledQueue() {
        return QueueBuilder.durable(NOTIFICATION_CANCELLED_QUEUE)
            .withArgument("x-dead-letter-exchange", "trip.events.dlx")
            .build();
    }

    @Bean
    public Binding matchedBinding(Queue matchedQueue, TopicExchange tripEventsExchange) {
        return BindingBuilder.bind(matchedQueue).to(tripEventsExchange).with("trip.matched");
    }

    @Bean
    public Binding completedBinding(Queue completedQueue, TopicExchange tripEventsExchange) {
        return BindingBuilder.bind(completedQueue).to(tripEventsExchange).with("trip.completed");
    }

    @Bean
    public Binding cancelledBinding(Queue cancelledQueue, TopicExchange tripEventsExchange) {
        return BindingBuilder.bind(cancelledQueue).to(tripEventsExchange).with("trip.cancelled");
    }
}
```

#### `TripEventConsumer.java` in notification-service

```java
@Component
public class TripEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TripEventConsumer.class);

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_MATCHED_QUEUE)
    public void onTripMatched(TripMatchedEvent event) {
        log.info("[NOTIFY] Trip {} matched with driver {}. Sending push to rider {}",
            event.tripId(), event.driverId(), event.riderId());
        // Future: call FCM/APNs here
    }

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_COMPLETED_QUEUE)
    public void onTripCompleted(TripCompletedEvent event) {
        log.info("[NOTIFY] Trip {} completed. Notifying rider {} and driver {}",
            event.tripId(), event.riderId(), event.driverId());
    }

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_CANCELLED_QUEUE)
    public void onTripCancelled(TripCancelledEvent event) {
        log.info("[NOTIFY] Trip {} cancelled. Notifying rider {}",
            event.tripId(), event.riderId());
    }
}
```

---

### Config Server — Add RabbitMQ Config

Add `rabbitmq` section to your shared `application.yaml` in Config Server:

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

All services that need RabbitMQ (trip-service, notification-service) will pick this up from Config Server — **single source of truth**, no duplication.

---

### Updated Architecture After Phase 7

```
Rider App
    ↓ POST /trips
API Gateway (JWT check)
    ↓
Trip Service ──[Feign]──→ User Service       (validate rider)
             ──[Feign]──→ Matching Service   (find + claim driver)
                               ├──[Feign]──→ Location Service (PostGIS)
                               └──[Feign]──→ Driver Service   (claim)
             ──[Feign]──→ Driver Service     (release on complete/cancel)
             ──[RabbitMQ: trip.events]──→ Notification Service  ← NEW ASYNC
             ──[RabbitMQ: trip.events]──→ Payment Service       ← Phase 8
```

**Key property:** If Notification Service crashes, trips still get created. When it restarts, RabbitMQ delivers the backlog. This is **exactly** the exit criterion from the execution plan:
> "Stopping Notification Service doesn't block trip creation; restarting it processes the backlog correctly"

---

## Part 3 — Summary: Feign vs RabbitMQ Decision Table

| Service Call | Current Tool | After Phase 7 | Reason |
|---|---|---|---|
| trip-service → user-service (validate rider) | Feign ✅ | Feign ✅ **Keep** | Need response to proceed |
| trip-service → matching-service (find driver) | Feign ✅ | Feign ✅ **Keep** | Need driverId in response |
| matching-service → location-service (nearby) | Feign ✅ | Feign ✅ **Keep** | Need spatial results now |
| matching-service → driver-service (filter + claim) | Feign ✅ | Feign ✅ **Keep** | Atomic claim, need 409 |
| trip-service → driver-service (release) | Feign ✅ | Feign ✅ **Keep** (for now) | Race condition risk if async |
| trip-service → notification-service | ❌ Doesn't exist | **RabbitMQ 🆕** | Fire-and-forget, must not block |
| trip-service → payment-service | ❌ Doesn't exist | **RabbitMQ 🆕** | Fire-and-forget, Phase 8 |

> **Bottom line from your execution plan:** You are in **Phase 7**. Feign is correct and stays for all synchronous request-response flows. RabbitMQ is additive — it handles the new async notification + payment event flows that don't exist yet in code.

---

## Part 4 — Phase 7 Execution Checklist

Follow this in order:

- [ ] **Run RabbitMQ locally** via Docker: `docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management`
- [ ] **Verify** management UI at `http://localhost:15672` (guest/guest)
- [ ] **Add AMQP dependency** to `trip-service/pom.xml`
- [ ] **Create** `RabbitMQConfig.java` in trip-service (exchange only)
- [ ] **Create** event POJOs: `TripMatchedEvent`, `TripCompletedEvent`, `TripCancelledEvent`
- [ ] **Create** `TripEventPublisher.java` in trip-service
- [ ] **Inject** publisher into `TripService`, add publish calls at state transitions
- [ ] **Add** RabbitMQ config to Config Server `application.yaml`
- [ ] **Create** new `notification-service` Spring Boot project
- [ ] **Create** `RabbitMQConfig.java` in notification-service (queues + bindings + DLQ)
- [ ] **Create** `TripEventConsumer.java` in notification-service
- [ ] **Test exit criteria:**
  - Create a trip → confirm `notification.trip.matched` queue shows 1 message in broker UI
  - Notification Service is running → check logs for `[NOTIFY]` messages
  - Stop Notification Service → create another trip → restart → verify it processes backlog
  - **Trip creation must work even with Notification Service stopped**

---

## Part 5 — Execution Plan Phase Reference

| Phase | Status | What it means for you |
|---|---|---|
| 0 — Foundations | ✅ Done | PostgreSQL, JWT, repo structure |
| 1 — Core Services | ✅ Done | User, Driver, Trip services with business logic |
| 2 — Config Server | ✅ Done | Centralized config via Spring Cloud Config |
| 3 — Eureka | ✅ Done | All services discoverable by name |
| 4 — API Gateway | ✅ Done | JWT centralized at gateway, all routing working |
| 5 — Location Service | ✅ Done | PostGIS spatial queries, `findNearbyDrivers` |
| 6 — Matching Service | ✅ Done | Expanding radius search, atomic driver claim, cancelTrip state machine |
| **7 — RabbitMQ + Notification** | **🔜 YOU ARE HERE** | Add RabbitMQ, build Notification Service as async consumer |
| 8 — Payment Service | 🔜 Next | Payment Service consumes `trip.completed` from RabbitMQ |
| 9 — Docker (Advanced) | ✅ Partially Done | Add RabbitMQ container to docker-compose |
| 10 — AWS | ✅ Partially Done | Add Amazon MQ or RabbitMQ container to EC2 |
