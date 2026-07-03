# Blackbox Change Report â€” `driver-service` JWT Security Layer

> **Purpose:** Full forensic record of changes made to `driver-service` after the last committed state.  
> Used for deployment post-mortems, debugging, and audit trails.

---

## 1. Repository & Environment

| Field | Value |
|---|---|
| Repository | `ride-sharing-backend` |
| Workspace path | `e:\Projects\ride-sharing-backend` |
| Report generated at | `2026-06-18T16:43 IST` |
| Changed by | Antigravity (AI pair programmer) |
| Change session | Conversation `615cd31a-8fd0-4664-96d7-28668b817e1f` |

---

## 2. Git Commit History at Time of Change

The following was the full commit graph when changes were applied. **All changes are currently UNCOMMITTED (working tree only).**

```
8763aea  2026-06-17 21:30 +0530  DriverService vehicle endpoints        â† HEAD at change time
715a8d0  2026-06-17 16:22 +0530  driver-service till controllers
00eab08  2026-06-16 16:33 +0530  User service jwt auth
217b9e4  2026-06-15 20:43 +0530  User service-v1
7a4017b  2026-06-14 14:25 +0530  Initial project setup
```

> **IMPORTANT:**
> HEAD at the time of this change was `8763aeaaddd37bcb11c274e817ba9580c194d501`.
> None of the changes below are committed. If the service fails at deployment, compare working tree
> against this commit to isolate the regression.

---

## 3. Motivation

`driver-service` had no authentication layer. `user-service` (commit `00eab08`) already had a complete JWT security stack (Spring Security + JJWT 0.12.5). The task was to replicate the **exact same pattern** in `driver-service` without touching any other codebase.

---

## 4. Files Changed

### 4.1 `git status` Output (Working Tree vs HEAD `8763aea`)

```
 M microservices/driver-service/pom.xml
 M microservices/driver-service/src/main/resources/application.yaml
 M microservices/driver-service/src/main/java/com/rideshare/driverservice/exception/GlobalExceptionHandler.java   <- IDE auto-reformat only
?? microservices/driver-service/src/main/java/com/rideshare/driverservice/config/                                 <- new directory
?? microservices/driver-service/src/main/java/com/rideshare/driverservice/service/CustomDriverDetailService.java  <- new file
?? microservices/driver-service/src/main/java/com/rideshare/driverservice/service/JwtService.java                 <- new file
```

> **NOTE:**
> `GlobalExceptionHandler.java` shows as modified by `git diff` but the **functional content is identical** to the committed version â€”
> it is a whitespace/line-ending reformat introduced by the IDE when the file was read. No handler logic was changed.

---

## 5. Exact Diffs

### 5.1 `pom.xml` â€” Added Dependencies

```diff
--- a/microservices/driver-service/pom.xml  (8763aea)
+++ b/microservices/driver-service/pom.xml  (working tree)
@@ -58,6 +58,30 @@
 		<artifactId>spring-boot-starter-test</artifactId>
 		<scope>test</scope>
 	</dependency>
+	<dependency>
+		<groupId>org.springframework.boot</groupId>
+		<artifactId>spring-boot-starter-security</artifactId>
+	</dependency>
+
+	<dependency>
+		<groupId>io.jsonwebtoken</groupId>
+		<artifactId>jjwt-api</artifactId>
+		<version>0.12.5</version>
+	</dependency>
+
+	<dependency>
+		<groupId>io.jsonwebtoken</groupId>
+		<artifactId>jjwt-impl</artifactId>
+		<version>0.12.5</version>
+		<scope>runtime</scope>
+	</dependency>
+
+	<dependency>
+		<groupId>io.jsonwebtoken</groupId>
+		<artifactId>jjwt-jackson</artifactId>
+		<version>0.12.5</version>
+		<scope>runtime</scope>
+	</dependency>
 </dependencies>
```

**Versions pinned to:** `jjwt 0.12.5` â€” identical to `user-service/pom.xml`.  
**Spring Security version:** managed by Spring Boot parent `3.5.15` (no explicit version).

---

### 5.2 `application.yaml` â€” Added JWT Config

```diff
--- a/microservices/driver-service/src/main/resources/application.yaml  (8763aea)
+++ b/microservices/driver-service/src/main/resources/application.yaml  (working tree)
@@ -16,4 +16,8 @@
     properties:
       hibernate:
         format_sql: true
+
+jwt:
+  secret: myVeryStrongSecretKeyForRideShareApplication2025
+  expiration: 86400000
```

> **WARNING:**
> The JWT secret is **identical to user-service**. This is intentional â€” it allows a token issued by
> `user-service` on login to be validated by `driver-service` (cross-service trust via shared secret).
> **Before production deployment**, this secret MUST be moved to an environment variable or secrets manager
> (e.g., AWS Secrets Manager, Vault, Kubernetes Secret). Never commit a live production secret in plaintext.

---

### 5.3 `service/JwtService.java` â€” New File

**Path:** `microservices/driver-service/src/main/java/com/rideshare/driverservice/service/JwtService.java`

```java
package com.rideshare.driverservice.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private Key getSignKey() {
        return Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
    }

    public String generateToken(String subject) {
        return Jwts.builder()
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignKey())
                .compact();
    }

    public String extractSubject(String token) {
        return Jwts.parser()
                .verifyWith((SecretKey) getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public boolean isTokenValid(String token, String subject) {
        return subject.equals(extractSubject(token));
    }
}
```

**Difference from `user-service`:** Method `extractEmail()` renamed to `extractSubject()` because
the driver JWT subject is a `userId` (Long as String), not an email address.

---

### 5.4 `service/CustomDriverDetailService.java` â€” New File

**Path:** `microservices/driver-service/src/main/java/com/rideshare/driverservice/service/CustomDriverDetailService.java`

```java
package com.rideshare.driverservice.service;

import com.rideshare.driverservice.entity.Driver;
import com.rideshare.driverservice.repository.DriverRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CustomDriverDetailService implements UserDetailsService {

    private final DriverRepository driverRepository;

    public CustomDriverDetailService(DriverRepository driverRepository) {
        this.driverRepository = driverRepository;
    }

    // "username" here = userId string (the JWT subject)
    @Override
    public UserDetails loadUserByUsername(String userId)
            throws UsernameNotFoundException {

        Optional<Driver> driver = driverRepository.findByUserId(Long.parseLong(userId));
        if (driver.isEmpty()) {
            throw new UsernameNotFoundException("Driver not found for userId: " + userId);
        }
        Driver foundDriver = driver.get();
        return new User(
                foundDriver.getUserId().toString(),
                "",   // no stored password â€” token-only auth
                List.of(new SimpleGrantedAuthority("ROLE_DRIVER"))
        );
    }
}
```

**Key design decisions:**
- `loadUserByUsername(String userId)` â€” the "username" is `userId` cast to String (the JWT subject).
- Password field is an **empty string** `""` â€” `Driver` entity has no password column; auth is solely token-based.
- Authority is hardcoded to `ROLE_DRIVER`.

**Difference from `user-service`:** `user-service` loads by `email` and reads the stored BCrypt password. Driver service has neither â€” it only validates the JWT subject maps to a real driver row.

---

### 5.5 `config/JwtAuthenticationFilter.java` â€” New File

**Path:** `microservices/driver-service/src/main/java/com/rideshare/driverservice/config/JwtAuthenticationFilter.java`

```java
package com.rideshare.driverservice.config;

import com.rideshare.driverservice.service.CustomDriverDetailService;
import com.rideshare.driverservice.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomDriverDetailService customDriverDetailService;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   CustomDriverDetailService customDriverDetailService) {
        this.jwtService = jwtService;
        this.customDriverDetailService = customDriverDetailService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        String userId = jwtService.extractSubject(token);

        if (userId != null &&
                SecurityContextHolder.getContext().getAuthentication() == null) {

            UserDetails userDetails = customDriverDetailService.loadUserByUsername(userId);

            if (jwtService.isTokenValid(token, userDetails.getUsername())) {
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities()
                        );
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        filterChain.doFilter(request, response);
    }
}
```

**Logic is identical to `user-service` filter** â€” only package and injected service names differ.

---

### 5.6 `config/SecurityConfig.java` â€” New File

**Path:** `microservices/driver-service/src/main/java/com/rideshare/driverservice/config/SecurityConfig.java`

```java
package com.rideshare.driverservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}
```

**Key difference from `user-service`:**
- `user-service` permits `"/auth/**"` without a token (login/register are public).
- `driver-service` has **no public endpoints** â€” every request requires a valid Bearer token.
- No `PasswordEncoder` bean â€” `driver-service` does not encode or validate passwords.

---

## 6. Security Architecture â€” JWT Flow Across Services

```
Client
  |
  |  POST /auth/login  {email, password}
  v
user-service (port 8080)
  |  validates credentials, generates JWT
  |  subject = user.email
  |  signed with: myVeryStrongSecretKeyForRideShareApplication2025
  |
  |  returns: { "token": "eyJ..." }
  |
  v
Client stores token

  |
  |  POST /drivers  {userId, vehicleId}
  |  Authorization: Bearer eyJ...
  v
driver-service (port 8082)
  |  JwtAuthenticationFilter intercepts
  |  extracts subject (userId string) from token
  |  validates signature using same shared secret
  |  loads Driver from DB by userId
  |  sets SecurityContext
  |
  v
DriverController.createDriver()  <- executes only if token is valid
```

> **NOTE:**
> The shared secret means the driver-service trusts **any token issued by user-service**.
> This is a common microservice pattern (shared symmetric key). The limitation is that a
> compromised user token also grants access to driver endpoints. Consider asymmetric JWT
> (RS256) for a stricter production setup.

---

## 7. Risk Register

| # | Risk | Severity | Notes |
|---|---|---|---|
| R1 | JWT secret in plaintext in `application.yaml` | **HIGH** | Must be externalised before any cloud deployment |
| R2 | `CustomDriverDetailService` does `Long.parseLong(userId)` without try/catch | **MEDIUM** | A malformed token subject (non-numeric) will throw `NumberFormatException`, resulting in HTTP 500 instead of 401 |
| R3 | Empty password `""` in `UserDetails` | **LOW** | Benign â€” no password-based auth path exists. If Spring Security's `DaoAuthenticationProvider` is ever wired in, empty password could cause subtle failures |
| R4 | No public endpoint in `SecurityConfig` | **LOW-MEDIUM** | If a health-check endpoint (e.g., `/actuator/health`) is added later, it will require a token unless `SecurityConfig` is updated |
| R5 | `GlobalExceptionHandler.java` line endings changed by IDE | **NONE** | Functionally identical to committed version; safe to commit as-is |
| R6 | Single shared secret across services | **MEDIUM** | Rotating the secret requires redeployment of both services simultaneously |

---

## 8. Files NOT Changed

The following driver-service files were explicitly left untouched:

- `DriverController.java`
- `VehicleController.java`
- `DriverService.java`
- `VehicleService.java`
- `DriverRepository.java`
- `VehicleRepository.java`
- All `entity/` classes (`Driver`, `Vehicle`, `Availability`, `Status`, `VerificationStatus`)
- All `dto/` classes
- All `exception/` classes (logic unchanged; only line endings differ in `GlobalExceptionHandler`)
- `DriverserviceApplication.java`

**No files in `user-service` or any other service were touched.**

---

## 9. Rollback Instructions

To fully revert all changes and return to HEAD `8763aea`:

```bash
# Discard all working tree modifications to tracked files
git checkout -- microservices/driver-service/pom.xml
git checkout -- microservices/driver-service/src/main/resources/application.yaml
git checkout -- microservices/driver-service/src/main/java/com/rideshare/driverservice/exception/GlobalExceptionHandler.java

# Remove all untracked new files and directories
git clean -fd microservices/driver-service/src/main/java/com/rideshare/driverservice/config/
rm microservices/driver-service/src/main/java/com/rideshare/driverservice/service/JwtService.java
rm microservices/driver-service/src/main/java/com/rideshare/driverservice/service/CustomDriverDetailService.java
```

After rollback, the driver-service will have **no authentication** â€” all endpoints will be publicly accessible again.

---

## 10. Recommended Next Steps Before Deployment

- [ ] Commit these changes: `git add microservices/driver-service && git commit -m "driver-service: add JWT security layer"`
- [ ] Move `jwt.secret` to an environment variable (`JWT_SECRET`) and reference as `${JWT_SECRET}` in `application.yaml`
- [ ] Add try/catch around `Long.parseLong(userId)` in `CustomDriverDetailService` (see Risk R2)
- [ ] Test: call `POST /drivers` **without** a token â†’ expect `403 Forbidden`
- [ ] Test: call `POST /drivers` with a **valid** user-service token â†’ expect `201 Created`
- [ ] Test: call `POST /drivers` with an **expired/tampered** token â†’ expect `403 Forbidden`
- [ ] If actuator health checks are required, permit `/actuator/health` in `SecurityConfig`
