# FixFlow – Database Design Document

**Project:** FixFlow – Smart Maintenance and Complaint Management Platform  
**Version:** 1.0.0  
**Status:** Draft  
**Date:** 2026-11-15  
**Author:** FixFlow Architecture Team  
**Database Engine:** MySQL 8.0+

---

## Table of Contents

1. [Design Principles](#1-design-principles)
2. [Database Schemas Overview](#2-database-schemas-overview)
3. [User Service Database – `fixflow_users`](#3-user-service-database--fixflow_users)
4. [Complaint Service Database – `fixflow_complaints`](#4-complaint-service-database--fixflow_complaints)
5. [Technician Service Database – `fixflow_technicians`](#5-technician-service-database--fixflow_technicians)
6. [Notification Service Database – `fixflow_notifications`](#6-notification-service-database--fixflow_notifications)
7. [Reporting Service Database – `fixflow_reporting`](#7-reporting-service-database--fixflow_reporting)
8. [Entity Relationship Overview](#8-entity-relationship-overview)
9. [Data Types & Conventions](#9-data-types--conventions)
10. [Indexing Strategy](#10-indexing-strategy)

---

## 1. Design Principles

| Principle | Description |
|-----------|-------------|
| **Database per Service** | Each microservice owns a separate MySQL schema; no cross-schema JOINs |
| **UUID Primary Keys** | All entities use `CHAR(36)` UUID primary keys to support distributed generation |
| **Soft Deletes** | Entities use `is_deleted BOOLEAN` and `deleted_at TIMESTAMP` instead of hard deletes |
| **Tenant Isolation** | All business entities include `tenant_id` for multi-tenant data isolation |
| **Audit Columns** | Every table includes `created_at`, `updated_at`, `created_by`, `updated_by` |
| **Enum as VARCHAR** | Status and type enums stored as `VARCHAR(50)` with CHECK constraints for readability |
| **Flyway Migrations** | All schema changes managed via Flyway versioned migration scripts |
| **Optimistic Locking** | Entities subject to concurrent updates include a `version` column (Hibernate `@Version`) |

---

## 2. Database Schemas Overview

| Schema | Service | Tables |
|--------|---------|--------|
| `fixflow_users` | User Service | tenants, users, roles, user_roles, refresh_tokens, audit_logs |
| `fixflow_complaints` | Complaint Service | complaints, complaint_attachments, complaint_comments, sla_configurations |
| `fixflow_technicians` | Technician Service | technicians, technician_skills, assignments, assignment_notes, technician_availability |
| `fixflow_notifications` | Notification Service | notification_templates, notifications, notification_logs |
| `fixflow_reporting` | Reporting Service | complaint_metrics, technician_metrics, audit_events, report_schedules |

---

## 3. User Service Database – `fixflow_users`

### 3.1 `tenants`

Stores top-level tenant (organization) records. All users belong to a tenant.

```sql
CREATE TABLE tenants (
    id              CHAR(36)        NOT NULL PRIMARY KEY,
    name            VARCHAR(255)    NOT NULL,
    slug            VARCHAR(100)    NOT NULL UNIQUE,          -- URL-safe identifier
    contact_email   VARCHAR(255)    NOT NULL,
    contact_phone   VARCHAR(20),
    address         TEXT,
    plan            VARCHAR(50)     NOT NULL DEFAULT 'STANDARD',  -- STANDARD, PREMIUM, ENTERPRISE
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    is_deleted      BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMP       NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_tenants_plan CHECK (plan IN ('STANDARD','PREMIUM','ENTERPRISE'))
);
```

---

### 3.2 `users`

Core user entity. Linked to a tenant. Credentials stored here.

```sql
CREATE TABLE users (
    id              CHAR(36)        NOT NULL PRIMARY KEY,
    tenant_id       CHAR(36)        NOT NULL,
    first_name      VARCHAR(100)    NOT NULL,
    last_name       VARCHAR(100)    NOT NULL,
    email           VARCHAR(255)    NOT NULL,
    phone           VARCHAR(20),
    password_hash   VARCHAR(255)    NOT NULL,
    avatar_url      VARCHAR(500),
    status          VARCHAR(50)     NOT NULL DEFAULT 'ACTIVE',
    mfa_enabled     BOOLEAN         NOT NULL DEFAULT FALSE,
    mfa_secret      VARCHAR(255),                              -- Encrypted TOTP secret
    failed_logins   INT             NOT NULL DEFAULT 0,
    locked_until    TIMESTAMP       NULL,
    last_login_at   TIMESTAMP       NULL,
    is_deleted      BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMP       NULL,
    version         INT             NOT NULL DEFAULT 0,        -- Optimistic locking
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      CHAR(36),
    updated_by      CHAR(36),
    CONSTRAINT uq_users_email_tenant UNIQUE (email, tenant_id),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE','INACTIVE','LOCKED','PENDING_VERIFICATION')),
    CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);
```

---

### 3.3 `roles`

System-level role definitions.

```sql
CREATE TABLE roles (
    id              CHAR(36)        NOT NULL PRIMARY KEY,
    name            VARCHAR(50)     NOT NULL UNIQUE,          -- ADMIN, MANAGER, TECHNICIAN, CUSTOMER
    description     VARCHAR(255),
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_roles_name CHECK (name IN ('ADMIN','MANAGER','TECHNICIAN','CUSTOMER'))
);
```

---

### 3.4 `user_roles`

Many-to-many join: a user can have multiple roles within a tenant context.

```sql
CREATE TABLE user_roles (
    user_id         CHAR(36)        NOT NULL,
    role_id         CHAR(36)        NOT NULL,
    assigned_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by     CHAR(36),
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles(id)
);
```

---

### 3.5 `refresh_tokens`

Stores issued refresh tokens for token rotation.

```sql
CREATE TABLE refresh_tokens (
    id              CHAR(36)        NOT NULL PRIMARY KEY,
    user_id         CHAR(36)        NOT NULL,
    token_hash      VARCHAR(255)    NOT NULL UNIQUE,          -- SHA-256 hash of token
    issued_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMP       NOT NULL,
    revoked         BOOLEAN         NOT NULL DEFAULT FALSE,
    revoked_at      TIMESTAMP       NULL,
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(500),
    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_rt_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_rt_expires_at ON refresh_tokens(expires_at);
```

---

### 3.6 `user_audit_logs`

Immutable audit trail for user service operations.

```sql
CREATE TABLE user_audit_logs (
    id              CHAR(36)        NOT NULL PRIMARY KEY,
    tenant_id       CHAR(36)        NOT NULL,
    actor_id        CHAR(36),                                 -- User performing the action
    action          VARCHAR(100)    NOT NULL,                 -- USER_CREATED, ROLE_ASSIGNED, etc.
    target_type     VARCHAR(100),                             -- User, Tenant, Role
    target_id       CHAR(36),
    details         JSON,
    ip_address      VARCHAR(45),
    occurred_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ual_tenant (tenant_id),
    INDEX idx_ual_actor (actor_id),
    INDEX idx_ual_occurred (occurred_at)
);
```

---

## 4. Complaint Service Database – `fixflow_complaints`

### 4.1 `sla_configurations`

Defines SLA resolution timeframes per category and priority, per tenant.

```sql
CREATE TABLE sla_configurations (
    id              CHAR(36)        NOT NULL PRIMARY KEY,
    tenant_id       CHAR(36)        NOT NULL,
    category        VARCHAR(50)     NOT NULL,
    priority        VARCHAR(20)     NOT NULL,
    resolution_hours INT            NOT NULL,                 -- Target resolution in hours
    escalation_hours INT            NOT NULL,                 -- Escalate if unresolved after N hours
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_sla_tenant_cat_pri UNIQUE (tenant_id, category, priority),
    CONSTRAINT chk_sla_category CHECK (category IN ('ELECTRICAL','PLUMBING','HVAC','STRUCTURAL','IT','CLEANING','SAFETY','OTHER')),
    CONSTRAINT chk_sla_priority CHECK (priority IN ('CRITICAL','HIGH','MEDIUM','LOW'))
);
```

---

### 4.2 `complaints`

Core complaint entity with full lifecycle tracking.

```sql
CREATE TABLE complaints (
    id                  CHAR(36)        NOT NULL PRIMARY KEY,
    tenant_id           CHAR(36)        NOT NULL,
    reference_number    VARCHAR(20)     NOT NULL UNIQUE,      -- e.g. FX-2026-00001
    title               VARCHAR(255)    NOT NULL,
    description         TEXT            NOT NULL,
    category            VARCHAR(50)     NOT NULL,
    priority            VARCHAR(20)     NOT NULL,
    status              VARCHAR(50)     NOT NULL DEFAULT 'OPEN',
    location            VARCHAR(500),                         -- Room/zone/building
    requester_id        CHAR(36)        NOT NULL,             -- Ref to users.id (User Service)
    assigned_to_id      CHAR(36),                             -- Ref to technicians.id (Technician Service)
    sla_deadline        TIMESTAMP       NULL,
    sla_breached        BOOLEAN         NOT NULL DEFAULT FALSE,
    escalated           BOOLEAN         NOT NULL DEFAULT FALSE,
    escalated_at        TIMESTAMP       NULL,
    resolved_at         TIMESTAMP       NULL,
    closed_at           TIMESTAMP       NULL,
    resolution_notes    TEXT,
    satisfaction_score  TINYINT         NULL,                 -- 1-5 rating from customer
    is_deleted          BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP       NULL,
    version             INT             NOT NULL DEFAULT 0,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by          CHAR(36)        NOT NULL,
    updated_by          CHAR(36),
    CONSTRAINT chk_complaints_category CHECK (category IN ('ELECTRICAL','PLUMBING','HVAC','STRUCTURAL','IT','CLEANING','SAFETY','OTHER')),
    CONSTRAINT chk_complaints_priority CHECK (priority IN ('CRITICAL','HIGH','MEDIUM','LOW')),
    CONSTRAINT chk_complaints_status CHECK (status IN ('OPEN','ASSIGNED','IN_PROGRESS','ON_HOLD','RESOLVED','CLOSED','CANCELLED','ESCALATED')),
    INDEX idx_complaints_tenant (tenant_id),
    INDEX idx_complaints_status (status),
    INDEX idx_complaints_requester (requester_id),
    INDEX idx_complaints_assigned (assigned_to_id),
    INDEX idx_complaints_sla (sla_deadline, sla_breached),
    INDEX idx_complaints_created (created_at)
);
```

---

### 4.3 `complaint_attachments`

Stores metadata for files attached to complaints (actual files in S3).

```sql
CREATE TABLE complaint_attachments (
    id              CHAR(36)        NOT NULL PRIMARY KEY,
    complaint_id    CHAR(36)        NOT NULL,
    tenant_id       CHAR(36)        NOT NULL,
    file_name       VARCHAR(255)    NOT NULL,
    file_type       VARCHAR(100)    NOT NULL,                 -- MIME type
    file_size_bytes BIGINT          NOT NULL,
    s3_key          VARCHAR(1000)   NOT NULL,                 -- S3 object key
    s3_bucket       VARCHAR(255)    NOT NULL,
    uploaded_by     CHAR(36)        NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ca_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(id),
    INDEX idx_ca_complaint (complaint_id)
);
```

---

### 4.4 `complaint_comments`

Threaded comments on a complaint from any stakeholder.

```sql
CREATE TABLE complaint_comments (
    id              CHAR(36)        NOT NULL PRIMARY KEY,
    complaint_id    CHAR(36)        NOT NULL,
    tenant_id       CHAR(36)        NOT NULL,
    author_id       CHAR(36)        NOT NULL,                 -- User who posted
    author_role     VARCHAR(50)     NOT NULL,
    content         TEXT            NOT NULL,
    is_internal     BOOLEAN         NOT NULL DEFAULT FALSE,   -- Internal notes hidden from customer
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_cc_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(id),
    INDEX idx_cc_complaint (complaint_id)
);
```

---

## 5. Technician Service Database – `fixflow_technicians`

### 5.1 `technicians`

Technician profile record (mirrors user data from User Service for the TECHNICIAN role).

```sql
CREATE TABLE technicians (
    id              CHAR(36)        NOT NULL PRIMARY KEY,
    tenant_id       CHAR(36)        NOT NULL,
    user_id         CHAR(36)        NOT NULL UNIQUE,          -- Ref to users.id in User Service
    employee_id     VARCHAR(50),
    zone            VARCHAR(255),                             -- Assigned physical zone/building
    status          VARCHAR(50)     NOT NULL DEFAULT 'AVAILABLE',
    max_concurrent_jobs INT         NOT NULL DEFAULT 3,
    is_deleted      BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMP       NULL,
    version         INT             NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_tech_status CHECK (status IN ('AVAILABLE','BUSY','ON_LEAVE','INACTIVE')),
    INDEX idx_tech_tenant (tenant_id),
    INDEX idx_tech_status (status)
);
```

---

### 5.2 `technician_skills`

Skills and certifications per technician (used for auto-assignment matching).

```sql
CREATE TABLE technician_skills (
    id              CHAR(36)        NOT NULL PRIMARY KEY,
    technician_id   CHAR(36)        NOT NULL,
    category        VARCHAR(50)     NOT NULL,                 -- Matches complaint category
    proficiency     VARCHAR(20)     NOT NULL DEFAULT 'INTERMEDIATE',
    certified       BOOLEAN         NOT NULL DEFAULT FALSE,
    cert_expiry     DATE            NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ts_tech_cat UNIQUE (technician_id, category),
    CONSTRAINT chk_ts_category CHECK (category IN ('ELECTRICAL','PLUMBING','HVAC','STRUCTURAL','IT','CLEANING','SAFETY','OTHER')),
    CONSTRAINT chk_ts_proficiency CHECK (proficiency IN ('BEGINNER','INTERMEDIATE','EXPERT')),
    CONSTRAINT fk_ts_technician FOREIGN KEY (technician_id) REFERENCES technicians(id)
);
```

---

### 5.3 `assignments`

Links a complaint to a technician; tracks the full job lifecycle.

```sql
CREATE TABLE assignments (
    id                  CHAR(36)        NOT NULL PRIMARY KEY,
    tenant_id           CHAR(36)        NOT NULL,
    complaint_id        CHAR(36)        NOT NULL,             -- Ref in Complaint Service
    technician_id       CHAR(36)        NOT NULL,
    assigned_by         CHAR(36)        NOT NULL,             -- Manager/system userId
    assignment_type     VARCHAR(20)     NOT NULL DEFAULT 'AUTO',  -- AUTO, MANUAL
    status              VARCHAR(50)     NOT NULL DEFAULT 'PENDING',
    priority            VARCHAR(20)     NOT NULL DEFAULT 'MEDIUM',
    scheduled_at        TIMESTAMP       NULL,
    accepted_at         TIMESTAMP       NULL,
    started_at          TIMESTAMP       NULL,
    completed_at        TIMESTAMP       NULL,
    on_hold_at          TIMESTAMP       NULL,
    on_hold_reason      TEXT,
    resolution_summary  TEXT,
    is_deleted          BOOLEAN         NOT NULL DEFAULT FALSE,
    version             INT             NOT NULL DEFAULT 0,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_assign_status CHECK (status IN ('PENDING','ACCEPTED','IN_PROGRESS','ON_HOLD','COMPLETED','CANCELLED')),
    CONSTRAINT chk_assign_type CHECK (assignment_type IN ('AUTO','MANUAL')),
    CONSTRAINT fk_assign_technician FOREIGN KEY (technician_id) REFERENCES technicians(id),
    INDEX idx_assign_technician (technician_id),
    INDEX idx_assign_complaint (complaint_id),
    INDEX idx_assign_status (status),
    INDEX idx_assign_tenant (tenant_id)
);
```

---

### 5.4 `assignment_notes`

Work notes added by technicians during job execution.

```sql
CREATE TABLE assignment_notes (
    id              CHAR(36)        NOT NULL PRIMARY KEY,
    assignment_id   CHAR(36)        NOT NULL,
    technician_id   CHAR(36)        NOT NULL,
    note_type       VARCHAR(30)     NOT NULL DEFAULT 'PROGRESS',  -- PROGRESS, ISSUE, RESOLUTION
    content         TEXT            NOT NULL,
    attachment_url  VARCHAR(1000),
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_an_assignment FOREIGN KEY (assignment_id) REFERENCES assignments(id),
    INDEX idx_an_assignment (assignment_id)
);
```

---

### 5.5 `technician_availability`

Calendar-based availability records for leave and shift management.

```sql
CREATE TABLE technician_availability (
    id              CHAR(36)        NOT NULL PRIMARY KEY,
    technician_id   CHAR(36)        NOT NULL,
    availability_type VARCHAR(20)   NOT NULL,                 -- LEAVE, SHIFT, TRAINING
    start_date      DATE            NOT NULL,
    end_date        DATE            NOT NULL,
    notes           VARCHAR(500),
    approved_by     CHAR(36),
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_ta_type CHECK (availability_type IN ('LEAVE','SHIFT','TRAINING','OTHER')),
    CONSTRAINT fk_ta_technician FOREIGN KEY (technician_id) REFERENCES technicians(id),
    INDEX idx_ta_technician_dates (technician_id, start_date, end_date)
);
```

---

## 6. Notification Service Database – `fixflow_notifications`

### 6.1 `notification_templates`

Per-tenant, per-event-type message templates (Thymeleaf syntax).

```sql
CREATE TABLE notification_templates (
    id              CHAR(36)        NOT NULL PRIMARY KEY,
    tenant_id       CHAR(36)        NOT NULL,
    event_type      VARCHAR(100)    NOT NULL,
    channel         VARCHAR(20)     NOT NULL,                 -- EMAIL, SMS, IN_APP
    subject         VARCHAR(500),                             -- Email subject (supports variables)
    body_template   TEXT            NOT NULL,                 -- Thymeleaf template
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_nt_tenant_event_channel UNIQUE (tenant_id, event_type, channel),
    CONSTRAINT chk_nt_channel CHECK (channel IN ('EMAIL','SMS','IN_APP'))
);
```

---

### 6.2 `notifications`

Each notification instance created for a user based on an event.

```sql
CREATE TABLE notifications (
    id              CHAR(36)        NOT NULL PRIMARY KEY,
    tenant_id       CHAR(36)        NOT NULL,
    recipient_id    CHAR(36)        NOT NULL,                 -- Target userId
    event_type      VARCHAR(100)    NOT NULL,
    channel         VARCHAR(20)     NOT NULL,
    subject         VARCHAR(500),
    body            TEXT            NOT NULL,
    reference_id    CHAR(36),                                 -- Related entity (complaint/assignment)
    reference_type  VARCHAR(50),                              -- COMPLAINT, ASSIGNMENT
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    read_at         TIMESTAMP       NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_notif_status CHECK (status IN ('PENDING','SENT','READ','FAILED')),
    INDEX idx_notif_recipient (recipient_id, status),
    INDEX idx_notif_tenant (tenant_id)
);
```

---

### 6.3 `notification_delivery_logs`

Full delivery audit log for every send attempt.

```sql
CREATE TABLE notification_delivery_logs (
    id              CHAR(36)        NOT NULL PRIMARY KEY,
    notification_id CHAR(36)        NOT NULL,
    attempt_number  INT             NOT NULL DEFAULT 1,
    channel         VARCHAR(20)     NOT NULL,
    recipient_address VARCHAR(500)  NOT NULL,                 -- Email/phone
    status          VARCHAR(20)     NOT NULL,                 -- SENT, FAILED, BOUNCED
    provider_response TEXT,                                   -- External API response body
    attempted_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ndl_notification FOREIGN KEY (notification_id) REFERENCES notifications(id),
    INDEX idx_ndl_notification (notification_id)
);
```

---

## 7. Reporting Service Database – `fixflow_reporting`

### 7.1 `complaint_metrics`

Pre-aggregated daily complaint statistics per tenant (materialized by Kafka consumers).

```sql
CREATE TABLE complaint_metrics (
    id                      CHAR(36)    NOT NULL PRIMARY KEY,
    tenant_id               CHAR(36)    NOT NULL,
    metric_date             DATE        NOT NULL,
    category                VARCHAR(50) NOT NULL,
    priority                VARCHAR(20) NOT NULL,
    total_opened            INT         NOT NULL DEFAULT 0,
    total_resolved          INT         NOT NULL DEFAULT 0,
    total_escalated         INT         NOT NULL DEFAULT 0,
    total_sla_breached      INT         NOT NULL DEFAULT 0,
    avg_resolution_hours    DECIMAL(10,2),
    created_at              TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_cm_tenant_date_cat_pri UNIQUE (tenant_id, metric_date, category, priority),
    INDEX idx_cm_tenant_date (tenant_id, metric_date)
);
```

---

### 7.2 `technician_metrics`

Daily performance metrics per technician.

```sql
CREATE TABLE technician_metrics (
    id                      CHAR(36)    NOT NULL PRIMARY KEY,
    tenant_id               CHAR(36)    NOT NULL,
    technician_id           CHAR(36)    NOT NULL,
    metric_date             DATE        NOT NULL,
    jobs_assigned           INT         NOT NULL DEFAULT 0,
    jobs_completed          INT         NOT NULL DEFAULT 0,
    jobs_on_hold            INT         NOT NULL DEFAULT 0,
    avg_completion_hours    DECIMAL(10,2),
    sla_compliance_rate     DECIMAL(5,2),                     -- Percentage
    created_at              TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_tm_tech_date UNIQUE (technician_id, metric_date),
    INDEX idx_tm_tenant_date (tenant_id, metric_date)
);
```

---

### 7.3 `audit_events`

Centralized audit event store aggregated from all Kafka topics.

```sql
CREATE TABLE audit_events (
    id              CHAR(36)        NOT NULL PRIMARY KEY,
    tenant_id       CHAR(36)        NOT NULL,
    event_id        CHAR(36)        NOT NULL UNIQUE,          -- Kafka event UUID (idempotency)
    event_type      VARCHAR(100)    NOT NULL,
    source_service  VARCHAR(100)    NOT NULL,
    actor_id        CHAR(36),
    entity_type     VARCHAR(100),
    entity_id       CHAR(36),
    payload         JSON,
    occurred_at     TIMESTAMP       NOT NULL,
    ingested_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ae_tenant_occurred (tenant_id, occurred_at),
    INDEX idx_ae_entity (entity_type, entity_id),
    INDEX idx_ae_event_type (event_type)
);
```

---

## 8. Entity Relationship Overview

```
fixflow_users:
  tenants (1) ──────── (*) users
  users   (*) ──────── (*) roles        [via user_roles]
  users   (1) ──────── (*) refresh_tokens

fixflow_complaints:
  complaints (1) ────── (*) complaint_attachments
  complaints (1) ────── (*) complaint_comments
  sla_configurations ── (drives SLA on) ── complaints

fixflow_technicians:
  technicians (1) ───── (*) technician_skills
  technicians (1) ───── (*) assignments
  assignments (1) ───── (*) assignment_notes
  technicians (1) ───── (*) technician_availability

fixflow_notifications:
  notification_templates ── (renders) ── notifications
  notifications      (1) ─── (*) notification_delivery_logs

fixflow_reporting:
  audit_events          [aggregated from all Kafka events]
  complaint_metrics     [pre-aggregated from complaint-events]
  technician_metrics    [pre-aggregated from assignment-events]

Cross-service references (by ID, no FK constraints):
  complaints.requester_id      → fixflow_users.users.id
  complaints.assigned_to_id    → fixflow_technicians.technicians.id
  technicians.user_id          → fixflow_users.users.id
  assignments.complaint_id     → fixflow_complaints.complaints.id
  notifications.recipient_id   → fixflow_users.users.id
```

> **Note:** Cross-schema foreign keys are **intentionally absent**. Services reference other service entities by UUID only. Data consistency is maintained through Kafka events and eventual consistency patterns.

---

## 9. Data Types & Conventions

| Column Type | Usage | MySQL Type |
|-------------|-------|-----------|
| Primary Keys | All entity IDs | `CHAR(36)` (UUID v4) |
| Names / Titles | Short text fields | `VARCHAR(255)` |
| Long text | Descriptions, notes, templates | `TEXT` |
| Enums | Status, priority, category | `VARCHAR(50)` + CHECK constraint |
| Timestamps | All date/time fields | `TIMESTAMP` (UTC stored) |
| Boolean flags | Active/deleted/enabled | `BOOLEAN` (TINYINT(1)) |
| Monetary / Rates | Percentages, metrics | `DECIMAL(10,2)` |
| JSON | Flexible payloads, audit details | `JSON` (MySQL 8+ native) |
| File sizes | Bytes | `BIGINT` |
| Counters | Version, attempt numbers | `INT` |

### Naming Conventions

- Table names: `snake_case`, plural nouns
- Column names: `snake_case`
- Primary keys: always `id`
- Foreign key columns: `{referenced_table_singular}_id`
- Boolean columns: prefix with `is_` or `has_`
- Timestamp columns: suffix with `_at`
- Index names: `idx_{table}_{columns}`
- Constraint names: `fk_{table}_{ref}`, `uq_{table}_{columns}`, `chk_{table}_{column}`

---

## 10. Indexing Strategy

### High-Priority Indexes

| Table | Index | Reason |
|-------|-------|--------|
| `users` | `(email, tenant_id)` | Login lookup |
| `complaints` | `(tenant_id, status)` | Dashboard filtering |
| `complaints` | `(sla_deadline, sla_breached)` | SLA monitoring queries |
| `complaints` | `(requester_id)` | Customer "my complaints" view |
| `assignments` | `(technician_id, status)` | Technician job queue |
| `assignments` | `(complaint_id)` | Complaint-to-assignment join |
| `notifications` | `(recipient_id, status)` | Unread notification count |
| `audit_events` | `(tenant_id, occurred_at)` | Compliance log queries |
| `refresh_tokens` | `(token_hash)` | Token validation |

### Flyway Migration File Structure

```
src/main/resources/db/migration/
├── V1__create_initial_schema.sql
├── V2__add_sla_configurations.sql
├── V3__add_audit_log_indexes.sql
├── V4__add_mfa_columns_to_users.sql
└── ...
```

---

*Document End – FixFlow Database Design Document v1.0.0*
