# Product Store Merchant User Manual

**Role:** Merchant owner and merchant staff
**Last updated:** November 2026

---

## Table of Contents

1. [Getting Started](#getting-started)
2. [Creating Your Store](#creating-your-store)
3. [Signing In to Admin](#signing-in-to-admin)
4. [Owner vs Staff](#owner-vs-staff)
5. [Plan and Billing](#plan-and-billing)
6. [Dashboard](#dashboard)
7. [Products](#products)
8. [Promotions](#promotions)
9. [Orders](#orders)
10. [Insights](#insights)
11. [Store Settings](#store-settings)
12. [Salon: Services, Staff, Bookings, Payments](#salon-services-staff-bookings-payments)
13. [Team and Payroll](#team-and-payroll)
14. [Alerts (Notifications)](#alerts-notifications)
15. [Help and Tickets](#help-and-tickets)
16. [Troubleshooting](#troubleshooting)

---

## Getting Started

Your store has two sides:

| Side | URL | Who uses it |
|------|-----|-------------|
| Public storefront | `/m/<your-slug>` | Customers |
| Merchant admin | `/m/<your-slug>/admin` | You and your staff |

The SIT demo store uses the slug `demo` (`/m/demo`, `/m/demo/admin`).

**Shortcut:** on your public storefront, press and hold the **Shop** button (or **Salon** on salon-only stores) for about one second to open the admin.

---

## Creating Your Store

1. Go to `/signup` (**Create your store**).
2. Enter your **Business name**. Below it you see a preview **Store link: /m/<slug>**. If the name is taken, the final link may end in `-2`.
3. Enter **Owner email** and **Password**.
4. Click **Create Business Owner account**.
5. You are signed in and taken to **Plan & billing** in your new admin.

Every new store starts with a **7-day Free Trial** with full access. You do not need to choose a plan or pay until the trial ends.

---

## Signing In to Admin

1. Open `/m/<your-slug>/admin`.
2. Enter **Email** and **Password**, then click **Sign in**.
3. If you open another store's admin URL, you are redirected to your own store.

- Forgot your password? Click **Forgot password?** under the password field. See the reset steps in the [Client manual](CLIENT_USER_MANUAL.md#forgot-or-reset-password). The process is the same.
- No account yet? Click **Sign up**.
- To sign out, use **Sign out** on the Dashboard under **Your account**.

The **Live sync** chip means the admin updates in real time. Tabs show orange badges when something needs attention.

---

## Owner vs Staff

There are two merchant roles. Staff handle day-to-day work; the owner runs the store. Some tabs are visible to
staff but the server refuses the action with a permission error.

| Action | Owner | Staff |
|--------|:-----:|:-----:|
| Orders and bookings: view, confirm cash / EFT payments, change fulfilment | Yes | Yes |
| Alerts (notifications) | Yes | Yes |
| Team & Payroll: **My income** tab | Yes | Yes |
| Products (add, edit, stock, remove) | Yes | No |
| Store settings (delivery, banking, contact, branding, shop type) | Yes | No |
| Salon services, staff and availability | Yes | No |
| Help tickets to platform support | Yes | No |
| Choose a plan, pay subscription with Paystack | Yes | No (buttons say **Owner only** or are disabled) |
| Team & Payroll: **Login team** and **Payroll** tabs (create staff, mark paid) | Yes | No |

Staff accounts are created by the owner under [Team and Payroll](#team-and-payroll).

---

## Plan and Billing

Tab: **Plan** (`/admin/subscription`, page title **Plan & billing**).

### During the free trial

- The status chip shows **Free Trial** and **N days left**. The trial dates are shown in UTC.
- You have full Premium access. The **Free Trial** card says no payment is required until the trial ends.
- Under **Preferred plan after trial** you can click **Save preference** on **Starter**, **Standard** or **Premium**. This does not change your trial.

### After the trial ends

When the trial or a paid period expires, the admin is **locked**. Only the **Plan** and **Help** tabs remain, and a yellow banner explains why.

To unlock (owner only):

1. Open **Plan**.
2. Under **Choose a plan**, click **Select** on a plan.
3. In **Pay for this billing period**, choose **Card** or **Instant EFT**.
4. Click **Continue to Paystack** and complete the payment on the Paystack page.
5. You return to the **Online payment** page. Click **Continue** to go back to Plan & billing.

Access activates automatically after Paystack confirms the payment. Cash and Manual EFT are **not** accepted for subscriptions.

| Status chip | Meaning |
|-------------|---------|
| **Free Trial** | Within the 7-day trial |
| **Active** | Paid period running (see **Period** dates) |
| **Not active** | Payment required; admin locked |

---

## Dashboard

Tab: **Dashboard**.

- **Stat tiles:** **Catalogue** (products on the shop), **Open orders** (not counting completed and cancelled), **Awaiting payment** (confirm these when paid). Click a tile to open the related page.
- **Shortcuts (owner):** **Orders**, **Products**, **Settings**, **Store settings**.
- **Shortcuts (staff):** **Orders**, **Alerts**, **My income**.
- **Settings** (`/admin/settings`, owner) gathers everything in one place: **Catalogue** (Products, Salon services),
  **People** (Staff management, Team, Payroll, My income), **Store** (Store settings, Salon payments) and
  **Account** (Plan & billing, Insights, Help).
- **Your account:** the signed-in email and **Sign out**.
- Store location: if no store pin is saved, a note tells you to set it under **Shop & delivery** (needed for per-km delivery).
- **Tips** panel with reminders.

---

## Products

Tab: **Products** (hidden for **Salon only** stores). A badge shows how many products are out of stock.

### Add a product

1. Under **New product**, fill in **Product name**, **Category**, **Price (e.g. 100)** and **Stock quantity**.
2. Optionally choose a **Product image**.
3. Click **Publish product**. It appears on the shop immediately.

### Manage inventory

- Use **Search products** and the **Category** filter. Choose the page size with **Items per page**.
- Click **Edit product** to change the name, category, **Price (ZAR)**, stock or image (**Replace image (optional)**), then click **Save product**.
- Click **Remove**, then **Remove product**, to delete a listing. Past orders that include it are kept.

Customers cannot order more than the stock you have. Cancelling an unpaid order releases its items for other customers.

---

## Promotions

Tab: **Promotions**.

1. Click **New promotion**.
2. Fill in **Title**, **Description**, **Discount type** (`PERCENTAGE` or `FIXED`), **Discount value**, **Minimum order (ZAR)**, **Start date** and **End date**.
3. Leave **Active** on, then click **Save**.
4. Use **Edit** or **Delete** in the list to change or remove a promotion.

Active promotions appear on your public **Offers** page (`/m/<slug>/promotions`).

> Note: the current product checkout and salon booking screens have no field to apply a promotion. Treat the Offers page as information for customers.

---

## Orders

Tab: **Orders** (product checkouts only; hidden for **Salon only** stores). The badge counts unpaid, non-cancelled orders.

### Find orders

Use **Search orders** (name, email, order ID, product) and the filters:

| Filter | Options |
|--------|---------|
| **Status** | All, Pending payment, Processing, Ready, Completed, Cancelled |
| **Payment** | All, Cash in store, Manual EFT, Paystack (online) |
| **Verification** | Awaiting proof, Auto-verified, Manual review, Manual rejected |
| **Delivery** | All, Delivery, Pickup |

Columns: **Customer**, **Total**, **Items**, **Payment**, **Verification** (with **View proof** for EFT uploads), **Status**, **Fulfilment**.

### Order lifecycle

```
Pending payment  ->  Paid  ->  Processing  ->  Ready for pickup / Out for delivery  ->  Completed
       |
       +-> Cancelled (only while unpaid)
```

Fulfilment can only move forward. You can go straight from Processing to Completed.

### Confirm a cash payment

1. When the customer pays, open the row menu and click **Confirm payment**.
2. In **Confirm cash payment**, enter the **Customer payment code** from their confirmation.
3. Click **Confirm payment**. If the code matches, the order is marked **Paid**.

### Confirm a Manual EFT payment

- If the customer's reference matched, the order is **Auto-verified** and paid automatically.
- If it shows **Manual review**, click **View proof** and check your bank. Then choose **Confirm payment** from the row menu.
- Only confirm after the money shows in your bank account.

When you confirm an EFT payment and the store has active team members, a browser prompt asks you to assign the work to a team member for payroll. Click Cancel to skip.

### Online orders

These are confirmed automatically when Paystack confirms the payment. No action is needed.

### Update fulfilment

For paid orders, use the **Fulfilment** dropdown: **Processing**, then **Ready for pickup** (or **Out for delivery**), then **Completed**. Customers see these statuses on the Track page and can rate you once the order is completed.

### Other row actions

| Action | When available |
|--------|----------------|
| **Print invoice** | Always. Opens a print page with **Print / Save as PDF** and **Back to admin**. |
| **Assign to team (payroll)** | Paid orders |
| **Cancel order** | Unpaid orders only. Releases the items. The order stays as **Cancelled**. |
| **Delete permanently** | Unpaid orders only. Cannot be undone. |

---

## Insights

Tab: **Insights**. This requires an active **Standard** or **Premium** plan (the free trial includes Premium). Otherwise you see **Open Plan & billing**.

- **Period:** Last 7 days, Last 30 days, This month, All time, or Custom range (**From** / **To**).
- Filters: **Delivery**, **Payment**, **Product category**, **Confirmed payments only**.
- Tiles: **Revenue**, **Subtotal (items)**, **Delivery fees**, **Avg. order value**.
- **Top selling product** is the product with the most units sold in the period.
- Cancelled orders are always excluded.

---

## Store Settings

Tab: **Store**. Each card has its own save button.

### Shop & delivery

1. Choose **Delivery pricing**: **Standard — flat fee per delivery** or **Per kilometre — rate × distance (km)**.
2. Enter **Flat delivery fee (ZAR)** or **Rate per kilometre (ZAR)**.
3. Click the map where your shop is, or use **Use my location**. Per-km delivery needs this pin.
4. Click **Save delivery settings**. Pickup is always free.

### Customer payments (which methods customers can use)

1. Enter banking details for Manual EFT: **Bank name**, **Account holder**, **Account number**, **Branch / universal branch code** (optional), and **Extra EFT / payment notes (optional)**.
2. Switch on the methods you accept:
   - **Offer pay in store (cash)**
   - **Offer Manual EFT (bank transfer with proof)**
   - **Offer online payment (card or Instant EFT)**
3. Click **Save payment settings**.

- At least one method must stay on.
- These settings apply to both product checkout and salon booking.
- Manual EFT only shows to customers once bank details are saved.
- Paystack must also be configured on the platform to appear at checkout.

### Store Branding

- **Store type:**
  - **Normal store**: products only (default)
  - **Salon + store**: bookings and product catalogue
  - **Salon only**: bookings only, no product catalogue
- **Store name \***: replaces the default site title for customers.
- **Store icon** (shown in the navigation bar) and **Store advert image** (shown on the home page hero). Use **Remove icon** or **Remove advert image** to clear them.
- Click **Save store branding**. The admin tabs change to match the store type.

### Contact page

Enter **Email**, **Phone**, **Address** and **Extra notes**, then click **Save contact details**. Blank fields are hidden. Customers see these details on `/m/<slug>/contact`.

### Working hours

Switch on each day and set **From** and **To** (24-hour time), then click **Save opening hours**. For salons, these hours also limit which booking slots are offered.

---

## Salon: Services, Staff, Bookings, Payments

The **Salon**, **Payments** and **Bookings** tabs appear when the store type is **Salon + store** or **Salon only**.

### Salon (services)

1. Fill in **Service name**, **Description**, **Duration (minutes)** (minimum 5), **Price (ZAR)** and an optional **Service image**. Leave **Active** on.
2. Click **Publish service**.
3. To change a service, click **Edit service**, then **Save service**.

### Staff management

- People enrolled under **Team & Payroll** appear here automatically. Click **Open Team & Payroll** to add them.
- For someone who should be bookable but has no login, enter a **Display name** and click **Add extra bookable person**.
- Click **Schedule** on a person, choose **Weekday**, **From** and **To**, and click **Add window**. Booking slots use these windows together with your working hours and the service duration.

### Bookings

Filter with **Search bookings**, **Status**, **Payment** and **Verification**. Row actions depend on the booking:

| Action | When |
|--------|------|
| **Confirm payment** | Pending cash bookings. Enter the customer's payment code. |
| **Approve EFT** / **Reject EFT** | Pending EFT bookings in manual review (check **View proof** first) |
| **Confirm booking** | Other pending bookings |
| **Start service** | Paid bookings |
| **Complete service** | Paid or in-progress bookings. The customer can then review you. |
| **Cancel booking** / **Delete permanently** | Pending bookings only |

Status labels: **Pending**, **Paid**, **In progress**, **Completed**, **Cancelled**.

### Payments

A list of recent orders with status and date. Use **Search client or reference**, **Refresh** and **Export CSV**. Payments are confirmed on **Orders** and **Bookings**, not here.

---

## Team and Payroll

Tab: **Team & Payroll** with three sub-tabs. The owner can use all three; staff can use **My income**.

### Login team (owner)

1. Under **Add team member**, fill in **Display name**, **Login email**, **Password**, **Role label** and **Bonus %**, then the two pay settings:
   - **Service pay** (salon bookings): **Pay method** (Per service, % of service price, Per hour, Per day, Weekly, Monthly) and **Pay rate**. For % of service price the rate is a percentage (0 to 100, up to 2 decimals) of what the customer pays for the service after any discount; otherwise it is a rand amount.
   - **Order pay** (orders): **No order pay** (the default for new team members), **Fixed amount per order**, or **% of order total**. The percentage is of the order total after discounts, without the delivery fee.
   - Team members added before order pay existed keep earning for orders what they earned before: Per service or Per hour staff get a fixed amount per order equal to their rate (one hour's pay for hourly staff); Per day, Weekly and Monthly staff get no order pay, because orders never added to their day rate or salary. Edit the person to change it.
2. Click **Create staff login**. The person can now sign in at `/m/<your-slug>/admin` as staff.
3. Click **Edit** to change details (then **Save changes**), or **Deactivate** to switch someone off.

The bonus percentage is added on top of both service pay and order pay.

### Payroll (owner)

- Set **From** and **To** dates and click **Refresh**, or click **All time**.
- Each person shows their **Pending** total and number of unpaid jobs. Open a person to see **Service pay pending**, **Order pay pending** and **Total pending**, and each job with its kind of pay.
- Click **Mark paid** or **Unmark** on single jobs, or **Pay all** for a person.
- Bookings count once they are credited to the person (confirmed, in progress or completed). Orders count once they are paid, credited to the person (use **Assign to team (payroll)** on paid orders) and **Completed**, and only if the person has order pay. Cancelled orders never count.
- A job marked paid keeps the amount it was paid with. Changing someone's pay settings later only changes unpaid work.

### My income (owner and staff)

Shows the signed-in person's **Owed**, **Upcoming** and **Paid out** amounts, with a summary of service pay, order pay and the total for each, and the list of jobs. Paid orders credited to you that are not completed yet show as Upcoming. Click **Refresh** to update.

---

## Alerts (Notifications)

Tab: **Alerts**. In-app alerts for new orders, bookings and payroll. Click an alert to see details. Use **Mark all read** or **Refresh**. The tab badge shows the unread count.

---

## Help and Tickets

Tab: **Help**. This tab stays available even when the admin is locked.

- **Platform contact:** support email, phone, WhatsApp and hours.
- **Open a ticket:** enter a **Subject** and **Message**, then click **Send ticket**.
- **Your tickets:** subject, status and created date.

---

## Troubleshooting

| Problem | What to try |
|---------|-------------|
| Only **Plan** and **Help** tabs show | Your trial or plan has expired. The owner must pay on **Plan**. |
| **Continue to Paystack** disabled | Only the owner can pay. If you are the owner, Paystack may not be configured; open a ticket. |
| Products or Orders tab missing | The store type is **Salon only**. Change it under **Store > Store Branding**. |
| Salon tabs missing | The store type is **Normal store**. Change it and save. |
| Customers cannot pick Manual EFT | Save bank details under **Customer payments**. |
| Customers cannot choose delivery | With per-km pricing, set the store pin under **Shop & delivery**. |
| Cash code rejected | Ask the customer for the code on their confirmation or Track page. |
| Fulfilment change fails | Fulfilment only moves forward, and only on paid orders. |
| **Insights require an active Standard or Premium plan** | Upgrade on **Plan**. |
| Staff get errors on Login team or Payroll | These tabs are owner-only. |
