# Product Store (mobile)

React Native WebView shell that loads the Product Store Vue web app — same UX as the browser.

## Environments

| Flavor | WebView | API | App ID |
|--------|---------|-----|--------|
| **sit** | `http://10.0.2.2:8085` | `http://10.0.2.2:8080` | `com.productstore.sit` |
| **uat** / **prod** | Set in `src/config/environments.ts` | Railway API default included | `com.productstore[.uat]` |

Set `HOSTED_WEB_APP_URL` and `DEFAULT_MERCHANT_SLUG` in `src/config/environments.ts` before UAT/PROD builds. The app opens `/{slug}` for the guest storefront (clients — no login). Staff and owners sign in at `/login`; UAT/PROD inject `https://product-store-production-b8bf.up.railway.app` into the WebView so API calls hit Railway.

## Commands

```bash
npm install
npm run android:sit
npm run android:uat
npm run android:prod
```

## Roles

- **Guests** on `/m/:slug` — native bottom tabs (Shop, Salon, Cart, Contact, Admin)
- **Merchant / Support** — web app bar + navigation inside the WebView

Payments (Cash, Manual EFT, Peach) run in the web app — identical to the mobile browser.
