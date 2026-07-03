# Ride-Sharing Platform — Execution System
### Master Plan, Learning Strategy, Risk Management & Operating Rhythm

This document turns the onboarding blueprint and dependency graph into a runnable execution system. It assumes the build order established earlier: **User → Driver → Trip → Config → Eureka → Gateway → Location/PostGIS → Matching → RabbitMQ/Notification → Payment → Docker → AWS/Monitoring.**

---

# PART 1 — Project Master Plan

Each phase has a single dominant theme. You should never be learning more than one "new category" of complexity at a time (per the Dangerous Waters analysis).

## Phase 0: Foundations Setup
**Theme:** Environment + the two real knowledge gaps (PostgreSQL/JPA, JWT)

- Milestones:
  - Repo structure decided (monorepo with multi-module Maven/Gradle, or multi-repo)
  - Local PostgreSQL running, one test entity persisted via JPA
  - One working JWT issue/validate flow in a throwaway Spring Boot app
- Expected outputs: Empty project skeleton(s) committed to GitHub; `Learning.md` and `Decisions.md` files created
- Exit criteria: You can create an entity, save/retrieve it via JPA, and issue + validate a JWT — without referring to a tutorial mid-task

## Phase 1: Core Services (Monolith-style)
**Theme:** Business logic correctness, zero infrastructure complexity

- Milestones:
  - User Service: register/login (JWT), profile CRUD
  - Driver Service: vehicle info, availability status, references User by ID
  - Trip Service: trip lifecycle states (REQUESTED → MATCHED → IN_PROGRESS → COMPLETED/CANCELLED), calls User/Driver via hardcoded URLs
- Expected outputs: 3 independently runnable Spring Boot apps, each with its own Postgres DB, basic Postman/HTTP test collection
- Exit criteria: A full manual flow — register user, register driver, create trip, manually transition trip states — works end-to-end via hardcoded URLs

## Phase 2: Config Server
**Theme:** Centralized configuration (lowest-risk infra addition)

- Milestones: All 3 services pull config (DB credentials, ports, JWT secret) from a Config Server
- Expected outputs: Config repo/folder with per-service + shared config files
- Exit criteria: Changing a config value in one place changes behavior across services without code redeploys (where Spring Cloud refresh applies)

## Phase 3: Service Discovery (Eureka)
**Theme:** Dynamic service-to-service communication

- Milestones: All 3 services register with Eureka; Trip Service calls User/Driver by logical name, not hardcoded URL
- Expected outputs: Eureka dashboard showing all 3 services healthy
- Exit criteria: You can restart any service on a different port and the others still find it correctly — without code changes

## Phase 4: API Gateway
**Theme:** Single entry point for clients

- Milestones: Gateway routes `/users/**`, `/drivers/**`, `/trips/**` to the correct services via Eureka
- Expected outputs: All client traffic now goes through one base URL
- Exit criteria: Postman collection updated to hit only the Gateway; old direct-service URLs still work internally but are no longer used externally

## Phase 5: Location Service + PostGIS
**Theme:** Geospatial data — your second major learning gap

- Milestones: Location Service stores driver coordinates, exposes "nearest drivers" query via PostGIS
- Expected outputs: Standalone, tested Location Service (registered with Eureka/Config like the others)
- Exit criteria: A query for "drivers within X km of point Y" returns correct, sorted results against seeded test data

## Phase 6: Matching Service
**Theme:** Orchestration across multiple services — first real distributed logic

- Milestones: Matching Service queries Location + Driver, assigns a driver to a trip, handles the "two trips match the same driver" race condition explicitly
- Expected outputs: Trip Service → Matching → Location/Driver flow working end-to-end through the Gateway
- Exit criteria: Concurrent trip requests don't double-assign the same driver (tested manually with parallel requests)

## Phase 7: RabbitMQ + Notification Service
**Theme:** Asynchronous, event-driven communication — your third major gap

- Milestones: Trip Service publishes "trip matched/completed" events; Notification Service consumes and logs them
- Expected outputs: RabbitMQ running locally, management UI showing live queues
- Exit criteria: Stopping Notification Service doesn't block trip creation; restarting it processes the backlog correctly

## Phase 8: Payment Service
**Theme:** Sensitive, isolated, idempotent operations

- Milestones: Fare calculation, mock/sandbox payment gateway integration, idempotent charge endpoint
- Expected outputs: Payment Service consuming "trip completed" events, producing "payment processed" events
- Exit criteria: Retrying the same charge request twice does not double-charge

## Phase 9: Dockerization
**Theme:** Containerize everything you've already proven works

- Milestones: Dockerfile per service, Docker Compose bringing up the entire stack (Postgres x N, RabbitMQ, Eureka, Config, Gateway, all services)
- Expected outputs: `docker-compose up` brings up the full system from a clean machine
- Exit criteria: A full end-to-end trip flow works purely through `docker-compose up`, no manually-started processes

## Phase 10: AWS Deployment + Monitoring
**Theme:** Production-style hosting and observability

- Milestones: Services deployed to AWS (ECS or EC2 with Docker), basic CloudWatch/Prometheus dashboards, centralized logs
- Expected outputs: Publicly reachable Gateway URL, dashboard showing live metrics
- Exit criteria: You can detect and locate a deliberately-introduced failure using only the monitoring dashboard (no SSH-and-grep)

## Phase 11: Polish & Portfolio Packaging
**Theme:** Documentation, architecture diagrams, write-up

- Milestones: README with architecture diagrams, `Architecture.md` finalized, demo video/walkthrough
- Expected outputs: A portfolio-ready repo and a written "engineering journal" narrative
- Exit criteria: A stranger (recruiter/engineer) can understand the system's architecture and your design decisions from the README alone

---

# PART 2 — Learning Sprint Plan

Each sprint maps to the phase it unlocks. **Just-in-time** — don't start a sprint until the previous phase's exit criteria are met.

### Sprint A — PostgreSQL + JPA/Hibernate (before Phase 1)
- **Learn:** Entities, repositories, relationships (`@OneToOne`, `@ManyToOne`), basic queries, transactions
- **Don't learn yet:** Advanced query optimization, multi-database transactions, Hibernate caching internals
- **Why it matters:** Every service from Phase 1 onward needs this — it's the most foundational gap
- **Effort:** 4–6 focused sessions (~1.5–2 hours each)

### Sprint B — JWT Authentication (before Phase 1)
- **Learn:** Token structure, signing/verification, stateless auth filter in Spring Security
- **Don't learn yet:** OAuth2, refresh token rotation, SSO — overkill for this project's scope
- **Why it matters:** User Service issues tokens; every other service needs to validate them
- **Effort:** 2–3 sessions

### Sprint C — Spring Cloud Config (before Phase 2)
- **Learn:** External config repos, `@RefreshScope`, profile-based config (dev/prod)
- **Don't learn yet:** Vault/encrypted secrets management — add later if needed
- **Why it matters:** Foundation for Eureka and everything after
- **Effort:** 1–2 sessions (conceptually simple, mostly setup)

### Sprint D — Eureka Service Discovery (before Phase 3)
- **Learn:** Registration, client-side discovery, `@LoadBalanced` RestTemplate/WebClient
- **Don't learn yet:** Multi-zone/multi-region discovery, custom health check tuning
- **Why it matters:** Core to the "microservices" part of microservices
- **Effort:** 2 sessions

### Sprint E — Spring Cloud Gateway (before Phase 4)
- **Learn:** Route definitions, predicates, filters, JWT validation at the gateway
- **Don't learn yet:** Custom load-balancing algorithms, advanced rate-limiting
- **Why it matters:** Single entry point; also where you'll first centralize auth enforcement
- **Effort:** 2 sessions

### Sprint F — PostGIS (before Phase 5)
- **Learn:** Geography/geometry types, `ST_DWithin`/nearest-neighbor queries, spatial indexes
- **Don't learn yet:** Complex polygon operations, routing/distance-matrix algorithms (use a Maps API for that)
- **Why it matters:** Core to Location/Matching — and the concept (spatial indexes) doesn't exist in your current knowledge
- **Effort:** 3–4 sessions

### Sprint G — Concurrency & Idempotency Patterns (before Phase 6)
- **Learn:** Optimistic locking (`@Version`), idempotency keys, basic distributed-lock concepts
- **Don't learn yet:** Distributed consensus algorithms (Raft/Paxos) — not needed at this scale
- **Why it matters:** Without this, Matching Service will have race-condition bugs that are hard to reproduce later
- **Effort:** 2–3 sessions

### Sprint H — RabbitMQ + Event-Driven Patterns (before Phase 7)
- **Learn:** Exchanges, queues, bindings, publisher confirms, consumer acknowledgment, dead-letter queues
- **Don't learn yet:** Kafka, stream processing, exactly-once semantics — RabbitMQ's at-least-once + your own idempotency is sufficient
- **Why it matters:** Core async backbone of the system
- **Effort:** 3–4 sessions

### Sprint I — Docker + Docker Compose (before Phase 9)
- **Learn:** Dockerfiles for Spring Boot (multi-stage builds), Compose networking, volumes for Postgres data
- **Don't learn yet:** Kubernetes, Helm — Compose is the right tool for this project's scale
- **Why it matters:** Final packaging step before deployment
- **Effort:** 3–4 sessions

### Sprint J — AWS Production Deployment + Monitoring (before Phase 10)
- **Learn:** ECS (or EC2 + Docker), ALB basics, RDS, CloudWatch logs/metrics, ECR
- **Don't learn yet:** Multi-region failover, auto-scaling tuning, cost optimization — note these as "future work" in your README
- **Why it matters:** Final production step; observability is what makes the system "real"
- **Effort:** 4–6 sessions

---

# PART 3 — Technology Introduction Schedule

| Technology | Introduce At | Why Not Earlier |
|---|---|---|
| **PostgreSQL + JPA** | Phase 0 (before any service) | It's the data layer for everything — no service can exist without it |
| **JWT** | Phase 0 / Phase 1 | User Service's core function is auth; needed from the first real endpoint |
| **Config Server** | Phase 2 | Needs at least 2–3 working services with *something* to centralize — introducing it on day 1 with nothing to configure makes it abstract and pointless |
| **Eureka** | Phase 3 | Needs Config Server already in place (Eureka itself benefits from centralized config) and needs multiple real services to discover — otherwise it's "magic" with no payoff |
| **Gateway** | Phase 4 | Needs Eureka so it can route by logical service name — a Gateway without discovery is just a reverse proxy with hardcoded URLs, missing the point |
| **PostGIS** | Phase 5 | Needs PostgreSQL/JPA fundamentals solid first; isolated enough to learn on its own once basics are in place |
| **RabbitMQ** | Phase 7 | Needs a *real event* to publish (trip matched/completed from Phases 1–6) — learning messaging with no concrete event to send makes the pattern feel arbitrary |
| **Docker** | Phase 9 | Needs all services functionally complete — containerizing unfinished/buggy services means debugging business logic and Docker networking simultaneously (the #1 beginner trap per the risk analysis) |
| **AWS** | Phase 10 | Needs a working Docker Compose setup that mirrors production topology — deploying to AWS before this means debugging cloud infra AND containerization AND app logic at once |
| **Monitoring** | Phase 10 (alongside AWS) | Only meaningful once there's a deployed, running system to observe — monitoring an empty local dev setup teaches you the tool but not the *judgment* of what to watch for |

**General principle behind this ordering:** every technology enters at the point where (a) you have a *concrete problem* it solves, and (b) its prerequisites are already working and trusted. This is what keeps learning "just-in-time" instead of speculative.

---

# PART 4 — Architecture Review Checklist

**Run this before writing the first line of code for any new service.**

### Ownership & Boundaries
- [ ] Does this service own its own database/schema, with no other service writing to it directly?
- [ ] Can I name this service's single core responsibility in one sentence? If it takes "and," it's probably two services.
- [ ] Is there any data here that's a *copy* of data another service owns? If so, is that copy justified (read-optimization) or is it actually a boundary violation?

### Coupling & Communication
- [ ] Does this service need to call another service *synchronously* (REST) or could this be an *event* (async)?
- [ ] If Service X is down, does this service degrade gracefully or does it hard-fail? Is that the *intended* behavior?
- [ ] Am I about to hardcode a URL, port, or hostname that should come from Config/Eureka instead?

### Consistency & Concurrency
- [ ] If two requests hit this service at the same time for the same resource, what happens? Have I thought about this explicitly?
- [ ] Does this operation need to be idempotent (safe to retry)? If yes, how is that enforced (idempotency key, unique constraint, etc.)?
- [ ] Am I assuming data from another service is "fresh" when it might be eventually consistent?

### API Design
- [ ] Are the exposed endpoints aligned with this service's responsibilities (not leaking another service's concerns)?
- [ ] Is authentication/authorization enforced at the right layer (Gateway vs. service-level)?
- [ ] Have I documented this service's API contract somewhere (even informally) before other services start depending on it?

### Future-Proofing (without over-engineering)
- [ ] If this service needs to scale independently later, does anything in this design prevent that?
- [ ] Am I adding complexity "for later" that isn't justified by a current, real requirement? (If yes — cut it. YAGNI applies even in a learning project.)

---

# PART 5 — Deployment Review Checklist

Use this **for every service, every deployment** — local Docker Compose or AWS.

## Pre-Deployment Checklist
- [ ] All tests (or at minimum, the manual end-to-end flow) pass locally without Docker first
- [ ] Service's config values (DB connection, JWT secret, broker URL) are externalized — nothing hardcoded for "this environment"
- [ ] `/actuator/health` and `/actuator/env` endpoints are enabled and return expected values
- [ ] Dependencies this service needs (DB, Eureka, Config, RabbitMQ) are confirmed running and healthy *first*
- [ ] Docker image builds successfully and runs standalone (`docker run`) before adding to Compose/AWS

## Deployment Checklist
- [ ] Deploy in dependency order: Config → Eureka → [services] → Gateway → RabbitMQ-dependent services last
- [ ] Only deploy **one new service at a time** if multiple are changing — isolate variables
- [ ] Watch logs live during startup — don't deploy-and-walk-away
- [ ] Confirm registration in Eureka dashboard (if applicable) immediately after startup

## Post-Deployment Checklist
- [ ] Hit `/actuator/health` on the deployed instance — confirm `UP`
- [ ] Run the smallest possible smoke test (e.g., one GET request) through the Gateway
- [ ] Check the relevant message broker queue (if applicable) for unexpected backlog or errors
- [ ] Check logs for any startup warnings, even if the service "came up fine" — silent config issues often log a warning, not an error
- [ ] Update `Deployment.md` with what was deployed, when, and the verification result

---

# PART 6 — Risk Register

| # | Risk | Warning Signs | Root Cause | Prevention | Recovery |
|---|---|---|---|---|---|
| 1 | Scope creep — adding features beyond the blueprint | Backlog growing faster than completed items; "wouldn't it be cool if..." moments | No defined "done" per phase | Exit criteria per phase (Part 1) are hard gates — write new ideas into a "Future Work" backlog instead | Review backlog weekly; explicitly defer items, don't silently abandon them |
| 2 | Premature infrastructure adoption (Docker/Eureka too early) | Debugging "is this my code or the infra?" constantly | Skipping the monolith-first phase | Follow Tech Introduction Schedule (Part 3) strictly | Roll back to the last working pre-infra commit; re-introduce infra one piece at a time |
| 3 | Service boundary violations (shared DB tables, leaking responsibilities) | One service's code reaching into another's data structures | Convenience during early coding | Architecture Review Checklist (Part 4) before every new service | Refactor immediately when found — don't let it compound; treat as a blocking bug |
| 4 | Race conditions in Matching Service | Same driver assigned to two trips intermittently | No concurrency control on availability updates | Sprint G (idempotency/optimistic locking) before Phase 6 | Add `@Version`/locking retroactively; write a reproduction test first |
| 5 | RabbitMQ messages silently dropped | "I published it but nothing happened," no errors | Wrong exchange/routing key, or queue not bound | Always check broker management UI first, before app code | Re-declare bindings explicitly; replay messages from dead-letter queue if configured |
| 6 | Poison messages crash consumers repeatedly | Consumer keeps restarting/crashing on the same message | No error handling / dead-letter queue configured | Configure DLQ from the start in Sprint H | Manually inspect and remove the bad message from the queue; add validation before reprocessing |
| 7 | Config drift between local and "production" (Compose vs AWS) | "Works locally, broken when deployed" | Different env files/values not tracked centrally | Single source of config (Config Server profiles), never duplicate manually | Diff actual resolved config (`/actuator/env`) between environments to find the divergence |
| 8 | Docker networking confusion (`localhost` vs service names) | "Connection refused" only inside Compose | Misunderstanding container networking | Sprint I covers this explicitly before Phase 9 | Replace `localhost` references with Compose service names; verify with `docker network inspect` |
| 9 | Eureka registration failures | "No instances available for service X" | Service starts before Eureka ready, or wrong app name | Startup ordering in Compose (`depends_on` + healthchecks) | Restart the affected service after confirming Eureka is healthy |
| 10 | JWT secret mismatch across services | Auth works on one service, fails on another with valid token | Secret not centralized via Config Server | Store JWT secret in Config Server from Phase 2 onward | Sync secret via Config Server; restart affected services |
| 11 | PostGIS query performance issues | "Nearest drivers" query slow under test load | Missing spatial index | Learn spatial indexing explicitly in Sprint F, not just geometry types | Add `GIST` index on geometry column; re-test with `EXPLAIN ANALYZE` |
| 12 | Double-charging in Payment Service | Same trip charged twice on retry | No idempotency key on charge requests | Idempotency built in from Phase 8 design, not retrofitted | Use payment gateway's idempotency key feature to detect/prevent duplicate; refund if already occurred |
| 13 | Burnout from learning too many new things at once | Avoidance of the project, frustration, "I don't understand any of this" feeling | Violating just-in-time learning — stacking multiple Level 3+ topics simultaneously | Strict adherence to Learning Sprint Plan (Part 2) and Weekly Workflow (Part 7) | Pause new learning entirely for a week; consolidate by re-doing the last working phase from memory |
| 14 | Losing the "why" behind early decisions | Can't explain in an interview why you chose X | No `Decisions.md` discipline | Log every non-trivial choice the day it's made | Reconstruct from git commit messages/PRs if needed — but this is a sign to start logging now |
| 15 | Over-engineering "for production" too early | Adding Kubernetes/Kafka/multi-region before Phase 9 | Comparing to "real" company architectures prematurely | Tech Introduction Schedule explicitly excludes these; note as Future Work | Remove the premature component; document it as a deliberate scope decision in `Decisions.md` |
| 16 | Deployment of multiple services simultaneously hides root cause | A deploy "breaks something" but unclear what | Batch deployments | Deployment Checklist: one new service at a time | Roll back all but one service; reintroduce one at a time to isolate |
| 17 | Local environment ≠ Compose ≠ AWS topology drift | Things that work in Compose fail on AWS | Different env var names, missing services in AWS setup | Keep Compose as the "spec" for what AWS must replicate | Diff Compose service list against AWS deployed services; close gaps one at a time |
| 18 | Forgetting to test failure/degradation paths | System "works" but you don't know what happens when X is down | Only testing happy paths | Add "what if this dependency is down?" to Architecture Review Checklist | Deliberately stop a dependency in Compose and observe; document actual behavior in `Architecture.md` |
| 19 | Monitoring added too late to be useful for debugging earlier issues | Past deployment issues have no log/metric trail | Monitoring deferred to "later" indefinitely | Phase 10 explicitly includes monitoring before considering the project "production-grade" | Add monitoring now even if late; treat historical gaps as a known limitation, not a blocker |
| 20 | Portfolio presentation as an afterthought | Finished system, but no coherent story for interviews | Documentation treated as optional throughout | Engineering Notebook (Part 8) maintained continuously, not written retroactively | Block dedicated time in Phase 11 to consolidate notes into a narrative — budget for this as a real phase, not a footnote |

---

# PART 7 — Weekly Engineering Workflow

A repeatable weekly rhythm — adjust days to your schedule, but keep the *structure*.

### Monday — Planning + Architecture Review
- Review last week's exit criteria — were they actually met?
- If starting a new service/component: run the **Architecture Review Checklist** (Part 4)
- Set this week's specific, scoped goal (one phase milestone, not a whole phase)

### Tuesday–Thursday — Learning + Coding (paired)
- **Just-in-time learning block first** (30–45 min): only the specific sprint topic needed for *this week's* milestone — not general study
- **Coding block** (majority of time): implement the milestone
- End each session by updating `Learning.md` with what clicked and what's still fuzzy

### Friday — Integration + Debugging
- Run the full end-to-end flow for everything built so far (not just this week's piece)
- Apply the **Debugging Playbook** (layered approach) to anything that breaks
- Update `Debugging.md` with any non-trivial issue encountered and how it was resolved

### Saturday — Deployment Day (when a milestone is deployment-ready)
- Run **Pre-Deployment → Deployment → Post-Deployment Checklists** (Part 5)
- Update `Deployment.md`

### Sunday — Documentation + Reflection (light, ~30–60 min)
- Update `Architecture.md` if anything changed structurally
- Log any decisions made this week in `Decisions.md`
- Review the **Risk Register** — any new risks surfaced? Any warning signs from existing risks observed?
- One-sentence reflection: "What's the one thing I understand better this week than last week?"

**Burnout safeguard:** if by Wednesday you haven't made progress on the weekly goal, that's a signal to *shrink the goal*, not work longer. The goal should be re-scoped, not the week extended.

---

# PART 8 — Engineering Notebook Structure

A `/docs` folder in your repo with these files, maintained continuously (per Part 7's rhythm) — not written retroactively.

### `Architecture.md`
- **Purpose:** The current, accurate picture of the system as it exists *right now*
- **Contents:** Service list with responsibilities, current architecture diagram (update the mermaid diagrams from earlier as the system evolves), data ownership map, known degradation behaviors (what happens when X is down)
- **Update frequency:** Whenever a structural change happens (new service, new dependency between services, new infra component) — same day

### `Learning.md`
- **Purpose:** Running log of what you've learned, in your own words, mapped to the Learning Sprint Plan
- **Contents:** One entry per sprint — what was learned, what's still unclear, links/resources that actually helped (vs. ones that didn't)
- **Update frequency:** End of every session that involves new learning

### `Deployment.md`
- **Purpose:** History of every deployment — local and AWS — with outcomes
- **Contents:** Date, what was deployed, checklist results, any issues found and how resolved
- **Update frequency:** Every deployment, immediately after

### `Debugging.md`
- **Purpose:** Catalog of non-trivial bugs and how they were diagnosed — your personal pattern library
- **Contents:** Symptom, which layer it was actually in (per the Debugging Playbook), root cause, fix, and "how I'd recognize this faster next time"
- **Update frequency:** Whenever a bug takes more than ~20 minutes to diagnose

### `Decisions.md`
- **Purpose:** Architecture Decision Records (ADRs) — the "why" behind non-obvious choices
- **Contents:** One entry per decision: context, options considered, decision made, why, and what would make you revisit it
- **Update frequency:** Whenever you make a choice you might later be asked "why did you do it this way?"

### `Risks.md`
- **Purpose:** Living version of the Risk Register (Part 6), specific to your actual project
- **Contents:** Status of each risk (not yet relevant / actively mitigated / occurred + how handled), plus any new project-specific risks discovered
- **Update frequency:** Weekly (Sunday reflection)

---

# PART 9 — Personal Skill Growth

### Junior → Intermediate (foundational shifts)

- **From "one database" to "data ownership"** — understanding that each service owning its data isn't bureaucracy, it's what enables independent scaling and deployment
- **From synchronous-only thinking to async-aware design** — recognizing *when* a REST call is wrong and an event is right
- **From "it compiles and runs" to "it's idempotent and handles retries"** — payment and matching logic forces this shift concretely
- **From copy-pasted config to externalized, environment-aware configuration** — Config Server makes "it works in dev but not prod" a solvable category of problem, not a mystery
- **From "the code is the documentation" to maintaining living architecture docs** — the Engineering Notebook habit
- **From PostgreSQL as "a place to store rows" to PostgreSQL with spatial awareness (PostGIS)** — a genuinely uncommon skill at the intermediate level

### Intermediate → Advanced (systems-thinking shifts)

- **From "my service works" to "my service degrades gracefully"** — designing and *testing* failure modes, not just happy paths
- **From debugging by reading code to debugging by layered isolation** — the Debugging Playbook becomes second nature; you check dashboards and health endpoints before opening an editor
- **From "I added Docker" to understanding container networking deeply enough to diagnose it under pressure**
- **From "deployed it" to "deployed it incrementally, verified at each step, with a rollback plan"** — the deployment discipline in Part 5 becomes instinct
- **From risk-blind to risk-aware project execution** — maintaining and *acting on* a living risk register is a senior/staff-level habit, not just a junior checklist exercise
- **From "I built a system" to "I can reason about the ripple effects of changing any part of it"** — this is the Section 10 success criterion from the original blueprint, and it's the single biggest junior-to-senior transformation

---

# PART 10 — Mission Control Dashboard

A Notion-style dashboard structure. Recreate this as a Notion page (or a `DASHBOARD.md` with tables, or a simple project board) — the structure matters more than the tool.

## 🎯 Current Focus
*(single line, updated weekly)*
> Phase: ___ | This week's goal: ___ | Blocked by: ___

## 📚 Learning Progress
| Sprint | Topic | Status | Notes |
|---|---|---|---|
| A | PostgreSQL + JPA | Not Started / In Progress / Done | |
| B | JWT Auth | | |
| C | Spring Cloud Config | | |
| D | Eureka | | |
| E | Spring Cloud Gateway | | |
| F | PostGIS | | |
| G | Concurrency/Idempotency | | |
| H | RabbitMQ + EDA | | |
| I | Docker/Compose | | |
| J | AWS + Monitoring | | |

## �?��? Service Progress
| Service | Phase | Code Status | Tests/Manual Flow | Registered (Eureka) | Dockerized | Deployed (AWS) |
|---|---|---|---|---|---|---|
| User Service | 1 | | | | | |
| Driver Service | 1 | | | | | |
| Trip Service | 1 | | | | | |
| Config Server | 2 | | | n/a | | |
| Eureka | 3 | | | n/a | | |
| Gateway | 4 | | | | | |
| Location Service | 5 | | | | | |
| Matching Service | 6 | | | | | |
| RabbitMQ | 7 | | | n/a | | |
| Notification Service | 7 | | | | | |
| Payment Service | 8 | | | | | |

## 🚀 Deployment Status
| Environment | Last Deployed | All Services Healthy? | Smoke Test Passed? | Notes |
|---|---|---|---|---|
| Local (Compose) | | | | |
| AWS | | | | |

## ⚠�? Risk Status
| Risk # (from Register) | Status | Last Reviewed |
|---|---|---|
| 1–20 | Not Yet Relevant / Monitoring / Mitigated / Occurred | |

*(Update during Sunday reflection — most risks stay "Not Yet Relevant" until their phase arrives)*

## 📓 Documentation Status
| File | Last Updated | Up to Date? |
|---|---|---|
| Architecture.md | | |
| Learning.md | | |
| Deployment.md | | |
| Debugging.md | | |
| Decisions.md | | |
| Risks.md | | |

## 🧭 This Week's Plan (Monday snapshot)
- [ ] Architecture review done (if new service)
- [ ] Milestone goal: ___
- [ ] Learning block scheduled (topic): ___
- [ ] Deployment planned? Y/N
- [ ] Documentation updates planned: ___

---

**How to use this system:** Part 1 tells you *where you are*. Part 2/3 tell you *what to learn next and when*. Parts 4/5 are *gates* you pass through before coding/deploying. Part 6 is what you check when something feels wrong. Part 7 is your rhythm. Part 8 is where the learning becomes permanent. Part 9 is how you'll know it worked. Part 10 is the single page you open every Monday.
