# Ride-Sharing Backend — API Reference

> **Generated:** June 22, 2026  
> **Architecture:** Spring Boot Microservices · PostgreSQL · Spring Cloud Config Server  
> **Config Server:** `http://localhost:8888`  
> **Authentication:** JWT Bearer Token (required on protected routes — see security notes per service)

---

## Table of Contents

1. [Service Registry](#service-registry)
2. [User Service — Port 8081](#1-user-service-port-8081)
   - [POST /auth/register](#post-authregister)
   - [POST /auth/login](#post-authlogin)
   - [GET /users/{id}](#get-usersid)
   - [PUT /users/{id}](#put-usersid)
   - [GET /users/by-email/{email}](#get-usersby-emailemail)
3. [Driver Service — Port 8082](#2-driver-service-port-8082)
   - [POST /drivers](#post-drivers)
   - [GET /drivers/{id}](#get-driversid)
   - [GET /drivers/{id}/availability](#get-driversidavailability)
   - [GET /drivers/users/{userId}](#get-driversusersuserid)
   - [PUT /drivers/status](#put-driversstatus)
   - [PUT /drivers/availability](#put-driversavailability)
   - [POST /vehicles](#post-vehicles)
   - [GET /vehicles/{id}](#get-vehiclesid)
   - [PUT /vehicles/{id}](#put-vehiclesid)
4. [Trip Service — Port 8083](#3-trip-service-port-8083)
   - [POST /trips](#post-trips)
   - [GET /trips/{id}](#get-tripsid)
   - [GET /trips/rider/{riderId}](#get-tripsriderriderid)
   - [GET /trips/driver/{driverId}](#get-tripsdriverdriverid)
   - [PATCH /trips/{id}/assign-driver](#patch-tripsidassign-driver)
   - [PATCH /trips/{id}/status](#patch-tripsidstatus)
5. [Enum Reference](#enum-reference)
6. [Error Response Format](#error-response-format)
7. [Quick Reference Table](#quick-reference--all-endpoints)

---

## Service Registry

| Service | App Name | Base URL | Database |
|---|---|---|---|
| Config Server | `config-server` | `http://localhost:8888` | — |
| User Service | `userservice` | `http://localhost:8081` | `rideshare_users` (PostgreSQL) |
| Driver Service | `driverservice` | `http://localhost:8082` | `rideshare_drivers` (PostgreSQL) |
| Trip Service | `tripservice` | `http://localhost:8083` | `rideshare_trips` (PostgreSQL) |

---

## 1. User Service (Port 8081)

Handles user registration, authentication, and profile management.  
**Base URL:** `http://localhost:8081`

---

### POST /auth/register

Register a new user (rider or driver) in the system.

**Method:** `POST`  
**URL:** `http://localhost:8081/auth/register`  
**Auth Required:** No  
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
| `username` | String | Yes | Display name of the user |
| `email` | String | Yes | Unique email address |
| `password` | String | Yes | Plain-text password (hashed server-side) |
| `phoneNumber` | String | Yes | Contact phone number |
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

| Field | Type | Description |
|---|---|---|
| `id` | Long | Auto-generated user ID |
| `username` | String | Registered username |
| `phoneNumber` | String | Registered phone number |
| `email` | String | Registered email |
| `role` | Enum | `RIDER` or `DRIVER` |

---

### POST /auth/login

Authenticate a user and receive a JWT token.

**Method:** `POST`  
**URL:** `http://localhost:8081/auth/login`  
**Auth Required:** No  
**Content-Type:** `application/json`

#### Request Body

```json
{
  "email": "john@example.com",
  "password": "secret123"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `email` | String | Yes | Registered email address |
| `password` | String | Yes | User's password |

#### Response — `200 OK`

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

| Field | Type | Description |
|---|---|---|
| `token` | String | JWT Bearer token to use in subsequent requests |

#### Response — `401 Unauthorized`

```json
{
  "message": "Invalid email or password"
}
```

---

### GET /users/{id}

Retrieve a user profile by their unique ID.

**Method:** `GET`  
**URL:** `http://localhost:8081/users/{id}`  
**Auth Required:** Yes — `Authorization: Bearer <token>`

#### Path Parameters

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | The unique user ID |

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
  "message": "User not found with id: 1"
}
```

---

### PUT /users/{id}

Update a user's username and/or email address.

**Method:** `PUT`  
**URL:** `http://localhost:8081/users/{id}`  
**Auth Required:** Yes — `Authorization: Bearer <token>`  
**Content-Type:** `application/json`

#### Path Parameters

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | The unique user ID to update |

#### Request Body

```json
{
  "username": "john_updated",
  "email": "john_new@example.com"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `username` | String | Yes | New display name |
| `email` | String | Yes | New email address |

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

Look up a user by their email address (used internally by other services).

**Method:** `GET`  
**URL:** `http://localhost:8081/users/by-email/{email}`  
**Auth Required:** Yes — `Authorization: Bearer <token>`

#### Path Parameters

| Parameter | Type | Description |
|---|---|---|
| `email` | String | The email address to look up |

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
  "message": "User not found with email: john@example.com"
}
```

---

## 2. Driver Service (Port 8082)

Manages driver profiles, vehicle registrations, and operational status tracking.  
**Base URL:** `http://localhost:8082`

---

### POST /drivers

Create a new driver profile by linking a registered user to a vehicle.

**Method:** `POST`  
**URL:** `http://localhost:8082/drivers`  
**Auth Required:** Yes — `Authorization: Bearer <token>`  
**Content-Type:** `application/json`

#### Request Body

```json
{
  "userId": 1,
  "vehicleId": 10
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `userId` | Long | Yes | ID of the existing user (from User Service) |
| `vehicleId` | Long | Yes | ID of the registered vehicle |

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

| Field | Type | Description |
|---|---|---|
| `id` | Long | Auto-generated driver profile ID |
| `userId` | Long | Linked user ID |
| `vehicleId` | Long | Linked vehicle ID |
| `availability` | Enum | `ONLINE`, `OFFLINE`, or `BUSY` |
| `status` | Enum | `PENDING`, `ACTIVE`, or `SUSPENDED` |
| `rating` | Double | Driver rating (default: `0.0`) |

---

### GET /drivers/{id}

Retrieve a driver profile by driver ID.

**Method:** `GET`  
**URL:** `http://localhost:8082/drivers/{id}`  
**Auth Required:** Yes — `Authorization: Bearer <token>`

#### Path Parameters

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | The unique driver ID |

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
  "timestamp": "2026-06-22T20:00:00"
}
```

---

### GET /drivers/{id}/availability

Get the current availability status of a driver without fetching the full profile.

**Method:** `GET`  
**URL:** `http://localhost:8082/drivers/{id}/availability`  
**Auth Required:** Yes — `Authorization: Bearer <token>`

#### Path Parameters

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | The unique driver ID |

#### Response — `200 OK`

```json
{
  "driverId": 5,
  "availability": "ONLINE"
}
```

| Field | Type | Description |
|---|---|---|
| `driverId` | Long | The driver's ID |
| `availability` | Enum | `ONLINE`, `OFFLINE`, or `BUSY` |

#### Response — `404 Not Found`

```json
{
  "message": "Driver not found",
  "status": 404,
  "timestamp": "2026-06-22T20:00:00"
}
```

---

### GET /drivers/users/{userId}

Retrieve a driver profile using the linked user's ID.

**Method:** `GET`  
**URL:** `http://localhost:8082/drivers/users/{userId}`  
**Auth Required:** Yes — `Authorization: Bearer <token>`

#### Path Parameters

| Parameter | Type | Description |
|---|---|---|
| `userId` | Long | The user ID linked to the driver profile |

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

Update a driver's account status (e.g., activate or suspend).

**Method:** `PUT`  
**URL:** `http://localhost:8082/drivers/status`  
**Auth Required:** Yes — `Authorization: Bearer <token>`  
**Content-Type:** `application/json`

#### Request Body

```json
{
  "id": 5,
  "status": "ACTIVE"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | Long | Yes | Driver ID to update |
| `status` | Enum | Yes | `PENDING`, `ACTIVE`, or `SUSPENDED` |

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

Toggle a driver's real-time availability (online/offline/busy).

**Method:** `PUT`  
**URL:** `http://localhost:8082/drivers/availability`  
**Auth Required:** Yes — `Authorization: Bearer <token>`  
**Content-Type:** `application/json`

#### Request Body

```json
{
  "id": 5,
  "availability": "ONLINE"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | Long | Yes | Driver ID to update |
| `availability` | Enum | Yes | `ONLINE`, `OFFLINE`, or `BUSY` |

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

Register a new vehicle in the system.

**Method:** `POST`  
**URL:** `http://localhost:8082/vehicles`  
**Auth Required:** Yes — `Authorization: Bearer <token>`  
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

| Field | Type | Required | Description |
|---|---|---|---|
| `plateNumber` | String | Yes | Unique license plate number |
| `make` | String | Yes | Vehicle manufacturer (e.g., Toyota) |
| `model` | String | Yes | Vehicle model (e.g., Camry) |
| `color` | String | Yes | Vehicle color |

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

| Field | Type | Description |
|---|---|---|
| `id` | Long | Auto-generated vehicle ID |
| `plateNumber` | String | License plate number |
| `make` | String | Vehicle manufacturer |
| `model` | String | Vehicle model |
| `color` | String | Vehicle color |
| `verificationStatus` | Enum | `PENDING`, `VERIFIED`, or `REJECTED` |

---

### GET /vehicles/{id}

Retrieve a vehicle by its ID.

**Method:** `GET`  
**URL:** `http://localhost:8082/vehicles/{id}`  
**Auth Required:** Yes — `Authorization: Bearer <token>`

#### Path Parameters

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | The unique vehicle ID |

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

Update the verification status of a vehicle (admin operation).

**Method:** `PUT`  
**URL:** `http://localhost:8082/vehicles/{id}`  
**Auth Required:** Yes — `Authorization: Bearer <token>`  
**Content-Type:** `application/json`

#### Path Parameters

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | The vehicle ID (path variable) |

#### Request Body

```json
{
  "id": 10,
  "verificationStatus": "VERIFIED"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | Long | Yes | Vehicle ID to update |
| `verificationStatus` | Enum | Yes | `PENDING`, `VERIFIED`, or `REJECTED` |

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

Manages the full lifecycle of ride trips, from request to completion.  
**Base URL:** `http://localhost:8083`

---

### POST /trips

Create a new trip request from a rider.

**Method:** `POST`  
**URL:** `http://localhost:8083/trips`  
**Auth Required:** Yes — `Authorization: Bearer <token>`  
**Content-Type:** `application/json`

#### Request Body

```json
{
  "riderId": 1,
  "pickUpLocation": "123 Main St, Downtown",
  "dropLocation": "456 Airport Rd, Terminal 2"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `riderId` | Long | Yes | ID of the rider requesting the trip |
| `pickUpLocation` | String | Yes | Pickup address or location name |
| `dropLocation` | String | Yes | Drop-off address or location name |

#### Response — `201 Created`

```json
{
  "id": 100,
  "riderId": 1,
  "driverId": null,
  "pickupLocation": "123 Main St, Downtown",
  "dropLocation": "456 Airport Rd, Terminal 2",
  "status": "REQUESTED",
  "createdAt": "2026-06-22T20:00:00",
  "updatedAt": "2026-06-22T20:00:00"
}
```

| Field | Type | Description |
|---|---|---|
| `id` | Long | Auto-generated trip ID |
| `riderId` | Long | ID of the requesting rider |
| `driverId` | Long / null | Assigned driver ID (null until matched) |
| `pickupLocation` | String | Pickup location |
| `dropLocation` | String | Drop-off location |
| `status` | Enum | Initial value: `REQUESTED` |
| `createdAt` | LocalDateTime | ISO-8601 creation timestamp |
| `updatedAt` | LocalDateTime | ISO-8601 last-updated timestamp |

---

### GET /trips/{id}

Retrieve a single trip by its ID.

**Method:** `GET`  
**URL:** `http://localhost:8083/trips/{id}`  
**Auth Required:** Yes — `Authorization: Bearer <token>`

#### Path Parameters

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | The unique trip ID |

#### Response — `200 OK`

```json
{
  "id": 100,
  "riderId": 1,
  "driverId": 5,
  "pickupLocation": "123 Main St, Downtown",
  "dropLocation": "456 Airport Rd, Terminal 2",
  "status": "IN_PROGRESS",
  "createdAt": "2026-06-22T20:00:00",
  "updatedAt": "2026-06-22T20:15:00"
}
```

#### Response — `404 Not Found`

```json
{
  "message": "Trip not found with id: 100",
  "status": 404,
  "timestamp": "2026-06-22T20:00:00"
}
```

---

### GET /trips/rider/{riderId}

Retrieve all trips associated with a specific rider.

**Method:** `GET`  
**URL:** `http://localhost:8083/trips/rider/{riderId}`  
**Auth Required:** Yes — `Authorization: Bearer <token>`

#### Path Parameters

| Parameter | Type | Description |
|---|---|---|
| `riderId` | Long | The rider's user ID |

#### Response — `200 OK`

```json
[
  {
    "id": 100,
    "riderId": 1,
    "driverId": 5,
    "pickupLocation": "123 Main St, Downtown",
    "dropLocation": "456 Airport Rd, Terminal 2",
    "status": "COMPLETED",
    "createdAt": "2026-06-22T18:00:00",
    "updatedAt": "2026-06-22T18:45:00"
  },
  {
    "id": 105,
    "riderId": 1,
    "driverId": null,
    "pickupLocation": "Office Park A",
    "dropLocation": "Central Station",
    "status": "REQUESTED",
    "createdAt": "2026-06-22T20:00:00",
    "updatedAt": "2026-06-22T20:00:00"
  }
]
```

---

### GET /trips/driver/{driverId}

Retrieve all trips assigned to a specific driver.

**Method:** `GET`  
**URL:** `http://localhost:8083/trips/driver/{driverId}`  
**Auth Required:** Yes — `Authorization: Bearer <token>`

#### Path Parameters

| Parameter | Type | Description |
|---|---|---|
| `driverId` | Long | The driver's profile ID |

#### Response — `200 OK`

```json
[
  {
    "id": 100,
    "riderId": 1,
    "driverId": 5,
    "pickupLocation": "123 Main St, Downtown",
    "dropLocation": "456 Airport Rd, Terminal 2",
    "status": "COMPLETED",
    "createdAt": "2026-06-22T18:00:00",
    "updatedAt": "2026-06-22T18:45:00"
  }
]
```

---

### PATCH /trips/{id}/assign-driver

Assign a driver to an existing trip (dispatcher/matching operation).

**Method:** `PATCH`  
**URL:** `http://localhost:8083/trips/{id}/assign-driver`  
**Auth Required:** Yes — `Authorization: Bearer <token>`  
**Content-Type:** `application/json`

#### Path Parameters

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | The trip ID to assign a driver to |

#### Request Body

```json
{
  "driverId": 5
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `driverId` | Long | Yes | ID of the driver to assign |

#### Response — `200 OK`

```json
{
  "id": 100,
  "riderId": 1,
  "driverId": 5,
  "pickupLocation": "123 Main St, Downtown",
  "dropLocation": "456 Airport Rd, Terminal 2",
  "status": "MATCHED",
  "createdAt": "2026-06-22T20:00:00",
  "updatedAt": "2026-06-22T20:05:00"
}
```

---

### PATCH /trips/{id}/status

Update the status of a trip (start, complete, or cancel).

**Method:** `PATCH`  
**URL:** `http://localhost:8083/trips/{id}/status`  
**Auth Required:** Yes — `Authorization: Bearer <token>`  
**Content-Type:** `application/json`

#### Path Parameters

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | The trip ID to update |

#### Request Body

```json
{
  "status": "IN_PROGRESS"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `status` | Enum | Yes | New trip status (see Enum Reference) |

#### Response — `200 OK`

```json
{
  "id": 100,
  "riderId": 1,
  "driverId": 5,
  "pickupLocation": "123 Main St, Downtown",
  "dropLocation": "456 Airport Rd, Terminal 2",
  "status": "IN_PROGRESS",
  "createdAt": "2026-06-22T20:00:00",
  "updatedAt": "2026-06-22T20:10:00"
}
```

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
| `PENDING` | Driver registered but not yet approved |
| `ACTIVE` | Driver is approved and can accept trips |
| `SUSPENDED` | Driver account has been suspended |

### `Availability` — Driver Service

| Value | Description |
|---|---|
| `ONLINE` | Driver is available and can accept new trips |
| `OFFLINE` | Driver is not available |
| `BUSY` | Driver is currently on a trip |

### `VerificationStatus` — Driver Service (Vehicle)

| Value | Description |
|---|---|
| `PENDING` | Vehicle submitted but not yet reviewed |
| `VERIFIED` | Vehicle has been approved |
| `REJECTED` | Vehicle failed verification |

### `TripStatus` — Trip Service

| Value | Description | Typical Next State |
|---|---|---|
| `REQUESTED` | Trip created, awaiting driver | `MATCHED` or `CANCELLED` |
| `MATCHED` | Driver assigned | `IN_PROGRESS` or `CANCELLED` |
| `IN_PROGRESS` | Driver has picked up the rider | `COMPLETED` or `CANCELLED` |
| `COMPLETED` | Trip successfully finished | Terminal state |
| `CANCELLED` | Trip was cancelled | Terminal state |

---

## Error Response Format

All three services return a JSON error body on failure. The schema varies slightly by service:

### User Service

```json
{
  "message": "User not found with id: 99"
}
```

### Driver Service & Trip Service

```json
{
  "message": "Driver not found",
  "status": 404,
  "timestamp": "2026-06-22T20:00:00"
}
```

| Field | Type | Description |
|---|---|---|
| `message` | String | Human-readable error description |
| `status` | Integer | HTTP status code |
| `timestamp` | LocalDateTime | ISO-8601 timestamp of the error |

---

## Quick Reference — All Endpoints

| # | Method | URL | Service | Auth | HTTP Status | Description |
|---|---|---|---|---|---|---|
| 1 | `POST` | `http://localhost:8081/auth/register` | User | No | 200 | Register new user |
| 2 | `POST` | `http://localhost:8081/auth/login` | User | No | 200 | Login and get JWT |
| 3 | `GET` | `http://localhost:8081/users/{id}` | User | Yes | 200 | Get user by ID |
| 4 | `PUT` | `http://localhost:8081/users/{id}` | User | Yes | 200 | Update user profile |
| 5 | `GET` | `http://localhost:8081/users/by-email/{email}` | User | Yes | 200 | Get user by email |
| 6 | `POST` | `http://localhost:8082/drivers` | Driver | Yes | 200 | Create driver profile |
| 7 | `GET` | `http://localhost:8082/drivers/{id}` | Driver | Yes | 200 | Get driver by ID |
| 8 | `GET` | `http://localhost:8082/drivers/{id}/availability` | Driver | Yes | 200 | Get driver availability |
| 9 | `GET` | `http://localhost:8082/drivers/users/{userId}` | Driver | Yes | 200 | Get driver by user ID |
| 10 | `PUT` | `http://localhost:8082/drivers/status` | Driver | Yes | 200 | Update driver status |
| 11 | `PUT` | `http://localhost:8082/drivers/availability` | Driver | Yes | 200 | Update driver availability |
| 12 | `POST` | `http://localhost:8082/vehicles` | Driver | Yes | 200 | Register new vehicle |
| 13 | `GET` | `http://localhost:8082/vehicles/{id}` | Driver | Yes | 200 | Get vehicle by ID |
| 14 | `PUT` | `http://localhost:8082/vehicles/{id}` | Driver | Yes | 200 | Update vehicle verification |
| 15 | `POST` | `http://localhost:8083/trips` | Trip | Yes | 201 | Create trip request |
| 16 | `GET` | `http://localhost:8083/trips/{id}` | Trip | Yes | 200 | Get trip by ID |
| 17 | `GET` | `http://localhost:8083/trips/rider/{riderId}` | Trip | Yes | 200 | Get all trips by rider |
| 18 | `GET` | `http://localhost:8083/trips/driver/{driverId}` | Trip | Yes | 200 | Get all trips by driver |
| 19 | `PATCH` | `http://localhost:8083/trips/{id}/assign-driver` | Trip | Yes | 200 | Assign driver to trip |
| 20 | `PATCH` | `http://localhost:8083/trips/{id}/status` | Trip | Yes | 200 | Update trip status |
