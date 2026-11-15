# Product Store mobile

React Native WebView shell (`mobile/`) that loads the hosted web app, so phone UX matches the browser
(Cash, Manual EFT and PayFast included). Flavors `sit` / `uat` / `prod`.

- Setup, release signing and tests: [mobile/README.md](mobile/README.md)
- Android SDK setup: [mobile/ANDROID-SETUP.md](mobile/ANDROID-SETUP.md)
- Per-environment URLs: `mobile/src/config/environments.ts` (`HOSTED_WEB_APP_URL`, `HOSTED_API_BASE_URL`, `DEFAULT_MERCHANT_SLUG`)
