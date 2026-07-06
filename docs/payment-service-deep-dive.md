# Payment Service — Deep Dive

> A complete conceptual guide to the Payment Service in the context of this ride-sharing backend.
> Stack: Java 21 · Spring Boot 3 · PostgreSQL · RabbitMQ · Spring Cloud

---

## Table of Contents

1. [What Is the Payment Service's Actual Job?](#1-what-is-the-payment-services-actual-job)
2. [What Is a "Mock Gateway" and Why Do You Need It?](#2-what-is-a-mock-gateway-and-why-do-you-need-it)
3. [Should the Fare Be Sent in the Event, or Calculated Inside Payment Service?](#3-should-the-fare-be-sent-in-the-event-or-calculated-inside-payment-service)
4. [The Real-World Scenario: Priya Books a Ride](#4-the-real-world-scenario-priya-books-a-ride)
5. [The Payment State Machine](#5-the-payment-state-machine)
6. [Idempotency — The Most Critical Concept](#6-idempotency--the-most-critical-concept)
7. [Concurrency — What Happens If Two Events Arrive at the Same Time?](#7-concurrency--what-happens-if-two-events-arrive-at-the-same-time)
8. [Transactions — What Gets Wrapped in @Transactional?](#8-transactions--what-gets-wrapped-in-transactional)
9. [Event-Driven Pattern — The Full Message Flow](#9-event-driven-pattern--the-full-message-flow)
10. [The Full Microservice Orchestration](#10-the-full-microservice-orchestration-trip-request-to-payment-success)
11. [Why Is the Payment Service "Last" in the Build Order?](#11-why-is-the-payment-service-last-in-the-build-order)
12. [Summary: Key Concepts and Where They Live](#12-summary-key-concepts-and-where-they-live)

---

## 1. What Is the Payment Service's Actual Job?

Let's kill the confusion right now. The Payment Service has **one core responsibility**:

> **Own the money side of every completed trip — from calculating the fare to recording the charge and settling the transaction.**

It is NOT the Trip Service's job to handle money. It is NOT the Notification Service's job. The Payment Service is the single source of truth for:

- **How much a trip costs** (fare calculation)
- **Whether the rider has been charged**
- **Whether the charge succeeded or failed**
- **The transaction history** (the ledger)

That's it. Nothing more, nothing less.

### What Payment Service Does NOT Do

❌ **Match drivers** — that's Matching Service
❌ **Track trip status** — that's Trip Service
❌ **Send notifications** — that's Notification Service
❌ **Store user profiles** — that's User Service

### What Payment Service DOES Do

✅ **Listen for `TripCompletedEvent`** from RabbitMQ
✅ **Calculate fare** based on distance, time, and surge factors
✅ **Call payment gateway** (mock or real Stripe)
✅ **Record transaction** in its own database
✅ **Publish `PaymentCompletedEvent`** for downstream services
✅ **Handle retries** and idempotency

---

## 2. What Is a "Mock Gateway" and Why Do You Need It?

When GPT said "mock gateway", it means you're **not integrating with Stripe or Razorpay yet** — instead you simulate the external payment provider's behavior in code.

Here's the real picture:

```
Payment Service
    │
    └──→ PaymentGateway (interface)
              ├── MockPaymentGateway (what you build now)
              └── StripePaymentGateway (what you'd build in production)
```

### The Mock Gateway Implementation

The mock gateway is just a Java class that:
1. Receives a charge request (amount + rider ID)
2. Randomly succeeds 90% of the time, fails 10% (to simulate real-world failures)
3. Returns a fake transaction ID like `"MOCK-TXN-1234567890"`

```java
// The interface — your service only depends on this
public interface PaymentGateway {
    GatewayResponse charge(String idempotencyKey, Long riderId, BigDecimal amount);
}

// Mock implementation — used in dev/test
@Component
@ConditionalOnProperty(name = "payment.gateway", havingValue = "mock")
public class MockPaymentGateway implements PaymentGateway {
    
    private final Map<String, GatewayResponse> processedKeys = new ConcurrentHashMap<>();

    @Override
    public GatewayResponse charge(String idempotencyKey, Long riderId, BigDecimal amount) {
        // If we've seen this key before, return the cached result (idempotency)
        return processedKeys.computeIfAbsent(idempotencyKey, key -> {
            // Simulate network delay
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // 90% success, 10% failure
            boolean success = Math.random() < 0.9;
            
            if (success) {
                return new GatewayResponse(
                    "MOCK-TXN-" + System.currentTimeMillis(),
                    "SUCCESS"
                );
            } else {
                return new GatewayResponse(null, "INSUFFICIENT_FUNDS");
            }
        });
    }
}
```

### Why Is This Important?

Because the payment service code — the state machine, idempotency logic, event publishing — is **100% identical** whether you're using Stripe or the mock. The gateway is behind an interface. When you're ready to go real, you just swap `MockPaymentGateway` for `StripePaymentGateway` and inject it. **Zero changes to the rest of the service.**

This is the **Strategy Pattern** applied to external integrations.

```java
// Real Stripe implementation — swapped in via config
@Component
@ConditionalOnProperty(name = "payment.gateway", havingValue = "stripe")
public class StripePaymentGateway implements PaymentGateway {
    
    @Override
    public GatewayResponse charge(String idempotencyKey, Long riderId, BigDecimal amount) {
        // Call real Stripe API with idempotency key
        // com.stripe.Stripe.apiKey = "sk_test_..."
        // PaymentIntent intent = PaymentIntent.create(params);
        return new GatewayResponse(intent.getId(), intent.getStatus());
    }
}
```

The mock gateway is not about saving time — it's about **designing your architecture correctly from the start** so the real gateway is just a plug-in.

---

## 3. Should the Fare Be Sent in the Event, or Calculated Inside Payment Service?

This is the exact question GPT raised and it's a great one. Let's settle it.

**GPT's suggestion:** Trip Service sends `TripCompletedEvent` with the fare already calculated.

**The problem with that:** Trip Service does not own fare logic. If tomorrow you add surge pricing, promo codes, or corporate discounts — that logic lives in Payment Service, not Trip Service. If Trip Service is calculating fares, you now have business logic bleeding across service boundaries.

### The Right Design

**Trip Service publishes RAW DATA:**

```java
TripCompletedEvent {
    tripId: 101,
    riderId: 42,
    driverId: 7,
    pickupLocation: { lat: 28.6139, lng: 77.2090 },
    dropLocation: { lat: 28.5562, lng: 77.1000 },
    tripStartTime: "2026-07-05T10:00:00",
    tripEndTime: "2026-07-05T10:45:00",
    distanceKm: 23.4         // ← Location Service can compute this
}
```

**Payment Service calculates the fare:**

```java
@Service
public class FareCalculationService {
    
    public BigDecimal calculateFare(TripCompletedEvent event) {
        BigDecimal baseFare = new BigDecimal("50.00");  // ₹50
        BigDecimal pricePerKm = new BigDecimal("12.00"); // ₹12/km
        BigDecimal pricePerMinute = new BigDecimal("1.50"); // ₹1.50/min
        
        long durationMinutes = Duration.between(
            event.tripStartTime(),
            event.tripEndTime()
        ).toMinutes();
        
        BigDecimal distanceCharge = pricePerKm.multiply(
            BigDecimal.valueOf(event.distanceKm())
        );
        
        BigDecimal timeCharge = pricePerMinute.multiply(
            BigDecimal.valueOf(durationMinutes)
        );
        
        // Future: Apply surge factor
        BigDecimal surgeFactor = getSurgeFactor(event.pickupLocation());
        
        return baseFare.add(distanceCharge).add(timeCharge).multiply(surgeFactor);
    }
    
    private BigDecimal getSurgeFactor(Location location) {
        // In future: Query demand/supply ratio for this area
        // For now: return 1.0 (no surge)
        return BigDecimal.ONE;
    }
}
```

### Why This Is the Correct Boundary

| Concern | Owner |
|---|---|
| "The trip happened and here's the raw data" | Trip Service |
| "What does this trip cost in money?" | Payment Service |
| "Charge the rider this amount" | Payment Service → Gateway |
| "Notify the rider of the charge" | Notification Service (via event) |

**Trip Service knows about trips. Payment Service knows about money. Never cross-contaminate.**

Tomorrow when you add:
- **Surge pricing** → change only Payment Service
- **Promo codes** → change only Payment Service
- **Corporate accounts** → change only Payment Service
- **Subscription discounts** → change only Payment Service

Trip Service remains unchanged. This is **proper service boundaries**.

---

## 4. The Real-World Scenario: Priya Books a Ride

Let's walk through the entire system — from the moment Priya opens the app to the moment her card is charged — using your actual codebase as the map.

**The players:**
- **Priya** — rider (riderId: 42), wants to go from Connaught Place to IGI Airport
- **Raj** — driver (driverId: 7), Toyota Innova, currently ONLINE near CP

### Step 1 — Priya Requests a Trip

```
Priya's App
  │ Opens app, enters:
  │   Pickup: Connaught Place (28.6139, 77.2090)
  │   Drop: IGI Airport (28.5562, 77.1000)
  │ Taps "Request Ride"
  ↓
  POST /trips
  Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
  {
    "riderId": 42,
    "pickup": { "latitude": 28.6139, "longitude": 77.2090 },
    "destination": { "latitude": 28.5562, "longitude": 77.1000 }
  }
  ↓
API Gateway (port 8080)
  │ JwtAuthenticationFilter validates JWT ✅
  │ Routes to lb://TRIPSERVICE
  ↓
Trip Service (port 8083)
  │ TripController.createTrip()
  │ TripService.createTrip()
  │
  │ 1. Feign → User Service: GET /users/42
  │    → Validates Priya exists ✅
  │
  │ 2. Save Trip:
  │    INSERT INTO trips (rider_id, pickup_lat, pickup_lng, drop_lat, drop_lng, status)
  │    VALUES (42, 28.6139, 77.2090, 28.5562, 77.1000, 'REQUESTED')
  │    → Trip ID: 101
  │
  │ 3. Feign → Matching Service: POST /matching/match
  │    { tripId: 101, riderId: 42, pickup: {...}, destination: {...} }
```

**Payment Service: does nothing yet.** The trip is just a request.

---

### Step 2 — Raj Is Matched

```
Matching Service (port 8085)
  │ MatchingService.matchDriver()
  │
  │ Loop through radii: [3000m, 5000m, 8000m, 12000m, 20000m]
  │
  │ Radius: 3000m
  │ Feign → Location Service: POST /locations/drivers/nearby
  │    { latitude: 28.6139, longitude: 77.2090, radius: 3000 }
  │ ↓
  Location Service (port 8084)
  │ PostGIS query:
  │   SELECT driver_id, ST_Distance(location, ST_MakePoint(77.2090, 28.6139))
  │   FROM driver_locations
  │   WHERE ST_DWithin(location, ST_MakePoint(77.2090, 28.6139), 3000)
  │   ORDER BY distance ASC
  │ Returns: [
  │   { driverId: 7, distance: 1200m },
  │   { driverId: 8, distance: 2800m }
  │ ]
  ↓
Matching Service
  │ Got nearby: [Driver 7, Driver 8]
  │
  │ Feign → Driver Service: POST /drivers/available
  │    { driverIds: [7, 8] }
  │ ↓
  Driver Service (port 8082)
  │ Filter: only ACTIVE + ONLINE drivers
  │ Driver 7: ACTIVE + ONLINE ✅
  │ Driver 8: ACTIVE + OFFLINE ❌
  │ Returns: [{ id: 7, availability: ONLINE }]
  ↓
Matching Service
  │ Available drivers: [Driver 7]
  │
  │ Try to claim Driver 7:
  │ Feign → Driver Service: POST /drivers/7/claim
  │ ↓
  Driver Service
  │ @Transactional
  │ findById(7) → Driver { availability: ONLINE, version: 5 }
  │ Check: ONLINE ✅
  │ Set availability = BUSY
  │ save()
  │   UPDATE drivers
  │   SET availability='BUSY', version=6
  │   WHERE id=7 AND version=5
  │   → 1 row updated ✅
  │ Return 200 OK
  ↓
Matching Service
  │ Driver 7 claimed successfully ✅
  │ Return MatchResponse { tripId: 101, driverId: 7 }
  ↓
Trip Service
  │ Update trip:
  │   UPDATE trips SET driver_id=7, status='MATCHED' WHERE id=101
  │
  │ Publish to RabbitMQ:
  │   Exchange: trip.events
  │   Routing Key: trip.matched
  │   Payload: TripMatchedEvent { tripId: 101, driverId: 7, riderId: 42 }
  ↓
RabbitMQ
  │ Routes to queue: notification.trip.matched
  ↓
Notification Service
  │ @RabbitListener consumes event
  │ Log: "[NOTIFY] Trip 101 matched with driver 7. Sending push to rider 42"
  │ Future: Push notification → Priya's phone: "Driver Raj is on the way!"
```

**Payment Service: still does nothing.** No money has been earned yet — Raj hasn't even started driving.

---

### Step 3 — Raj Picks Up Priya, Trip Goes IN_PROGRESS

```
Raj's App
  │ Raj arrives at Connaught Place
  │ Taps "Start Trip"
  ↓
  PATCH /trips/101/status
  { "status": "IN_PROGRESS" }
  ↓
Trip Service
  │ TripController.updateTripStatus()
  │ Validate state transition:
  │   Current: MATCHED
  │   Requested: IN_PROGRESS
  │   Valid transition ✅
  │
  │ UPDATE trips
  │ SET status='IN_PROGRESS', trip_start_time=NOW()
  │ WHERE id=101
  │
  │ Publish TripInProgressEvent (optional)
  │ Return 200 OK
```

**Payment Service: still nothing.** The fare can only be calculated once the trip ends.



---

### Step 4 — They Arrive at IGI Airport. Trip COMPLETED.

**This is where Payment Service wakes up.**

```
Raj's App
  │ 45 minutes later, arrives at IGI Airport
  │ Taps "Complete Trip"
  ↓
  PATCH /trips/101/status
  { "status": "COMPLETED" }
  ↓
Trip Service
  │ Validate transition: IN_PROGRESS → COMPLETED ✅
  │
  │ Calculate distance:
  │   Feign → Location Service: calculate route distance
  │   Returns: 23.4 km
  │
  │ UPDATE trips
  │ SET status='COMPLETED',
  │     trip_end_time=NOW(),
  │     distance_km=23.4
  │ WHERE id=101
  │
  │ Release driver:
  │   Feign → Driver Service: POST /drivers/7/release
  │   Driver 7: BUSY → ONLINE ✅
  │
  │ Publish to RabbitMQ:
  │   Exchange: trip.events
  │   Routing Key: trip.completed
  │   Payload: TripCompletedEvent {
  │     tripId: 101,
  │     riderId: 42,
  │     driverId: 7,
  │     distanceKm: 23.4,
  │     tripStartTime: "2026-07-05T10:00:00",
  │     tripEndTime: "2026-07-05T10:45:00"
  │   }
  ↓
RabbitMQ (trip.events exchange)
  │ Routes to multiple queues:
  │   1. notification.trip.completed  → Notification Service
  │   2. payment.trip.completed       → Payment Service  ← HERE!
```

---

### Step 5 — Payment Service Receives the Event

**NOW the Payment Service springs into action:**

```
Payment Service
  │ @RabbitListener(queues = "payment.trip.completed")
  │ onTripCompleted(TripCompletedEvent event)
  ↓
PaymentService.processPayment(event)
  │
  │ 1. IDEMPOTENCY CHECK
  │    if (paymentRepository.existsByTripId(101)) {
  │        log.warn("Payment already processed for trip 101");
  │        return; // ACK message, do nothing
  │    }
  │    ✅ Not found, proceed
  │
  │ 2. CALCULATE FARE
  │    FareCalculationService.calculateFare(event)
  │      baseFare = ₹50
  │      distanceCharge = 23.4 km × ₹12/km = ₹280.80
  │      timeCharge = 45 min × ₹1.50/min = ₹67.50
  │      surgeFactor = 1.0 (no surge)
  │      TOTAL = ₹50 + ₹280.80 + ₹67.50 = ₹398.30
  │
  │ 3. CREATE PENDING PAYMENT RECORD
  │    @Transactional START (Transaction 1)
  │    Payment payment = new Payment();
  │    payment.setTripId(101);
  │    payment.setRiderId(42);
  │    payment.setAmount(new BigDecimal("398.30"));
  │    payment.setStatus(PaymentStatus.PENDING);
  │    payment.setIdempotencyKey("trip-101");
  │    paymentRepository.save(payment);
  │    → Payment ID: 55
  │    COMMIT (DB lock released)
  │
  │ 4. CALL PAYMENT GATEWAY (outside transaction)
  │    MockPaymentGateway.charge(
  │        idempotencyKey: "trip-101",
  │        riderId: 42,
  │        amount: 398.30
  │    )
  │    
  │    Gateway checks: "Have I seen key 'trip-101' before?"
  │    → No, this is first time
  │    → Simulate 200ms network delay
  │    → Random success (90% chance)
  │    → Return GatewayResponse {
  │        transactionId: "MOCK-TXN-1720171234567",
  │        status: "SUCCESS"
  │      }
  │
  │ 5. UPDATE TO COMPLETED
  │    @Transactional START (Transaction 2)
  │    Payment payment = paymentRepository.findById(55);
  │    payment.setStatus(PaymentStatus.COMPLETED);
  │    payment.setTransactionId("MOCK-TXN-1720171234567");
  │    paymentRepository.save(payment);
  │    → UPDATE payments
  │      SET status='COMPLETED',
  │          transaction_id='MOCK-TXN-1720171234567',
  │          version=1
  │      WHERE id=55 AND version=0
  │    COMMIT
  │
  │ 6. PUBLISH PAYMENT COMPLETED EVENT
  │    PaymentCompletedEvent paymentEvent = new PaymentCompletedEvent(
  │        tripId: 101,
  │        riderId: 42,
  │        amount: 398.30,
  │        transactionId: "MOCK-TXN-1720171234567"
  │    );
  │    
  │    rabbitTemplate.convertAndSend(
  │        "payment.events",
  │        "payment.completed",
  │        paymentEvent
  │    );
  │
  │ 7. ACK RABBITMQ MESSAGE
  │    Message acknowledged ✅
  ↓
RabbitMQ (payment.events exchange)
  │ Routes to: notification.payment.completed
  ↓
Notification Service
  │ @RabbitListener consumes PaymentCompletedEvent
  │ Log: "[NOTIFY] Trip 101 charged ₹398.30. TXN: MOCK-TXN-1720171234567"
  │ Future: Push notification → Priya's phone:
  │         "Your trip is complete! ₹398.30 charged to your card."
```

**Priya sees the charge notification. Raj gets his earnings recorded. The trip is fully settled.**

---

## 5. The Payment State Machine

A payment goes through these states:

```
[TripCompletedEvent received]
          ↓
       PENDING
     (created in DB)
          ↓
    [Gateway called]
    ↙            ↘
COMPLETED       FAILED
(txn recorded)  (gateway rejected)
                    ↓
                [Retry logic or manual intervention]
```

### Why Does PENDING Exist?

Because the gap between "we decided to charge" and "the charge actually happened" is real. The gateway call takes 200–800ms. If your service crashes in that window, you need to know:

- Was the payment attempted?
- Did it succeed?
- Can it be safely retried?

**PENDING is the "I started but haven't finished" state.** Without it, a crash leaves you with no way to distinguish "never tried" from "tried and we don't know the result."

### The Payment Entity

```java
@Entity
@Table(name = "payments",
       uniqueConstraints = @UniqueConstraint(columnNames = "trip_id"))
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false, unique = true)
    private Long tripId;

    @Column(name = "rider_id", nullable = false)
    private Long riderId;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;   // PENDING, COMPLETED, FAILED

    @Column(name = "transaction_id")
    private String transactionId;   // from gateway

    @Column(name = "idempotency_key", unique = true, nullable = false)
    private String idempotencyKey;  // "trip-{tripId}"

    @Version
    private Long version;           // optimistic locking

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
```

**Key fields:**
- `trip_id` — unique constraint prevents duplicate payments
- `status` — tracks where we are in the process
- `idempotency_key` — for gateway idempotency
- `version` — prevents concurrent updates

---

## 6. Idempotency — The Most Critical Concept in Payment Service

**Idempotency means: calling the same operation multiple times produces the same result as calling it once.**

### The Nightmare Scenario Without Idempotency

```
1. TripCompletedEvent arrives → Payment Service starts processing
2. Gateway.charge() succeeds → Priya is charged ₹398.30
3. Before Payment Service saves the COMPLETED status to DB → CRASH
4. RabbitMQ sees the message was never ACKed → re-delivers the message
5. Payment Service starts processing again
6. Gateway.charge() called again → Priya is charged ₹398.30 AGAIN
7. Priya is now charged ₹796.60 for one trip
```

**This is a double-charge. This is catastrophic. This ends your product.**

### How You Prevent It — Three Layers

#### Layer 1: Unique Constraint on trip_id

```sql
ALTER TABLE payments ADD CONSTRAINT uq_payments_trip_id UNIQUE (trip_id);
```

On the second processing attempt, `INSERT INTO payments (trip_id=101...)` throws a `DataIntegrityViolationException`. The DB itself prevents duplicate records. **This is your hard stop.**

#### Layer 2: Check-Before-Process in Application Code

```java
@Transactional
public void processPayment(TripCompletedEvent event) {
    // Check: has this trip already been processed?
    if (paymentRepository.existsByTripId(event.tripId())) {
        log.warn("Duplicate event for tripId {}. Skipping.", event.tripId());
        return;  // acknowledge the message, do nothing
    }
    // ... proceed with payment
}
```

This is your first guard. Fast, cheap, readable.

#### Layer 3: Idempotency Key with the Gateway

Real payment gateways (Stripe, Razorpay) support idempotency keys. You pass a unique key with your charge request. If you call the gateway twice with the same key, the second call returns the SAME result as the first — no second charge.

```java
String idempotencyKey = "trip-" + event.tripId();  // e.g., "trip-101"
GatewayResponse response = gateway.charge(idempotencyKey, event.riderId(), fare);
// Even if called twice with "trip-101", Stripe only charges once
```

Your mock gateway should simulate this:

```java
public class MockPaymentGateway implements PaymentGateway {
    private final Map<String, GatewayResponse> processedKeys = new ConcurrentHashMap<>();

    @Override
    public GatewayResponse charge(String idempotencyKey, Long riderId, BigDecimal amount) {
        // If we've seen this key before, return the cached result
        return processedKeys.computeIfAbsent(idempotencyKey, key ->
            new GatewayResponse("MOCK-TXN-" + System.currentTimeMillis(), "SUCCESS")
        );
    }
}
```

**All three layers together mean: no matter how many times the event is delivered, Priya is charged exactly once.**

---

## 7. Concurrency — What Happens If Two Events Arrive at the Same Time?

In a distributed system, the same message can be consumed by two threads simultaneously. Imagine two instances of Payment Service are running (horizontal scaling), and both receive `TripCompletedEvent` for trip 101 at the same moment.

```
Thread A: SELECT * FROM payments WHERE trip_id=101 → not found → proceed
Thread B: SELECT * FROM payments WHERE trip_id=101 → not found → proceed
Thread A: INSERT INTO payments ... → SUCCESS
Thread B: INSERT INTO payments ... → DataIntegrityViolationException (unique constraint)
```

Thread B gets a DB exception. You catch it and treat it as "already processed":

```java
@Transactional
public void processPayment(TripCompletedEvent event) {
    try {
        // Check + insert + charge logic
        if (paymentRepository.existsByTripId(event.tripId())) {
            return; // Already processed
        }
        
        Payment payment = createPendingPayment(event);
        GatewayResponse response = gateway.charge(...);
        finalizePayment(payment.getId(), response);
        
    } catch (DataIntegrityViolationException e) {
        // Another thread beat us to it — this is fine, not an error
        log.info("Payment for tripId {} already being processed by another thread.", event.tripId());
    }
}
```

### Optimistic Locking for the Update Phase

Once a Payment record exists (status: PENDING), if two threads try to update it simultaneously (e.g., both trying to set it to COMPLETED), the `@Version` field prevents that:

```
Thread A: UPDATE payments SET status='COMPLETED', version=1 WHERE id=5 AND version=0 → 1 row updated ✅
Thread B: UPDATE payments SET status='COMPLETED', version=1 WHERE id=5 AND version=0 → 0 rows updated ❌
         → Spring throws OptimisticLockingFailureException
         → Retry or log — the payment was already completed by Thread A
```

This is the same `@Version` pattern you already use in Driver Service to prevent double-claiming a driver. **Same concept, different domain.**

---

## 8. Transactions — What Gets Wrapped in @Transactional?

### Bad Pattern (Single Long Transaction)

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

### Good Pattern (Split Transactions)

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

## 9. Event-Driven Pattern — The Full Message Flow

Here's the complete RabbitMQ picture for the payment service:

```
trip.events (TopicExchange)
     │
     └── routing key: trip.completed
              ├── Queue: notification.trip.completed  → Notification Service
              └── Queue: payment.trip.completed       → Payment Service

payment.events (TopicExchange)
     │
     └── routing key: payment.completed
              └── Queue: notification.payment.completed → Notification Service

Dead Letter Exchange (trip.events.dlx):
     └── Queue: trip.events.dead-letter
              └── Manual inspection + replay
```

### Why Does the Payment Service Publish Its OWN Event After Processing?

Because the Notification Service needs to know:
- **"Trip completed"** → "Your driver arrived" notification
- **"Payment processed"** → "₹398.30 charged to your card" notification

These are two different notifications with different data (payment amount, transaction ID). The Notification Service shouldn't calculate the fare — it should just receive a ready-to-display payload.

```java
// After payment is COMPLETED:
PaymentCompletedEvent paymentEvent = new PaymentCompletedEvent(
    payment.getTripId(),
    payment.getRiderId(),
    payment.getAmount(),          // ← ₹398.30 — notification service displays this
    payment.getTransactionId()    // ← for the receipt
);
rabbitTemplate.convertAndSend("payment.events", "payment.completed", paymentEvent);
```

---

## 10. The Full Microservice Orchestration: Trip Request to Payment Success

Here is the complete flow with every service, every tool, and every pattern involved:

```
RIDER APP
  │
  │  POST /trips  (JWT in header)
  ↓
API GATEWAY (port 8080)
  │  JWT validation (JwtAuthenticationFilter)
  │  Route: lb://TRIPSERVICE
  ↓
TRIP SERVICE (port 8083)
  │  Feign → USER SERVICE: validate riderId (sync, need response)
  │  DB: INSERT trip { status: REQUESTED }
  │  Feign → MATCHING SERVICE: find driver (sync, need driverId)
  │     └→ MATCHING SERVICE
  │           │  Feign → LOCATION SERVICE: nearest drivers (PostGIS)
  │           │  Feign → DRIVER SERVICE: filter ONLINE + ACTIVE
  │           │  Feign → DRIVER SERVICE: /claim (optimistic lock @Version)
  │           └→ Returns driverId
  │  DB: UPDATE trip { status: MATCHED, driverId: X }
  │  RabbitMQ: publish trip.matched → Notification queue
  └→ Returns Trip to Rider App

[...Raj drives Priya to destination...]

DRIVER APP
  │  PATCH /trips/101/status { status: COMPLETED }
  ↓
TRIP SERVICE
  │  Validate state transition: IN_PROGRESS → COMPLETED ✅
  │  DB: UPDATE trip { status: COMPLETED, endTime: now }
  │  Feign → DRIVER SERVICE: /release (Raj → ONLINE)
  │  RabbitMQ: publish trip.completed →
  │               ├── notification.trip.completed
  │               └── payment.trip.completed         ← Payment wakes up here
  └→ Returns 200 to Driver App

PAYMENT SERVICE (async, consuming from payment.trip.completed)
  │  Idempotency check: SELECT * WHERE trip_id=101 → not found
  │  Fare calculation: ₹50 + (23.4km × ₹12) + (45min × ₹1.50) = ₹398.30
  │  DB: INSERT payment { trip_id:101, status: PENDING, amount: 398.30 }
  │  Mock Gateway: charge(key:"trip-101", riderId:42, amount:398.30)
  │     └→ Returns { txnId: "MOCK-TXN-...", status: "SUCCESS" }
  │  DB: UPDATE payment { status: COMPLETED, txnId: "MOCK-TXN-..." }
  │  RabbitMQ: publish payment.completed →
  │               └── notification.payment.completed
  └→ Message ACKed

NOTIFICATION SERVICE (consuming from notification.payment.completed)
  └→ Log: "Trip 101 charged ₹398.30. TXN: MOCK-TXN-..."
          [Future: push notification to Priya's phone]
```

---

## 11. Why Is the Payment Service "Last" in the Build Order?

From your `execution-system.md`:

> **Phase 8: Payment Service — "Sensitive, isolated, idempotent operations"**

It's last because it depends on everything else being stable:

1. **RabbitMQ** must exist (the trigger is the `trip.completed` event)
2. **Trip Service** must publish well-formed events (needs the trip lifecycle to be rock solid)
3. **Idempotency patterns** must be understood (Sprint G — concurrency/idempotency)
4. **State machine design** must be understood (you built one for trips already)

You've now built:
- Optimistic locking in Driver Service
- State machines in Trip Service
- Event-driven patterns with RabbitMQ (Phase 7)

**Payment Service is the capstone** — it uses every one of those patterns simultaneously.

---

## 12. Summary: Key Concepts and Where They Live

| Concept | Where It Appears | Why It Matters |
|---|---|---|
| **State Machine** | `Payment` entity: PENDING → COMPLETED / FAILED | Tracks where each payment is in its lifecycle; prevents acting on stale state |
| **Idempotency** | Unique constraint on `trip_id`, check-before-insert, idempotency key to gateway | Prevents double-charging on message re-delivery or retries |
| **Unique Constraint** | `@UniqueConstraint(columnNames = "trip_id")` on Payment table | Database-level guard — the last line of defence against duplicates |
| **Optimistic Locking** | `@Version` on Payment entity | Prevents two threads from simultaneously updating the same payment record |
| **Concurrency** | Two consumers racing on same event; `DataIntegrityViolationException` handled gracefully | Horizontal scaling means multiple instances can consume the same message |
| **@Transactional** | Split into two short transactions (PENDING insert + COMPLETED update) | Keeps DB locks short; gateway call happens between transactions |
| **Event-Driven Pattern** | Consumes `trip.completed`, publishes `payment.completed` | Loose coupling — Trip Service doesn't know Payment Service exists |
| **Mock Gateway** | `MockPaymentGateway implements PaymentGateway` | Strategy pattern — real gateway is a drop-in replacement later |
| **Fare Calculation** | Inside Payment Service, not Trip Service | Correct boundary — payment logic belongs in the payment domain |
| **Dead Letter Queue** | Messages that fail processing go to DLQ for inspection | Prevents poison messages from crashing the consumer in an infinite retry loop |

---

## Final Thoughts

The Payment Service is where **all your distributed systems knowledge comes together**:
- Transactions from Trip Service
- Optimistic locking from Driver Service
- Event-driven patterns from Phase 7
- Idempotency for distributed message delivery
- State machines for lifecycle management

Build it last because by then, these patterns will feel natural. You'll recognize where each one belongs and why.

---

**End of Document**
