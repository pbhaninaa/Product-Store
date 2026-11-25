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
| Orders and bookings: view, hand over with the handover code, change fulfilment, mark no-shows | Yes | Yes |
| Take cash with the handover code, confirm payments | Yes | Yes (needs the confirm-payments permission) |
| Alerts (notifications) | Yes | Yes |
| Team & Payroll: **My income** tab | Yes | Yes |
| Products (add, edit, stock, remove) | Yes | No |
| Store settings (delivery, payment options, payout details, booking fee, contact, branding, shop type) | Yes | No |
| Store earnings and payouts (in **My income**) | Yes | No |
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

Access activates automatically after Paystack confirms the payment. Subscriptions are paid online with Paystack only.

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
| **Payment** | All, Cash in store, Paystack (online). Older Manual EFT orders show as **Manual EFT (legacy)**. |
| **Verification** | Only appears while you have older Manual EFT orders: Awaiting proof, Auto-verified, Manual review, Manual rejected |
| **Delivery** | All, Delivery, Pickup |

Columns: **Customer**, **Total**, **Items**, **Payment**, **Verification** (only for older Manual EFT orders, with **View proof**), **Status**, **Fulfilment**.

### Order lifecycle

```
Pending payment  ->  Paid  ->  Processing  ->  Ready for pickup / Out for delivery  ->  Completed
       |
       +-> Cancelled (only while unpaid)
```

Fulfilment can only move forward. You can go straight from Processing to Completed.

### The handover code

Every order and every booking gets a 6-digit **handover code**, whatever the payment method. It replaces the old "cash payment code". The customer sees it on their order or booking confirmation, on the Track page, in their history and on the online payment return page, and shows it to you when they collect, when delivery arrives, or when they arrive for a service.

- **Orders:** moving an order to **Completed** (collection or delivery) needs the customer's code. Other fulfilment steps do not.
- **Bookings:** **Start service** needs the code. So does completing a confirmed booking straight away without starting it first. Completing a service that is already in progress does not.
- **Unpaid cash:** for an unpaid cash order or booking the same code also records the cash payment, so take the cash first, then enter the code. Recording a payment needs the confirm-payments permission.
- **Online payments:** the code only authorises the handover or start; the payment is already recorded.
- **Wrong codes:** after 5 wrong tries for the same order or booking, further tries are refused for 30 minutes. Cash confirmation and handover share the same count.
- Spaces in the code are ignored, so you can type it the way the customer reads it out.

### Hand over an order

1. In the **Fulfilment** dropdown choose **Completed**. The **Hand over order** dialog opens.
2. Ask the customer for the **handover code** and enter it under **Customer handover code**.
3. Click **Hand over**. If the code matches, the order is marked **Completed**.

### Hand over and take cash (unpaid cash order)

1. Take the cash from the customer.
2. Open the row menu and click **Hand over and take cash**.
3. Enter the customer's handover code and confirm. The order is marked **Paid** and **Completed** in one step.

### Confirm a cash payment before handover

If the customer pays before collection, open the row menu, click **Confirm payment**, enter the code (the dialog field is labelled **Customer payment code**; it is the same handover code) and click **Confirm payment**. The order is marked **Paid**. You still need the code again at handover.

### Older Manual EFT orders

Customers can no longer choose Manual EFT at checkout or when booking. Orders and bookings made earlier with Manual EFT stay in your lists as **Manual EFT (legacy)**.

- If it shows **Manual review**, click **View proof** and check your bank. Then choose **Confirm payment** from the row menu.
- Only confirm after the money shows in your bank account.

When you confirm a payment and the store has active team members, a browser prompt asks you to assign the work to a team member for payroll. Click Cancel to skip.

### Online orders

These are confirmed automatically when Paystack confirms the payment. No action is needed.

### Update fulfilment

For paid orders, use the **Fulfilment** dropdown: **Processing**, then **Ready for pickup** (or **Out for delivery**), then **Completed** (this asks for the [handover code](#the-handover-code)). Customers see these statuses on the Track page and can rate you once the order is completed.

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

The card is headed **Payments & payouts**.

1. Under **Checkout & booking**, switch on the methods you accept:
   - **Offer pay in store (cash)**
   - **Offer online payment (card or Instant EFT)**
2. Under **Payout details**, enter where the platform pays your earnings from online payments: **Bank name**, **Account holder**, **Account number** and **Branch / universal branch code** (optional, 6 digits). Fill in all three account fields, or leave them all empty for now. Customers never see these details.
3. Save the card.

- At least one method must stay on.
- These settings apply to both product checkout and salon booking.
- Only the owner can change them.
- Online payment must also be switched on for the platform to appear at checkout. If it is off, customers can pay cash.
- Manual EFT (bank transfer with proof) has been removed. Your bank details are now only used to pay you.

### Booking fee (salons)

The **Booking fee** card appears on **Store** for stores that take bookings. It lets you charge customers who book with cash a part of the price online, to cut no-shows.

1. Enter **Booking fee** as a whole number from **0% to 50%**. 0% means no fee. The card shows an example, such as a R1 000.00 booking: the customer pays part online and the rest at the shop.
2. Click **Save booking fee**. Anything outside 0 to 50 is rejected.

How it works:

- The fee applies only to bookings paid with **cash in store**. Customers who pay the full price online pay no booking fee.
- The customer pays the fee online with Paystack when they book. The rest is paid in cash at the shop.
- If the fee is not paid within **2 hours**, the booking is cancelled and the time is freed.
- A change to the percentage applies to new bookings only. Existing bookings keep their fee.
- Changing it needs the store-settings permission.

| What happens | The fee |
|--------------|---------|
| Customer cancels at least 24 hours before the start | Refunded to the customer |
| Customer cancels less than 24 hours before the start | You keep it |
| Customer does not show up (you mark **No show**) | You keep it |
| You cancel the booking, at any time | Always refunded |

Customers see these terms before they pay.

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
| **Confirm payment** | Pending cash bookings. Enter the customer's handover code. With a paid booking fee, this takes only the remainder and marks the booking fully paid. |
| **Confirm booking** | Other pending bookings |
| **Start service** | Confirmed (paid) bookings, and pending cash bookings. Opens a dialog that asks for the **handover code**. For an unpaid cash booking, take the cash first: the code also marks it **Paid**. |
| **Complete service** | Confirmed or in-progress bookings. Completing straight from confirmed also needs the handover code. The customer can then review you. |
| **Mark as no show** | Pending or confirmed bookings whose start time has passed. The booking is cancelled, shows as **No show**, and you keep any paid booking fee. Cannot be undone. Needs the cancel-bookings permission. |
| **Cancel booking** / **Delete permanently** | Pending bookings only. If the customer paid a booking fee, cancelling refunds it and the dialog says so. |
| **Approve EFT** / **Reject EFT** | Only for older Manual EFT bookings in review (check **View proof** first) |

Customers can also cancel their own pending cash bookings before the start time, from the Track page or their history. The fee is then refunded or kept by the 24-hour rule above.

For bookings with a booking fee, the **Payment** column shows the fee status (for example **Booking fee paid**) and what is still due at the shop, such as **R 800,00 due**.

Status labels: **Pending**, **Paid**, **In progress**, **Completed**, **Cancelled**.

### Payments

A list of recent orders with status and date. Use **Search client or reference**, **Refresh** and **Export CSV**. Payments are confirmed on **Orders** and **Bookings**, not here.

Bookings that carry a booking fee also show their receipt lines, each on its own line: **Booking fee (paid online)**, **Paid at the shop (cash)** or **Due at the shop (cash)**, **Booking fee refunded**, **Booking fee refund in progress** or **Booking fee kept by the shop**, and **Total**.

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

Shows the signed-in person's **Owed**, **Upcoming** and **Paid out** amounts, with a summary of service pay, order pay and the total for each, and the list of jobs. Paid orders credited to you that are not completed yet show as Upcoming. Click **Refresh** to update. The page opens on the last 7 days; use **From**, **To** or **All dates** to change the range.

#### Store earnings (owner only)

Owners also see a **Store earnings** card. In-app (online) payments are collected by the platform and paid out to your store, less Paystack fees and the platform commission. Cash orders and bookings are paid to you directly and are not shown here.

- Tiles: **Owed by the platform** (what is not yet paid out, for all dates), **Gross**, **Paystack fees** and **Commission** (with the current percentage).
- **In app payments** lists each payment in the dates you chose, with **Gross**, **Fee** (or **Fee unknown** until the Paystack fee is known) and a status of **Owed** or **Paid out**.
- **Payouts received** lists each payout with its date, **Net paid**, **Commission**, **Fees** and **Reference**.
- Staff do not see this card.

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
| Customers cannot choose delivery | With per-km pricing, set the store pin under **Shop & delivery**. |
| Handover code rejected | Ask the customer for the code on their confirmation, Track page or history. After 5 wrong tries for that order or booking, wait 30 minutes. |
| A cash booking disappeared | If you charge a booking fee, the customer has 2 hours to pay it online or the booking is cancelled. |
| Nothing in **Store earnings** | Only online payments appear there, and only for the owner. |
| Fulfilment change fails | Fulfilment only moves forward. Moving to Completed needs the customer's handover code. |
| **Insights require an active Standard or Premium plan** | Upgrade on **Plan**. |
| Staff get errors on Login team or Payroll | These tabs are owner-only. |
