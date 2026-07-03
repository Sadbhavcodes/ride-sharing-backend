# Ride-Sharing Backend — API Reference

> **Updated:** June 25, 2026
> **Architecture:** Spring Boot Microservices · PostgreSQL · Spring Cloud Config · Eureka · API Gateway
> **Single Entry Point:** `http://localhost:8080` (API Gateway)
> **Authentication:** JWT Bearer Token — validated centrally at the Gateway. All requests must include `Authorization: Bearer <token>` except `/auth/register` and `/auth/login`.

---

## Table of Contents

1. [Service Registry](#service-registry)
2. [Authentication Flow](#authentication-flow)
3. [User Service](#1-user-service-port-8081)
4. [Driver Service](#2-driver-service-port-8082)
5. [Trip Service](#3-trip-service-port-8083)
6. [Enum Reference](#enum-reference)
7. [Error Response Format](#error-response-format)
8. [Quick Reference Table](#quick-reference--all-endpoints)

---

## Service Registry

| Service | App Name | Internal Port | Public via Gateway |
|---|---|---|---|
| API Gateway | `gatewayserver` | `8080` | — (is the entry point) |
| Config Server | `config-server` | `8888` | Not exposed |
| Eureka Server | `eurekaserver` | `8761` | Not exposed |
| User Service | `userservice` | `8081` | `http://localhost:8080/auth/**`, `/users/**` |
| Driver Service | `driverservice` | `8082` | `http://localhost:8080/drivers/**`, `/vehicles/**` |
| Trip Service | `tripservice` | `8083` | `http://localhost:8080/trips/**` |
| Location Service | `locationservice` | `8084` | `http://localhost:8080/locations/**` |
| Matching Service | `matchingservice` | `8085` | Not exposed (Internal only) |

> **IMPORTANT:**
> All client-facing requests must go through the Gateway at port **8080**.
> Direct service ports (8081, 8082, 8083) are internal only and should never be used by external clients.

---

## Authentication Flow

```
POST http://localhost:8080/auth/login
  → Gateway permits (public route)
  → user-service issues JWT

All other requests:
  → Gateway intercepts
  → Validates JWT signature against shared secret
  → On valid: forwards request to target service
  → On invalid/missing: returns 401 immediately (request never reaches service)
```

Inter-service communication (Feign) goes **directly between services via Eureka** — it does not pass through the gateway and requires no token.

---

## 1. User Service (Port 8081)

Handles user registration, authentication, and profile management.
**Gateway base:** `http://localhost:8080`

---

### POST /auth/register

Register a new user (rider or driver).

**Method:** `POST`
**URL:** `http://localhost:8080/auth/register`
**Auth Required:** No (public route)
**Content-Type:** `application/json`

#### Request Body

```json
{
  "username": "john_doe",
  "email": "john@example.com",
  "password": "secret123",
  "phoneNumber": "+1234567890",
  "role": "RIDER"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `username` | String | Yes | Display name |
| `email` | String | Yes | Unique email address |
| `password` | String | Yes | Plain-text (hashed server-side via BCrypt) |
| `phoneNumber` | String | Yes | Contact number |
| `role` | Enum | Yes | `RIDER` or `DRIVER` |

#### Response — `200 OK`

```json
{
  "id": 1,
  "username": "john_doe",
  "phoneNumber": "+1234567890",
  "email": "john@example.com",
  "role": "RIDER"
}
```

#### Response — `400 Bad Request`

```json
{
  "message": "Email already exists"
}
```

---

### POST /auth/login

Authenticate and receive a JWT token.

**Method:** `POST`
**URL:** `http://localhost:8080/auth/login`
**Auth Required:** No (public route)
**Content-Type:** `application/json`

#### Request Body

```json
{
  "email": "john@example.com",
  "password": "secret123"
}
```

#### Response — `200 OK`

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

#### Response — `400 Bad Request`

```json
{
  "message": "Invalid email or password"
}
```

---

### GET /users/{id}

Retrieve a user profile by ID.

**Method:** `GET`
**URL:** `http://localhost:8080/users/{id}`
**Auth Required:** Yes

#### Path Parameters

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | User ID |

#### Response — `200 OK`

```json
{
  "id": 1,
  "username": "john_doe",
  "email": "john@example.com",
  "phoneNumber": "+1234567890",
  "role": "RIDER"
}
```

#### Response — `404 Not Found`

```json
{
  "message": "User not found"
}
```

---

### PUT /users/{id}

Update username and/or email.

**Method:** `PUT`
**URL:** `http://localhost:8080/users/{id}`
**Auth Required:** Yes
**Content-Type:** `application/json`

#### Request Body

```json
{
  "username": "john_updated",
  "email": "john_new@example.com"
}
```

#### Response — `200 OK`

```json
{
  "id": 1,
  "username": "john_updated",
  "email": "john_new@example.com",
  "phoneNumber": "+1234567890",
  "role": "RIDER"
}
```

---

### GET /users/by-email/{email}

Look up a user by email (used internally by Feign clients).

**Method:** `GET`
**URL:** `http://localhost:8080/users/by-email/{email}`
**Auth Required:** Yes

#### Response — `200 OK`

```json
{
  "id": 1,
  "username": "john_doe",
  "email": "john@example.com",
  "phoneNumber": "+1234567890",
  "role": "RIDER"
}
```

---

## 2. Driver Service (Port 8082)

Manages driver profiles, vehicle registrations, and operational status.
**Gateway base:** `http://localhost:8080`

---

### POST /drivers

Create a driver profile by linking a user to a vehicle.

**Method:** `POST`
**URL:** `http://localhost:8080/drivers`
**Auth Required:** Yes
**Content-Type:** `application/json`

#### Request Body

```json
{
  "userId": 1,
  "vehicleId": 10
}
```

#### Response — `200 OK`

```json
{
  "id": 5,
  "userId": 1,
  "vehicleId": 10,
  "availability": "OFFLINE",
  "status": "PENDING",
  "rating": 0.0
}
```

---

### GET /drivers/{id}

Retrieve a driver profile by driver ID.

**Method:** `GET`
**URL:** `http://localhost:8080/drivers/{id}`
**Auth Required:** Yes

#### Response — `200 OK`

```json
{
  "id": 5,
  "userId": 1,
  "vehicleId": 10,
  "availability": "ONLINE",
  "status": "ACTIVE",
  "rating": 4.7
}
```

#### Response — `404 Not Found`

```json
{
  "message": "Driver not found",
  "status": 404,
  "timestamp": "2026-06-25T20:00:00"
}
```

---

### GET /drivers/{id}/availability

Get a driver's current availability without fetching the full profile.

**Method:** `GET`
**URL:** `http://localhost:8080/drivers/{id}/availability`
**Auth Required:** Yes

#### Response — `200 OK`

```json
{
  "driverId": 5,
  "availability": "ONLINE"
}
```

---

### GET /drivers/users/{userId}

Retrieve a driver profile using the linked user's ID.

**Method:** `GET`
**URL:** `http://localhost:8080/drivers/users/{userId}`
**Auth Required:** Yes

#### Response — `200 OK`

```json
{
  "id": 5,
  "userId": 1,
  "vehicleId": 10,
  "availability": "ONLINE",
  "status": "ACTIVE",
  "rating": 4.7
}
```

---

### PUT /drivers/status

Update a driver's account status (activate / suspend).

**Method:** `PUT`
**URL:** `http://localhost:8080/drivers/status`
**Auth Required:** Yes
**Content-Type:** `application/json`

#### Request Body

```json
{
  "id": 5,
  "status": "ACTIVE"
}
```

#### Response — `200 OK`

```json
{
  "id": 5,
  "userId": 1,
  "vehicleId": 10,
  "availability": "OFFLINE",
  "status": "ACTIVE",
  "rating": 0.0
}
```

---

### PUT /drivers/availability

Toggle a driver's real-time availability.

**Method:** `PUT`
**URL:** `http://localhost:8080/drivers/availability`
**Auth Required:** Yes
**Content-Type:** `application/json`

#### Request Body

```json
{
  "id": 5,
  "availability": "ONLINE"
}
```

#### Response — `200 OK`

```json
{
  "id": 5,
  "userId": 1,
  "vehicleId": 10,
  "availability": "ONLINE",
  "status": "ACTIVE",
  "rating": 4.7
}
```

---

### POST /vehicles

Register a new vehicle.

**Method:** `POST`
**URL:** `http://localhost:8080/vehicles`
**Auth Required:** Yes
**Content-Type:** `application/json`

#### Request Body

```json
{
  "plateNumber": "ABC-1234",
  "make": "Toyota",
  "model": "Camry",
  "color": "White"
}
```

#### Response — `200 OK`

```json
{
  "id": 10,
  "plateNumber": "ABC-1234",
  "make": "Toyota",
  "model": "Camry",
  "color": "White",
  "verificationStatus": "PENDING"
}
```

---

### GET /vehicles/{id}

Retrieve a vehicle by ID.

**Method:** `GET`
**URL:** `http://localhost:8080/vehicles/{id}`
**Auth Required:** Yes

#### Response — `200 OK`

```json
{
  "id": 10,
  "plateNumber": "ABC-1234",
  "make": "Toyota",
  "model": "Camry",
  "color": "White",
  "verificationStatus": "VERIFIED"
}
```

---

### PUT /vehicles/{id}

Update a vehicle's verification status (admin operation).

**Method:** `PUT`
**URL:** `http://localhost:8080/vehicles/{id}`
**Auth Required:** Yes
**Content-Type:** `application/json`

#### Request Body

```json
{
  "id": 10,
  "verificationStatus": "VERIFIED"
}
```

#### Response — `200 OK`

```json
{
  "id": 10,
  "plateNumber": "ABC-1234",
  "make": "Toyota",
  "model": "Camry",
  "color": "White",
  "verificationStatus": "VERIFIED"
}
```

---

## 3. Trip Service (Port 8083)

Manages the full lifecycle of ride trips.
**Gateway base:** `http://localhost:8080`

---

### POST /trips

Create a new trip request from a rider.

**Method:** `POST`
**URL:** `http://localhost:8080/trips`
**Auth Required:** Yes
**Content-Type:** `application/json`

#### Request Body

```json
{
  "riderId": 1,
  "pickUpLocation": "Delhi",
  "dropLocation": "Noida"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `riderId` | Long | Yes | Must be a valid existing user ID |
| `pickUpLocation` | String | Yes | Pickup address or location name |
| `dropLocation` | String | Yes | Drop-off address or location name |

#### Response — `200 OK`

```json
{
  "id": 3,
  "riderId": 1,
  "driverId": null,
  "pickupLocation": "Delhi",
  "dropLocation": "Noida",
  "status": "REQUESTED",
  "createdAt": "2026-06-25T20:30:00",
  "updatedAt": "2026-06-25T20:30:00"
}
```

| Field | Type | Description |
|---|---|---|
| `id` | Long | Auto-generated trip ID |
| `riderId` | Long | Confirmed rider ID (validated against user-service) |
| `driverId` | Long / null | `null` until a driver is matched |
| `pickupLocation` | String | Pickup location |
| `dropLocation` | String | Drop-off location |
| `status` | Enum | Always `REQUESTED` on creation |
| `createdAt` | LocalDateTime | ISO-8601 — auto-set on insert |
| `updatedAt` | LocalDateTime | ISO-8601 — auto-set on insert and every update |

#### Response — `404 Not Found` (invalid riderId)

```json
{
  "message": "Rider not found with id: 10",
  "status": 404,
  "timestamp": "2026-06-25T20:30:00"
}
```

> **NOTE:**
> `riderId` is validated via a Feign call to `user-service` before the trip is saved. If the user does not exist, the request fails with `404` — not `500`.

---

### GET /trips/{id}

Retrieve a single trip by ID.

**Method:** `GET`
**URL:** `http://localhost:8080/trips/{id}`
**Auth Required:** Yes

#### Response — `200 OK`

```json
{
  "id": 3,
  "riderId": 1,
  "driverId": 5,
  "pickupLocation": "Delhi",
  "dropLocation": "Noida",
  "status": "IN_PROGRESS",
  "createdAt": "2026-06-25T20:00:00",
  "updatedAt": "2026-06-25T20:15:00"
}
```

#### Response — `404 Not Found`

```json
{
  "message": "Trip not found with id: 99",
  "status": 404,
  "timestamp": "2026-06-25T20:00:00"
}
```

---

### GET /trips/rider/{riderId}

Retrieve all trips for a rider.

**Method:** `GET`
**URL:** `http://localhost:8080/trips/rider/{riderId}`
**Auth Required:** Yes

#### Response — `200 OK`

```json
[
  {
    "id": 3,
    "riderId": 1,
    "driverId": 5,
    "pickupLocation": "Delhi",
    "dropLocation": "Noida",
    "status": "COMPLETED",
    "createdAt": "2026-06-25T18:00:00",
    "updatedAt": "2026-06-25T18:45:00"
  }
]
```

---

### GET /trips/driver/{driverId}

Retrieve all trips assigned to a driver.

**Method:** `GET`
**URL:** `http://localhost:8080/trips/driver/{driverId}`
**Auth Required:** Yes

#### Response — `200 OK`

```json
[
  {
    "id": 3,
    "riderId": 1,
    "driverId": 5,
    "pickupLocation": "Delhi",
    "dropLocation": "Noida",
    "status": "COMPLETED",
    "createdAt": "2026-06-25T18:00:00",
    "updatedAt": "2026-06-25T18:45:00"
  }
]
```

---

### PATCH /trips/{id}/assign-driver

Assign a driver to a trip (manual dispatch / future matching service).

**Method:** `PATCH`
**URL:** `http://localhost:8080/trips/{id}/assign-driver`
**Auth Required:** Yes
**Content-Type:** `application/json`

#### Request Body

```json
{
  "driverId": 5
}
```

#### Business Rules

- Trip must be in `REQUESTED` status — throws `409 Conflict` otherwise
- Trip must not already have a driver — throws `409 Conflict` otherwise

#### Response — `200 OK`

```json
{
  "id": 3,
  "riderId": 1,
  "driverId": 5,
  "pickupLocation": "Delhi",
  "dropLocation": "Noida",
  "status": "MATCHED",
  "createdAt": "2026-06-25T20:00:00",
  "updatedAt": "2026-06-25T20:05:00"
}
```

---

### PATCH /trips/{id}/status

Update a trip's lifecycle status.

**Method:** `PATCH`
**URL:** `http://localhost:8080/trips/{id}/status`
**Auth Required:** Yes
**Content-Type:** `application/json`

#### Request Body

```json
{
  "status": "IN_PROGRESS"
}
```

#### Valid Transitions

| From | Allowed Next States |
|---|---|
| `REQUESTED` | `MATCHED`, `CANCELLED` |
| `MATCHED` | `IN_PROGRESS`, `CANCELLED` |
| `IN_PROGRESS` | `COMPLETED` |
| `COMPLETED` | — (terminal) |
| `CANCELLED` | — (terminal) |

Invalid transitions return `409 Conflict`.

#### Response — `200 OK`

```json
{
  "id": 3,
  "riderId": 1,
  "driverId": 5,
  "pickupLocation": "Delhi",
  "dropLocation": "Noida",
  "status": "IN_PROGRESS",
  "createdAt": "2026-06-25T20:00:00",
  "updatedAt": "2026-06-25T20:10:00"
}
```

---

### POST /trips/{id}/cancel

Cancel a trip (handles REQUESTED and MATCHED state gracefully).

**Method:** `POST`
**URL:** `http://localhost:8080/trips/{id}/cancel`
**Auth Required:** Yes

#### Response — `200 OK`

```json
{
  "tripId": 3
}
```

---

## 4. Location Service (Port 8084)

Manages geospatial data (PostGIS) for driver tracking and nearby search.
**Gateway base:** `http://localhost:8080`

---

### POST /locations/ping

Update driver's real-time geospatial location.

**Method:** `POST`
**URL:** `http://localhost:8080/locations/ping`
**Auth Required:** Yes
**Content-Type:** `application/json`

#### Request Body

```json
{
  "driverId": 5,
  "longitude": 77.1025,
  "latitude": 28.7041
}
```

---

### POST /locations/drivers/nearby

Find drivers within a specific radius (used internally).

**Method:** `POST`
**URL:** `http://localhost:8080/locations/drivers/nearby`
**Auth Required:** Yes

#### Request Body

```json
{
  "longitude": 77.1025,
  "latitude": 28.7041,
  "radius": 5000.0
}
```

---

## 5. Matching Service (Port 8085)

Orchestrates the rider-to-driver matching algorithm.
**Internal Service Only (No Gateway Exposure)**

- Exposes `POST /matching/match` for `trip-service` to call.
- Iteratively searches expanding radii and atomically claims the nearest available driver.

---

## Enum Reference

### `Role` — User Service

| Value | Description |
|---|---|
| `RIDER` | A customer who books rides |
| `DRIVER` | A driver who provides rides |

### `Status` — Driver Service

| Value | Description |
|---|---|
| `PENDING` | Registered but not yet approved |
| `ACTIVE` | Approved and can accept trips |
| `SUSPENDED` | Account suspended |

### `Availability` — Driver Service

| Value | Description |
|---|---|
| `ONLINE` | Available for new trips |
| `OFFLINE` | Not available |
| `BUSY` | Currently on a trip |

### `VerificationStatus` — Vehicle (Driver Service)

| Value | Description |
|---|---|
| `PENDING` | Submitted but not reviewed |
| `VERIFIED` | Approved |
| `REJECTED` | Failed verification |

### `TripStatus` — Trip Service

| Value | Description | Valid Next |
|---|---|---|
| `REQUESTED` | Trip created, awaiting driver | `MATCHED`, `CANCELLED` |
| `MATCHED` | Driver assigned | `IN_PROGRESS`, `CANCELLED` |
| `IN_PROGRESS` | Rider picked up | `COMPLETED` |
| `COMPLETED` | Trip finished | Terminal |
| `CANCELLED` | Trip cancelled | Terminal |

---

## Error Response Format

### User Service errors

Plain message string — no structured wrapper:

```json
{
  "message": "Email already exists"
}
```

### Driver Service & Trip Service errors

Structured `ErrorResponse`:

```json
{
  "message": "Rider not found with id: 10",
  "status": 404,
  "timestamp": "2026-06-25T20:30:00"
}
```

| Field | Type | Description |
|---|---|---|
| `message` | String | Human-readable error description |
| `status` | Integer | HTTP status code |
| `timestamp` | LocalDateTime | ISO-8601 error timestamp |

### Gateway-level errors (JWT)

```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "JWT token is missing or invalid"
}
```

---

## Quick Reference — All Endpoints

All URLs are via Gateway at `http://localhost:8080`.

| # | Method | Path | Auth | Description |
|---|---|---|---|---|
| 1 | `POST` | `/auth/register` | No | Register new user |
| 2 | `POST` | `/auth/login` | No | Login, get JWT |
| 3 | `GET` | `/users/{id}` | Yes | Get user by ID |
| 4 | `PUT` | `/users/{id}` | Yes | Update user profile |
| 5 | `GET` | `/users/by-email/{email}` | Yes | Get user by email |
| 6 | `POST` | `/drivers` | Yes | Create driver profile |
| 7 | `GET` | `/drivers/{id}` | Yes | Get driver by ID |
| 8 | `GET` | `/drivers/{id}/availability` | Yes | Get driver availability |
| 9 | `GET` | `/drivers/users/{userId}` | Yes | Get driver by user ID |
| 10 | `PUT` | `/drivers/status` | Yes | Update driver status |
| 11 | `PUT` | `/drivers/availability` | Yes | Update driver availability |
| 12 | `POST` | `/drivers/available` | Yes | Filter nearby drivers (Internal) |
| 13 | `POST` | `/drivers/{id}/claim` | Yes | Atomic claim driver (Internal) |
| 14 | `POST` | `/drivers/{id}/release` | Yes | Release claimed driver (Internal) |
| 15 | `POST` | `/vehicles` | Yes | Register vehicle |
| 16 | `GET` | `/vehicles/{id}` | Yes | Get vehicle by ID |
| 17 | `PUT` | `/vehicles/{id}` | Yes | Update vehicle verification |
| 18 | `POST` | `/trips` | Yes | Create trip & trigger matching |
| 19 | `GET` | `/trips/{id}` | Yes | Get trip by ID |
| 20 | `GET` | `/trips/rider/{riderId}` | Yes | Get all trips by rider |
| 21 | `GET` | `/trips/driver/{driverId}` | Yes | Get all trips by driver |
| 22 | `PATCH` | `/trips/{id}/assign-driver` | Yes | Assign driver to trip |
| 23 | `PATCH` | `/trips/{id}/status` | Yes | Update trip status |
| 24 | `POST` | `/trips/{id}/cancel` | Yes | Cancel trip safely |
| 25 | `POST` | `/locations/ping` | Yes | Update driver location |
| 26 | `POST` | `/locations/drivers/nearby` | Yes | Find nearby drivers (Internal) |
