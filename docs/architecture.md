# FixFlow – System Architecture Document

**Project:** FixFlow – Smart Maintenance and Complaint Management Platform  
**Version:** 1.0.0  
**Status:** Draft  
**Date:** 2026-11-15  
**Author:** FixFlow Architecture Team

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Architecture Principles](#2-architecture-principles)
3. [Microservices Decomposition](#3-microservices-decomposition)
4. [Service Responsibilities](#4-service-responsibilities)
5. [Inter-Service Communication](#5-inter-service-communication)
6. [Technology Stack](#6-technology-stack)
7. [Infrastructure & Deployment](#7-infrastructure--deployment)
8. [Security Architecture](#8-security-architecture)
9. [Observability](#9-observability)
10. [Data Flow Diagrams](#10-data-flow-diagrams)

---

## 1. Architecture Overview

FixFlow follows a **cloud-native microservices architecture** using the **API Gateway pattern**, **Event-Driven Architecture (EDA)** via Apache Kafka, and the **Database-per-Service** pattern for strong service isolation.

```
                        ┌──────────────────────────────┐
                        │        React Frontend         │
                        │  (Web App – Port 3000)        │
                        └──────────────┬───────────────┘
                                       │ HTTPS
                        ┌──────────────▼───────────────┐
                        │       API Gateway             │
                        │  (Spring Cloud Gateway)       │
                        │  - JWT Validation             │
                        │  - Rate Limiting              │
                        │  - Load Balancing             │
                        │  - Request Routing            │
                        └──┬────┬────┬────┬────┬───────┘
                           │    │    │    │    │
           ┌───────────────┘    │    │    │    └──────────────────┐
           │              ┌─────┘    └─────┐                      │
           ▼              ▼                ▼                      ▼
   ┌────────────┐  ┌────────────┐  ┌──────────────┐    ┌─────────────────┐
   │   User     │  │ Complaint  │  │  Technician  │    │   Reporting     │
   │  Service   │  │  Service   │  │   Service    │    │   Service       │
   │  :8081     │  │  :8082     │  │   :8083      │    │   :8085         │
   └─────┬──────┘  └─────┬──────┘  └──────┬───────┘    └────────┬────────┘
         │               │                │                      │
         └───────────────┴───────┬────────┘                      │
                                 │                               │
                    ┌────────────▼──────────────┐                │
                    │      Apache Kafka          │◄───────────────┘
                    │  (Event Streaming Bus)     │
                    │  Topics:                   │
                    │  - complaint-events        │
                    │  - assignment-events       │
                    │  - notification-events     │
                    └────────────┬──────────────┘
                                 │
                    ┌────────────▼──────────────┐
                    │  Notification Service      │
                    │  :8084                     │
                    │  (Kafka Consumer)          │
                    └───────────────────────────┘
```

---

## 2. Architecture Principles

| Principle | Description |
|-----------|-------------|
| **Single Responsibility** | Each microservice owns one bounded context and is independently deployable |
| **Database per Service** | No shared databases; each service has its own MySQL schema |
| **API-First** | All capabilities exposed as versioned REST APIs with OpenAPI specs |
| **Event-Driven** | Asynchronous cross-service communication via Kafka events |
| **Fail-Safe** | Circuit breakers (Resilience4j) prevent cascading failures |
| **Stateless Services** | No server-side session state; JWT carries identity and claims |
| **Externalized Config** | All configuration via Spring Cloud Config; no hardcoded values |
| **Observability-First** | Structured logging, distributed tracing, and health checks from day one |
| **Security by Default** | Every endpoint is secured; least privilege access enforced |

---

## 3. Microservices Decomposition

| Service | Port | Database | Owns |
|---------|------|----------|------|
| API Gateway | 8080 | None | Routing, auth filter, rate limiting |
| User Service | 8081 | `fixflow_users` | Users, roles, tenants, auth tokens |
| Complaint Service | 8082 | `fixflow_complaints` | Complaints, categories, SLA, attachments |
| Technician Service | 8083 | `fixflow_technicians` | Technicians, skills, assignments, job status |
| Notification Service | 8084 | `fixflow_notifications` | Notification templates, delivery logs |
| Reporting Service | 8085 | `fixflow_reporting` | Aggregated metrics, reports, audit logs |
| Config Server | 8888 | None | Centralized configuration (Spring Cloud Config) |
| Service Registry | 8761 | None | Service discovery (Eureka) |

---

## 4. Service Responsibilities

### 4.1 API Gateway (Spring Cloud Gateway)

**Primary Role:** Single entry point for all client traffic.

**Responsibilities:**
- Route incoming HTTP requests to the appropriate downstream microservice
- Validate JWT tokens using the public key obtained from User Service
- Enforce rate limiting per tenant (using Redis token bucket)
- Apply global CORS configuration
- Strip internal headers before forwarding to services
- Aggregate and return meaningful error responses to clients
- Apply request/response logging filter for audit trails
- Load balance across multiple service instances via Eureka

**Key Dependencies:**
- Spring Cloud Gateway
- Spring Security (JWT filter)
- Redis (rate limiting state)
- Spring Cloud Eureka Client

**Routes:**
```
/api/v1/auth/**        → User Service
/api/v1/users/**       → User Service
/api/v1/complaints/**  → Complaint Service
/api/v1/technicians/** → Technician Service
/api/v1/assignments/** → Technician Service
/api/v1/reports/**     → Reporting Service
```

---

### 4.2 User Service (Port 8081)

**Primary Role:** Identity, authentication, and authorization management.

**Responsibilities:**
- **Registration & Profile Management:** User sign-up, profile update, avatar upload
- **Authentication:** Issue JWT access tokens (RS256, 15-min TTL) and refresh tokens (7-day TTL)
- **Authorization:** Role management (ADMIN, MANAGER, TECHNICIAN, CUSTOMER)
- **Tenant Management:** Create and manage tenants (organizations); enforce tenant isolation
- **Password Management:** Hashed passwords (BCrypt), forgot-password flow, account lockout
- **Token Blacklisting:** Maintain revoked tokens in Redis for immediate invalidation
- **Public Key Exposure:** Expose `/auth/public-key` endpoint for JWT validation by Gateway
- **Audit Logging:** Publish user lifecycle events to Kafka (`user-events` topic)
- **MFA:** Optional TOTP-based MFA for Admin accounts

**Key Spring Dependencies:**
- `spring-boot-starter-security`
- `spring-boot-starter-data-jpa`
- `jjwt` (JWT library)
- `spring-kafka`
- `spring-boot-starter-validation`

**Database:** `fixflow_users`

---

### 4.3 Complaint Service (Port 8082)

**Primary Role:** Full lifecycle management of maintenance complaints and requests.

**Responsibilities:**
- **Complaint CRUD:** Create, read, update, and soft-delete complaints
- **Reference Generation:** Auto-generate unique complaint reference (e.g., `FX-2026-00001`)
- **Status Machine:** Manage complaint state transitions:
  `OPEN → ASSIGNED → IN_PROGRESS → ON_HOLD → RESOLVED → CLOSED | CANCELLED | ESCALATED`
- **Category & Priority:** Categorize by type (Electrical, Plumbing, etc.) and priority (CRITICAL–LOW)
- **SLA Management:** Start SLA timer on creation; flag/escalate on SLA breach
- **Attachments:** Accept multipart file uploads; store in S3; persist metadata in DB
- **Comments:** Allow requester and assignee to add threaded comments
- **Event Publishing:** Emit Kafka events on every state transition (`complaint-events` topic)
- **Search & Filtering:** Support rich query parameters (status, category, date range, priority)

**Key Spring Dependencies:**
- `spring-boot-starter-web`
- `spring-boot-starter-data-jpa`
- `spring-kafka`
- `spring-boot-starter-validation`
- `aws-sdk-java` (S3 attachment storage)

**Database:** `fixflow_complaints`

---

### 4.4 Technician Service (Port 8083)

**Primary Role:** Technician registry, skill management, and complaint assignment lifecycle.

**Responsibilities:**
- **Technician Registry:** Maintain technician profiles, skill sets, certifications, and zones
- **Availability Management:** Track technician working hours, leave calendar, and current workload
- **Assignment Engine:** Auto-assign complaints to the best-available technician based on:
  - Skill match to complaint category
  - Current workload (open assignment count)
  - Availability status
- **Manual Assignment:** Managers can override auto-assignment
- **Job Status Updates:** Technicians update assignment status:
  `PENDING → ACCEPTED → IN_PROGRESS → ON_HOLD → COMPLETED`
- **Work Notes:** Technicians add timestamped notes and resolution descriptions
- **Performance Metrics:** Calculate and store per-technician KPIs (completion rate, avg time, SLA adherence)
- **Event Publishing:** Emit Kafka events on assignment creation and status changes (`assignment-events` topic)
- **Event Consumption:** Listen to `complaint-events` to trigger auto-assignment on `OPEN` complaints

**Key Spring Dependencies:**
- `spring-boot-starter-web`
- `spring-boot-starter-data-jpa`
- `spring-kafka`
- `resilience4j-spring-boot3` (circuit breaker for User Service calls)

**Database:** `fixflow_technicians`

---

### 4.5 Notification Service (Port 8084)

**Primary Role:** Event-driven delivery of notifications across multiple channels.

**Responsibilities:**
- **Kafka Consumer:** Subscribe to `complaint-events`, `assignment-events`, and `user-events` topics
- **Multi-Channel Delivery:**
  - **Email:** Via JavaMail / SendGrid API
  - **In-App Notifications:** Persist notifications; expose REST endpoints for client polling / WebSocket push
  - **SMS:** Via Twilio SDK (configurable per tenant)
- **Template Engine:** Manage per-tenant, per-event-type notification templates (Thymeleaf)
- **User Preference:** Respect user-configured notification preferences
- **Retry Logic:** Exponential backoff retry for failed deliveries (up to 5 attempts)
- **Delivery Log:** Persist every notification attempt with status and timestamp
- **Dead Letter Queue:** Route persistently failed notifications to DLQ for manual review

**Key Spring Dependencies:**
- `spring-kafka`
- `spring-boot-starter-mail`
- `spring-boot-starter-thymeleaf`
- `spring-boot-starter-data-jpa`
- `twilio-java` (SMS)

**Database:** `fixflow_notifications`

---

### 4.6 Reporting Service (Port 8085)

**Primary Role:** Near-real-time analytics, dashboards, and report generation.

**Responsibilities:**
- **Kafka Consumer:** Consume all event topics to build aggregated read models
- **Dashboard KPIs:** Expose APIs for:
  - Open complaint count (by category, priority, tenant)
  - SLA compliance rate
  - Average resolution time
  - Technician utilization rates
- **Report Generation:** On-demand reports in JSON, PDF (JasperReports), and Excel (Apache POI)
- **Trend Analysis:** Time-series data for complaint volume and resolution metrics
- **Audit Trail API:** Expose queryable audit event log for compliance teams
- **Scheduled Reports:** Scheduled daily/weekly report email via Kafka `notification-events`

**Key Spring Dependencies:**
- `spring-kafka`
- `spring-boot-starter-data-jpa`
- `jasperreports` (PDF reports)
- `apache-poi` (Excel export)
- `spring-boot-starter-quartz` (scheduled jobs)

**Database:** `fixflow_reporting`

---

## 5. Inter-Service Communication

### 5.1 Synchronous Communication (REST)

Used for real-time, request-response interactions where the client needs an immediate response.

| Caller | Called Service | Endpoint | Purpose |
|--------|---------------|----------|---------|
| API Gateway | User Service | `GET /auth/public-key` | Retrieve JWT public key for validation |
| Technician Service | User Service | `GET /api/v1/users/{id}` | Enrich assignment with user details |
| Reporting Service | Complaint Service | `GET /api/v1/complaints/summary` | Pull data for on-demand reports |

**Pattern:** Resilience4j Circuit Breaker wraps all synchronous inter-service calls with:
- Timeout: 3 seconds
- Circuit opens after 5 failures in 10 seconds
- Half-open retry after 10 seconds

### 5.2 Asynchronous Communication (Apache Kafka)

Used for all cross-service event notifications where decoupling is required.

#### Kafka Topics

| Topic | Partitions | Producers | Consumers | Key Events |
|-------|-----------|-----------|-----------|------------|
| `complaint-events` | 6 | Complaint Service | Technician Service, Notification Service, Reporting Service | `COMPLAINT_CREATED`, `COMPLAINT_ASSIGNED`, `COMPLAINT_STATUS_CHANGED`, `COMPLAINT_ESCALATED`, `COMPLAINT_CLOSED` |
| `assignment-events` | 4 | Technician Service | Notification Service, Reporting Service | `ASSIGNMENT_CREATED`, `ASSIGNMENT_ACCEPTED`, `ASSIGNMENT_STATUS_UPDATED`, `ASSIGNMENT_COMPLETED` |
| `user-events` | 2 | User Service | Notification Service, Reporting Service | `USER_REGISTERED`, `USER_DEACTIVATED`, `PASSWORD_RESET_REQUESTED` |
| `notification-events` | 2 | Reporting Service | Notification Service | `REPORT_READY`, `SLA_BREACH_ALERT` |

#### Kafka Event Schema (Complaint Created Example)

```json
{
  "eventId": "uuid-v4",
  "eventType": "COMPLAINT_CREATED",
  "occurredAt": "2026-11-15T17:00:00Z",
  "version": "1.0",
  "tenantId": "tenant-uuid",
  "payload": {
    "complaintId": "uuid",
    "referenceNumber": "FX-2026-00001",
    "title": "Air conditioning failure in Room 302",
    "category": "HVAC",
    "priority": "HIGH",
    "requesterId": "user-uuid",
    "createdAt": "2026-11-15T17:00:00Z",
    "slaDeadline": "2026-11-16T17:00:00Z"
  }
}
```

#### Consumer Group Strategy

| Service | Consumer Group | Offset Strategy |
|---------|---------------|-----------------|
| Technician Service | `technician-service-cg` | `earliest` |
| Notification Service | `notification-service-cg` | `latest` |
| Reporting Service | `reporting-service-cg` | `earliest` |

---

## 6. Technology Stack

| Layer | Technology | Version | Purpose |
|-------|-----------|---------|---------|
| Language | Java | 21 (LTS) | All backend services |
| Framework | Spring Boot | 3.3.x | Service scaffolding, REST, JPA |
| Gateway | Spring Cloud Gateway | 4.x | API Gateway |
| Discovery | Spring Cloud Netflix Eureka | 4.x | Service registration and discovery |
| Config | Spring Cloud Config | 4.x | Centralized configuration |
| Security | Spring Security + JWT | 6.x / JJWT | Authentication and authorization |
| ORM | Spring Data JPA + Hibernate | 6.x | Database access |
| Database | MySQL | 8.0+ | Relational data store |
| Messaging | Apache Kafka | 3.6+ | Event streaming |
| Resilience | Resilience4j | 2.x | Circuit breaker, retry, rate limiter |
| Frontend | React | 18.x | Web client |
| State Management | Redux Toolkit | 2.x | Frontend state management |
| Containerization | Docker | 24.x | Container images |
| Orchestration | Kubernetes | 1.28+ | Container orchestration |
| CI/CD | GitHub Actions | - | Build, test, deploy pipeline |
| Tracing | Micrometer + Zipkin | - | Distributed tracing |
| Logging | Logback + ELK Stack | - | Centralized log aggregation |
| Monitoring | Prometheus + Grafana | - | Metrics and alerting |

---

## 7. Infrastructure & Deployment

### 7.1 Docker Compose (Local Development)

```
fixflow-network
├── mysql-users        (fixflow_users DB)
├── mysql-complaints   (fixflow_complaints DB)
├── mysql-technicians  (fixflow_technicians DB)
├── mysql-notifications(fixflow_notifications DB)
├── mysql-reporting    (fixflow_reporting DB)
├── kafka              (Apache Kafka + Zookeeper)
├── redis              (Rate limiting / Token blacklist)
├── config-server      (:8888)
├── eureka-server      (:8761)
├── api-gateway        (:8080)
├── user-service       (:8081)
├── complaint-service  (:8082)
├── technician-service (:8083)
├── notification-service(:8084)
├── reporting-service  (:8085)
└── react-frontend     (:3000)
```

### 7.2 Kubernetes Production Layout

Each microservice is deployed as a **Kubernetes Deployment** with:
- **HorizontalPodAutoscaler (HPA):** Scale based on CPU (target 60%) and custom Kafka lag metrics
- **PodDisruptionBudget (PDB):** Minimum 1 pod always available during upgrades
- **ConfigMaps + Secrets:** Injected via environment variables
- **Liveness + Readiness Probes:** `/actuator/health/liveness` and `/actuator/health/readiness`
- **Resource Limits:** CPU and memory limits per pod

### 7.3 CI/CD Pipeline

```
Push to main/develop
       │
       ▼
  GitHub Actions
       │
  ├── Lint & Static Analysis (Checkstyle, SpotBugs)
  ├── Unit Tests (JUnit 5, Mockito)
  ├── Integration Tests (Testcontainers)
  ├── Build Docker Image
  ├── Push to Container Registry (ECR/Docker Hub)
  └── Deploy to Kubernetes (kubectl / Helm)
```

---

## 8. Security Architecture

### 8.1 Authentication Flow

```
Client                   API Gateway              User Service
  │                           │                        │
  │── POST /api/v1/auth/login ─►                        │
  │                           │── Forward to ──────────►│
  │                           │                  Validate credentials
  │                           │◄── JWT (access + refresh) ──┤
  │◄── 200 OK + JWT ──────────│                        │
  │                           │                        │
  │── GET /api/v1/complaints ─►                        │
  │   (Authorization: Bearer JWT)                      │
  │                   Validate JWT signature            │
  │                   Extract tenantId, userId, roles   │
  │                   Add X-User-* headers to request   │
  │                           │── Forward to Complaint Service
```

### 8.2 JWT Structure

```json
Header: { "alg": "RS256", "typ": "JWT" }
Payload: {
  "sub": "user-uuid",
  "tenantId": "tenant-uuid",
  "roles": ["ROLE_MANAGER"],
  "email": "user@example.com",
  "iat": 1700000000,
  "exp": 1700000900
}
```

### 8.3 RBAC Matrix

| Endpoint | ADMIN | MANAGER | TECHNICIAN | CUSTOMER |
|----------|-------|---------|------------|---------|
| POST /users | ✅ | ❌ | ❌ | ❌ |
| GET /users | ✅ | ✅ (own tenant) | ❌ | ❌ |
| POST /complaints | ✅ | ✅ | ❌ | ✅ |
| GET /complaints | ✅ | ✅ (own tenant) | ✅ (assigned) | ✅ (own) |
| PUT /complaints/{id}/assign | ✅ | ✅ | ❌ | ❌ |
| PUT /assignments/{id}/status | ✅ | ✅ | ✅ (own) | ❌ |
| GET /reports | ✅ | ✅ | ❌ | ❌ |

---

## 9. Observability

### 9.1 Logging

- All services use **Logback** with JSON structured logging format
- Each log entry includes: `traceId`, `spanId`, `tenantId`, `userId`, `serviceName`
- Logs shipped to **ELK Stack** (Elasticsearch, Logstash, Kibana) or **Cloud Logging**

### 9.2 Distributed Tracing

- **Micrometer Tracing** + **Zipkin** propagates `traceId` and `spanId` across all services
- API Gateway injects trace context into all forwarded requests
- Kafka message headers carry trace context for async correlation

### 9.3 Metrics

- Each service exposes **Prometheus** metrics via `/actuator/prometheus`
- **Grafana** dashboards for:
  - Request rate, error rate, latency (RED metrics)
  - JVM memory, GC, thread pools
  - Kafka consumer lag per topic/partition
  - SLA breach count

### 9.4 Health Checks

| Endpoint | Purpose |
|----------|---------|
| `GET /actuator/health` | Overall service health (Kubernetes liveness probe) |
| `GET /actuator/health/readiness` | Readiness (DB + Kafka connectivity) |
| `GET /actuator/info` | Build version and git commit info |
| `GET /actuator/metrics` | JVM and application metrics |

---

## 10. Data Flow Diagrams

### 10.1 Complaint Creation Flow

```
Customer (React)
   │ POST /api/v1/complaints
   ▼
API Gateway → JWT validate → route to Complaint Service
   │
Complaint Service
   ├── Validate input (Bean Validation)
   ├── Generate reference number (FX-2026-XXXXX)
   ├── Set status = OPEN, start SLA timer
   ├── Upload attachments → S3
   ├── Persist to fixflow_complaints DB
   └── Publish event → Kafka [complaint-events: COMPLAINT_CREATED]
         │
         ├──► Technician Service (Consumer)
         │       ├── Find best available technician
         │       ├── Create assignment record
         │       └── Publish → Kafka [assignment-events: ASSIGNMENT_CREATED]
         │
         ├──► Notification Service (Consumer)
         │       ├── Send email to requester (Complaint Received)
         │       └── Send email to Manager (New Complaint Alert)
         │
         └──► Reporting Service (Consumer)
                 └── Update complaint count metrics
```

### 10.2 Assignment Completion Flow

```
Technician (React)
   │ PUT /api/v1/assignments/{id}/status  { status: COMPLETED }
   ▼
Technician Service
   ├── Update assignment status = COMPLETED
   ├── Record completion timestamp, work notes
   ├── Publish → Kafka [assignment-events: ASSIGNMENT_COMPLETED]
         │
         ├──► Complaint Service (REST call)
         │       └── Update complaint status = RESOLVED
         │           └── Publish → Kafka [complaint-events: COMPLAINT_STATUS_CHANGED]
         │
         ├──► Notification Service (Consumer)
         │       └── Send resolution email to requester + satisfaction survey
         │
         └──► Reporting Service (Consumer)
                 └── Update resolution time metrics, technician KPIs
```

---

*Document End – FixFlow Architecture Document v1.0.0*
