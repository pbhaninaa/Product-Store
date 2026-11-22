/** Hosts that must stay inside the WebView so PayFast + API callbacks complete in-app. */

/**
 * Lower-cased hostname (no port) of an http(s) URL, or null when the URL is malformed or ambiguous.
 *
 * Deliberately strict, because the browser and this check must agree on the host:
 * - a backslash is refused (`https://evil.com\.payfast.co.za/` loads evil.com in a browser);
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

const HOSTED_CHECKOUT_HOST_SUFFIXES = [
  'payfast.co.za',
  'peachpayments.com',
  'peachpayments.co.za',
];

function hostMatches(host: string, allowed: string): boolean {
  const a = allowed.toLowerCase();
  return host === a || host.endsWith(`.${a}`);
}

function isLanHost(host: string): boolean {
  return (
    host === 'localhost' ||
    host === '127.0.0.1' ||
    host === '10.0.2.2' ||
    /^192\.168\.\d{1,3}\.\d{1,3}$/.test(host) ||
    /^10\.\d{1,3}\.\d{1,3}\.\d{1,3}$/.test(host)
  );
}

declare const __DEV__: boolean | undefined;
const IS_DEV_BUILD = typeof __DEV__ !== 'undefined' && __DEV__ === true;

/**
 * True when this URL should load in the WebView (SPA, API, PayFast hosted checkout).
 * Local/LAN addresses are allowed only in development builds (emulator / SIT on the LAN), never in release.
 */
export function isAllowedWebViewUrl(
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

  if (HOSTED_CHECKOUT_HOST_SUFFIXES.some((suffix) => hostMatches(host, suffix))) {
    return true;
  }

  return allowLan && isLanHost(host);
}
