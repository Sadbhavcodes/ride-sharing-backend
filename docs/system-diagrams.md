# Ride-Sharing Microservices Architecture Diagrams

These diagrams map out the core architecture, synchronous and asynchronous workflows, state machines, and event-driven data flows of the Ride-Sharing Backend. They are designed to be easily embedded in your `README.md` or used for LinkedIn posts.

## 1. System Architecture Map
This represents the production-ready topological view of the infrastructure and microservices layer.

```mermaid
graph TB
    subgraph Clients
        RiderApp[📱 Rider App]
        DriverApp[🚗 Driver App]
    end

    subgraph AWS_Edge["AWS Edge / Entry"]
        ALB[⚖️ Load Balancer]
        Gateway[🔀 API Gateway :8080]
    end

    subgraph Core_Infra["Core Infrastructure"]
        Config[⚙️ Config Server :8888]
        Eureka[🔍 Eureka Discovery :8761]
    end

    subgraph Microservices["Microservices Layer"]
        User[👤 User Service :8081]
        Driver[🚗 Driver Service :8082]
        Trip[🗺️ Trip Service :8083]
        Location[📍 Location Service :8085]
        Matching[🎯 Matching Service :8084]
        Notify[🔔 Notification Svc :8086]
        Payment[💳 Payment Svc :8087]
    end

    subgraph Data["Data Layer (PostgreSQL)"]
        UserDB[(User DB)]
        DriverDB[(Driver DB)]
        TripDB[(Trip DB)]
        LocDB[(Location DB + PostGIS)]
        PayDB[(Payment DB)]
    end

    subgraph Messaging["Event Driven Architecture"]
        Broker[(🐇 RabbitMQ :5672)]
    end

    RiderApp --> ALB
    DriverApp --> ALB
    ALB --> Gateway

    Gateway -. "lookup" .-> Eureka
    User -. "register" .-> Eureka
    Driver -. "register" .-> Eureka
    Trip -. "register" .-> Eureka
    Location -. "register" .-> Eureka
    Matching -. "register" .-> Eureka
    Payment -. "register" .-> Eureka
    Notify -. "register" .-> Eureka

    Gateway --> User
    Gateway --> Driver
    Gateway --> Trip
    Gateway --> Location

    User --> UserDB
    Driver --> DriverDB
    Trip --> TripDB
    Location --> LocDB
    Payment --> PayDB

    Trip -- "sync (Feign)" --> Matching
    Matching -- "sync (Feign)" --> Location
    Matching -- "sync (Feign)" --> Driver
    Trip -- "sync (Feign)" --> Location

    Trip -- "publish events" --> Broker
    Payment -- "publish events" --> Broker
    Broker -- "consume" --> Notify
    Broker -- "consume" --> Payment
```

---

## 2. Trip Workflow & Orchestration (Sequence Diagram)
This details the separation between the **synchronous** trip creation flow and the **asynchronous** payment and notification processing.

```mermaid
sequenceDiagram
    autonumber
    actor Rider
    participant GW as API Gateway
    participant Trip as Trip Service
    participant Match as Matching Service
    participant Loc as Location Service
    participant Driver as Driver Service
    participant MQ as RabbitMQ
    participant Pay as Payment Service

    Rider->>GW: POST /trips
    GW->>Trip: Create Trip Request
    Trip->>Match: findMatch() [Feign]
    Match->>Loc: findNearbyDrivers(lat, lng, radius) [Feign]
    Loc-->>Match: List of Drivers (PostGIS ST_DWithin)
    Match->>Driver: getAvailableDrivers() [Feign]
    Driver-->>Match: Confirmed Active/Online Drivers
    Match->>Driver: claimDriver(driverId) [Feign + Opt.Lock]
    Driver-->>Match: Success (Status = BUSY)
    Match-->>Trip: MatchResponse (driverId)
    Trip->>MQ: publish: trip.matched
    Trip-->>Rider: Trip Created (Status = MATCHED)

    Note over Rider, Driver: ... Trip in progress ...
    
    Rider->>Trip: PUT /trips/{id}/status (COMPLETED)
    Trip->>Driver: releaseDriver(driverId) [Feign]
    Trip->>Loc: calculateDistance() [Feign]
    Loc-->>Trip: Distance (Haversine formula)
    Trip->>MQ: publish: trip.completed
    Trip-->>Rider: Trip Completed response

    Note over MQ, Pay: Asynchronous Payment Processing
    MQ->>Pay: consume: trip.completed
    Pay->>Pay: Calculate Fare
    Pay->>Pay: Process via Gateway
    Pay->>MQ: publish: payment.completed
```

---

## 3. Core State Machines
Visualizing the lifecycle constraints enforced by the system for both Trips and Driver availability.

```mermaid
stateDiagram-v2
    %% Trip State Machine
    state "Trip Lifecycle (Trip Service)" as TripState {
        [*] --> REQUESTED: POST /trips
        REQUESTED --> MATCHED: Driver Found
        REQUESTED --> CANCELLED: No Driver / Rider Cancels
        MATCHED --> IN_PROGRESS: Driver Arrives & Starts
        MATCHED --> CANCELLED: Rider/Driver Cancels
        IN_PROGRESS --> COMPLETED: Destination Reached
        CANCELLED --> [*]
        COMPLETED --> [*]
    }

    %% Driver State Machine
    state "Driver Availability (Driver Service)" as DriverState {
        [*] --> OFFLINE: Initial State
        OFFLINE --> ONLINE: Driver goes online
        ONLINE --> OFFLINE: Driver goes offline
        ONLINE --> BUSY: Trip Matched (claimed)
        BUSY --> ONLINE: Trip Completed / Cancelled
    }
```

---

## 4. RabbitMQ Event-Driven Architecture
Shows how domain events are routed through Topic Exchanges to multiple distinct consumer queues.

```mermaid
graph LR
    subgraph Publishers
        Trip[🗺️ Trip Service]
        Payment[💳 Payment Service]
    end

    subgraph Exchanges
        TopicTrip{trip.events\nTopic Exchange}
        TopicPay{payment.events\nTopic Exchange}
    end

    subgraph Queues
        Q_NotifyMatch[notification.trip.matched]
        Q_NotifyCancel[notification.trip.cancelled]
        Q_NotifyComp[notification.trip.completed]
        Q_PayComp[payment.trip.completed]
        Q_NotifyPay[notification.payment.completed]
        DLQ[Dead Letter Queue]
    end

    subgraph Consumers
        Notify[🔔 Notification Service]
        PayConsumer[💳 Payment Service]
    end

    Trip -- "trip.matched" --> TopicTrip
    Trip -- "trip.cancelled" --> TopicTrip
    Trip -- "trip.completed" --> TopicTrip

    Payment -- "payment.completed" --> TopicPay

    TopicTrip -- "rk: trip.matched" --> Q_NotifyMatch
    TopicTrip -- "rk: trip.cancelled" --> Q_NotifyCancel
    TopicTrip -- "rk: trip.completed" --> Q_NotifyComp
    TopicTrip -- "rk: trip.completed" --> Q_PayComp

    TopicPay -- "rk: payment.completed" --> Q_NotifyPay

    Q_NotifyMatch --> Notify
    Q_NotifyCancel --> Notify
    Q_NotifyComp --> Notify
    Q_NotifyPay --> Notify

    Q_PayComp --> PayConsumer

    %% Error Handling
    Q_PayComp -. "failure / nack" .-> DLQ
```
