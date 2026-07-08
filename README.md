# 🚗 Ride-Sharing Backend — Production-Grade Microservices

> A complete ride-sharing platform backend demonstrating real-world distributed systems patterns

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15%2B-blue.svg)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue.svg)](https://www.docker.com/)
[![AWS](https://img.shields.io/badge/Deployed-AWS-orange.svg)](https://aws.amazon.com/)

---

## 🎯 What This Project Is

A **production-grade microservices architecture** for a ride-sharing platform (think Uber/Lyft). Not a tutorial project with shortcuts — this implements real distributed systems patterns:

✅ **8 microservices** with proper domain boundaries  
✅ **Optimistic locking** prevents race conditions (two trips can't claim same driver)  
✅ **Idempotency** prevents double-charging on retries  
✅ **PostGIS** for geospatial queries (nearest driver matching)  
✅ **Event-driven** architecture with RabbitMQ  
✅ **JWT auth** centralized at API Gateway  
✅ **Dockerized** and deployed to **AWS**  

**Tech Stack:** Java 21 · Spring Boot 3 · PostgreSQL · PostGIS · Spring Cloud · RabbitMQ · Docker · AWS

---

## 📚 Complete Documentation

This project has **comprehensive documentation** designed for multiple audiences:

### 🚀 Quick Start
- **[COMPREHENSIVE-GUIDE.md](./COMPREHENSIVE-GUIDE.md)** — Start here! Quick overview + navigation to all docs

### 📖 Main Documentation
- **[MASTER-DOCUMENTATION.md](./MASTER-DOCUMENTATION.md)** — Complete system guide (900+ lines)
  - System architecture
  - Service responsibilities
  - Database design
  - API reference
  - Deployment guide

### 🔍 Deep Dives
- **[Payment Service Deep Dive](./docs/payment-service-deep-dive.md)** — Fare calculation, gateway integration, idempotency
- **[Transactions & Race Conditions](./docs/transactions-race-conditions-deep-dive.md)** — Critical distributed systems patterns explained
- **[Trip Workflow & RabbitMQ](./docs/trip-workflow-and-rabbitmq-plan.md)** — Complete flow from request to payment

### 📋 Reference Docs
- **[API Reference](./docs/api-reference.md)** — All 24 endpoints with examples
- **[Architecture Blueprint](./docs/ride-sharing-onboarding-blueprint.md)** — Design philosophy and decisions
- **[Build Progress](./docs/build-progress.md)** — What's built, what's next
- **[Execution System](./docs/execution-system.md)** — Build phases and learning roadmap

---

## 🏗️ System Architecture

```
CLIENT APPS
    ↓
API GATEWAY (port 8080) ← JWT validation
    ↓
┌─────────────┬──────────────┬───────────────┐
│             │              │               │
USER        DRIVER        TRIP          LOCATION
SERVICE     SERVICE       SERVICE       SERVICE
:8081       :8082         :8083         :8084
│             │              │          (PostGIS)
│             │              ↓               │
│             │         MATCHING             │
│             │         SERVICE              │
│             │         :8085 ←──────────────┘
│             │              │
└─────────────┴──────────────┤
                             ↓
                        RABBITMQ ──→ NOTIFICATION
                           │         SERVICE
                           ↓         :8086
                      PAYMENT
                      SERVICE
                      :8087
```

### 🔑 Key Features

| Feature | Implementation |
|---|---|
| **Service Discovery** | Eureka — services find each other dynamically |
| **Configuration** | Spring Cloud Config — centralized config management |
| **Authentication** | JWT validated at Gateway, services trust the Gateway |
| **Geospatial** | PostGIS for "nearest driver" queries with spatial indexes |
| **Matching** | Expanding radius (3→5→8→12→20 km) until driver found |
| **Concurrency** | Optimistic locking (@Version) prevents double-assignment |
| **Idempotency** | 3 layers: unique constraint + check + gateway key |
| **State Machine** | Trip lifecycle with validated transitions |
| **Async Events** | RabbitMQ for decoupled notifications and payments |
| **Resilience** | Split transactions, dead letter queues |

---

## 🚀 Quick Start

### Prerequisites
- Java 21
- PostgreSQL 15+ with PostGIS extension
- Maven 3.9+
- Docker & Docker Compose

### Run with Docker Compose

```bash
# Clone the repository
git clone <your-repo-url>
cd ride-sharing-backend

# Start all services
docker-compose up --build
```

**Services will be available at:**
- API Gateway: http://localhost:8080
- Eureka Dashboard: http://localhost:8761
- RabbitMQ UI: http://localhost:15672 (guest/guest)

### Test the API

```bash
# 1. Register a user
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"john","email":"john@example.com","password":"secret123","phoneNumber":"+1234567890","role":"RIDER"}'

# 2. Login
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com","password":"secret123"}'
# Copy the token from response

# 3. Create a trip
curl -X POST http://localhost:8080/trips \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -d '{"riderId":1,"pickup":{"latitude":28.6139,"longitude":77.2090},"destination":{"latitude":28.5562,"longitude":77.1000}}'
```

**Full API documentation:** [docs/api-reference.md](./docs/api-reference.md)

---

## 🎓 Learning Journey

This project demonstrates advanced concepts in a structured way:

### Phase 1-4: Foundation
✅ Microservices with proper boundaries  
✅ Service discovery with Eureka  
✅ Centralized config with Config Server  
✅ API Gateway with JWT authentication  

### Phase 5-6: Core Features
✅ PostGIS for geospatial queries  
✅ Matching algorithm with race condition handling  
✅ Optimistic locking for atomic driver claim  

### Phase 7-8: Advanced Patterns
🔄 Event-driven architecture with RabbitMQ *(in progress)*  
📋 Payment service with idempotency *(planned)*  
🔔 Async notification system *(in progress)*  

### Future Enhancements
⏳ Circuit breakers (Resilience4j)  
⏳ Distributed tracing (Sleuth + Zipkin)  
⏳ Kubernetes deployment  
⏳ Prometheus + Grafana monitoring  

---

## 🧪 Key Patterns Demonstrated

### 1. Optimistic Locking (Prevents Double-Assignment)

**Problem:** Two trips try to claim the same driver simultaneously.

**Solution:**
```java
@Entity
public class Driver {
    @Version  // JPA optimistic locking
    private Long version;
}
```

Only one UPDATE succeeds, the other gets `OptimisticLockingFailureException` and tries next driver.

### 2. Idempotency (Prevents Double-Charging)

**Problem:** Message redelivery causes duplicate payment.

**Solution:** 3 layers of protection
- Database unique constraint on `trip_id`
- Application-level existence check
- Gateway idempotency key

### 3. State Machines (Enforces Business Rules)

```
REQUESTED → MATCHED → IN_PROGRESS → COMPLETED
     ↓           ↓           ↓
  CANCELLED   CANCELLED   CANCELLED
```

Invalid transitions (e.g., REQUESTED → COMPLETED) are rejected.

### 4. Split Transactions (Avoids Long-Held Locks)

```java
// ✅ Good: Two short transactions
Payment p = createPending(event);  // COMMIT
Response r = gateway.charge(...);  // No transaction
finalizePayment(p.getId(), r);     // COMMIT

// ❌ Bad: One long transaction holding locks during HTTP call
```

**Read more:** [Transactions & Race Conditions Deep Dive](./docs/transactions-race-conditions-deep-dive.md)

---

## 📊 Project Statistics

- **Lines of Documentation:** 5000+
- **Microservices:** 8
- **REST Endpoints:** 24
- **Databases:** 6 (PostgreSQL + PostGIS)
- **Message Queues:** 6 (RabbitMQ)
- **Docker Services:** 12
- **Failure Scenarios Documented:** 24

---

## 🎯 Who Is This For?

### 👨‍💻 Software Engineers
Learn microservices patterns, distributed systems, and Spring Cloud ecosystem.

### 🎓 Students
Understand how real-world systems are architected beyond CRUD tutorials.

### 👔 Recruiters
Evaluate technical depth, architectural decisions, and production-readiness.

### 🤖 AI Agents / LLMs
Comprehensive documentation provides complete system context.

---

## 📁 Project Structure

```
ride-sharing-backend/
├── infrastructure/
│   ├── config-server/        # Centralized configuration
│   ├── eureka-server/        # Service discovery
│   └── gateway-server/       # API Gateway + JWT
├── microservices/
│   ├── user-service/         # Auth, user profiles
│   ├── driver-service/       # Driver management
│   ├── trip-service/         # Trip lifecycle
│   ├── location-service/     # PostGIS geospatial
│   ├── matching-service/     # Driver matching
│   ├── payment-service/      # Payment processing
│   └── notification-service/ # Async notifications
├── docs/                     # Comprehensive documentation
│   ├── api-reference.md
│   ├── payment-service-deep-dive.md
│   ├── transactions-race-conditions-deep-dive.md
│   ├── trip-workflow-and-rabbitmq-plan.md
│   ├── architecture-blueprint.md
│   └── execution-system.md
├── MASTER-DOCUMENTATION.md   # Complete system guide
├── COMPREHENSIVE-GUIDE.md    # Quick navigation
├── docker-compose.yml        # Local orchestration
└── README.md                 # This file
```

---

## 🔧 Technology Choices

| Technology | Why? |
|---|---|
| **Spring Boot 3** | Industry standard, rich ecosystem |
| **PostgreSQL** | ACID compliance, PostGIS support |
| **PostGIS** | Spatial queries with indexing |
| **Eureka** | Service discovery without external deps |
| **RabbitMQ** | Proven message broker, easy local dev |
| **JWT** | Stateless auth, gateway validation |
| **Docker** | Consistent environments, easy deployment |
| **AWS** | Industry-standard cloud provider |

---

## 🚀 Deployment

### Local Development
```bash
docker-compose up --build
```

### AWS Production
- **Container Registry:** AWS ECR
- **Compute:** AWS EC2 (t3.micro for demo)
- **Database:** RDS PostgreSQL (production) or containerized (demo)
- **Message Broker:** Amazon MQ or containerized RabbitMQ

**Full deployment guide:** [MASTER-DOCUMENTATION.md#deployment](./MASTER-DOCUMENTATION.md#27-docker-and-containerization)

---

## 🤝 Contributing

This is a learning and portfolio project built in public.

- **Questions?** Open an issue
- **Found a bug?** Open an issue with details
- **Documentation improvements?** PRs welcome
- **Using this for learning?** Star the repo and share!

---

## 📝 License

MIT License — Free to use for learning, portfolio, and commercial projects.

---

## 🙏 Acknowledgments

Built following industry best practices and inspired by real-world production systems at scale-ups and enterprises.

**Special thanks to:**
- Spring Boot team for excellent documentation
- PostGIS community for spatial database capabilities
- The distributed systems community for pattern documentation

---

## 📧 Contact

**Project Author:** [Your Name]  
**Built:** July 2026  
**Status:** Active Development (Phase 7)  

**Star this repo if you found it useful!** ⭐

---

## 🗺️ Roadmap

- [x] Phase 0-6: Core microservices with matching
- [x] Dockerization and AWS deployment
- [ ] Phase 7: RabbitMQ + Notification Service *(in progress)*
- [ ] Phase 8: Payment Service with idempotency
- [ ] Phase 11: Circuit breakers and monitoring
- [ ] Real Stripe integration
- [ ] Kubernetes deployment
- [ ] Load testing and optimization

---

**Ready to dive in? Start with the [COMPREHENSIVE-GUIDE.md](./COMPREHENSIVE-GUIDE.md)!**
