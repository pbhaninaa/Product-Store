# Product Store User Manuals

**Last updated:** November 2026

Product Store is a multi-store platform. Merchants run online shops and salons; customers browse, buy, book and pay; the platform team supports everyone from one console. There is one manual per type of user.

---

## Which Manual Is for Me?

### I'm a customer / shopper

**Who you are:**
- You buy products from a store, or book salon appointments
- You may have a direct store link (e.g. `/m/demo`) or use the Customer hub to find nearby shops and salons

**What you'll learn:**
- Finding shops and salons near you
- Cart, checkout, pickup vs delivery
- Paying with Cash, Manual EFT (with proof upload) or Paystack (card or Instant EFT)
- Tracking orders and bookings, rating the store
- Your customer account, history and password reset

**Read:** [CLIENT_USER_MANUAL.md](CLIENT_USER_MANUAL.md)

**Quick links:**
- [Your Customer Account](CLIENT_USER_MANUAL.md#your-customer-account)
- [Finding Shops and Salons](CLIENT_USER_MANUAL.md#finding-shops-and-salons)
- [Cart and Checkout](CLIENT_USER_MANUAL.md#cart-and-checkout)
- [Paying for Your Order](CLIENT_USER_MANUAL.md#paying-for-your-order)
- [Tracking an Order or Booking](CLIENT_USER_MANUAL.md#tracking-an-order-or-booking)
- [Booking a Salon Appointment](CLIENT_USER_MANUAL.md#booking-a-salon-appointment)
- [Troubleshooting](CLIENT_USER_MANUAL.md#troubleshooting)

---

### I own or work at a store (merchant owner or staff)

**Who you are:**
- You signed up at `/signup`, or the owner gave you a staff login
- You manage products, orders, salon bookings and settings at `/m/<your-slug>/admin`

**What you'll learn:**
- Creating a store and the 7-day Free Trial
- Paying for a plan with Paystack (card or Instant EFT)
- Products, promotions, orders, fulfilment and invoices
- Confirming cash and Manual EFT payments
- Store settings: delivery, payment methods, banking, branding, hours
- Salon services, staff schedules and bookings
- Team & Payroll, alerts and help tickets
- What owners can do that staff cannot

**Read:** [MERCHANT_USER_MANUAL.md](MERCHANT_USER_MANUAL.md)

**Quick links:**
- [Creating Your Store](MERCHANT_USER_MANUAL.md#creating-your-store)
- [Owner vs Staff](MERCHANT_USER_MANUAL.md#owner-vs-staff)
- [Plan and Billing](MERCHANT_USER_MANUAL.md#plan-and-billing)
- [Orders](MERCHANT_USER_MANUAL.md#orders)
- [Store Settings](MERCHANT_USER_MANUAL.md#store-settings)
- [Salon: Services, Staff, Bookings, Payments](MERCHANT_USER_MANUAL.md#salon-services-staff-bookings-payments)
- [Team and Payroll](MERCHANT_USER_MANUAL.md#team-and-payroll)
- [Troubleshooting](MERCHANT_USER_MANUAL.md#troubleshooting)

---

### I'm on the platform team (platform admin or support)

**Who you are:**
- You have a `PLATFORM_ADMIN` or `SUPPORT_USER` account
- You sign in at `/m/platform/admin?support=1` and work in the Platform console at `/support`

**What you'll learn:**
- Permissions and which tabs each role sees
- Managing merchants and checking subscription status
- Answering tickets
- Shadow sessions (signing in as a merchant owner)
- Support staff, feature flags, audit log, help contact, referrals

**Read:** [SUPPORT_USER_MANUAL.md](SUPPORT_USER_MANUAL.md)

**Quick links:**
- [Signing In](SUPPORT_USER_MANUAL.md#signing-in)
- [Roles and Permissions](SUPPORT_USER_MANUAL.md#roles-and-permissions)
- [Merchants](SUPPORT_USER_MANUAL.md#merchants)
- [Billing (Subscriptions)](SUPPORT_USER_MANUAL.md#billing-subscriptions)
- [Tickets](SUPPORT_USER_MANUAL.md#tickets)
- [Shadow (Sign In as Merchant)](SUPPORT_USER_MANUAL.md#shadow-sign-in-as-merchant)
- [Troubleshooting](SUPPORT_USER_MANUAL.md#troubleshooting)

---

## Manual Contents Summary

| Manual | Audience | Main topics |
|--------|----------|-------------|
| **CLIENT** | Customers | Discover, store browsing, checkout, payments, tracking, salon booking, history |
| **MERCHANT** | Store owners and staff | Signup, trial and billing, products, orders, settings, salon, team and payroll |
| **SUPPORT** | Platform admin and support | Console tabs, permissions, merchants, billing, tickets, shadow, staff |

---

## Key URLs

| Page | URL | Who |
|------|-----|-----|
| Customer hub | `/` | Signed-in customers |
| Discover nearby shops / salons | `/discover` | Signed-in customers |
| Customer sign-in / register | `/account/login`, `/account/register` | Customers |
| Customer history | `/account/history` | Signed-in customers |
| Store front | `/m/<slug>` (SIT demo: `/m/demo`) | Anyone |
| Checkout | `/m/<slug>/checkout` | Anyone |
| Track order / booking | `/m/<slug>/track` | Anyone with order ID and email |
| Salon services | `/m/<slug>/salon/services` | Anyone (salon stores) |
| Offers / Contact / Help | `/m/<slug>/promotions`, `/m/<slug>/contact`, `/m/<slug>/help` | Anyone |
| Merchant signup | `/signup` | New store owners |
| Merchant admin | `/m/<slug>/admin` | Merchant owner and staff |
| Support sign-in | `/m/platform/admin?support=1` | Platform admin and support |
| Platform console | `/support` | Platform admin and support |
| Password reset | `/forgot-password` | Anyone with an account |

---

## Common Workflows

| I want to... | See |
|--------------|-----|
| Buy something and pay by bank transfer | [Client: Manual EFT](CLIENT_USER_MANUAL.md#manual-eft-bank-transfer-with-proof) |
| Check where my order is | [Client: Tracking](CLIENT_USER_MANUAL.md#tracking-an-order-or-booking) |
| Open a new store | [Merchant: Creating Your Store](MERCHANT_USER_MANUAL.md#creating-your-store) |
| Unlock my admin after the trial | [Merchant: Plan and Billing](MERCHANT_USER_MANUAL.md#plan-and-billing) |
| Mark a cash order as paid | [Merchant: Confirm a cash payment](MERCHANT_USER_MANUAL.md#confirm-a-cash-payment) |
| Choose which payment methods customers see | [Merchant: Customer payments](MERCHANT_USER_MANUAL.md#customer-payments-which-methods-customers-can-use) |
| Help a merchant inside their admin | [Support: Shadow](SUPPORT_USER_MANUAL.md#shadow-sign-in-as-merchant) |
| Add a support colleague | [Support: Staff](SUPPORT_USER_MANUAL.md#staff) |

---

## Payments at a Glance

| Used for | Cash | Manual EFT (proof upload) | Paystack (card / Instant EFT) |
|----------|:----:|:-------------------------:|:----------------------------:|
| Product orders | If the store enables it | If the store enables it and has saved bank details | If the store enables it and Paystack is configured on the platform |
| Salon bookings | Same as above | Same as above | Same as above |
| Merchant subscriptions | No | No | Yes (only option) |

---

## Account Security Tips

- Use a password of at least 8 characters. Never share it.
- Owners: give each staff member their own login under **Team & Payroll** and deactivate leavers.
- Support: always click **Exit shadow** when you finish a shadow session, and sign out of the console when you are done.
