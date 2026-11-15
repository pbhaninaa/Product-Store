# Product Store Client User Manual

**Role:** Customer / Shopper
**Last updated:** November 2026

---

## Table of Contents

1. [Getting Started](#getting-started)
2. [Your Customer Account](#your-customer-account)
3. [Finding Shops and Salons](#finding-shops-and-salons)
4. [Browsing a Store](#browsing-a-store)
5. [Cart and Checkout](#cart-and-checkout)
6. [Paying for Your Order](#paying-for-your-order)
7. [PayFast Return Page](#payfast-return-page)
8. [Tracking an Order or Booking](#tracking-an-order-or-booking)
9. [Booking a Salon Appointment](#booking-a-salon-appointment)
10. [Your Activity and History](#your-activity-and-history)
11. [Forgot or Reset Password](#forgot-or-reset-password)
12. [Troubleshooting](#troubleshooting)

---

## Getting Started

As a customer you can:

- Find nearby shops and salons, sorted by rating
- Buy products from a store and choose pickup or delivery
- Pay with cash, Manual EFT (bank transfer with proof) or PayFast (card or Instant EFT), depending on what the store accepts
- Book salon appointments
- Track orders and bookings, and rate the store once they are completed

### Two ways in

| You have... | Go to |
|-------------|-------|
| A link to one store, e.g. `/m/demo` | That store's shop page directly. No account needed to buy. |
| No particular store in mind | `/account/login` (or `/account/register`), then the **Customer hub** at `/` |

> A customer account is **not required** to buy from a store link. You need one for the hub (`/`), **Discover** (`/discover`) and **Your activity** (`/account/history`).

### Top bar on a store page

| Button | What it does |
|--------|--------------|
| **Shop** | Back to the store's product page |
| **Salon** | Shown for salon stores; opens the service list |
| **Cart** | Opens checkout; the number shows how many items are in your cart |
| **Offers** | Active promotions for this store |
| **Sign in** / **Account** | Customer sign-in, or your activity page once signed in |
| **Find providers** | Shown when you are signed in; returns to the Customer hub |

The footer has **Contact us**, **Track order** and **Help** links for the store.

---

## Your Customer Account

### Create an account

1. Go to `/account/register` (or click **Register** on the sign-in page).
2. Fill in **Name**, **Email** and **Password**.
3. Optionally enter a **Referral code (optional)**. If someone shared a link with `?ref=CODE`, it is filled in for you.
4. Click **Create account**. You land on the Customer hub.

### Sign in

1. Go to `/account/login` (or click **Sign in** in the top bar).
2. Enter **Email** and **Password**, then click **Sign in**.
3. If you were sent to sign-in from another page, you go back to that page afterwards.

### Sign out

On the Customer hub, click **Sign out** (top right).

---

## Finding Shops and Salons

### Customer hub (`/`)

After sign-in you see two cards:

- **Shop** - **Browse shops**: nearby stores with ratings and prices.
- **Salon** - **Browse salons**: nearby salons with star ratings.

Below them, **Your orders & bookings** has an **Open history** button.

### Discover (`/discover`)

1. Choose **Browse shops** or **Browse salons** on the hub.
2. Under **Your location**, do one of the following:
   - Click **Use my location** on the map, or
   - Type in **Search an address (optional)** and click **Find this place**, or
   - Click the map to drop a pin.
3. Adjust **Search radius** if needed. If nothing is found, click the **Search ...** button that offers a wider radius.
4. Optionally narrow the list with **Filter by shop or product name (optional)** (or **Filter by salon or service name (optional)**).
5. Results are sorted by rating, then number of reviews, then distance. Each card shows rating, distance and a few products or services.
6. Click **Choose this shop** / **Choose this salon** on a card, or select a card and click **Continue with this shop** / **Continue with this salon** in the bar at the bottom.

| Message | Meaning |
|---------|---------|
| **Set your location to see providers** | No location yet. Use my location, search an address, or tap the map. |
| **No shops in this radius yet** | Move the pin, widen the radius, or try later. |
| **No matches for that filter** | Clear the filter text. |

---

## Browsing a Store

A store lives at `/m/<store-slug>` (for the SIT demo: `/m/demo`).

1. Use **Search by product name...** to find items.
2. Use **Category** to filter (or leave it on **All categories**).
3. The count shows **Showing X of Y**.
4. On a product card, set **Quantity** and click **Add to cart**.
5. Items marked **Sold out** cannot be added.

The catalogue updates live; new items appear without refreshing.

### Offers

Click **Offers** in the top bar (`/m/<slug>/promotions`) to see the store's active promotions, e.g. "10% off" or "R50 off", with their start and end dates. If there are none you see **No promotions running today.**

---

## Cart and Checkout

Click **Cart** to open `/m/<slug>/checkout`. This checkout is for **products only**. Salon appointments use the booking flow ([see below](#booking-a-salon-appointment)).

### Step 1: Your details

All three fields are required:

| Field | Rule |
|-------|------|
| **Full name** | At least 2 characters |
| **Email** | Valid email. Used for order updates and for tracking. |
| **Phone** | 8-32 characters with at least 8 digits |

### Step 2: Delivery

| Option | Details |
|--------|---------|
| **Pickup in store** | **Free**. Collect when your order is ready. |
| **Delivery** | Enter a **Delivery address**. The fee depends on how the store has set up delivery. |

- **Flat fee stores:** the fee is shown on the Delivery option. You can click **Use my location to fill address**.
- **Per-km stores:** after entering your address, pin your location on the map under **Where should we deliver?** (drag the marker or use **Use my location**). The page shows the straight-line distance and the delivery charge.
- If the store has not set its location, per-km delivery is unavailable. Choose pickup or contact the store.

### Step 3: Check the summary

The **Order summary** on the right shows each item (you can change **Qty**), **Subtotal**, **Delivery** and **Total**. Prices are checked again when you place the order.

### Step 4: Choose how to pay

Under **How would you like to pay?** pick one of the options the store offers. See [Paying for Your Order](#paying-for-your-order).

### Step 5: Place the order

Click **Place order**.

- If stock changed while you were checking out, a dialog **Some items are no longer available** lists what is still possible. Click **Continue with this order** to go ahead with the remaining items, or **Go back** to edit your cart.
- On success you see **Your order is in** with **Your order number**. Click **Copy number** and keep it. You need it to track the order and as your EFT payment reference.

From the confirmation screen you can click **Back to shop** or **Track this order**.

---

## Paying for Your Order

Only the methods the store has switched on are shown.

| Method | How it works | When the order is paid |
|--------|--------------|------------------------|
| **Cash** | Pay cash at the counter or to the courier. | When the store confirms payment in their admin. |
| **Manual EFT** | Transfer from your banking app, then upload proof. | Automatically if your reference matches; otherwise after the store checks it. |
| **PayFast** | Choose **Card** or **Instant EFT** and pay on PayFast. | When PayFast confirms the payment. |

### Cash

1. Choose **Cash** and place the order.
2. The confirmation screen says your order is **pending** until the shop confirms payment.
3. If a **Your payment code** is shown, give this code to staff when you pay. They enter it to mark the order paid.
4. The code is also shown when you look up the order on the Track page.

### Manual EFT (bank transfer with proof)

1. Choose **Manual EFT**. The store's bank details appear under **Transfer to**.
2. Place the order. The confirmation shows **Pay by Manual EFT** with **Bank**, **Account name**, **Account no.** and **Branch**.
3. Pay the **full amount** in your banking app. Use your **order number** as the payment reference.
4. Under **Upload proof of payment**:
   - Check **Reference you used on the transfer**.
   - Click **Choose PDF or image**. A PDF is recommended because the amount and date are checked automatically. For an image, only the reference is checked.
   - Click **Upload proof**.
5. If the reference matches, the payment is confirmed automatically. If not, the store checks it manually.

> If the store has not published bank details, Manual EFT is greyed out and you see **Manual EFT unavailable**.

### PayFast (card or Instant EFT)

1. Choose **PayFast**, then pick **Card** or **Instant EFT**.
2. Click **Place order**. You are redirected to PayFast to pay.
3. If you are not redirected, click **Continue to PayFast** on the confirmation screen.

> If you see **Online PayFast checkout is not available on this platform yet**, choose another method.

---

## PayFast Return Page

After paying on PayFast you return to the **PayFast payment** page.

| Heading | Meaning |
|---------|---------|
| **Payment processing** | Waiting for PayFast. This usually takes a few seconds, so leave the page open. |
| **Payment successful** | Your order or booking is marked as paid. |
| **Payment cancelled** | You cancelled on PayFast. You can try again (see **Pay now** under [Your Activity and History](#your-activity-and-history)). |

Click **Continue** to go to the store's Track page.

---

## Tracking an Order or Booking

1. Open `/m/<slug>/track`, or click **Track order** in the store footer or **Track this order** after checkout.
2. Choose **Product order** or **Salon booking**.
3. Enter the **Order or booking ID** and the **Email used at checkout**.
4. Click **Look up**.

### Order statuses

| Status shown | Meaning |
|--------------|---------|
| **Pending payment** | Payment is not confirmed yet |
| **Processing** | Paid; the store is preparing your order |
| **Ready for pickup** | Pickup orders: you can collect |
| **Out for delivery** | Delivery orders: on the way |
| **Completed** | Done |
| **Cancelled** | The order was cancelled |

### Booking statuses

**Pending payment**, **Paid / confirmed**, **In progress**, **Completed**, **Cancelled**.

### Rate the store

When an order or booking is **Completed**, a **Rate this shop** section appears. Choose stars, optionally add a **Comment (optional)**, and click **Submit review**. You can rate each order or booking once.

---

## Booking a Salon Appointment

Salon stores show a **Salon** button in the top bar (`/m/<slug>/salon/services`).

1. Optionally set **Day for your visit** and search by service name.
2. Each service shows its duration (minutes) and price. Click **Book**.
3. On **Choose your date and time**, pick a date. **Available times** lists open slots.
4. Tap a time. A booking window opens.
5. Enter **Full name**, **Phone** and **Email**.
6. Choose a payment method (if the store accepts only one, it is pre-selected):

| Option | Button | What happens next |
|--------|--------|-------------------|
| **Cash (pay in store)** | **Confirm booking (pay in store)** | You get a payment code. Give it to staff when you pay. |
| **Manual EFT (bank transfer)** | **Request booking (Manual EFT)** | Transfer using the reference shown, then upload proof. The slot stays pending until then. |
| **PayFast (card & Instant EFT)** | **Continue to PayFast payment** | You are redirected to PayFast (choose **Card** or **Instant EFT** first). |

7. Note your **Reference**. Click **Track booking** to check the status later, or **Done** to close.

If no slots appear, you see **No available slots**. Try a different date.

---

## Your Activity and History

Open `/account/history` (top bar **Account**, or **Open history** on the hub). This page requires sign-in.

| Section | What you see |
|---------|--------------|
| **Referral code** | Your personal code to share. |
| **Orders** | Store, total, **View store**. **Pay now** appears for unpaid PayFast orders. |
| **Bookings** | Salon, service, status, start time, **View salon**. **Pay now** appears for unpaid PayFast bookings. |

Click **Pay now** to restart PayFast checkout for that order or booking.

> History lists orders and bookings linked to your customer account.

---

## Forgot or Reset Password

1. Go to `/forgot-password`. The customer sign-in page has no link, so type the address.
2. Enter your **Email** and click **Send reset link**.
3. If the email is registered, you receive a link. Open it to reach **Reset password**.
4. Enter **New password** and **Confirm new password** (at least 8 characters).
5. Click **Update password**, then sign in again at `/account/login`.

If you see **This reset link is missing a token**, open the link from your email again, or click **Request a new link**.

---

## Troubleshooting

| Problem | What to try |
|---------|-------------|
| Red banner **Cannot reach the API** | Check your internet connection and click **Retry**. |
| Sent to the sign-in page when opening `/` or `/discover` | These pages need a customer account. Register or sign in. |
| **Your cart is empty** | Add products on the store page first. Each store has its own checkout. |
| **This shop has no payment methods enabled right now** | Contact the store using **Contact us** in the footer. |
| Manual EFT is greyed out | The store has not published bank details. Use another method. |
| Delivery option disabled | The store uses per-km pricing but has not set its location. Choose pickup. |
| **Tap the map to set your delivery location** | Per-km delivery needs a pin on the map. |
| Track page finds nothing | Check the ID and use the same email you entered at checkout. Choose the right type (order or booking). |
| PayFast page left before confirming | Open **Your activity** and use **Pay now**, or check status on the Track page. |
| No shops or salons on Discover | Widen the radius or move the pin. Some stores have not set a location. |
| Salon page says the shop is a **normal store** | That store does not take bookings. |

For store-specific questions use the store's **Contact us** page or **Help** (`/m/<slug>/help`).
