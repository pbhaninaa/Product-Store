/** Hosts that must stay inside the WebView so PayFast + API callbacks complete in-app. */

export function hostOf(url: string): string | null {
  try {
    const match = url.match(/^https?:\/\/([^/?#]+)/i);
    return match?.[1]?.toLowerCase() ?? null;
  } catch {
    return null;
  }
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

/** True when this URL should load in the WebView (SPA, API, PayFast hosted checkout). */
export function isAllowedWebViewUrl(
  requestUrl: string,
  webAppUrl: string,
  apiBaseUrl?: string,
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

  // Local SIT (emulator / LAN)
  if (
    host === 'localhost' ||
    host === '127.0.0.1' ||
    host === '10.0.2.2' ||
    /^192\.168\.\d+\.\d+$/.test(host) ||
    /^10\.\d+\.\d+\.\d+$/.test(host)
  ) {
    return true;
  }

  return false;
}
