# FixFlow – Requirements Specification

**Project:** FixFlow – Smart Maintenance and Complaint Management Platform  
**Version:** 1.0.0  
**Status:** Draft  
**Date:** 2026-11-15  
**Author:** FixFlow Architecture Team

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Stakeholders](#2-stakeholders)
3. [Functional Requirements](#3-functional-requirements)
4. [Non-Functional Requirements](#4-non-functional-requirements)
5. [System Constraints](#5-system-constraints)
6. [Assumptions and Dependencies](#6-assumptions-and-dependencies)

---

## 1. Project Overview

**FixFlow** is a cloud-native, microservices-based Smart Maintenance and Complaint Management Platform designed for large enterprises, residential complexes, manufacturing facilities, and service organizations. It enables tenants, customers, and employees to raise maintenance requests and complaints, tracks their lifecycle from submission to resolution, dispatches field technicians, and provides management dashboards with real-time analytics.

### 1.1 Business Goals

| Goal | Description |
|------|-------------|
| Faster Resolution | Reduce average complaint resolution time by 40% |
| Transparency | Provide full lifecycle visibility to requesters |
| Automation | Auto-assign technicians based on skills, location, and availability |
| Analytics | Real-time dashboards for SLA compliance and resource utilization |
| Scalability | Support 100,000+ concurrent users across multiple facilities |

### 1.2 Scope

- Multi-tenant complaint and maintenance request lifecycle management
- Technician dispatch and field workforce management
- Real-time notifications and status updates via Kafka event streaming
- Role-based access control (Admin, Manager, Technician, Customer/Tenant)
- RESTful API-first design for web (React) and future mobile clients

---

## 2. Stakeholders

| Stakeholder | Role | Key Interest |
|-------------|------|--------------|
| System Admin | Platform administrator | User management, system health, audit logs |
| Facility Manager | Manages a facility or zone | Complaint overview, SLA tracking, technician assignments |
| Technician | Field engineer | Assigned jobs, status updates, job completion |
| Customer/Tenant | End user | Raise requests, track status, provide feedback |
| Operations Team | Ops oversight | Analytics, reports, escalation management |
| Developer/DevOps | Build and deploy | API contracts, CI/CD, infrastructure |

---

## 3. Functional Requirements

### 3.1 User Management Service

| ID | Requirement | Priority |
|----|-------------|----------|
| UM-01 | Users shall be able to self-register with name, email, phone, and role | High |
| UM-02 | Admins shall be able to create, update, deactivate, and delete users | High |
| UM-03 | System shall authenticate users via JWT-based login | High |
| UM-04 | System shall support role assignment: ADMIN, MANAGER, TECHNICIAN, CUSTOMER | High |
| UM-05 | System shall support OAuth2 / SSO integration (Google, Microsoft) | Medium |
| UM-06 | System shall enforce password complexity and account lockout after failed attempts | High |
| UM-07 | Admins shall manage tenants (organizations) and their associated users | High |
| UM-08 | System shall maintain audit logs for all user-related operations | High |
| UM-09 | Users shall be able to update their profiles and upload avatars | Medium |
| UM-10 | System shall support multi-tenancy with strict data isolation | High |

### 3.2 Complaint / Request Service

| ID | Requirement | Priority |
|----|-------------|----------|
| CR-01 | Customers shall submit complaints with title, description, category, priority, and attachments | High |
| CR-02 | System shall auto-generate a unique complaint reference number | High |
| CR-03 | System shall support complaint categories: Electrical, Plumbing, HVAC, Structural, IT, Other | High |
| CR-04 | System shall support priority levels: CRITICAL, HIGH, MEDIUM, LOW | High |
| CR-05 | Customers shall be able to track complaint status in real time | High |
| CR-06 | Customers shall be able to add comments and attachments during the lifecycle | Medium |
| CR-07 | System shall allow complaint escalation if SLA is breached | High |
| CR-08 | System shall emit Kafka events on complaint state transitions | High |
| CR-09 | Managers shall be able to reassign or reprioritize complaints | High |
| CR-10 | System shall support bulk complaint import via CSV | Low |
| CR-11 | Complaints shall have a configurable SLA timer based on priority and category | High |
| CR-12 | Customers shall receive a satisfaction survey upon complaint closure | Medium |

### 3.3 Technician / Assignment Service

| ID | Requirement | Priority |
|----|-------------|----------|
| TS-01 | System shall auto-assign complaints to available technicians based on skill match | High |
| TS-02 | Managers shall manually assign or reassign complaints to technicians | High |
| TS-03 | Technicians shall view their assigned job queue with details | High |
| TS-04 | Technicians shall update job status: ACCEPTED, IN_PROGRESS, ON_HOLD, COMPLETED | High |
| TS-05 | System shall maintain a technician skill registry (categories and certifications) | High |
| TS-06 | System shall track technician availability calendar and working hours | Medium |
| TS-07 | System shall calculate and store technician performance metrics | Medium |
| TS-08 | Technicians shall add work notes and attach photos during job execution | Medium |
| TS-09 | System shall emit Kafka events when assignments are created or status changes | High |
| TS-10 | System shall flag overloaded technicians and suggest rebalancing | Low |

### 3.4 Notification Service

| ID | Requirement | Priority |
|----|-------------|----------|
| NS-01 | System shall send email notifications on complaint creation, assignment, and resolution | High |
| NS-02 | System shall send in-app push notifications for real-time status updates | High |
| NS-03 | System shall support SMS notifications (Twilio integration) | Medium |
| NS-04 | Notification templates shall be configurable per tenant | Medium |
| NS-05 | System shall allow users to configure notification preferences | Medium |
| NS-06 | Notification service shall consume Kafka events for all notification triggers | High |
| NS-07 | System shall maintain a full notification delivery log | Medium |
| NS-08 | System shall retry failed notifications with exponential backoff | High |

### 3.5 Reporting / Analytics Service

| ID | Requirement | Priority |
|----|-------------|----------|
| RP-01 | System shall provide a management dashboard with KPIs: open complaints, SLA compliance, avg resolution time | High |
| RP-02 | System shall generate complaint volume reports (daily, weekly, monthly) | High |
| RP-03 | System shall generate technician performance reports | Medium |
| RP-04 | Reports shall be exportable in PDF and Excel formats | Medium |
| RP-05 | System shall provide trend analysis and category-wise breakdown | Medium |
| RP-06 | System shall surface SLA breach alerts in real time | High |
| RP-07 | System shall maintain an audit trail of all system events | High |
| RP-08 | Analytics data shall be near-real-time (< 30 seconds lag) | Medium |

---

## 4. Non-Functional Requirements

### 4.1 Performance

| ID | Requirement | Target |
|----|-------------|--------|
| PF-01 | API response time (95th percentile) under normal load | < 300 ms |
| PF-02 | API response time under peak load | < 1000 ms |
| PF-03 | System throughput | ≥ 1,000 requests/sec |
| PF-04 | Kafka event processing latency | < 500 ms end-to-end |
| PF-05 | Database query response time | < 100 ms (95th percentile) |
| PF-06 | File upload (attachments, images) max size | 20 MB per file |

### 4.2 Availability and Reliability

| ID | Requirement | Target |
|----|-------------|--------|
| AV-01 | System uptime SLA | 99.9% (< 8.7 hours downtime/year) |
| AV-02 | Recovery Point Objective (RPO) | < 1 hour |
| AV-03 | Recovery Time Objective (RTO) | < 30 minutes |
| AV-04 | No single point of failure for any critical service | Mandatory |
| AV-05 | Circuit breaker on all inter-service calls | Mandatory |

### 4.3 Scalability

| ID | Requirement | Target |
|----|-------------|--------|
| SC-01 | Each microservice shall scale horizontally independently | Mandatory |
| SC-02 | System shall support multi-region deployment | Medium term |
| SC-03 | Kafka partitions shall scale with load | Mandatory |
| SC-04 | Database read replicas shall be supported | Mandatory |
| SC-05 | System shall support up to 500 tenants | Mandatory |

### 4.4 Security

| ID | Requirement |
|----|-------------|
| SE-01 | All API endpoints shall be secured with JWT (RS256) |
| SE-02 | All data in transit shall be encrypted with TLS 1.3 |
| SE-03 | Sensitive data at rest (PII) shall be encrypted (AES-256) |
| SE-04 | Role-based access control (RBAC) enforced at service layer |
| SE-05 | API Gateway shall enforce rate limiting per tenant |
| SE-06 | OWASP Top 10 vulnerabilities shall be mitigated |
| SE-07 | Input validation shall be enforced on all API endpoints |
| SE-08 | Audit logs shall be tamper-evident and retained for 2 years |
| SE-09 | Multi-factor authentication (MFA) supported for Admins |
| SE-10 | Secrets shall be managed via environment variables / Vault (not hardcoded) |

### 4.5 Maintainability

| ID | Requirement |
|----|-------------|
| MT-01 | Each microservice shall have ≥ 80% unit test coverage |
| MT-02 | All APIs shall have OpenAPI 3.0 specifications |
| MT-03 | Services shall emit structured JSON logs |
| MT-04 | Distributed tracing (Zipkin/Sleuth) shall be enabled across all services |
| MT-05 | Health check endpoints (`/actuator/health`) shall be exposed by all services |
| MT-06 | Configuration shall be externalized via Spring Cloud Config |

### 4.6 Compliance

| ID | Requirement |
|----|-------------|
| CP-01 | System shall comply with GDPR for EU users (data export, deletion) |
| CP-02 | PII data handling shall follow data minimization principles |
| CP-03 | System shall maintain a complete audit trail for compliance reporting |

---

## 5. System Constraints

| Constraint | Description |
|-----------|-------------|
| Technology Stack | Java 21, Spring Boot 3.x, Spring Cloud, MySQL 8.x, Kafka, React 18, Docker, Kubernetes |
| Deployment | Containerized via Docker; orchestrated via Kubernetes |
| Communication | Synchronous via REST/HTTP; Asynchronous via Apache Kafka |
| API Style | RESTful with JSON payloads; versioned at `/api/v1/` |
| Authentication | JWT (RS256) issued by User Service; validated at API Gateway |
| Database | Separate MySQL schema per microservice (database-per-service pattern) |
| Cloud | Cloud-agnostic; primary deployment target: AWS (EKS) or GCP (GKE) |

---

## 6. Assumptions and Dependencies

### 6.1 Assumptions

- Each tenant has a unique subdomain or tenant ID in every API request
- Technician geolocation is provided by field workers' mobile devices (future mobile app)
- Email provider is configured externally (SMTP/SendGrid)
- Kafka cluster is provisioned separately (managed Kafka or Confluent Cloud)
- File attachments are stored in object storage (AWS S3 or equivalent)

### 6.2 External Dependencies

| Dependency | Purpose | Provider |
|-----------|---------|----------|
| Apache Kafka | Event streaming between microservices | Self-hosted / Confluent |
| MySQL 8.x | Persistent relational data store | AWS RDS / GCP CloudSQL |
| AWS S3 / MinIO | Attachment file storage | AWS / Self-hosted |
| SendGrid / SMTP | Email notifications | SendGrid |
| Twilio | SMS notifications | Twilio |
| Docker Registry | Container image storage | Docker Hub / ECR |
| Spring Cloud Config | Centralized configuration management | Self-hosted |
| Zipkin | Distributed tracing | Self-hosted |

---

*Document End – FixFlow Requirements Specification v1.0.0*
