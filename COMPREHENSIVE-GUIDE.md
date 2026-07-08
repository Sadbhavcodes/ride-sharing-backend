# Ride-Sharing Backend — The Complete Guide

> **The definitive documentation for understanding this production-grade microservices system**

**Quick Navigation:**
- 📘 [Full Master Documentation](./MASTER-DOCUMENTATION.md) — Complete system architecture (900+ lines)
- 🔧 [API Reference](./docs/api-reference.md) — All endpoints with request/response examples
- 🏗️ [Architecture Blueprint](./docs/ride-sharing-onboarding-blueprint.md) — Design philosophy and decisions
- 📊 [Build Progress](./docs/build-progress.md) — What's built, what's next
- 🔄 [Trip Workflow](./docs/trip-workflow-and-rabbitmq-plan.md) — Complete flow from request to completion
- 💰 [Payment Service Deep Dive](./docs/payment-service-deep-dive.md) — Payment processing explained
- ⚡ [Transactions & Race Conditions](./docs/transactions-race-conditions-deep-dive.md) — Distributed systems patterns
- 🎯 [Execution System](./docs/execution-system.md) — Build phases and learning roadmap

---

## What This Project Is

A **production-grade ride-sharing backend** implementing real-world distributed systems patterns:

✅ 8 microservices with proper boundaries  
✅ JWT auth centralized at API Gateway  
✅ Service discovery with Eureka  
✅ PostGIS for geospatial queries  
✅ Optimistic locking prevents race conditions  
✅ Idempotency prevents double-charging  
✅ Event-driven architecture with RabbitMQ  
✅ Dockerized and deployed to AWS  

**Stack:** Java 21 · Spring Boot 3 · PostgreSQL · PostGIS · Spring Cloud · RabbitMQ · Docker · AWS

---

## For Different Audiences

### 👨‍💻 Software Engineers

**Want to understand microservices?** Start here:
1. Read [Architecture Blueprint](./docs/ride-sharing-onboarding-blueprint.md) — Understand WHY before HOW
2. Review [Trip Workflow](./docs/trip-workflow-and-rabbitmq-plan.md) — See how services orchestrate
3. Study [Transactions & Race Conditions](./docs/transactions-race-conditions-deep-dive.md) — Learn critical patterns
4. Explore [API Reference](./docs/api-reference.md) — See actual implementation

**Key Patterns to Learn:**
- Optimistic locking with `@Version` (Driver claim)
- Idempotency with unique constraints (Payment processing)
- Event-driven decoupling (Trip → Payment via RabbitMQ)
- State machines (Trip lifecycle, Payment status)
- Split transactions (Short DB → Long external call → Short DB)

### 🤖 AI Agents / LLMs

**Need system context?** Read in this order:
1. [Master Documentation](./MASTER-DOCUMENTATION.md) — Complete architecture overview
2. [AI Agent Onboarding](./docs/backend_ai_agent_onboarding.md) — System blackbox view
3. [Build Progress](./docs/build-progress.md) — Current state and decisions log

**Critical Context:**
- Authentication is centralized at Gateway (services trust the Gateway)
- Feign calls bypass Gateway (internal, no JWT needed)
- Each service owns its database (no shared schemas)
- Matching uses expanding radius (3km → 5km → 8km → 12km → 20km)
- Driver claim is atomic with optimistic locking
- Payment processing is idempotent with 3 layers of protection

### 🎓 Students / Learners

**Want to learn by doing?** Follow this path:
1. Review [Technology Dependency Graph](./docs/tech-dependency-graph.md) — What to learn first
2. Study [Execution System](./docs/execution-system.md) — Build order and learning sprints
3. Read [Architecture Blueprint](./docs/ride-sharing-onboarding-blueprint.md) — Design principles
4. Clone the repo and follow the build phases

**Learning Outcomes:**
- Service boundary design (DDD-lite)
- Synchronous vs asynchronous communication
- Concurrency control in distributed systems
- Database per service pattern
- API Gateway pattern
- Service discovery and config management
- Event-driven architecture

### 👔 Recruiters / Hiring Managers

**Evaluating technical depth?** Check:
- [Build Progress](./docs/build-progress.md) — See what's actually implemented
- [Key Decisions](./docs/backend_ai_agent_onboarding.md#4-key-flows--implementation-details) — Architecture choices
- [API Reference](./docs/api-reference.md) — Production-ready endpoints
- [Transactions Deep Dive](./docs/transactions-race-conditions-deep-dive.md) — Advanced patterns

**Technical Highlights:**
- ✅ Proper service boundaries (not a distributed monolith)
- ✅ Handles race conditions correctly (optimistic locking)
- ✅ Idempotent payment processing (prevents double-charging)
- ✅ State machines with validation
- ✅ Async event-driven flows
- ✅ Containerized with Docker
- ✅ Deployed to AWS (ECR + EC2)

---

## System Architecture At A Glance

```
CLIENT APPS
    ↓
API GATEWAY (port 8080) ← JWT validation here
    ↓
┌────────────────┬─────────────────┬──────────────────┐
│                │                 │                  │
USER SERVICE   DRIVER SERVICE   TRIP SERVICE    LOCATION SERVICE
(port 8081)    (port 8082)      (port 8083)     (port 8084)
    │              │                  │               │
rideshare_users  rideshare_drivers  rideshare_trips  rideshare_locations
                                     │               (PostGIS)
                                     ↓
                              MATCHING SERVICE ← orchestrates matching
                              (port 8085)
                                     ↓
                              ┌─────┴─────┐
                              ↓           ↓
                         RABBITMQ    PAYMENT SERVICE
                         (broker)    (port 8087)
                              │           │
                              └───→ NOTIFICATION SERVICE
                                    (port 8086)
```

**Key Flows:**
1. **Authentication:** Client → Gateway (JWT check) → Service
2. **Trip Creation:** Trip Service → User Service (validate) → Matching Service → Location + Driver Services
3. **Trip Completion:** Trip Service → RabbitMQ → Payment Service + Notification Service
4. **Payment:** Payment Service → Mock Gateway → Update DB → Publish event

---

## Critical Patterns Explained

### 🔒 Optimistic Locking (Driver Claim)

**Problem:** Two trips try to claim the same driver simultaneously.

**Solution:**
```java
@Entity
public class Driver {
    @Version  // ← Magic happens here
    private Long version;
}
```

**How it works:**
```sql
-- Thread A
UPDATE drivers SET availability='BUSY', version=6
WHERE id=7 AND version=5;  -- ✅ 1 row updated

-- Thread B (same time)
UPDATE drivers SET availability='BUSY', version=6
WHERE id=7 AND version=5;  -- ❌ 0 rows updated (version already 6)
```

Only one thread succeeds. The other catches `OptimisticLockingFailureException` and tries the next driver.

---

### 🔁 Idempotency (Payment Processing)

**Problem:** RabbitMQ redelivers message → Rider charged twice.

**Solution (3 layers):**

1. **Database unique constraint:**
   ```sql
   CONSTRAINT uq_payments_trip_id UNIQUE (trip_id)
   ```

2. **Application check:**
   ```java
   if (paymentRepository.existsByTripId(tripId)) {
       return; // Already processed
   }
   ```

3. **Gateway idempotency key:**
   ```java
   gateway.charge(idempotencyKey: "trip-101", ...);
   // Calling twice with same key returns same result
   ```

All three layers ensure: **Message delivered 5 times = Payment charged once.**

---

### 🔄 State Machines (Trip Lifecycle)

**Valid transitions:**
```
REQUESTED → MATCHED → IN_PROGRESS → COMPLETED
     ↓           ↓           ↓
  CANCELLED   CANCELLED   CANCELLED
```

**Enforced in code:**
```java
public void updateTripStatus(Long tripId, TripStatus newStatus) {
    Trip trip = tripRepository.findById(tripId).orElseThrow();
    
    if (!isValidTransition(trip.getStatus(), newStatus)) {
        throw new IllegalStateException("Invalid transition");
    }
    
    trip.setStatus(newStatus);
    tripRepository.save(trip);
}
```

---

### ⚡ Split Transactions (Payment Service)

**Bad:** Long transaction holding DB locks during external HTTP call.

**Good:** Split into two short transactions:

```java
// Transaction 1: Quick DB write
Payment payment = createPendingPayment(event);  // COMMIT

// External call (no transaction)
GatewayResponse response = gateway.charge(...);  // 500ms

// Transaction 2: Quick DB update
finalizePayment(payment.getId(), response);  // COMMIT
```

**Result:** DB locks held for 10ms instead of 510ms.

---

## Quick Start Guide

### Prerequisites
- Java 21
- PostgreSQL 15+ with PostGIS
- Maven 3.9+
- Docker (for RabbitMQ and containerization)

### Run Locally

1. **Start infrastructure:**
   ```bash
   docker run -d --name postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:15
   docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
   ```

2. **Start services in order:**
   ```bash
   # 1. Config Server
   cd infrastructure/config-server
   mvn spring-boot:run

   # 2. Eureka Server
   cd infrastructure/eureka-server
   mvn spring-boot:run

   # 3. Services (in any order)
   cd microservices/user-service
   mvn spring-boot:run
   # ... repeat for other services

   # 4. API Gateway (last)
   cd infrastructure/gateway-server
   mvn spring-boot:run
   ```

3. **Verify:**
   - Eureka Dashboard: http://localhost:8761
   - RabbitMQ UI: http://localhost:15672 (guest/guest)
   - API Gateway: http://localhost:8080

### Run with Docker Compose

```bash
docker-compose up --build
```

All services start automatically in correct order.

---

## API Quick Reference

**Base URL:** `http://localhost:8080` (all requests via Gateway)

### Authentication
```bash
# Register
POST /auth/register
{"username":"john","email":"john@example.com","password":"secret","phoneNumber":"+1234","role":"RIDER"}

# Login
POST /auth/login
{"email":"john@example.com","password":"secret"}
# Returns: {"token":"eyJhbGci..."}

# Use token in all subsequent requests
Authorization: Bearer eyJhbGci...
```

### Create Trip
```bash
POST /trips
{
  "riderId": 1,
  "pickup": {"latitude": 28.6139, "longitude": 77.2090},
  "destination": {"latitude": 28.5562, "longitude": 77.1000}
}
```

**Full API documentation:** [api-reference.md](./docs/api-reference.md)

---

## Project Structure

```
ride-sharing-backend/
├── infrastructure/
│   ├── config-server/        # Centralized configuration
│   ├── eureka-server/        # Service discovery
│   └── gateway-server/       # API Gateway + JWT validation
├── microservices/
│   ├── user-service/         # Authentication, user profiles
│   ├── driver-service/       # Driver profiles, vehicles, atomic claim
│   ├── trip-service/         # Trip lifecycle, state machine
│   ├── location-service/     # PostGIS spatial queries
│   ├── matching-service/     # Driver matching algorithm
│   ├── payment-service/      # Fare calculation, payment processing
│   └── notification-service/ # Async notifications
├── docs/                     # All documentation
├── docker-compose.yml        # Local orchestration
└── docker-compose.prod.yml   # Production (pulls from ECR)
```

---

## Key Insights

### Why This Architecture?

**Not "because microservices are trendy"** — but because:

1. **Location Service** has high-frequency writes (GPS pings) — needs independent scaling
2. **Matching Service** is CPU-heavy — benefits from scaling during peak hours
3. **Payment Service** needs strict isolation and different deployment cadence
4. **Notification Service** can degrade gracefully without breaking core flows

**If one service is down, others keep working.** That's the value proposition.

### What Makes It Production-Grade?

1. **Handles race conditions** — Two trips can't claim same driver (optimistic locking)
2. **Idempotent operations** — Retries don't cause duplicate payments
3. **Proper error handling** — 24 failure scenarios mapped and handled
4. **State validation** — Invalid trip transitions rejected
5. **Audit trail** — PENDING state tracks in-flight operations
6. **Observability ready** — Structured logging, health endpoints

### What's Not Built Yet

⏳ Circuit breakers (Resilience4j) — Phase 11  
⏳ Distributed tracing (Sleuth + Zipkin) — Phase 11  
⏳ Kubernetes deployment — Future work  
⏳ Prometheus + Grafana — Phase 11  
⏳ Real Stripe integration — Currently mock gateway  

**These are documented as "Future Work" — not skipped, but prioritized correctly.**

---

## Learning Resources

### Start Here (Beginner → Intermediate)
1. [Architecture Blueprint](./docs/ride-sharing-onboarding-blueprint.md) — Read this first
2. [Technology Dependency Graph](./docs/tech-dependency-graph.md) — What to learn in what order
3. [Execution System](./docs/execution-system.md) — Build phases and learning sprints

### Deep Dives (Intermediate → Advanced)
1. [Transactions & Race Conditions](./docs/transactions-race-conditions-deep-dive.md) — Critical distributed systems patterns
2. [Payment Service Deep Dive](./docs/payment-service-deep-dive.md) — Idempotency explained
3. [Trip Workflow](./docs/trip-workflow-and-rabbitmq-plan.md) — Complete orchestration flow

### Reference (As Needed)
1. [API Reference](./docs/api-reference.md) — All endpoints with examples
2. [Build Progress](./docs/build-progress.md) — What's implemented, what's next
3. [AI Agent Onboarding](./docs/backend_ai_agent_onboarding.md) — Blackbox system view

---

**Last Updated:** July 2026  
**Status:** Phase 7 in progress (RabbitMQ + Notification Service)  
**Next Milestone:** Phase 8 (Payment Service with idempotent processing)
