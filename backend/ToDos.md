<!-- Build and improve a multi-tenant merchant platform with the following requirements:

1. File Storage (Images & PDFs)

* Ensure that images and PDF documents (e.g., proof of payment) are stored in the database.
* Files must be accessible across multiple devices and sessions.
* Provide secure retrieval endpoints for these files.

2. Payment Verification Workflow

* Merchants and staff users must be able to view uploaded proof of payment before approving any transaction.
* Implement a review/approval flow where payment status depends on manual verification.

3. Booking Slot Alignment with Operating Hours

* Time slots must strictly align with the merchant’s configured operating hours.
* The first available slot must start exactly at the opening time (e.g., 07:00).
* The last slot must end exactly at the closing time (e.g., 15:00).
* Ensure that all generated slots respect these boundaries.

4. Employee Availability Constraints

* Time slots must consider employee availability schedules.
* A slot should only be available if at least one employee is available during that time.

5. Slot Capacity Based on Staff Count

* Each time slot must allow bookings up to the number of available employees.
* Example: If 5 employees are available for a slot, a maximum of 5 bookings can be made for that slot.
* Prevent overbooking beyond employee capacity.

Ensure the system is scalable, consistent, and enforces all constraints at both the API and database levels.
 -->

 <!-- Design and implement a robust employee management and payroll system with the following requirements:

## 1. Input Validation (Frontend & Backend)

* All user inputs must be validated on both the frontend and backend.
* Backend validation is mandatory and must not rely only on frontend checks.
* Validation should include required fields, correct data types, valid ranges, and business rules.

---

## 2. Edit Forms with Prepopulated Data

* When editing any entity (e.g., employees, schedules, payments), all form fields must be prepopulated with the existing stored values.
* Users should be able to modify only the necessary fields while retaining existing data integrity.

---

## 3. Employee Payment Configuration

When creating an employee:

* The system must allow selection of a payment type:

  * Hourly
  * Daily
  * Weekly (optional if needed)
* A payment rate must be specified (e.g., R50/hour or R300/day).
* This rate will be used for all payroll calculations.

---

## 4. Payroll Calculation & Status Tracking

* The system must calculate employee payments based on:

  * Payment type (hourly/daily)
  * Rate
  * Attendance / scheduled working time
* Each payment record must have a status:

  * Paid
  * Unpaid

---

## 5. Employee Off Days & Scheduling

* The system must support assigning off days to employees using schedules.
* Off days must be excluded from payroll calculations.
* Employee availability must be considered when calculating working periods.

---

## 6. Weekly Payroll Processing Logic

* If payroll is processed weekly:

  * The system must evaluate employee schedules for that week.
  * Only working days and valid scheduled hours must be counted.
  * Payments must be calculated based on actual availability and worked time.
  * Final computed amounts must be generated per employee for that period.

---

## 7. System Requirements

* Ensure data consistency between scheduling, attendance, and payroll.
* Maintain clear separation between:

  * Employee data
  * Scheduling system
  * Payroll calculation engine
* Ensure scalability for multiple employees and merchants.

The system must be reliable, auditable, and consistent across all calculations and validations.
 -->

 <!-- Design and implement a service management feature for a merchant platform with the following requirements:

## 1. Service Duration Input Rules

* When a merchant creates a service, the **duration field must include a unit suffix**:

  * `h` = hours (e.g., `2h`)
  * `m` = minutes (e.g., `30m`)
* The system must validate that every duration value ends with either `h` or `m`.
* Invalid formats must be rejected on both frontend and backend.

---

## 2. Duration Conversion Logic

* The system must automatically convert all service durations into a consistent internal format used for slot scheduling.
* Example rules:

  * Hours (`h`) must be converted into minutes (e.g., `2h = 120 minutes`)
  * Minutes (`m`) remain as-is (e.g., `30m = 30 minutes`)
* This normalized value will be used for:

  * Slot generation
  * Scheduling calculations
  * Availability matching

---

## 3. Slot Scaling Integration

* Converted service duration must directly determine how time slots are allocated.
* Slot creation logic must ensure services fit correctly within operating hours based on their converted duration.
* Prevent overlapping or invalid slot allocations.

---

## 4. Service Deletion

* Merchants must be able to delete services.
* Deletion should:

  * Remove the service from active listings
  * Prevent new bookings for that service
  * Preserve historical booking data if needed for reporting/auditing (soft delete preferred)

---

## 5. System Requirements

* Validation must exist on both frontend and backend.
* All duration parsing and conversion must be handled securely on the backend as the source of truth.
* Ensure consistency between:

  * Service definitions
  * Slot generation system
  * Booking engine

The system must remain scalable, reliable, and consistent when handling scheduling and service lifecycle changes.
 -->

 <!-- 
 Design and implement a **merchant-specific dashboard system** where all UI and data are dynamically tailored based on the merchant type.

---

## 1. Merchant-Type Based Dashboard

* The dashboard must detect the merchant type on login (e.g., Salon, Store, or other business types).
* The UI must dynamically adapt based on this merchant type.
* No generic dashboard content should be shown.

---

## 2. Salon-Specific Dashboard

If the merchant type is **Salon**:

* Show only salon-related features and modules.
* Populate dashboard cards with salon-specific data such as:

  * Appointments
  * Staff schedules
  * Services offered
  * Daily bookings
* Ensure all metrics reflect salon operations only.

---

## 3. Store-Specific Dashboard

If the merchant type is **Store**:

* Show only store-related features and modules.
* Populate dashboard cards with store-specific data such as:

  * Products
  * Orders
  * Inventory levels
  * Sales performance
* Ensure all metrics reflect retail/store operations only.

---

## 4. Insights Section (Dynamic Analytics)

* The "Insights" section must be fully dynamic and merchant-aware.
* Display analytics relevant only to the logged-in merchant’s business type.

  * Salon: bookings trends, peak hours, staff utilization, service popularity
  * Store: sales trends, best-selling products, stock alerts, revenue insights
* No cross-merchant or irrelevant data should be shown.

---

## 5. System-Wide Requirement: Merchant Isolation

* The entire system must be **merchant-aware and tenant-isolated**.
* All data queries must be scoped to the logged-in merchant only.
* Ensure strict separation of data between merchants (multi-tenant architecture).
* No merchant should ever access another merchant’s data.

---

## 6. UI/UX Behavior

* Dashboard components must be conditionally rendered based on merchant type.
* Cards, charts, and widgets must be dynamically populated from backend APIs.
* Ensure consistent structure but variable content depending on merchant category.

---

## 7. Backend Requirements

* All APIs must filter data using merchant ID / tenant ID.
* Business logic must enforce merchant-level isolation.
* Analytics endpoints must return only merchant-specific aggregated data.

---

The system must be fully multi-tenant, secure, and context-aware, ensuring each merchant only sees data relevant to their business type and operations.
 -->

 <!-- 🧠 AI SYSTEM DESIGN PROMPT — MULTI-TENANT MERCHANT PLATFORM (SALON / STORE / BOTH + SUBSCRIPTIONS)

You are working on an existing live multi-tenant merchant platform. The system must be enhanced safely without breaking existing functionality.

The platform supports:

SALON
STORE
BOTH (hybrid merchant)
Subscription-based access control (NEW)

Everything must remain tenant-isolated, scalable, and backend-driven.

⚠️ CORE RULES (NON-NEGOTIABLE)
System is already live → no breaking changes allowed
All data must be strictly tenant-scoped
Backend is the source of truth for permissions and limits
Frontend only reflects state (no business logic enforcement)
No cross-merchant or cross-subscription leakage
Subscription rules must be enforced on backend
1. SUBSCRIPTION SYSTEM (NEW CORE FEATURE)
1.1 Navigation Addition

Add a new nav item:

Subscription Tab
1.2 Subscription Plans

The merchant can subscribe to:

BASIC PLAN

Restrictions:

Maximum 1 employee only
NO access to:
Insights dashboard
Employee payroll module
Limited analytics only (basic summary only)
PRO PLAN

Full access:

Unlimited employees
Full payroll system
Full insights dashboard
Full booking + store + salon features (based on merchant type)
1.3 Subscription Enforcement Rules (CRITICAL)

All restrictions MUST be enforced in backend:

Cannot create more than 1 employee under BASIC plan
Cannot access insights endpoints under BASIC plan
Cannot access payroll calculations under BASIC plan
Upgrade immediately unlocks features without data loss
1.4 Subscription State Model

Each merchant must have:

subscriptionType: BASIC | PRO
subscriptionStatus: ACTIVE | PAST_DUE | CANCELLED
planLimits enforced dynamically from backend
1.5 Feature Gating Logic
BASIC PLAN:
Hide/disable:
Insights tab
Payroll system UI
Restrict:
Employee creation (max 1)
PRO PLAN:
Full system access based on merchant type:
SALON
STORE
BOTH
2. MERCHANT TYPE SYSTEM (UNCHANGED BUT EXTENDED)

Merchant types:

SALON
STORE
BOTH

Subscription works ON TOP of merchant type rules.

IMPORTANT COMBINATION RULE:

Access is determined by:

Merchant Type + Subscription Plan

Example:

SALON + BASIC → limited salon system
STORE + PRO → full store system
BOTH + BASIC → restricted hybrid with 1 employee limit
3. FILE STORAGE SYSTEM (IMAGES + PDFs)
Store files in DB (LONGBLOB)
Fix oversized file errors
Provide secure endpoints:
/api/{entity}/{id}/file
Must support cross-device retrieval
Return correct MIME types
4. PAYMENT VERIFICATION SYSTEM
Staff must view proof before approval
Status:
PENDING
APPROVED
REJECTED
Cannot approve without proof file
Must be auditable
5. BOOKING SYSTEM (SALON LOGIC)
Slots must align with operating hours
Duration format:
2h, 30m
Convert all to minutes internally
Prevent overlap
Respect employee availability
Slot capacity = number of available employees
6. EMPLOYEE & PAYROLL SYSTEM
6.1 Employee Rules
Payment types:
HOURLY
DAILY
WEEKLY
Rate required
6.2 SUBSCRIPTION CONSTRAINT (IMPORTANT)
BASIC PLAN:
max 1 employee allowed
payroll disabled or limited (no advanced payroll engine)
PRO PLAN:
full payroll system enabled
6.3 Payroll Logic
Based on:
schedule
rate
worked time
Exclude off days
Weekly calculation supported
Status:
PAID
UNPAID
7. SERVICE MANAGEMENT
CRUD services
Duration must include h or m
Convert to minutes internally
Soft delete via archivedAt
Deleted services excluded from booking system
8. DASHBOARD SYSTEM (FULL CONTEXT AWARENESS)

Dashboard must depend on:

Merchant Type + Subscription Plan

SALON
Appointments
Staff schedules
Services
Basic or full insights (PRO only)
STORE
Products
Orders
Inventory
Sales analytics (PRO only)
BOTH

Split UI:

Salon Section:
bookings
services
staff
Store Section:
products
orders
inventory
Insights:
PRO only
BASIC → hidden
9. SYSTEM-WIDE REQUIREMENTS
Strict tenant isolation (mandatory)
All APIs must enforce:
tenant_id
subscription rules
Backend enforces ALL business logic
Frontend only reflects permissions
Fully scalable architecture
🎯 FINAL GOAL

Build a system that is:

multi-tenant secure
subscription-aware
merchant-type aware
scalable
production-safe
fully access-controlled per plan -->
