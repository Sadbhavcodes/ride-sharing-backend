# Ride-Sharing Backend â€” Build Progress Log

> **Project:** Ride-Sharing Microservices Backend
> **Stack:** Java 21 Â· Spring Boot 3 Â· PostgreSQL Â· Spring Cloud (Config, Eureka, Gateway)
> **Last Updated:** June 25, 2026
> **Repo:** `e:\Projects\ride-sharing-backend`

---

## Overview

This document tracks every completed phase, sprint, and architectural decision from project start to the current state. It maps work done against the execution plan defined in `execution-system.md`.

---

## Current Status Snapshot

| Layer | Component | Status |
|---|---|---|
| Infrastructure | Config Server | âœ… Complete |
| Infrastructure | Eureka Server | âœ… Complete |
| Infrastructure | API Gateway (JWT) | âœ… Complete |
| Microservice | User Service | âœ… Complete |
| Microservice | Driver Service | âœ… Complete |
| Microservice | Trip Service | âœ… Complete |
| Database | PostgreSQL (x3) | âœ… Complete |
| Cross-service | Feign (trip â†’ user) | âœ… Working |
| Cross-service | JWT centralized at gateway | âœ… Complete |
| Next | Location Service | ðŸ”œ Phase 5 |
| Next | Matching Service | ðŸ”œ Phase 6 |

---

## Phase 0 â€” Foundations âœ… Complete

**Theme:** Environment setup, PostgreSQL, JWT fundamentals

### What was done

- Monorepo structure decided: single Git repo, services as independent Maven projects under `/microservices/`, infrastructure under `/infrastructure/`
- Local PostgreSQL installed and running
- Three separate PostgreSQL databases created:
  - `rideshare_users`
  - `rideshare_drivers`
  - `rideshare_trips`
- JPA/Hibernate wired to each database via Spring Data JPA
- JWT understanding established (structure, signing, validation) before writing production code

### Repo structure established

```
ride-sharing-backend/
â”œâ”€â”€ microservices/
â”‚   â”œâ”€â”€ user-service/
â”‚   â”œâ”€â”€ driver-service/
â”‚   â””â”€â”€ trip-service/
â”œâ”€â”€ infrastructure/
â”‚   â”œâ”€â”€ config-server/
â”‚   â”œâ”€â”€ eureka-server/
â”‚   â””â”€â”€ gateway-server/
â””â”€â”€ docs/
```

### Exit criteria met
- âœ… Entities can be persisted and retrieved via JPA
- âœ… JWT can be issued and validated
- âœ… Repo skeleton committed to Git

---

## Phase 1 â€” Core Services âœ… Complete

**Theme:** Business logic â€” three independently runnable services, each with own DB

### Sprint A â€” PostgreSQL + JPA âœ… Done

- Entities, repositories, `@GeneratedValue`, `@Enumerated`
- Spring Data JPA `findById`, `findByEmail`, `existsByEmail`, custom queries
- `ddl-auto: update` for local development
- PostgreSQL type mapping (String enums via `EnumType.STRING`)

### Sprint B â€” JWT Auth âœ… Done

- Token issuance: `user-service` generates JWTs on login using JJWT library
- Token signing: HMAC-SHA256 with shared secret
- BCrypt password hashing via `PasswordEncoder`
- Auth filter chain (later centralized to gateway â€” see Phase 4)

---

### 1.1 User Service âœ… Complete

**Port:** 8081 | **DB:** `rideshare_users`

**Entities:**
- `User` â€” id, username, email, password (BCrypt hashed), phoneNumber, role (RIDER/DRIVER)

**Endpoints built:**
| Method | Path | Description |
|---|---|---|
| `POST` | `/auth/register` | Register new user |
| `POST` | `/auth/login` | Login, receive JWT |
| `GET` | `/users/{id}` | Get user by ID |
| `PUT` | `/users/{id}` | Update username/email |
| `GET` | `/users/by-email/{email}` | Look up user by email |

**Services built:**
- `AuthService` â€” register (email uniqueness check, password encode, save) + login (password verify, token generate)
- `UserServices` â€” getUser, updateUser, getUserByEmail
- `JwtService` â€” `generateToken(email)` only (validation moved to gateway)

**Architecture decisions:**
- User service is the **sole JWT issuer** in the system
- `SecurityConfig` simplified to `permitAll()` â€” gateway handles all auth enforcement
- `JwtAuthenticationFilter` and `CustomUserDetailService` removed (were validating tokens redundantly)
- `JwtService` keeps only `generateToken()` â€” `extractEmail()` and `isTokenValid()` removed

---

### 1.2 Driver Service âœ… Complete

**Port:** 8082 | **DB:** `rideshare_drivers`

**Entities:**
- `Driver` â€” id, userId (FK to user-service by ID reference), vehicleId, availability (ONLINE/OFFLINE/BUSY), status (PENDING/ACTIVE/SUSPENDED), rating
- `Vehicle` â€” id, plateNumber, make, model, color, verificationStatus (PENDING/VERIFIED/REJECTED)

**Endpoints built:**
| Method | Path | Description |
|---|---|---|
| `POST` | `/drivers` | Create driver profile (links userId + vehicleId) |
| `GET` | `/drivers/{id}` | Get driver by driver ID |
| `GET` | `/drivers/{id}/availability` | Get lean availability status |
| `GET` | `/drivers/users/{userId}` | Get driver by user ID |
| `PUT` | `/drivers/status` | Update driver account status |
| `PUT` | `/drivers/availability` | Toggle online/offline/busy |
| `POST` | `/vehicles` | Register a vehicle |
| `GET` | `/vehicles/{id}` | Get vehicle by ID |
| `PUT` | `/vehicles/{id}` | Update vehicle verification status |

**DTOs:**
- `CreateDriverRequest`, `UpdateDriverStatusRequest`, `UpdateDriverAvailabilityRequest`
- `DriverAvailabilityResponse` â€” lean DTO (driverId + availability only)

**Exception handling:**
- `GlobalExceptionHandler` with `ErrorResponse` (message, status, timestamp)
- `DriverNotFoundException` â†’ 404
- `IllegalStateException` â†’ 409

**Architecture decisions:**
- Driver service does **not** own user data â€” references `userId` by Long ID only, never calls user-service
- JWT validation code fully removed (was redundant once gateway is in place)
- No Spring Security dependency â€” zero auth overhead in this service

---

### 1.3 Trip Service âœ… Complete

**Port:** 8083 | **DB:** `rideshare_trips`

**Entities:**
- `Trip` â€” id, riderId, driverId (nullable), pickupLocation, dropLocation, status (TripStatus enum), createdAt, updatedAt

**Endpoints built:**
| Method | Path | Description |
|---|---|---|
| `POST` | `/trips` | Create trip (validates riderId via Feign) |
| `GET` | `/trips/{id}` | Get trip by ID |
| `GET` | `/trips/rider/{riderId}` | All trips for a rider |
| `GET` | `/trips/driver/{driverId}` | All trips for a driver |
| `PATCH` | `/trips/{id}/assign-driver` | Assign driver to trip |
| `PATCH` | `/trips/{id}/status` | Advance trip lifecycle status |

**Trip lifecycle state machine:**
```
REQUESTED â†’ MATCHED â†’ IN_PROGRESS â†’ COMPLETED
     â†“           â†“
  CANCELLED   CANCELLED
```

**Cross-service integration:**
- `UserFeignClient` â€” Feign call to `user-service` to validate `riderId` on trip creation
- `FeignClientErrorDecoder` â€” intercepts 404 from user-service, throws `RiderNotFoundException`
- Service discovery via Eureka: `@FeignClient(name = "userservice")` â€” no hardcoded URLs

**Exception handling:**
- `TripNotFoundException` â†’ 404
- `RiderNotFoundException` â†’ 404 (invalid riderId on create)
- `IllegalStateException` â†’ 409 (invalid state transition, driver already assigned)
- Generic `Exception` catch-all â†’ 500

**Bug fixes applied (June 25, 2026):**
- `createdAt`/`updatedAt` were null â€” fixed with `@PrePersist` / `@PreUpdate` JPA lifecycle hooks
- Invalid `riderId` returned 500 â€” fixed with `FeignClientErrorDecoder` + `RiderNotFoundException`

---

## Phase 2 â€” Config Server âœ… Complete

**Theme:** Centralized configuration for all services

### Sprint C â€” Spring Cloud Config âœ… Done

**What was done:**
- Spring Cloud Config Server set up at port `8888`
- All services pull config on startup via `spring.config.import: configserver:http://localhost:8888`
- Per-service config files in `/infrastructure/config-server/src/main/resources/config/`:

| File | Service | Key configs |
|---|---|---|
| `userservice.yaml` | User Service | port, DB URL, JWT secret + expiration |
| `driverservice.yaml` | Driver Service | port, DB URL, JWT secret |
| `tripservice.yaml` | Trip Service | port, DB URL |
| `gatewayserver.yaml` | Gateway | port, routes, JWT secret + expiration |
| `eurekaserver.yaml` | Eureka | port, self-registration off |
| `application.yaml` | Shared | Eureka client defaults |

**Shared JWT secret** â€” `myVeryStrongSecretKeyForRideShareApplication2025` â€” single source of truth via Config Server, same value used by user-service (to sign) and gateway (to verify).

### Exit criteria met
- âœ… All services pull their config from Config Server on startup
- âœ… JWT secret lives in one place â€” config server â€” not duplicated in individual `application.yaml` files

---

## Phase 3 â€” Service Discovery (Eureka) âœ… Complete

**Theme:** Dynamic service registration and discovery

### Sprint D â€” Eureka âœ… Done

**What was done:**
- Eureka Server running at port `8761`
- All services register themselves: `userservice`, `driverservice`, `tripservice`, `gatewayserver`
- Feign client in trip-service uses `@FeignClient(name = "userservice")` â€” resolves to actual instance via Eureka, no hardcoded IPs

**Startup order (dependency chain):**
```
Config Server (8888)
  â†’ Eureka Server (8761)
    â†’ User Service (8081)
    â†’ Driver Service (8082)
    â†’ Trip Service (8083)
      â†’ API Gateway (8080)
```

### Exit criteria met
- âœ… All 4 services visible on Eureka dashboard at `http://localhost:8761`
- âœ… Trip service resolves user-service by name, not hardcoded URL
- âœ… Gateway routes by logical service name via `lb://USERSERVICE` etc.

---

## Phase 4 â€” API Gateway (JWT Centralized) âœ… Complete

**Theme:** Single entry point + centralized authentication

### Sprint E â€” Spring Cloud Gateway âœ… Done

**What was done:**
- API Gateway built using Spring Cloud Gateway (WebFlux/reactive)
- Runs at port `8080` â€” single entry point for all client traffic
- Route definitions in `gatewayserver.yaml`:

```yaml
routes:
  - id: userservice
    uri: lb://USERSERVICE
    predicates:
      - Path=/users/**, /auth/**

  - id: driverservice
    uri: lb://DRIVERSERVICE
    predicates:
      - Path=/drivers/**, /vehicles/**

  - id: tripservice
    uri: lb://TRIPSERVICE
    predicates:
      - Path=/trips/**
```

**JWT validation at gateway:**
- Custom reactive `GlobalFilter` (`JwtAuthenticationFilter`) intercepts every request
- Public routes (`/auth/register`, `/auth/login`) bypass validation
- All other routes: extract `Authorization: Bearer <token>`, verify signature using shared secret
- Invalid/missing token â†’ `401 Unauthorized` before request reaches any service

**Gateway `JwtService`:**
- Validates token signature only (`extractEmail` + `isTokenValid`)
- Does **not** issue tokens â€” that stays in user-service

**Architecture refactor (gateway centralization):**

| Service | Before | After |
|---|---|---|
| `user-service` | Had `JwtAuthFilter` + `SecurityConfig` + `CustomUserDetailService` | `SecurityConfig` kept (for `PasswordEncoder` bean), JWT filter removed, `permitAll()` |
| `driver-service` | Had full JWT validation stack | All JWT/security code removed |
| `trip-service` | Had no security | No security added â€” gateway handles it |
| Gateway | Didn't exist | Full JWT validation reactive filter |

**Cross-service Feign communication:**
- Feign calls go **directly between services via Eureka** â€” they bypass the gateway entirely
- All services use `anyRequest().permitAll()` for internal traffic â€” no tokens needed on Feign calls
- Zero double-validation, zero redundant DB hits from security filters

### Exit criteria met
- âœ… All client traffic enters via `http://localhost:8080`
- âœ… JWT validated once at gateway â€” microservices have zero auth code
- âœ… `/auth/register` and `/auth/login` publicly accessible through gateway
- âœ… Invalid token returns `401` at gateway â€” never reaches downstream services
- âœ… trip-service â†’ user-service Feign calls work without tokens

---

## Key Architecture Decisions Log

| Decision | Rationale |
|---|---|
| Monorepo structure | Easier cross-service context during development; all docs, configs, and services in one place |
| User-service as sole JWT issuer | Single responsibility â€” one service creates identity tokens |
| Gateway as sole JWT validator | Eliminates duplicated validation logic across all microservices |
| Services trust each other internally | Feign calls bypass gateway; `permitAll()` on all internal endpoints |
| Per-service PostgreSQL databases | True service isolation â€” no shared schema, no cross-service DB queries |
| Feign over RestTemplate | Declarative, Eureka-aware, less boilerplate |
| `FeignClientErrorDecoder` | Translates HTTP errors from Feign calls to domain exceptions â€” prevents 500s leaking through |
| `@PrePersist`/`@PreUpdate` for timestamps | Auto-managed by JPA lifecycle â€” no manual wiring in service layer |
| `IllegalStateException` â†’ 409 Conflict | Business rule violations are conflicts, not server errors |

---

## Bugs Fixed Log

| Date | Service | Bug | Fix |
|---|---|---|---|
| June 25, 2026 | trip-service | `createdAt`/`updatedAt` always `null` | Added `@PrePersist` + `@PreUpdate` on `Trip` entity |
| June 25, 2026 | trip-service | Invalid `riderId` returned `500 Internal Server Error` | Added `FeignClientErrorDecoder` + `RiderNotFoundException` â†’ returns `404` |
| June 24, 2026 | driver-service | JWT validation was redundant after gateway was built | Removed `JwtAuthFilter`, `SecurityConfig`, `CustomDriverDetailService` |
| June 24, 2026 | user-service | JWT validation was redundant after gateway was built | Removed `JwtAuthenticationFilter`, `CustomUserDetailService`, trimmed `JwtService` |

---

## What is NOT Built Yet

Per the execution plan in `execution-system.md`:

| Phase | Component | Status |
|---|---|---|
| Phase 5 | Location Service (PostGIS) | ðŸ”œ Not started |
| Phase 6 | Matching Service | ðŸ”œ Not started |
| Phase 7 | RabbitMQ + Notification Service | ðŸ”œ Not started |
| Phase 8 | Payment Service | ðŸ”œ Not started |
| Phase 9 | Dockerization | ðŸ”œ Not started |
| Phase 10 | AWS Deployment + Monitoring | ðŸ”œ Not started |

> **NOTE:**
> `driverId` in a newly created trip is intentionally `null`. This is correct â€” driver assignment is the job of the **Matching Service** (Phase 6), which does not exist yet. The manual `PATCH /trips/{id}/assign-driver` endpoint exists as a placeholder for testing the trip lifecycle until matching is built.

---

## Documentation Index

| File | Purpose |
|---|---|
| `api-reference.md` | Full REST API reference for all services (this project) |
| `build-progress.md` | This file â€” phase/sprint completion log |
| `execution-system.md` | Master plan, phases, risk register, weekly rhythm |
| `ride-sharing-onboarding-blueprint.md` | Original architecture blueprint and design decisions |
| `tech-dependency-graph.md` | Service dependency graph |
| `gateway-arch-refactor-blackbox-report.md` | Forensic record of the gateway JWT centralization refactor |
| `driver-service-jwt-blackbox-report.md` | Forensic record of driver-service JWT cleanup |
