# Ride-Sharing Backend — AI Agent Onboarding Document

> **Purpose:** This document serves as a high-level "blackbox" architectural overview of the ride-sharing backend system. It is designed to be fed into LLM agents to provide immediate context on how the system is structured, what has been implemented so far, and the key architectural decisions made.

---

## 1. System Architecture

The project is a Spring Boot microservices architecture for a ride-sharing application. It utilizes the following stack:
- **Java 17 & Spring Boot 3.x**
- **Spring Cloud** (Gateway, Config Server, Eureka, OpenFeign)
- **Database:** PostgreSQL (with PostGIS extension for geospatial queries)
- **Build Tool:** Maven

### Microservices Landscape

1. **`config-server` (Port 8888):** Centralized configuration management.
2. **`eurekaserver` (Port 8761):** Service registry and discovery.
3. **`gatewayserver` (Port 8080):** API Gateway and central authentication hub. Validates JWTs before forwarding requests.
4. **`user-service` (Port 8081):** Manages riders, drivers (as base users), and authentication (JWT generation).
5. **`driver-service` (Port 8082):** Manages driver-specific profiles, vehicles, operational status (ACTIVE/SUSPENDED), and availability (ONLINE/OFFLINE/BUSY).
6. **`trip-service` (Port 8083):** Manages the ride lifecycle (REQUESTED → MATCHED → IN_PROGRESS → COMPLETED/CANCELLED). 
7. **`location-service` (Port 8084):** Uses PostGIS to store real-time driver coordinates and query nearby drivers using spatial SQL (`ST_DWithin`, `ST_Distance`).
8. **`matching-service` (Port 8085):** Orchestrates the algorithm to pair a rider with the nearest available driver.

---

## 2. Key Flows & Implementation Details

### A. Authentication
Authentication is centralized at the **API Gateway**.
- `user-service` issues a JWT on login.
- `gatewayserver` intercepts all requests, validates the JWT signature, and passes valid requests to downstream microservices.
- Downstream services trust the Gateway and do not perform JWT validation.

### B. Inter-Service Communication
Services communicate synchronously via **OpenFeign**.
- `trip-service` → `user-service` (Validate rider exists before trip creation)
- `trip-service` → `matching-service` (Trigger matching algorithm)
- `matching-service` → `location-service` (Find nearest drivers)
- `matching-service` → `driver-service` (Filter driver status/availability & claim driver)

### C. The Matching Algorithm (MVP)
The matching logic is orchestrated by `matching-service`:
1. **Iterative Radius Expansion:** Starts at 3km and progressively expands up to 20km (`3000, 5000, 8000, 12000, 20000`).
2. **PostGIS Sort:** `location-service` returns drivers sorted by `ST_Distance`.
3. **Availability Filter:** Driver IDs are batch-sent to `driver-service` (`POST /drivers/available`) to retain only `ACTIVE` and `ONLINE` drivers.
4. **Atomic Claim & Race Condition Handling:** The system walks the ordered candidate list and calls `POST /drivers/{id}/claim`. This method uses JPA Optimistic Locking (`@Version` on the `Driver` entity). If multiple trips try to claim the same driver concurrently, only one succeeds; the other gets a `409 Conflict` (converted to a `FeignException.Conflict`) and seamlessly retries with the next nearest candidate.

### D. Cancellation Logic
The `trip-service` owns the cancellation lifecycle via `POST /trips/{id}/cancel`.
- **REQUESTED -> CANCELLED:** Simple state update.
- **MATCHED -> CANCELLED:** The trip must release the driver. `trip-service` calls `POST /drivers/{id}/release` on `driver-service` to atomically flip the driver from `BUSY` back to `ONLINE`, preventing driver starvation.

---

## 3. Database Design

All microservices use PostgreSQL. Notably:
- **`Trip` Entity:** Uses custom `Coordinate` DTOs containing `Double latitude` and `Double longitude` instead of string addresses.
- **`Driver` Entity:** Uses `@Version` for optimistic locking to solve TOCTOU (Time-of-Check, Time-of-Use) concurrency bugs during the matching phase.
- **`DriverLocation` Entity:** Uses `org.locationtech.jts.geom.Point` mapped to PostGIS `geometry` types.

---

## 4. Pending / Future Scope

If you are an AI agent onboarding to extend this project, these are the natural next steps:
1. **Async Matching (Phase 5):** The current `TripService.createTrip` blocks HTTP while `matching-service` loops through radii. This should be decoupled using Kafka or RabbitMQ.
2. **Driver Acceptance Flow (Phase 2):** Implement WebSocket/SSE so drivers get a 30-second TTL notification to accept/decline a ride, rather than being force-assigned.
3. **Surge Pricing:** Adding logic to calculate surge multipliers based on driver supply vs active requests in the matching phase.
4. **Payment Gateway Integration:** Stripe or similar for fare calculation and holds.
