# FixFlow – API Documentation

**Project:** FixFlow – Smart Maintenance and Complaint Management Platform  
**Version:** 1.0.0  
**Status:** Draft  
**Date:** 2026-11-15  
**Author:** FixFlow Architecture Team  
**Base URL:** `https://api.fixflow.io/api/v1`  
**API Style:** RESTful JSON  
**Auth:** Bearer JWT (RS256)

---

## Table of Contents

1. [API Conventions](#1-api-conventions)
2. [Authentication APIs](#2-authentication-apis)
3. [User Management APIs](#3-user-management-apis)
4. [Complaint APIs](#4-complaint-apis)
5. [Technician APIs](#5-technician-apis)
6. [Assignment APIs](#6-assignment-apis)
7. [Notification APIs](#7-notification-apis)
8. [Reporting & Analytics APIs](#8-reporting--analytics-apis)
9. [Error Response Reference](#9-error-response-reference)
10. [Pagination & Filtering](#10-pagination--filtering)

---

## 1. API Conventions

### 1.1 Base URL Structure

```
https://api.fixflow.io/api/v1/{resource}
```

All endpoints are versioned. Future non-breaking additions will remain on `v1`; breaking changes will introduce `v2`.

### 1.2 Request Headers

| Header | Required | Description |
|--------|----------|-------------|
| `Authorization` | Yes (most endpoints) | `Bearer <JWT_ACCESS_TOKEN>` |
| `Content-Type` | Yes (write ops) | `application/json` |
| `X-Tenant-ID` | Optional | Override tenant (Admins only) |
| `X-Request-ID` | Optional | Client-provided idempotency key (UUID) |
| `Accept` | Optional | `application/json` (default) |

### 1.3 HTTP Methods

| Method | Usage |
|--------|-------|
| `GET` | Retrieve resource(s) — idempotent, cacheable |
| `POST` | Create a new resource |
| `PUT` | Full update of a resource |
| `PATCH` | Partial update of a resource |
| `DELETE` | Soft delete a resource |

### 1.4 HTTP Status Codes

| Code | Meaning |
|------|---------|
| `200 OK` | Successful read or update |
| `201 Created` | Resource successfully created |
| `204 No Content` | Successful deletion |
| `400 Bad Request` | Validation error or malformed request |
| `401 Unauthorized` | Missing or invalid JWT token |
| `403 Forbidden` | Valid token but insufficient permissions |
| `404 Not Found` | Resource not found |
| `409 Conflict` | Duplicate resource or state conflict |
| `422 Unprocessable Entity` | Business rule violation |
| `429 Too Many Requests` | Rate limit exceeded |
| `500 Internal Server Error` | Unexpected server error |

### 1.5 Standard Response Envelope

**Success:**
```json
{
  "success": true,
  "data": { ... },
  "meta": {
    "requestId": "uuid",
    "timestamp": "2026-11-15T17:00:00Z",
    "version": "1.0"
  }
}
```

**Paginated List:**
```json
{
  "success": true,
  "data": [ ... ],
  "pagination": {
    "page": 1,
    "size": 20,
    "totalElements": 150,
    "totalPages": 8,
    "hasNext": true,
    "hasPrevious": false
  },
  "meta": { ... }
}
```

**Error:**
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed",
    "details": [
      { "field": "email", "message": "must be a valid email address" }
    ]
  },
  "meta": { ... }
}
```

---

## 2. Authentication APIs

> **Service:** User Service (`/api/v1/auth`)  
> **Auth Required:** No (except token refresh and logout)

---

### `POST /api/v1/auth/register`

Register a new user account.

**Request Body:**
```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "phone": "+919876543210",
  "password": "StrongP@ss123",
  "tenantSlug": "acme-corp"
}
```

**Validation Rules:**
- `email`: valid format, unique within tenant
- `password`: min 8 chars, at least 1 uppercase, 1 digit, 1 special char
- `tenantSlug`: must be an existing, active tenant

**Response `201 Created`:**
```json
{
  "success": true,
  "data": {
    "userId": "uuid",
    "email": "john.doe@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "roles": ["CUSTOMER"],
    "tenantId": "uuid"
  }
}
```

---

### `POST /api/v1/auth/login`

Authenticate and obtain JWT tokens.

**Request Body:**
```json
{
  "email": "john.doe@example.com",
  "password": "StrongP@ss123",
  "tenantSlug": "acme-corp"
}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGc...",
    "refreshToken": "eyJhbGc...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "userId": "uuid",
      "email": "john.doe@example.com",
      "firstName": "John",
      "roles": ["CUSTOMER"],
      "tenantId": "uuid"
    }
  }
}
```

---

### `POST /api/v1/auth/refresh`

Rotate access token using a valid refresh token.

**Request Body:**
```json
{
  "refreshToken": "eyJhbGc..."
}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGc...",
    "refreshToken": "eyJhbGc...",
    "expiresIn": 900
  }
}
```

---

### `POST /api/v1/auth/logout`

🔒 **Auth Required**

Revoke the current refresh token.

**Request Body:**
```json
{
  "refreshToken": "eyJhbGc..."
}
```

**Response `204 No Content`**

---

### `POST /api/v1/auth/forgot-password`

Initiate password reset flow.

**Request Body:**
```json
{
  "email": "john.doe@example.com",
  "tenantSlug": "acme-corp"
}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "data": { "message": "Password reset email sent if account exists." }
}
```

---

### `GET /api/v1/auth/public-key`

Retrieve the RS256 public key for JWT validation (used by API Gateway).

**Response `200 OK`:**
```json
{
  "success": true,
  "data": {
    "publicKey": "-----BEGIN PUBLIC KEY-----\nMIIBIjAN...",
    "algorithm": "RS256"
  }
}
```

---

## 3. User Management APIs

> **Service:** User Service (`/api/v1/users`)  
> **Auth Required:** Yes

---

### `GET /api/v1/users`

🔒 **Roles:** ADMIN, MANAGER

List all users in the tenant with optional filtering.

**Query Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `page` | int | Page number (default: 0) |
| `size` | int | Page size (default: 20, max: 100) |
| `role` | string | Filter by role: ADMIN, MANAGER, TECHNICIAN, CUSTOMER |
| `status` | string | Filter by status: ACTIVE, INACTIVE, LOCKED |
| `search` | string | Search by name or email |

**Response `200 OK`:** Paginated list of user objects.

---

### `GET /api/v1/users/{userId}`

🔒 **Roles:** ADMIN, MANAGER (any user), TECHNICIAN/CUSTOMER (own profile only)

**Response `200 OK`:**
```json
{
  "success": true,
  "data": {
    "userId": "uuid",
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "phone": "+919876543210",
    "roles": ["TECHNICIAN"],
    "status": "ACTIVE",
    "avatarUrl": "https://cdn.fixflow.io/avatars/uuid.jpg",
    "lastLoginAt": "2026-11-15T10:00:00Z",
    "createdAt": "2026-01-01T00:00:00Z"
  }
}
```

---

### `POST /api/v1/users`

🔒 **Roles:** ADMIN

Create a user and assign roles.

**Request Body:**
```json
{
  "firstName": "Jane",
  "lastName": "Smith",
  "email": "jane.smith@example.com",
  "phone": "+919876543211",
  "roles": ["MANAGER"],
  "sendWelcomeEmail": true
}
```

**Response `201 Created`:** Created user object.

---

### `PATCH /api/v1/users/{userId}`

🔒 **Roles:** ADMIN (any user), TECHNICIAN/CUSTOMER (own profile only)

Partial update of a user profile.

**Request Body (all fields optional):**
```json
{
  "firstName": "Jane",
  "lastName": "Smith",
  "phone": "+919876543212"
}
```

**Response `200 OK`:** Updated user object.

---

### `DELETE /api/v1/users/{userId}`

🔒 **Roles:** ADMIN

Soft-delete a user account.

**Response `204 No Content`**

---

### `PUT /api/v1/users/{userId}/roles`

🔒 **Roles:** ADMIN

Replace a user's role set.

**Request Body:**
```json
{
  "roles": ["MANAGER", "TECHNICIAN"]
}
```

**Response `200 OK`:** Updated user object with new roles.

---

## 4. Complaint APIs

> **Service:** Complaint Service (`/api/v1/complaints`)  
> **Auth Required:** Yes

---

### `POST /api/v1/complaints`

🔒 **Roles:** ADMIN, MANAGER, CUSTOMER

Submit a new complaint.

**Request Body (`multipart/form-data` or `application/json`):**
```json
{
  "title": "Air conditioning failure in Room 302",
  "description": "The AC unit in Room 302 has been making a loud noise and no longer cools effectively.",
  "category": "HVAC",
  "priority": "HIGH",
  "location": "Building A, Floor 3, Room 302",
  "attachments": ["file1.jpg", "file2.pdf"]
}
```

**Response `201 Created`:**
```json
{
  "success": true,
  "data": {
    "complaintId": "uuid",
    "referenceNumber": "FX-2026-00001",
    "title": "Air conditioning failure in Room 302",
    "category": "HVAC",
    "priority": "HIGH",
    "status": "OPEN",
    "slaDeadline": "2026-11-16T17:00:00Z",
    "requesterId": "uuid",
    "createdAt": "2026-11-15T17:00:00Z"
  }
}
```

---

### `GET /api/v1/complaints`

🔒 **Roles:** ADMIN, MANAGER (tenant-wide); CUSTOMER (own only); TECHNICIAN (assigned only)

List complaints with filtering and pagination.

**Query Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `page` | int | Page number (default: 0) |
| `size` | int | Page size (default: 20) |
| `status` | string | Filter by status (comma-separated) |
| `category` | string | Filter by category |
| `priority` | string | Filter by priority |
| `slaBreached` | boolean | Filter SLA-breached complaints |
| `fromDate` | ISO date | Created on or after |
| `toDate` | ISO date | Created on or before |
| `search` | string | Search in title and description |
| `sortBy` | string | `createdAt`, `priority`, `slaDeadline` |
| `sortDir` | string | `asc`, `desc` (default: `desc`) |

**Response `200 OK`:** Paginated list of complaint summary objects.

---

### `GET /api/v1/complaints/{complaintId}`

🔒 **Roles:** ADMIN, MANAGER, relevant CUSTOMER, assigned TECHNICIAN

**Response `200 OK`:**
```json
{
  "success": true,
  "data": {
    "complaintId": "uuid",
    "referenceNumber": "FX-2026-00001",
    "title": "Air conditioning failure in Room 302",
    "description": "The AC unit in Room 302...",
    "category": "HVAC",
    "priority": "HIGH",
    "status": "IN_PROGRESS",
    "location": "Building A, Floor 3, Room 302",
    "slaDeadline": "2026-11-16T17:00:00Z",
    "slaBreached": false,
    "escalated": false,
    "requester": {
      "userId": "uuid",
      "name": "John Doe",
      "email": "john.doe@example.com"
    },
    "assignedTo": {
      "technicianId": "uuid",
      "name": "Mike Tech"
    },
    "attachments": [
      {
        "attachmentId": "uuid",
        "fileName": "room302.jpg",
        "fileType": "image/jpeg",
        "downloadUrl": "https://cdn.fixflow.io/attachments/uuid/room302.jpg"
      }
    ],
    "comments": [],
    "createdAt": "2026-11-15T17:00:00Z",
    "updatedAt": "2026-11-15T18:00:00Z"
  }
}
```

---

### `PATCH /api/v1/complaints/{complaintId}`

🔒 **Roles:** ADMIN, MANAGER

Partially update a complaint (priority, category, location).

**Request Body (all fields optional):**
```json
{
  "priority": "CRITICAL",
  "location": "Building A, Floor 3, Room 302 (updated)",
  "category": "ELECTRICAL"
}
```

**Response `200 OK`:** Updated complaint object.

---

### `PUT /api/v1/complaints/{complaintId}/status`

🔒 **Roles:** ADMIN, MANAGER (all statuses); TECHNICIAN (IN_PROGRESS, ON_HOLD, RESOLVED only)

Transition complaint status.

**Request Body:**
```json
{
  "status": "RESOLVED",
  "resolutionNotes": "Replaced faulty compressor unit. AC is now operational.",
  "satisfactionSurveyEnabled": true
}
```

**Valid Transitions:**
```
OPEN → ASSIGNED | CANCELLED
ASSIGNED → IN_PROGRESS | CANCELLED
IN_PROGRESS → ON_HOLD | RESOLVED
ON_HOLD → IN_PROGRESS | CANCELLED
RESOLVED → CLOSED | OPEN (reopen)
ESCALATED → ASSIGNED | CLOSED
```

**Response `200 OK`:** Updated complaint object.

---

### `POST /api/v1/complaints/{complaintId}/comments`

🔒 **Roles:** All authenticated users with access to the complaint

Add a comment to a complaint.

**Request Body:**
```json
{
  "content": "Parts have been ordered and will arrive by tomorrow.",
  "isInternal": false
}
```

**Response `201 Created`:** Created comment object.

---

### `POST /api/v1/complaints/{complaintId}/attachments`

🔒 **Roles:** ADMIN, MANAGER, CUSTOMER (own), TECHNICIAN (assigned)

Upload attachments to a complaint (`multipart/form-data`).

**Form Fields:**
- `files`: One or more files (max 20 MB each, max 5 files per request)

**Response `201 Created`:** List of created attachment metadata objects.

---

### `DELETE /api/v1/complaints/{complaintId}`

🔒 **Roles:** ADMIN, MANAGER

Soft-delete a complaint.

**Response `204 No Content`**

---

## 5. Technician APIs

> **Service:** Technician Service (`/api/v1/technicians`)  
> **Auth Required:** Yes

---

### `POST /api/v1/technicians`

🔒 **Roles:** ADMIN, MANAGER

Register a user as a technician with skills.

**Request Body:**
```json
{
  "userId": "uuid",
  "employeeId": "EMP-001",
  "zone": "Building A, North Wing",
  "maxConcurrentJobs": 3,
  "skills": [
    { "category": "HVAC", "proficiency": "EXPERT", "certified": true, "certExpiry": "2027-12-31" },
    { "category": "ELECTRICAL", "proficiency": "INTERMEDIATE", "certified": false }
  ]
}
```

**Response `201 Created`:** Created technician profile object.

---

### `GET /api/v1/technicians`

🔒 **Roles:** ADMIN, MANAGER

List technicians with filtering.

**Query Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `status` | string | AVAILABLE, BUSY, ON_LEAVE, INACTIVE |
| `skill` | string | Filter by skill category |
| `zone` | string | Filter by zone |
| `page` | int | Page number |
| `size` | int | Page size |

**Response `200 OK`:** Paginated list of technician summary objects.

---

### `GET /api/v1/technicians/{technicianId}`

🔒 **Roles:** ADMIN, MANAGER, TECHNICIAN (own profile)

**Response `200 OK`:** Full technician profile including skills and current stats.

---

### `PATCH /api/v1/technicians/{technicianId}`

🔒 **Roles:** ADMIN, MANAGER

Update technician profile.

**Request Body (all fields optional):**
```json
{
  "zone": "Building B, South Wing",
  "status": "AVAILABLE",
  "maxConcurrentJobs": 5
}
```

**Response `200 OK`:** Updated technician object.

---

### `PUT /api/v1/technicians/{technicianId}/skills`

🔒 **Roles:** ADMIN, MANAGER

Replace the full skill set for a technician.

**Request Body:**
```json
{
  "skills": [
    { "category": "HVAC", "proficiency": "EXPERT", "certified": true, "certExpiry": "2027-12-31" },
    { "category": "PLUMBING", "proficiency": "BEGINNER", "certified": false }
  ]
}
```

**Response `200 OK`:** Updated technician skill list.

---

### `GET /api/v1/technicians/{technicianId}/availability`

🔒 **Roles:** ADMIN, MANAGER, TECHNICIAN (own)

Retrieve availability calendar for a technician.

**Query Parameters:** `fromDate`, `toDate` (ISO date)

**Response `200 OK`:** List of availability period objects.

---

### `POST /api/v1/technicians/{technicianId}/availability`

🔒 **Roles:** ADMIN, MANAGER

Add a leave or shift record.

**Request Body:**
```json
{
  "availabilityType": "LEAVE",
  "startDate": "2026-12-24",
  "endDate": "2026-12-26",
  "notes": "Annual leave"
}
```

**Response `201 Created`:** Created availability record.

---

## 6. Assignment APIs

> **Service:** Technician Service (`/api/v1/assignments`)  
> **Auth Required:** Yes

---

### `POST /api/v1/assignments`

🔒 **Roles:** ADMIN, MANAGER

Manually create an assignment (override auto-assignment).

**Request Body:**
```json
{
  "complaintId": "uuid",
  "technicianId": "uuid",
  "scheduledAt": "2026-11-16T09:00:00Z",
  "priority": "HIGH"
}
```

**Response `201 Created`:**
```json
{
  "success": true,
  "data": {
    "assignmentId": "uuid",
    "complaintId": "uuid",
    "technicianId": "uuid",
    "assignedBy": "uuid",
    "assignmentType": "MANUAL",
    "status": "PENDING",
    "priority": "HIGH",
    "scheduledAt": "2026-11-16T09:00:00Z",
    "createdAt": "2026-11-15T17:00:00Z"
  }
}
```

---

### `GET /api/v1/assignments`

🔒 **Roles:** ADMIN, MANAGER (tenant-wide); TECHNICIAN (own queue)

List assignments with filtering.

**Query Parameters:** `status`, `technicianId`, `complaintId`, `fromDate`, `toDate`, `page`, `size`

**Response `200 OK`:** Paginated list of assignment objects.

---

### `GET /api/v1/assignments/{assignmentId}`

🔒 **Roles:** ADMIN, MANAGER, assigned TECHNICIAN

**Response `200 OK`:** Full assignment object including complaint summary and technician info.

---

### `PUT /api/v1/assignments/{assignmentId}/status`

🔒 **Roles:** ADMIN, MANAGER (all); TECHNICIAN (ACCEPTED, IN_PROGRESS, ON_HOLD, COMPLETED)

Update assignment status.

**Request Body:**
```json
{
  "status": "IN_PROGRESS",
  "note": "Starting work on the HVAC unit now."
}
```

**Valid Transitions:**
```
PENDING → ACCEPTED | CANCELLED
ACCEPTED → IN_PROGRESS | CANCELLED
IN_PROGRESS → ON_HOLD | COMPLETED
ON_HOLD → IN_PROGRESS | CANCELLED
```

**Response `200 OK`:** Updated assignment object.

---

### `POST /api/v1/assignments/{assignmentId}/notes`

🔒 **Roles:** ADMIN, MANAGER, assigned TECHNICIAN

Add a work note or progress update.

**Request Body:**
```json
{
  "noteType": "PROGRESS",
  "content": "Identified faulty capacitor in the HVAC unit. Ordering replacement.",
  "attachmentUrl": "https://cdn.fixflow.io/notes/uuid/photo.jpg"
}
```

**Response `201 Created`:** Created assignment note object.

---

### `GET /api/v1/assignments/{assignmentId}/notes`

🔒 **Roles:** ADMIN, MANAGER, assigned TECHNICIAN

**Response `200 OK`:** List of all notes for this assignment.

---

### `DELETE /api/v1/assignments/{assignmentId}`

🔒 **Roles:** ADMIN, MANAGER

Cancel and soft-delete an assignment.

**Response `204 No Content`**

---

## 7. Notification APIs

> **Service:** Notification Service (`/api/v1/notifications`)  
> **Auth Required:** Yes

---

### `GET /api/v1/notifications`

🔒 **Roles:** All authenticated users

Retrieve the authenticated user's notifications.

**Query Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `status` | string | PENDING, SENT, READ |
| `channel` | string | IN_APP, EMAIL, SMS |
| `page` | int | Page number |
| `size` | int | Page size |

**Response `200 OK`:** Paginated list of notification objects.

---

### `PATCH /api/v1/notifications/{notificationId}/read`

🔒 **Roles:** Notification recipient only

Mark a single notification as read.

**Response `200 OK`:**
```json
{
  "success": true,
  "data": {
    "notificationId": "uuid",
    "status": "READ",
    "readAt": "2026-11-15T18:00:00Z"
  }
}
```

---

### `PATCH /api/v1/notifications/read-all`

🔒 **Roles:** All authenticated users

Mark all unread in-app notifications as read.

**Response `200 OK`:**
```json
{
  "success": true,
  "data": { "markedReadCount": 12 }
}
```

---

### `GET /api/v1/notifications/unread-count`

🔒 **Roles:** All authenticated users

**Response `200 OK`:**
```json
{
  "success": true,
  "data": { "unreadCount": 5 }
}
```

---

### `GET /api/v1/notifications/templates`

🔒 **Roles:** ADMIN

List all notification templates for the tenant.

**Response `200 OK`:** List of template objects.

---

### `PUT /api/v1/notifications/templates/{templateId}`

🔒 **Roles:** ADMIN

Update a notification template.

**Request Body:**
```json
{
  "subject": "Your complaint {{referenceNumber}} has been assigned",
  "bodyTemplate": "<p>Dear {{firstName}},</p><p>Your complaint has been assigned to {{technicianName}}.</p>"
}
```

**Response `200 OK`:** Updated template object.

---

## 8. Reporting & Analytics APIs

> **Service:** Reporting Service (`/api/v1/reports`)  
> **Auth Required:** Yes  
> **Roles:** ADMIN, MANAGER

---

### `GET /api/v1/reports/dashboard`

Retrieve KPI summary for the management dashboard.

**Query Parameters:** `fromDate`, `toDate`

**Response `200 OK`:**
```json
{
  "success": true,
  "data": {
    "totalOpenComplaints": 42,
    "totalResolvedToday": 15,
    "slaComplianceRate": 87.5,
    "avgResolutionHours": 18.3,
    "escalatedComplaints": 3,
    "technicianUtilizationRate": 72.4,
    "complaintsByCategory": {
      "HVAC": 18,
      "ELECTRICAL": 12,
      "PLUMBING": 7,
      "OTHER": 5
    },
    "complaintsByPriority": {
      "CRITICAL": 3,
      "HIGH": 15,
      "MEDIUM": 18,
      "LOW": 6
    }
  }
}
```

---

### `GET /api/v1/reports/complaints`

Generate a complaint volume report.

**Query Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `fromDate` | ISO date | Start date |
| `toDate` | ISO date | End date |
| `groupBy` | string | `day`, `week`, `month` |
| `category` | string | Filter by category |
| `format` | string | `json` (default), `pdf`, `excel` |

**Response `200 OK`:** Report data or file download for PDF/Excel.

---

### `GET /api/v1/reports/technicians`

Generate a technician performance report.

**Query Parameters:** `fromDate`, `toDate`, `technicianId`, `format`

**Response `200 OK`:**
```json
{
  "success": true,
  "data": {
    "period": { "from": "2026-11-01", "to": "2026-11-15" },
    "technicians": [
      {
        "technicianId": "uuid",
        "name": "Mike Tech",
        "jobsAssigned": 25,
        "jobsCompleted": 23,
        "avgCompletionHours": 14.2,
        "slaComplianceRate": 91.3,
        "satisfactionScore": 4.6
      }
    ]
  }
}
```

---

### `GET /api/v1/reports/sla-breaches`

List all SLA-breached complaints within a date range.

**Query Parameters:** `fromDate`, `toDate`, `category`, `page`, `size`

**Response `200 OK`:** Paginated list of SLA breach records.

---

### `GET /api/v1/reports/audit-log`

Query the audit event log.

**Query Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `eventType` | string | Filter by event type |
| `actorId` | UUID | Filter by user who performed the action |
| `entityType` | string | COMPLAINT, ASSIGNMENT, USER |
| `entityId` | UUID | Filter by specific entity |
| `fromDate` | ISO datetime | Start timestamp |
| `toDate` | ISO datetime | End timestamp |
| `page` | int | Page number |
| `size` | int | Page size |

**Response `200 OK`:** Paginated list of audit event objects.

---

## 9. Error Response Reference

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| `VALIDATION_ERROR` | 400 | One or more request fields failed validation |
| `INVALID_JWT` | 401 | JWT is missing, malformed, or expired |
| `INSUFFICIENT_PERMISSIONS` | 403 | User lacks required role for this operation |
| `RESOURCE_NOT_FOUND` | 404 | The requested resource does not exist |
| `DUPLICATE_RESOURCE` | 409 | Unique constraint violated (e.g., email already exists) |
| `INVALID_STATE_TRANSITION` | 422 | The requested status change is not allowed from the current status |
| `SLA_CONFIG_MISSING` | 422 | No SLA configuration found for the complaint's category/priority |
| `TECHNICIAN_UNAVAILABLE` | 422 | No available technician found for auto-assignment |
| `RATE_LIMIT_EXCEEDED` | 429 | Too many requests from this tenant/IP |
| `INTERNAL_ERROR` | 500 | Unexpected server-side error |

**Example Error Response:**
```json
{
  "success": false,
  "error": {
    "code": "INVALID_STATE_TRANSITION",
    "message": "Cannot transition complaint from CLOSED to IN_PROGRESS",
    "details": [
      {
        "field": "status",
        "message": "Transition from CLOSED to IN_PROGRESS is not permitted"
      }
    ]
  },
  "meta": {
    "requestId": "uuid",
    "timestamp": "2026-11-15T17:00:00Z",
    "traceId": "abc123def456"
  }
}
```

---

## 10. Pagination & Filtering

### Pagination Request Parameters

| Parameter | Default | Max | Description |
|-----------|---------|-----|-------------|
| `page` | `0` | — | Zero-based page index |
| `size` | `20` | `100` | Number of records per page |
| `sortBy` | `createdAt` | — | Field to sort by |
| `sortDir` | `desc` | — | Sort direction: `asc` or `desc` |

### Pagination Response Fields

```json
"pagination": {
  "page": 0,
  "size": 20,
  "totalElements": 150,
  "totalPages": 8,
  "hasNext": true,
  "hasPrevious": false,
  "first": true,
  "last": false
}
```

### Filtering Conventions

- **Comma-separated multi-value:** `?status=OPEN,IN_PROGRESS`
- **Date range:** `?fromDate=2026-11-01&toDate=2026-11-15`
- **Text search:** `?search=air+conditioning` (applied to searchable text fields)
- **Boolean filters:** `?slaBreached=true`
- **Enum filters:** Exact match, case-insensitive

### Rate Limiting

| Tier | Limit |
|------|-------|
| Default (all tenants) | 1,000 requests / minute per tenant |
| Auth endpoints | 10 login attempts / minute per IP |
| File upload | 50 uploads / hour per user |
| Report generation | 10 reports / hour per user |

Rate limit headers returned on every response:
```
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 847
X-RateLimit-Reset: 1700000060
```

---

*Document End – FixFlow API Documentation v1.0.0*
