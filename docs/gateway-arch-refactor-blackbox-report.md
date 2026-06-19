# Blackbox Change Report — Gateway Architecture Refactor

> **Purpose:** Full forensic record of changes made in this session.  
> Used for deployment post-mortems, debugging, and audit trails.

---

## 1. Repository & Environment

| Field | Value |
|---|---|
| Repository | `ride-sharing-backend` |
| Workspace path | `e:\Projects\ride-sharing-backend` |
| Report generated at | `2026-06-19T20:47 IST` |
| Changed by | Antigravity (AI pair programmer) |
| Change session | Conversation `ffa8e088-cfaa-4d55-9e9d-c73e97c471bb` |

---

## 2. Git Commit History at Time of Change

```
3f850eb  2026-06-18  driver service jwt                           ← HEAD at change time
8763aea  2026-06-17  DriverService vehicle endpoints
715a8d0  2026-06-17  driver-service till controllers
00eab08  2026-06-16  User service jwt auth
217b9e4  2026-06-15  User service-v1
7a4017b  2026-06-14  Initial project setup
```

> [!IMPORTANT]
> HEAD at the time of this change was `3f850eb`.
> All changes below are currently **UNCOMMITTED (working tree only)**.
> If the service fails at deployment, compare working tree against this commit to isolate the regression.

---

## 3. Motivation & Architecture Decision

The original `driver-service` JWT layer (committed at `3f850eb`) included both **token issuance** (`generateToken()`) and **token validation** (`extractSubject()`, `isTokenValid()`).

The architecture has since been revised:

- **`user-service`** is the sole token issuer — all JWTs are generated here on login.
- **API Gateway** will intercept every inbound request and validate the JWT before routing to any downstream service.
- Individual microservices (`driver-service`, `trip-service`, future services) **do not need to issue tokens** and will eventually not need to validate them either (the gateway will forward a trusted identity header). For now, driver-service retains its validation stack as a defence-in-depth layer until the gateway is wired in.

**This session had two tasks:**
1. Strip the issuing logic from `driver-service` (keep the validation stack intact).
2. Add a `GlobalExceptionHandler` to `trip-service` so controllers return correct HTTP status codes.

---

## 4. Files Changed

### 4.1 `git status` Output (Working Tree vs HEAD `3f850eb`)

```
 M microservices/driver-service/src/main/java/com/rideshare/driverservice/service/JwtService.java
 M microservices/driver-service/src/main/resources/application.yaml
?? microservices/trip-service/trip-service/src/main/java/com/rideshare/tripservice/dto/ErrorResponse.java           ← new file
?? microservices/trip-service/trip-service/src/main/java/com/rideshare/tripservice/exception/GlobalExceptionHandler.java  ← new file
```

> [!NOTE]
> The `AM` entries in `git status` for other trip-service files are pre-existing staged files unrelated to this session — they were already in the working tree before this session began and were not touched.

---

## 5. Exact Diffs

### 5.1 `driver-service` — `service/JwtService.java` — Issuing Logic Removed

```diff
--- a/microservices/driver-service/src/main/java/com/rideshare/driverservice/service/JwtService.java  (3f850eb)
+++ b/microservices/driver-service/src/main/java/com/rideshare/driverservice/service/JwtService.java  (working tree)
@@ -8,7 +8,6 @@ import org.springframework.stereotype.Service;
 import javax.crypto.SecretKey;
 import java.nio.charset.StandardCharsets;
 import java.security.Key;
-import java.util.Date;

 @Service
 public class JwtService {
@@ -16,32 +15,12 @@ public class JwtService {
     @Value("${jwt.secret}")
     private String secret;

-    @Value("${jwt.expiration}")
-    private long expiration;
-
     private Key getSignKey() {
         return Keys.hmacShaKeyFor(
                 secret.getBytes(StandardCharsets.UTF_8)
         );
     }

-    public String generateToken(String subject) {
-
-        return Jwts.builder()
-                .subject(subject)
-                .issuedAt(new Date())
-                .expiration(
-                        new Date(
-                                System.currentTimeMillis()
-                                        + expiration
-                        )
-                )
-                .signWith(
-                        getSignKey()
-                )
-                .compact();
-    }
-
     public String extractSubject(String token) {
         ...
```

**Removed:**
- `import java.util.Date` — no longer needed
- `@Value("${jwt.expiration}") private long expiration` — only used by the issuing method
- `public String generateToken(String subject)` — the entire token-issuance method

**Kept (validation stack — untouched):**
- `getSignKey()` — used by both `extractSubject` and `isTokenValid`
- `extractSubject(String token)` — parses and verifies token signature, returns subject
- `isTokenValid(String token, String subject)` — checks subject matches

---

### 5.2 `driver-service` — `resources/application.yaml` — Expiration Config Removed

```diff
--- a/microservices/driver-service/src/main/resources/application.yaml  (3f850eb)
+++ b/microservices/driver-service/src/main/resources/application.yaml  (working tree)
@@ -19,5 +19,4 @@ spring:
         format_sql: true

 jwt:
-  secret: myVeryStrongSecretKeyForRideShareApplication2025
-  expiration: 86400000
+  secret: myVeryStrongSecretKeyForRideShareApplication2025
```

**Removed:** `jwt.expiration: 86400000` — only consumed by `generateToken()`, which is now gone.  
**Kept:** `jwt.secret` — still required by `getSignKey()` → `extractSubject()` for validating incoming tokens.

---

### 5.3 `trip-service` — `dto/ErrorResponse.java` — New File

**Path:** `microservices/trip-service/trip-service/src/main/java/com/rideshare/tripservice/dto/ErrorResponse.java`

```java
package com.rideshare.tripservice.dto;

import java.time.LocalDateTime;

public record ErrorResponse(
        String message,
        int status,
        LocalDateTime timestamp
) {
}
```

**Purpose:** Structured error payload returned by `GlobalExceptionHandler` on every non-2xx response. Identical shape to `driver-service`'s `ErrorResponse` for consistency across the platform.

---

### 5.4 `trip-service` — `exception/GlobalExceptionHandler.java` — New File

**Path:** `microservices/trip-service/trip-service/src/main/java/com/rideshare/tripservice/exception/GlobalExceptionHandler.java`

```java
package com.rideshare.tripservice.exception;

import com.rideshare.tripservice.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TripNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTripNotFoundException(
            TripNotFoundException ex) {

        ErrorResponse error = new ErrorResponse(
                ex.getMessage(),
                HttpStatus.NOT_FOUND.value(),
                LocalDateTime.now()
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(error);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(
            IllegalStateException ex) {

        ErrorResponse error = new ErrorResponse(
                ex.getMessage(),
                HttpStatus.CONFLICT.value(),
                LocalDateTime.now()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex) {

        ErrorResponse error = new ErrorResponse(
                ex.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                LocalDateTime.now()
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error);
    }
}
```

**HTTP status mapping:**

| Exception | HTTP Status | Trigger scenario |
|---|---|---|
| `TripNotFoundException` | `404 Not Found` | `GET /trips/{id}`, `PATCH /trips/{id}/status`, `PATCH /trips/{id}/assign-driver` — id not in DB |
| `IllegalStateException` | `409 Conflict` | Invalid status transition (e.g. COMPLETED → IN_PROGRESS), or assigning driver to a non-REQUESTED trip, or trip already has a driver |
| `Exception` (catch-all) | `500 Internal Server Error` | Any unexpected runtime failure |

**Before this handler existed:** Spring's default `DefaultHandlerExceptionResolver` would return a `500` for `TripNotFoundException` and `IllegalStateException` since neither extends `ResponseStatusException`. Now they return semantically correct codes.

---

## 6. Files Explicitly NOT Changed

### driver-service
- `pom.xml` — Spring Security + JJWT deps remain (validation still needs them)
- `config/JwtAuthenticationFilter.java` — untouched
- `config/SecurityConfig.java` — untouched
- `service/CustomDriverDetailService.java` — untouched
- All controllers, services, repositories, entities, DTOs (except `JwtService`) — untouched

### trip-service
- `pom.xml` — no new deps added (GlobalExceptionHandler needs none)
- `resources/application.yaml` — untouched
- `TripController.java` — untouched
- `TripService.java` — untouched
- All existing DTOs, entities, repositories — untouched

**No files in `user-service` were touched.**

---

## 7. Risk Register

| # | Risk | Severity | Notes |
|---|---|---|---|
| R1 | `driver-service` JWT secret still in plaintext in `application.yaml` | **HIGH** | Inherited from previous session. Must be externalised before cloud deployment |
| R2 | `driver-service` validates tokens but gateway doesn't yet exist | **MEDIUM** | During transition, driver-service is still the only line of defence on port 8082. Do not expose 8082 publicly until gateway is deployed |
| R3 | `trip-service` has no security layer at all | **HIGH** | No JWT filter, no Spring Security — all `/trips` endpoints are publicly accessible. Acceptable only if trip-service is hidden behind gateway / not yet exposed |
| R4 | `GlobalExceptionHandler` catch-all `Exception` handler may mask security exceptions | **LOW** | Spring Security's `AccessDeniedException` and `AuthenticationException` are handled by the security filter chain before reaching controllers, so they won't be intercepted by `@RestControllerAdvice`. Safe. |
| R5 | `IllegalStateException` mapped to 409 Conflict | **LOW** | Intentional — all business rule violations in `TripService` throw `IllegalStateException`. If a library dependency also throws `IllegalStateException` for an unrelated reason, it will incorrectly surface as 409. Monitor if deps are added. |

---

## 8. Rollback Instructions

### Revert driver-service changes

```bash
# Restore JwtService and application.yaml to HEAD 3f850eb
git checkout -- microservices/driver-service/src/main/java/com/rideshare/driverservice/service/JwtService.java
git checkout -- microservices/driver-service/src/main/resources/application.yaml
```

After rollback, `driver-service` will have `generateToken()` back and will require `jwt.expiration` in `application.yaml`.

### Revert trip-service additions

```bash
rm microservices/trip-service/trip-service/src/main/java/com/rideshare/tripservice/dto/ErrorResponse.java
rm microservices/trip-service/trip-service/src/main/java/com/rideshare/tripservice/exception/GlobalExceptionHandler.java
```

After rollback, `TripNotFoundException` and `IllegalStateException` will again surface as HTTP 500.

---

## 9. Recommended Next Steps

- [ ] Commit these changes: `git add microservices/driver-service microservices/trip-service && git commit -m "refactor: gateway arch — remove issuing from driver-service, add trip-service GlobalExceptionHandler"`
- [ ] Move `jwt.secret` to an environment variable (`JWT_SECRET`) in both `user-service` and `driver-service`
- [ ] Implement API Gateway service with JWT validation middleware
- [ ] Once gateway validates tokens, remove `JwtAuthenticationFilter` + `SecurityConfig` + `CustomDriverDetailService` from `driver-service` (they become redundant)
- [ ] Add Spring Security + JWT filter to `trip-service` OR rely on gateway — decide and document
- [ ] Test: `GET /trips/{id}` with a non-existent id → expect `404` with `ErrorResponse` body
- [ ] Test: `PATCH /trips/{id}/status` with invalid transition → expect `409` with `ErrorResponse` body
