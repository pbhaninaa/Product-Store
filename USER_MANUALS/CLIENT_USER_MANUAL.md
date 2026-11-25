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
7. [Online Payment Return Page](#online-payment-return-page)
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
- Pay with cash or Pay online (card or Instant EFT), depending on what the store accepts
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
- On success you see **Your order is in** with **Your order number**. Click **Copy number** and keep it. You need it to track the order. The confirmation also shows your **handover code** (see [Your handover code](#your-handover-code)).

From the confirmation screen you can click **Back to shop** or **Track this order**.

---

## Paying for Your Order

Only the methods the store has switched on are shown.

| Method | How it works | When the order is paid |
|--------|--------------|------------------------|
| **Cash** | Pay cash at the counter or to the courier. | When the store takes your cash and enters your handover code. |
| **Pay online** | Choose **Card** or **Instant EFT** and pay on the secure Paystack payment page. | When Paystack confirms the payment. |

Manual EFT (bank transfer with proof) is no longer offered.

### Cash

1. Choose **Cash** and place the order.
2. The confirmation screen says your order is **pending** until the shop confirms payment.
3. Your **handover code** is shown. Give it to staff when you pay and collect. They enter it to record your cash payment and hand over the order.
4. The code is also shown when you look up the order on the Track page and in your history.

### Your handover code

Every order and booking gets a 6-digit **handover code**, whichever way you pay. It replaces the old "payment code".

- You see it on the confirmation after checkout or booking, on the Track page, in your history and on the online payment return page, until the order is handed over or the service has started.
- Show it to staff when you collect your order, when your delivery arrives, or when you arrive for a service. Staff cannot complete the handover or start your service without it.
- For cash, the same code also records your payment, so staff take your cash and enter the code together.
- Keep it to yourself. If staff enter a wrong code 5 times for the same order or booking, they are locked out for 30 minutes.

### Pay online (card or Instant EFT)

1. Choose **Pay online**, then pick **Card** or **Instant EFT**.
2. Click **Place order**. You are redirected to Paystack to pay.
3. If you are not redirected, click **Continue to payment** on the confirmation screen.

> If you see **Online payment is not available on this platform yet**, choose another method.

---

## Online Payment Return Page

After paying on Paystack you return to the store, to a page headed **Online payment** with a heading such as **Payment successful**. We check your payment for you; the page asks Paystack to confirm it if the confirmation is slow.

| Heading | Meaning |
|---------|---------|
| **Payment processing** | Waiting for Paystack to confirm. This usually takes a few seconds, so leave the page open. |
| **Payment successful** | Your order or booking is marked as paid, and your **handover code** is shown. For a booking fee, the page says the fee was received, your booking is confirmed and how much to pay in cash at the shop. |
| **Payment cancelled** | You cancelled on Paystack. You can try again (see **Pay now** under [Your Activity and History](#your-activity-and-history)). |

If confirmation is slow, the page says we have not received confirmation yet. Click **Check again** after a moment. It also shows your Paystack reference, which you can quote to support.

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

**Pending payment**, **Paid / confirmed**, **In progress**, **Completed**, **Cancelled**. If you paid a booking fee, a pending booking reads **Confirmed, pay the rest at the shop**. If you have not paid the fee yet, a warning says to pay it within 2 hours of booking or the booking is cancelled.

The Track page shows your **handover code** until the order is handed over or the service has started. For a booking with a fee it also lists the fee, what you pay at the shop, and any refund or kept fee on separate lines. You can cancel a pending cash booking here (see [Booking fee](#booking-fee-for-cash-bookings)).

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
| **Cash (pay in store)** | **Confirm booking (pay in store)** (or **Continue to pay the booking fee** if the store charges one) | You get a handover code. Give it to staff when you arrive and pay. If the store charges a booking fee, you pay the fee online first (see below). |
| **Pay online (card or Instant EFT)** | **Continue to payment** | You are redirected to Paystack (choose **Card** or **Instant EFT** first). You pay the full amount, so there is no booking fee. |

7. Note your **Reference** and your **handover code**. Click **Track booking** to check the status later, or **Done** to close.

If no slots appear, you see **No available slots**. Try a different date.

### Booking fee for cash bookings

Some stores charge a **booking fee** to hold your time when you choose to pay cash at the shop. It is a percentage of the price (the store chooses 0% to 50%; at 0% there is no fee).

- When you pick **Cash** and the store charges a fee, the booking window shows, for example, **Booking fee: R200.00** paid online now, and **R800.00** due in cash at the shop (total R1 000.00).
- Choose **Card** or **Instant EFT** and pay the fee on Paystack. Your booking is confirmed once the fee is received.
- Pay the fee **within 2 hours**, or the booking is cancelled and the time is freed. If it is still unpaid, use **Pay booking fee** in your history.
- At the shop, staff take the rest in cash and enter your handover code.
- The fee is not charged if you pay the full amount online.

Refunds:

| What happens | Your booking fee |
|--------------|------------------|
| You cancel at least 24 hours before the start | Refunded to your card or account (it can take up to 10 business days to show) |
| You cancel less than 24 hours before the start | Not refunded |
| You do not show up | Not refunded |
| The shop cancels your booking | Always refunded |

If your booking starts within 24 hours, the booking window warns you that the fee is not refundable once paid. Cancelling before the start is allowed for pending cash bookings.

To cancel, open the booking on the Track page or in your history and click **Cancel booking**. The dialog tells you whether your fee will be refunded, and afterwards a message confirms the result. A refund in progress is shown as **Booking fee refund in progress**.

Your receipt shows these on separate lines: **Booking fee (paid online)**, **Paid at the shop (cash)** or **Due at the shop (cash)**, **Booking fee refunded** or **Booking fee kept by the shop**, and **Total**.

---

## Your Activity and History

Open `/account/history` (top bar **Account**, or **Open history** on the hub). This page requires sign-in.

| Section | What you see |
|---------|--------------|
| **Referral code** | Your personal code to share. |
| **Orders** | Store, total, **View store**, your **Handover code** until handed over. **Pay now** appears for unpaid online orders. |
| **Bookings** | Salon, service, status, start time, **View salon**, your **Handover code** until the service starts, and the booking fee lines. **Pay now** appears for unpaid online bookings, **Pay booking fee** for an unpaid booking fee, and **Cancel booking** for pending cash bookings. |

Click **Pay now** or **Pay booking fee** to restart online payment for that order or booking.

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
| Lost my handover code | Open the Track page or **Your activity**. It shows until the order is handed over or the service has started. |
| My booking was cancelled after I chose cash | If the store charges a booking fee, it must be paid within 2 hours of booking. Book again and pay the fee. |
| Where is my booking fee refund? | See **Booking fee refund in progress** on your booking. Refunds can take up to 10 business days to reach your card or account. |
| Delivery option disabled | The store uses per-km pricing but has not set its location. Choose pickup. |
| **Tap the map to set your delivery location** | Per-km delivery needs a pin on the map. |
| Track page finds nothing | Check the ID and use the same email you entered at checkout. Choose the right type (order or booking). |
| Paystack page left before confirming | Open **Your activity** and use **Pay now**, or check status on the Track page. |
| No shops or salons on Discover | Widen the radius or move the pin. Some stores have not set a location. |
| Salon page says the shop is a **normal store** | That store does not take bookings. |

For store-specific questions use the store's **Contact us** page or **Help** (`/m/<slug>/help`).
