/**
 * Which URLs stay inside the WebView (the web app, its API, and the online payment flow) and which pages may
 * talk to the native bridge (only the web app itself).
 */

/**
 * Lower-cased hostname (no port) of an http(s) URL, or null when the URL is malformed or ambiguous.
 *
 * Deliberately strict, because the browser and this check must agree on the host:
 * - a backslash is refused (`https://evil.com\.paystack.com/` loads evil.com in a browser);
 * - user-info (`https://app.example.com@evil.com/`) is refused;
 * - whitespace/control characters and anything but letters, digits, dots and hyphens in the host are refused.
 * React Native's URL polyfill does not reliably implement `hostname`, so the authority is parsed by hand.
 */
export function hostOf(url: string): string | null {
  if (typeof url !== 'string') return null;
  // eslint-disable-next-line no-control-regex
  if (/[\\\s\u0000-\u001f\u007f]/.test(url)) return null;
  const match = url.match(/^https?:\/\/([^/?#]*)/i);
  const authority = match?.[1];
  if (!authority || authority.includes('@')) return null;
  const portMatch = authority.match(/^([^:]+)(?::(\d{1,5}))?$/);
  if (!portMatch) return null;
  const host = portMatch[1].toLowerCase();
  if (!/^[a-z0-9.-]+$/.test(host) || host.startsWith('.') || host.endsWith('.') || host.includes('..')) {
    return null;
  }
  return host;
}

/**
 * Paystack hosts used by the hosted checkout: `checkout.paystack.com` (the payment page returned as
 * `authorization_url`) and `standard.paystack.co` (where card 3DS authorization finishes, `/close`).
 * Exact hosts only. Source: https://paystack.com/docs/guides/using_the_paystack_checkout_in_a_mobile_webview/
 */
const PAYMENT_CHECKOUT_HOST = 'checkout.paystack.com';
const PAYMENT_PROVIDER_HOSTS = [PAYMENT_CHECKOUT_HOST, 'standard.paystack.co'];

/** A payment flow ends by itself after this long, even if the customer never comes back to the app. */
export const PAYMENT_FLOW_MAX_MS = 30 * 60 * 1000;

export function hostMatches(host: string, allowed: string): boolean {
  const a = allowed.toLowerCase();
  return host === a || host.endsWith(`.${a}`);
}

export function isLanHost(host: string): boolean {
  return (
    host === 'localhost' ||
    host === '127.0.0.1' ||
    host === '10.0.2.2' ||
    /^192\.168\.\d{1,3}\.\d{1,3}$/.test(host) ||
    /^10\.\d{1,3}\.\d{1,3}\.\d{1,3}$/.test(host)
  );
}

declare const __DEV__: boolean | undefined;
export const IS_DEV_BUILD = typeof __DEV__ !== 'undefined' && __DEV__ === true;

/**
 * True for the app's own pages: the SPA host, the API host, and (development builds only) local/LAN addresses.
 * These are also the only pages trusted to post messages to the native bridge.
 */
export function isAppUrl(
  requestUrl: string,
  webAppUrl: string,
  apiBaseUrl?: string,
  allowLan: boolean = IS_DEV_BUILD,
): boolean {
  const host = hostOf(requestUrl);
  if (!host) return false;

  const appHost = hostOf(webAppUrl);
  if (appHost && hostMatches(host, appHost)) return true;

  if (apiBaseUrl) {
    const apiHost = hostOf(apiBaseUrl);
    if (apiHost && hostMatches(host, apiHost)) return true;
  }

  return allowLan && isLanHost(host);
}

/**
 * True only for a page of the app itself, so native code may inject scripts into it and accept its bridge
 * messages. Fails closed: a missing or unparseable URL is not trusted.
 */
export function isTrustedAppPage(
  pageUrl: string | null | undefined,
  webAppUrl: string,
  apiBaseUrl?: string,
  allowLan: boolean = IS_DEV_BUILD,
): boolean {
  return typeof pageUrl === 'string' && pageUrl !== '' && isAppUrl(pageUrl, webAppUrl, apiBaseUrl, allowLan);
}

/** True for the payment provider's hosted checkout pages (exact Paystack hosts, https only). */
export function isPaymentProviderUrl(requestUrl: string): boolean {
  const host = hostOf(requestUrl);
  return host != null && /^https:/i.test(requestUrl) && PAYMENT_PROVIDER_HOSTS.includes(host);
}

/** True for the Paystack checkout page itself: the only page that starts a payment flow. */
export function isPaymentCheckoutUrl(requestUrl: string): boolean {
  return /^https:/i.test(requestUrl) && hostOf(requestUrl) === PAYMENT_CHECKOUT_HOST;
}

/**
 * True when this URL should load in the WebView (SPA, API, Paystack hosted checkout).
 * Local/LAN addresses are allowed only in development builds (emulator / SIT on the LAN), never in release.
 */
export function isAllowedWebViewUrl(
  requestUrl: string,
  webAppUrl: string,
  apiBaseUrl?: string,
  allowLan: boolean = IS_DEV_BUILD,
): boolean {
  return isAppUrl(requestUrl, webAppUrl, apiBaseUrl, allowLan) || isPaymentProviderUrl(requestUrl);
}

/** An online payment flow in the WebView: active from the checkout page until the app loads again. */
export type PaymentFlow = { active: boolean; startedAt: number };

export const PAYMENT_FLOW_IDLE: PaymentFlow = Object.freeze({ active: false, startedAt: 0 });

/** True while the flow is active and younger than {@link PAYMENT_FLOW_MAX_MS}. */
export function isPaymentFlowActive(flow: PaymentFlow, now: number): boolean {
  return flow.active && now - flow.startedAt >= 0 && now - flow.startedAt <= PAYMENT_FLOW_MAX_MS;
}

/**
 * Flow after the WebView sees `url` as its top page (navigation request, load start or navigation state
 * change, so back/forward and programmatic source changes count too):
 * - the Paystack checkout page starts a flow (an active flow keeps its start time);
 * - any web app page ends it;
 * - anything else keeps an active flow until it times out.
 */
export function nextPaymentFlow(flow: PaymentFlow, url: string, webAppUrl: string, now: number): PaymentFlow {
  if (isPaymentCheckoutUrl(url)) {
    return isPaymentFlowActive(flow, now) ? flow : { active: true, startedAt: now };
  }
  const host = hostOf(url);
  const appHost = hostOf(webAppUrl);
  if (host && appHost && hostMatches(host, appHost)) return PAYMENT_FLOW_IDLE;
  return isPaymentFlowActive(flow, now) ? flow : PAYMENT_FLOW_IDLE;
}

export type WebViewNavigationContext = {
  webAppUrl: string;
  apiBaseUrl?: string;
  /** The customer opened the provider checkout and has not come back to the web app yet. */
  paymentInProgress: boolean;
  allowLan?: boolean;
};

/**
 * True when a navigation should stay in the WebView. While a payment is in progress the provider redirects to
 * the card issuer (3DS) or the customer's bank (Instant EFT). Those hosts cannot be listed up front, and the
 * payment fails if they open in the external browser, so any https page stays in the WebView until the web app
 * loads again. Those pages cannot use the native bridge and get no injected scripts (see `isTrustedAppPage`).
 */
export function shouldLoadInWebView(requestUrl: string, ctx: WebViewNavigationContext): boolean {
  const allowLan = ctx.allowLan ?? IS_DEV_BUILD;
  if (isAllowedWebViewUrl(requestUrl, ctx.webAppUrl, ctx.apiBaseUrl, allowLan)) return true;
  return ctx.paymentInProgress && /^https:\/\//i.test(requestUrl) && hostOf(requestUrl) != null;
}
