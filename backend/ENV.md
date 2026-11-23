# Environment variables — Product-Store backend

**Last updated:** November 2026

Every variable the Spring Boot API reads, whether it is needed, and where its value comes from.
Paste-ready templates per branch: [railway-env-values.example.txt](railway-env-values.example.txt).
Frontend (Vercel) variables: [../docs/ENVIRONMENTS.md](../docs/ENVIRONMENTS.md).

**Required** = UAT/PROD will not start or will not work without it. **Feature** = only when you use that
feature. **Optional** = has a safe default; leave unset. **Local** = developer machines only, never on Railway.

Railway can define a variable with an **empty** value. Boolean flags (`*_ENABLED`, `*_SANDBOX`,
`PLATFORM_ADMIN_SYNC_PASSWORD`) treat blank as "use the default", so a blank value never stops the app booting.

## Profile and database

| Variable | Needed | Where to get the value |
|----------|--------|------------------------|
| `SPRING_PROFILES_ACTIVE` | Required | Type it: `uat` or `prod` on Railway (`sit` = local H2, `local` = local MySQL). Without it the app uses `local` and looks for MySQL on localhost |
| `SPRING_DATASOURCE_URL` | Required* | Railway MySQL reference `jdbc:${{MySQL.MYSQL_URL}}` (must resolve to `jdbc:mysql://…`) |
| `SPRING_DATASOURCE_USERNAME` | Required* | `${{MySQL.MYSQLUSER}}` |
| `SPRING_DATASOURCE_PASSWORD` | Required* | `${{MySQL.MYSQLPASSWORD}}` |
| `MYSQLHOST`, `MYSQLPORT`, `MYSQLDATABASE`, `MYSQLUSER`, `MYSQLPASSWORD` | Fallback | Injected by Railway when the MySQL service is linked. Used only when the `SPRING_DATASOURCE_*` values are unset |
| `PORT` | Optional | Injected by Railway; defaults to `8080` |

\* Either the three `SPRING_DATASOURCE_*` values or the linked `MYSQL*` values.

Schema is managed by Hibernate (`ddl-auto=update`) plus idempotent startup migrations (e.g. `DatabaseSchemaUpkeep`).
**No manual SQL, no Flyway.** The `db/migration/V*.sql` files are kept for reference only.

## Security and URLs

| Variable | Needed | Where to get the value |
|----------|--------|------------------------|
| `APP_JWT_SECRET` | Required | Generate: `openssl rand -base64 48`. Different per environment; changing it signs everyone out. `JWT_SECRET` is accepted as a fallback name. The app refuses to start without it |
| `JWT_ISSUER`, `JWT_AUDIENCE` | Optional | Defaults `productstore` / `productstore-web` |
| `APP_JWT_TTL_HOURS` | Optional | How long a login lasts; default `12` hours |
| `PUBLIC_APP_BASE_URL` | Required | Vercel project → **Settings → Domains** (`https://`, no trailing slash). Used for password-reset and online payment return links. **UAT/PROD refuse to start if it is missing or not `https://`** |
| `UAT_CORS_ORIGINS` / `PROD_CORS_ORIGINS` | Required | Every frontend origin for that environment, comma-separated, no trailing slash. If unset (and `PUBLIC_APP_BASE_URL` too) browsers are refused and the log shows a CORS error |
| `PUBLIC_BASE_URL` | Required | Railway backend → **Settings → Networking → Public Domain**. Builds absolute product-image URLs |
| `UPLOADS_DIR` | Required | `/app/data/uploads` with a Railway **volume** mounted at `/app/data`, so product images survive redeploys |
| `PASSWORD_RESET_TOKEN_TTL_HOURS` | Optional | Default `24` |

## Platform admin account

| Variable | Needed | Where to get the value |
|----------|--------|------------------------|
| `PLATFORM_ADMIN_EMAIL` | Required on a new database | The support/platform admin login. Created on startup if missing |
| `PLATFORM_ADMIN_PASSWORD` | Required on a new database | Choose a strong password. **No default on UAT/PROD**: if either value is blank, no admin is created |
| `PLATFORM_ADMIN_SYNC_PASSWORD` | Optional | `true` once to reset the existing admin's password to `PLATFORM_ADMIN_PASSWORD`; redeploy, then set back to `false` |

## Payments: Paystack (card and Instant EFT)

Paystack is the online payment provider for customer orders, salon bookings and merchant subscriptions,
all paid into the single platform Paystack account.

| Variable | Needed | Where to get the value |
|----------|--------|------------------------|
| `PAYSTACK_ENABLED` | Feature | Type it: `true` once the secret key is set |
| `PAYSTACK_SECRET_KEY` | Feature | Paystack Dashboard → **Settings → API Keys & Webhooks** → Secret Key. `sk_test_…` on SIT/UAT, `sk_live_…` on PROD (PROD logs a warning for a test key). The same key verifies webhook signatures |
| `PAYSTACK_BASE_URL` | Optional | Default `https://api.paystack.co`. Must be `https://` |
| `PAYSTACK_VERIFY_CACHE_TTL` | Optional | Default `PT10S`. How long one Paystack "verify" answer is reused per payment reference, so polling cannot flood Paystack |
| `PAYMENTS_RECONCILE_ENABLED` | Optional | Default `true`. Turns the payment reconciliation job on or off (`app.payments.reconcile-enabled`) |
| `PAYMENTS_RECONCILE_INTERVAL` | Optional | Default `PT2M` (every 2 minutes, ISO 8601 duration; `app.payments.reconcile-interval`) |
| `PAYMENTS_RECONCILE_BATCH_SIZE` | Optional | Default `50` payments checked per run, oldest first |

**PROD needs a live key:** with `PAYSTACK_ENABLED=true`, PROD refuses to start unless `PAYSTACK_SECRET_KEY` starts with
`sk_live_`, and it never settles a Paystack transaction whose domain is not `live`. Other environments log a warning
when given a live key.

**Webhooks are optional.** The Paystack account is shared with another app and Paystack allows one webhook URL per
account and mode, so this app may receive no webhooks at all. The reconciliation job confirms every online order,
booking and subscription payment started in the last 24 hours that is still waiting (including orders cancelled by
expiry, which a late payment revives), using Paystack's verify call. It only ever touches references this app created
(`ORD`, `BKG`, `SUB` + 16 letters or digits, with matching metadata). If a relay forwards events, point it at
`{Railway backend public domain}/api/payments/paystack/webhook`; it is verified with `PAYSTACK_SECRET_KEY`.
With several backend replicas each runs the job; that only costs extra verify calls, since settling is idempotent
and locks the record row. Customers return to `PUBLIC_APP_BASE_URL` after paying.

Check with `GET /api/payments/paystack/configured`.

## Email and WhatsApp

| Variable | Needed | Where to get the value |
|----------|--------|------------------------|
| `SENDGRID_API_KEY` | Feature: email | SendGrid → **Settings → API Keys** (Mail Send). Can be shared across environments |
| `EMAIL_DOMAIN` | Feature: email | SendGrid → **Sender Authentication** → authenticated domain. Enables `info@`, `support@`, `security@`, `billing@`, `no-reply@` senders |
| `EMAIL_FROM` | Optional | Fallback sender when `EMAIL_DOMAIN` is blank |
| `SENDGRID_API_URL` | Optional | Default `https://api.sendgrid.com/v3/mail/send` |
| `WHATSAPP_ENABLED` | Feature: WhatsApp | `true` to send merchant WhatsApp alerts |
| `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN` | Feature: WhatsApp | Twilio Console → **Account Info** |
| `TWILIO_WHATSAPP_FROM` | Feature: WhatsApp | Twilio → **Messaging → Senders → WhatsApp senders**, as `whatsapp:+…` |

## Legacy platform banking (read-only history)

`PLATFORM_BANK_NAME`, `PLATFORM_BANK_ACCOUNT_NAME`, `PLATFORM_BANK_ACCOUNT_NUMBER`, `PLATFORM_BANK_BRANCH_CODE`,
`PLATFORM_BANK_REFERENCE_HINT`, `PLATFORM_BANK_PAYMENT_LINK` — optional seed values from when subscriptions were paid
by manual EFT. Subscription EFT is retired (endpoints return `410 Gone`); leave unset.

## SIT-only demo seed

Demo data is seeded only by the `sit` profile (see [../docs/DEMO_DATA.md](../docs/DEMO_DATA.md)). Overrides:
`DEMO_MERCHANT_EMAIL`, `DEMO_MERCHANT_PASSWORD`, `DEMO_CLIENT_EMAIL`, `DEMO_CLIENT_PASSWORD`, `DEMO_CLIENT_NAME`.
UAT and PROD hard-code the seed off.

## Local only

`application-local.properties` is gitignored. Copy `src/main/resources/application-local.example.properties`
and set `LOCAL_DB_PASSWORD` (or edit the file). Optional local mail catcher: `SMTP_HOST`, `SMTP_PORT`,
`SMTP_USERNAME`, `SMTP_PASSWORD`.
