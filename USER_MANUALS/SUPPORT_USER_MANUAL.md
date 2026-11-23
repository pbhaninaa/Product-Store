# Product Store Support User Manual

**Role:** Platform admin and support staff
**Last updated:** November 2026

---

## Table of Contents

1. [Getting Started](#getting-started)
2. [Signing In](#signing-in)
3. [Roles and Permissions](#roles-and-permissions)
4. [Dashboard](#dashboard)
5. [Merchants](#merchants)
6. [Billing (Subscriptions)](#billing-subscriptions)
7. [Orders and Bookings](#orders-and-bookings)
8. [Tickets](#tickets)
9. [Shadow (Sign In as Merchant)](#shadow-sign-in-as-merchant)
10. [Alerts](#alerts)
11. [Features](#features)
12. [Audit](#audit)
13. [Help Contact](#help-contact)
14. [Staff](#staff)
15. [Referrals](#referrals)
16. [Account](#account)
17. [Troubleshooting](#troubleshooting)

---

## Getting Started

The **Platform console** at `/support` is the back office for the whole platform. Use it to:

- Look up and manage merchants (stores)
- Check subscription and trial status
- See recent orders and bookings across all stores
- Answer merchant help tickets
- Open a merchant's admin as the owner ("shadow") to diagnose problems
- Manage support staff, feature flags, help contact details and referral commissions

Two roles can open the console:

| Role | Description |
|------|-------------|
| **PLATFORM_ADMIN** | Full access to every tab and action |
| **SUPPORT_USER** | Access depends on assigned permissions |

Merchants and customers cannot open `/support`. If they try, they are signed out and sent to the sign-in page.

---

## Signing In

1. Open `/m/platform/admin?support=1`.
2. Enter your **Email** and **Password**, then click **Sign in**.
3. You are sent straight to the **Platform console** dashboard (`/support`).

- If you are not signed in and open `/support`, you are redirected to this sign-in page.
- If you click **Forgot password?**, follow the email reset flow. The **Sign in** link on the reset pages also returns here.
- While signed in as support, merchant admin URLs (`/m/<slug>/admin/...`) redirect you back to the console. The only exception is a [shadow session](#shadow-sign-in-as-merchant).
- To sign out, click **Sign out** at the top of the console.

The console refreshes badge counts every 60 seconds. The **Live sync** chip shows it is connected.

---

## Roles and Permissions

Each console tab needs a permission. A **PLATFORM_ADMIN** has all of them.

| Tab | Permission needed |
|-----|-------------------|
| **Dashboard** | (any console user) |
| **Merchants** | `MANAGE_MERCHANTS` |
| **Billing** | `MANAGE_SUBSCRIPTIONS` |
| **Orders** | `VIEW_OPS` |
| **Bookings** | `VIEW_OPS` |
| **Tickets** | `MANAGE_TICKETS` |
| **Shadow** | `USE_SHADOW` |
| **Alerts** | (any console user) |
| **Features** | `MANAGE_FEATURES` |
| **Audit** | `VIEW_AUDIT` |
| **Help contact** | (any console user can view; saving is platform admin only) |
| **Staff** | `MANAGE_STAFF` |
| **Referrals** | `VIEW_OPS` |
| **Account** | (any console user) |

### Default support permissions

A new support user gets these five permissions:

`MANAGE_SUBSCRIPTIONS`, `MANAGE_MERCHANTS`, `USE_SHADOW`, `MANAGE_TICKETS`, `VIEW_OPS`

So by default a support user does **not** see **Features**, **Audit** or **Staff**, and cannot edit plan prices (`MANAGE_PLANS`).

### Platform-admin-only actions

These are restricted to platform admins, even if a support user has the related permission:

- **Reset owner password** (Merchants)
- Saving **Help contact**
- Creating support users from **Account**
- Suspending, activating or resetting passwords of support staff
- **Database reset** (Dashboard)

---

## Dashboard

Overview cards: **Merchants (tenants)**, **Users**, **Orders**, **Salon**, **Paid order revenue**, **Pending proofs**, **Open tickets**. Click **Refresh** to reload them.

### Database reset (platform admin only)

The red **Database reset** card deletes activity records: orders, bookings, payments, reviews, notifications, support tickets, payroll marks, password reset links and logs. It **keeps** all users and logins, stores and their settings (including bank details), staff and employee profiles, staff schedules, products, services, promotions, subscriptions, referrals and platform settings. The card only appears on test environments (local and SIT).

1. Click **Reset database**.
2. Type the confirmation phrase `RESET_DATABASE` exactly.
3. Confirm. **This cannot be undone.** Use it only on test environments such as SIT.

---

## Merchants

Tab: **Merchants**.

### Find a merchant

1. Type in **Search by name or slug**.
2. The table shows **Name**, **Slug**, **Orders**, **Active products** and **Paid revenue**.
3. Click the eye icon (**View**) to load details for that store: order counts (paid / pending), products (active / all), and salon numbers (bookings, confirmed, services, staff).

From the detail panel:

| Button | Action |
|--------|--------|
| **Storefront** | Opens the public store in a new tab |
| **Admin** | Opens the store's admin URL in a new tab. Support users are redirected back to the console; use **Shadow** to work inside it. |
| **Reset owner password** | Platform admin only. Enter a **New password** (minimum 8 characters) and click **Reset password**. |

### Add a merchant

1. Click **Add merchant**.
2. Fill in **Store name**, **URL slug** (lowercase letters, numbers, hyphens), **Owner email** and **Owner password**.
3. Click **Create**.

### Edit or delete

- Pencil icon (**Edit**): change **Store name** or set a **New slug (optional)**. Leave the slug blank to keep it. Click **Save**.
- Bin icon (**Delete**): removes the store and its related data. **Cannot be undone.**

---

## Billing (Subscriptions)

Tab: **Billing**.

How billing works:

- New merchants get a one-time **7-day Free Trial** (UTC) starting when the store is created.
- After the trial, a subscription activates only through a verified **Paystack** payment (Card or Instant EFT), paid by the merchant owner.
- Support **cannot** reset or reissue trials, and cannot force-activate a subscription.

| Sub-tab | What you can do |
|---------|-----------------|
| **Legacy proofs** | Read-only history of old EFT proofs. Click **PDF** to open one. Approve and reject are retired. |
| **Plans** | View and edit each plan: **Fee (ZAR)**, **Period days**, **Max employees (-1 unlimited)**, **Max products (-1 unlimited)**. Click **Save STARTER**, **Save STANDARD** or **Save PREMIUM**. Saving needs `MANAGE_PLANS`. |
| **Merchants** | Status per store: **Plan**, **Billed**, **Status** (**Free Trial** / **Active** / **Inactive**), **Trial end (UTC)**, **Days left**, **Period end**, **Proof**, **Paid with** (Card or Instant EFT). |
| **Banking (read-only)** | Historical platform banking record. Editing is disabled. |

When a merchant asks why their admin is locked, check the **Merchants** sub-tab. If the trial has ended and the status is **Inactive**, the owner must pay with Paystack on their **Plan** tab.

---

## Orders and Bookings

### Orders

Tab: **Orders**. **Recent orders (platform)** across all stores, with **Merchant**, **Customer**, **Status**, **Total** and **Created**. Click **Refresh** to reload.

### Bookings

Tab: **Bookings**. Recent salon bookings across all stores, with **Merchant**, **Customer**, **Status** and **Start**.

The **Open** link on a row points to the merchant's admin page. As a support user you are redirected back to the console unless you are in a shadow session. To act on a specific order or booking, start a [shadow session](#shadow-sign-in-as-merchant) for that store first.

Order status values: `pending_payment`, `paid`, `cancelled`. Paid orders then move through fulfilment: `processing`, `ready`, `completed`.

---

## Tickets

Tab: **Tickets**. Merchants open tickets from their admin **Help** tab. The tab badge shows open tickets.

1. Use the **Open** / **All** toggle to filter.
2. Read the **Merchant**, **Subject**, **Status** and **Created** columns.
3. Contact the merchant (use the **Merchants** tab or shadow if you need context).
4. Click **Resolve** when done. The status changes from `OPEN`.

---

## Shadow (Sign In as Merchant)

Tab: **Shadow** (needs `USE_SHADOW`).

1. Type in **Search merchants** and click **Search**.
2. The list shows **Name**, **Slug** and **Owner**.
3. Click **Enter** on the store.
4. The merchant admin opens **as the store owner**. A blue banner reads *Shadow support session — you are viewing this store as the merchant owner.*
5. Diagnose the problem: check orders, settings, plan status and so on.
6. Click **Exit shadow** in the banner to return to the **Shadow** tab.

Everything you do in a shadow session uses the owner's rights, including owner-only actions. Be careful and change only what the merchant has asked for.

---

## Alerts

Tab: **Alerts** (**Platform notifications**). Click a notification for details. Use **Mark all read** or **Refresh**. The badge shows the unread count.

---

## Features

Tab: **Features** (needs `MANAGE_FEATURES`). **Platform feature flags** lists each flag key with a description and a switch. Toggling a switch saves immediately.

---

## Audit

Tab: **Audit** (needs `VIEW_AUDIT`). The **Audit log** has columns **When**, **Actor**, **Action**, **Entity**, **Id** and **Detail**. Click **Refresh** to reload.

---

## Help Contact

Tab: **Help contact**. These are the details merchants see under **Platform contact** on their Help tab.

1. Fill in **Support email**, **Phone**, **WhatsApp**, **Hours** and **Notes**.
2. Click **Save** (platform admin only).

---

## Staff

Tab: **Staff** (needs `MANAGE_STAFF`).

### Create a support user

1. Under **Create support user**, enter **Email** and **Password**.
2. Click **Create**. The user receives the [default support permissions](#default-support-permissions).

### Staff roster

Columns **Email**, **Role** and **Status** (**Active** / **Suspended**). Row actions (platform admin only):

- **Suspend** / **Activate**
- **Reset PW**: enter a new password in the dialog.

> There is no screen for editing a support user's permissions yet. New users always start with the defaults.

---

## Referrals

Tab: **Referrals**.

- Chips show **Total** referrals and **Pending commission**.
- Table columns: **Referrer**, **Referee**, **Role**, **Subscribed**, **Commission**.
- Commission is 15% of the referred merchant owner's first paid subscription.
- When you have paid a referrer, click **Mark paid**. The button only appears for subscribed referrals that are not yet paid.

---

## Account

Tab: **Account**.

### Change your password

1. Enter **Current password**, **New password** (at least 8 characters) and **Confirm new password**.
2. Click **Update password**. You see **Password updated.**

### Create a support user (platform admin only)

Enter **Support email** and **Temporary password**, then click **Create support user**. Give the new user their sign-in URL: `/m/platform/admin?support=1`.

---

## Troubleshooting

| Problem | What to try |
|---------|-------------|
| Sent to the sign-in page when opening `/support` | Your session expired or your account is not support/admin. Sign in at `/m/platform/admin?support=1`. |
| A tab is missing | You lack the permission. Ask a platform admin. |
| **Open** or **Admin** link returns to the console | Expected for support users. Use **Shadow** > **Enter**. |
| Merchant says admin is locked | **Billing > Merchants**: trial ended or plan inactive. The owner must pay with Paystack on **Plan**. |
| Merchant asks to extend their trial | Not possible. Trials cannot be reset or reissued. |
| **Password reset failed (platform admin only)** | Only platform admins can reset owner passwords. |
| Help contact will not save | Saving is platform admin only. |
| Cannot get out of a merchant admin | Click **Exit shadow** in the blue banner. |
