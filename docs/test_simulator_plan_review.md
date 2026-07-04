# Test-Simulator Service — Plan Review & Architecture

> **Context:** Ride-Sharing Backend · Java 21 · Spring Boot 3 · Spring Cloud
> **Reviewer:** AI pair-programming session
> **Date:** July 04, 2026

---

## 1. Current System Snapshot (What the Simulator Will Target)

Before evaluating the plan, here's what exists to be tested:

| Layer | Component | Port | Status |
|---|---|---|---|
| Infrastructure | Config Server | 8888 | ✅ Live |
| Infrastructure | Eureka Server | 8761 | ✅ Live |
| Infrastructure | API Gateway (JWT) | 8080 | ✅ Live |
| Microservice | User Service | 8081 | ✅ Live |
| Microservice | Driver Service | 8082 | ✅ Live |
| Microservice | Trip Service | 8083 | ✅ Live |
| Microservice | Location Service | 8085 | ✅ Live |
| Microservice | Matching Service | TBD | ✅ Live |
| Messaging | RabbitMQ + Notification Service | 5672 | 🔜 Phase 7 |
| Payment | Payment Service | TBD | 🔜 Phase 8 |

**The core synchronous Feign chain the simulator must understand:**
```
Simulator → API Gateway (JWT) → Trip Service
                                    ├── User Service      (validate riderId)
                                    ├── Matching Service  (find + claim driver)
                                    │       ├── Location Service  (PostGIS nearby)
                                    │       └── Driver Service    (filter + claim)
                                    └── Driver Service    (release on complete)
```

> [!IMPORTANT]
> The simulator must be JWT-aware. Every request that enters via the gateway needs a valid `Authorization: Bearer <token>`. The simulator will need to pre-register test users and drivers, and store their tokens.

---

## 2. The Plan — Honest Assessment

### The Good Parts ✅

**1. The core concept is architecturally correct.**
A simulator that drives the system through its real API surface (not mocks) is the highest-value form of system testing for a distributed backend. You're describing **chaos engineering + load testing + integration testing** combined. That's exactly what tools like Netflix's Chaos Monkey, Gremlin, and k6 do at scale. You're proposing to build a lightweight version of this — a smart idea for learning.

**2. It fits the project perfectly as a follow-up phase.**
You've already validated the happy path manually. The simulator turns that manual validation into a repeatable, automated, programmable thing. This is the natural next step after "I know it works" → "I know it works *under these conditions*."

**3. The video-game metaphor is actually a design pattern.**
Framing it as "levels" or "scenarios" that get progressively harder is how professional chaos engineering works:
- Level 1: Everything works (baseline)
- Level 2: One service down
- Level 3: High load
- Level 4: Multiple failures simultaneously
- Level 5: Network partition scenarios

**4. The ML data pipeline vision is sound.**
Test reports → structured datasets → ML models is a legitimate real-world use case. Companies do this (SRE teams call it "anomaly detection"). The key is that the reports must be consistently structured from day 1 so the data is clean when the ML phase comes.

---

### The Gaps / Things to Think Through ⚠️

**1. The simulator needs to BE a system participant, not just a caller.**
It needs to simulate BOTH sides:
- **Rider side:** Call `POST /trips` through the gateway (JWT required)
- **Driver side:** Call `PUT /drivers/availability` to go ONLINE, respond to trip assignments, update location via `POST /locations/ping`

This means the simulator needs a pool of pre-seeded test users (both RIDER and DRIVER roles) with valid JWT tokens.

**2. "Random coordinates" needs to be geographic-aware.**
The PostGIS `ST_DWithin()` queries use real distance calculations. If you generate purely random lat/lon pairs globally, riders and drivers will never be within matching radius. You need a **bounding box** (e.g., Mumbai coordinates) and random points within that box. Both rider pickup and driver location must be within a sane radius of each other for matching to succeed.

**3. "Service is down" scenarios require Docker control.**
To simulate "location-service is down", you need to `docker stop location-service`. The simulator needs either:
- Docker API access (Docker SDK/socket) to stop/start containers programmatically, OR
- A separate "chaos agent" sidecar that controls Docker
This is more complex than it looks and should be Phase 2 of the simulator, not Phase 1.

**4. "Rain scenarios" need definition.**
"Rain scenario" in real ride-sharing means surge pricing + high demand. For your backend, that translates to:
- High concurrent `POST /trips` requests (configurable RPS/concurrency)
- Location service under write pressure (many driver pings)
- Matching service under contention (race conditions between trips claiming the same driver)
This is **load testing**, which you implement with configurable thread pools or async HTTP clients, not "rain" as a concept the backend understands.

**5. The test reports need a schema from Day 1.**
Don't generate free-form text reports. Design a structured schema (JSON) for every test run:
```json
{
  "scenario": "high_surge",
  "startTime": "...",
  "endTime": "...",
  "tripsRequested": 100,
  "tripsMatched": 87,
  "tripsCancelled": 13,
  "matchLatencyP50ms": 234,
  "matchLatencyP95ms": 891,
  "matchLatencyP99ms": 1423,
  "servicesDown": [],
  "errorsByType": {...},
  "driverUtilization": 0.73
}
```
This schema becomes the ML training data later. If reports are inconsistent across runs, the ML pipeline is broken before it starts.

---

## 3. Proposed Simulator Architecture

### Service Identity

```
microservices/
├── user-service/
├── driver-service/
├── trip-service/
├── location-service/
├── matching-service/
├── notification-service/     ← Phase 7 (in progress)
├── payment-service/          ← Phase 8
└── simulator-service/        ← NEW — Phase 12 (after project complete)
```

> [!NOTE]
> The simulator registers with Eureka and Config Server just like every other service. It has its own identity as `simulatorservice`. This lets it call other services through the same Feign/discovery path the real system uses.

---

### Simulator Internal Architecture

```
simulator-service/
├── config/
│   └── SimulatorConfig.java          ← Scenario registry, thread pool config
├── scenario/
│   ├── Scenario.java                 ← Interface: setup(), run(), teardown()
│   ├── BaselineScenario.java         ← Happy path, no failures
│   ├── HighSurgeScenario.java        ← Concurrent trip requests
│   ├── ServiceDownScenario.java      ← One service stopped (Phase 2)
│   ├── FaultInjectionScenario.java   ← Timeouts, bad payloads (Phase 2)
│   └── RainScenario.java             ← High demand + location pressure
├── actor/
│   ├── RiderActor.java               ← Simulates a rider (calls /trips, JWT)
│   ├── DriverActor.java              ← Simulates a driver (location pings, availability)
│   └── ActorPool.java                ← Manages N riders + M drivers
├── generator/
│   ├── CoordinateGenerator.java      ← Bounded random lat/lon (e.g., Mumbai)
│   └── ScenarioDataGenerator.java    ← Pre-seeds users/drivers via API
├── report/
│   ├── SimulationReport.java         ← Structured JSON schema
│   ├── MetricsCollector.java         ← Latency, success rate, error types
│   └── ReportExporter.java           ← Write to file / future: S3 / DB
└── SimulatorServiceApplication.java
```

---

### Phase Plan for Simulator

#### Phase 12-A: Foundation (MVP)
- Pre-seed test riders and drivers via User Service + Driver Service APIs
- Generate bounded random coordinates
- Run `BaselineScenario`: N riders request trips sequentially, verify all match
- Collect basic metrics: match rate, latency, error count
- Export structured JSON report

#### Phase 12-B: Load Testing
- Concurrent trip requests (configurable thread pool)
- `HighSurgeScenario`: 50 simultaneous trip requests, measure:
  - How many matched vs cancelled
  - Driver contention (409 Conflicts on `/claim`)
  - Latency distribution (P50/P95/P99)
- `RainScenario`: High surge + rapid driver location updates

#### Phase 12-C: Chaos (Requires Docker control)
- `ServiceDownScenario`: Stop one service mid-simulation, observe degradation
- `FaultInjectionScenario`: Inject bad JWT tokens, invalid coordinates, missing fields
- Measure: Does the system return correct error codes? Does it recover when service restarts?

#### Phase 12-D: Reporting & Data Pipeline
- Structured JSON reports per scenario run
- Report indexing (all runs in a session)
- Export to CSV for ML ingestion
- Comparison across runs (regression detection)

---

## 4. Scenario Breakdown

| Scenario | Simulates | Key Metrics | Chaos Required |
|---|---|---|---|
| **Baseline** | 10 riders, 20 drivers, sequential | Match rate, latency | No |
| **High Surge** | 100 concurrent trip requests | P95/P99 latency, queue depth | No |
| **Rain** | Surge + rapid driver location pings | Location write throughput, match rate | No |
| **Driver Shortage** | More riders than drivers in radius | Cancelled trip %, retry behavior | No |
| **Service Down: Location** | PostGIS unavailable | Matching failure rate, error propagation | Yes (Docker) |
| **Service Down: Matching** | Matching service stopped | Trip creation behavior (cancelled?) | Yes (Docker) |
| **Fault Injection: Bad JWT** | Expired/invalid tokens | 401 rate, gateway behavior | No (code-level) |
| **Fault Injection: Bad Coords** | Out-of-range lat/lon | 400/409 rates, validation behavior | No (code-level) |
| **Fake Load** | Hundreds of driver location pings | Location service throughput ceiling | No |
| **Driver Churn** | Drivers rapidly toggling ONLINE/OFFLINE | Race condition stress on claim | No |

---

## 5. Where It Fits the Execution Plan

| Phase | What | Status |
|---|---|---|
| 7 | RabbitMQ + Notification Service | 🔜 Do this first |
| 8 | Payment Service | 🔜 Do this second |
| 9/10 | Docker + AWS (partially done) | 🔜 Complete |
| 11 | Portfolio packaging | 🔜 Document |
| **12-A** | **Simulator MVP (baseline, seeding, reports)** | **📋 Planned** |
| **12-B** | **Load scenarios (surge, rain, shortage)** | **📋 Planned** |
| **12-C** | **Chaos scenarios (service down, fault injection)** | **📋 Planned** |
| **12-D** | **Structured data pipeline for ML** | **📋 Far future** |

> [!IMPORTANT]
> Don't build the simulator until Phases 7 and 8 are done. The simulator needs the full system — RabbitMQ flows, payment events, notification backlog — to generate meaningful test data. Testing an incomplete system produces incomplete (misleading) reports.

---

## 6. ML Data Pipeline — Far Future Vision

When the simulator generates enough structured reports, here's what the ML layer could do:

```
Simulator Runs (JSON reports)
    ↓
Data Pipeline (Python/Spark)
    ↓ Feature Engineering
ML Training Data
    ├── Anomaly Detection — "This match latency is abnormal for this load level"
    ├── Capacity Prediction — "At X concurrent trips, expect Y% cancel rate"
    ├── Failure Prediction — "These patterns precede a service cascade failure"
    └── Optimization — "Expanding radius from 3km→5km reduces cancel rate by 12%"
```

**What makes this viable:**
- Reports are consistently structured (schema defined from Day 1)
- Each run has labeled metadata: scenario name, services down, load level, time
- Labels + metrics = supervised learning dataset

**What you'd need to add later:**
- Historical report storage (PostgreSQL or S3)
- A data pipeline service (Python, pandas/polars)
- ML model training (scikit-learn for starters, then TensorFlow/PyTorch)
- A model serving API that takes "current system state" and predicts "expected behavior"

---

## 7. Tech Stack Recommendation for Simulator

| Choice | Recommendation | Why |
|---|---|---|
| Language | **Java (Spring Boot)** | Same stack — reuses Feign clients, Eureka registration, Config Server patterns you already know |
| HTTP client | **Feign** (internal) + **WebClient** (for load scenarios) | Feign for clean service calls; WebClient for concurrent/reactive load generation |
| Thread model | **Virtual Threads (Java 21)** | Java 21 is already your baseline; virtual threads make concurrent simulation trivial |
| Report format | **JSON** (Jackson) | Machine-readable, schema-enforced, directly ingestible by ML pipeline |
| Docker control | **Docker Java SDK** (`com.github.docker-java`) | Phase 12-C only; lets simulator start/stop containers programmatically |
| Coordinate generation | **Custom `CoordinateGenerator`** | Simple bounded random with configurable city bounding box |
| Metrics | **Micrometer** (already in Spring Boot) | Reuse existing metrics infrastructure; export to Prometheus later |

> [!TIP]
> Consider keeping the simulator as a **standalone CLI tool** rather than a long-running service for Phase 12-A/B. A Spring Boot application with `CommandLineRunner` is simpler to invoke (`java -jar simulator.jar --scenario=baseline`) and easier to script. Promote it to a full service (with REST API to trigger scenarios) in Phase 12-C.

---

## 8. Summary: The Plan Is Solid. Here's the Priority Order.

**What's great:**
- Conceptually correct — automated scenario-driven system testing is real, valuable, and not trivial
- Scalability path is well-thought-out (more scenarios → harder → ML)
- "Video game level" framing maps cleanly to chaos engineering phases

**What needs refinement:**
1. Simulator must be JWT-aware (pre-seed real users, store tokens)
2. Coordinates must be geographically bounded (not global random)
3. "Service down" scenarios need Docker API — keep this Phase 12-C, not 12-A
4. Define the report JSON schema before writing any scenario code
5. Don't start it until the full system (Phases 7 + 8) is complete

**Priority:**
```
Right now:      Phase 7 (RabbitMQ + Notification) → Phase 8 (Payment)
Then:           Phase 11 (Portfolio packaging)
Then:           Phase 12-A (Simulator MVP, baseline scenarios)
Later:          Phase 12-B (Load scenarios)
After that:     Phase 12-C (Chaos — needs Docker control)
Far future:     Phase 12-D (ML data pipeline)
```
