import { hostOf, IS_DEV_BUILD } from './allowedWebViewUrl';

/**
 * Wraps a script the native shell injects into the WebView so it only runs on the app's own pages.
 *
 * The WebView also shows the payment provider, card issuer (3DS) and bank pages during an online payment.
 * Scripts injected on every load (`injectedJavaScript*`) and replies sent later (location, geocoding, layout)
 * must never run there: they expose the API base URL, the customer's location and the bridge functions.
 * The check runs in the page at execution time, on `location.hostname`, so a reply that races a navigation
 * is dropped too. Hosts match exactly or as a subdomain, the same rule as `isAppUrl`.
 */
export function appHostsFor(webAppUrl: string, apiBaseUrl?: string): string[] {
  const hosts = [hostOf(webAppUrl), apiBaseUrl ? hostOf(apiBaseUrl) : null].filter(
    (h): h is string => typeof h === 'string' && h !== '',
  );
  return Array.from(new Set(hosts));
}

const LAN_HOST_PATTERN =
  '^(localhost|127\\.0\\.0\\.1|10\\.0\\.2\\.2|192\\.168\\.\\d{1,3}\\.\\d{1,3}|10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})$';

/** JavaScript expression that is true when the current page is one of `hosts` (or LAN, when allowed). */
export function appHostCheckExpression(hosts: string[], allowLan: boolean): string {
  const safeHosts = JSON.stringify(hosts.map((h) => h.toLowerCase()));
  const lan = allowLan ? `new RegExp(${JSON.stringify(LAN_HOST_PATTERN)}).test(h)` : 'false';
  return (
    '(function () {' +
    "  var h = String((window.location && window.location.hostname) || '').toLowerCase();" +
    '  if (!h) return false;' +
    `  var allowed = ${safeHosts};` +
    '  for (var i = 0; i < allowed.length; i++) {' +
    '    var a = allowed[i];' +
    "    if (h === a || (h.length > a.length && h.slice(-(a.length + 1)) === '.' + a)) return true;" +
    '  }' +
    `  return ${lan};` +
    '})()'
  );
}

/**
 * `script` guarded by the app host check. The bridge scripts are self-contained IIFEs and `window.x = ...`
 * assignments, so running them inside an `if` block keeps their meaning.
 */
export function buildAppOnlyScript(
  script: string,
  webAppUrl: string,
  apiBaseUrl?: string,
  allowLan: boolean = IS_DEV_BUILD,
): string {
  return `if (${appHostCheckExpression(appHostsFor(webAppUrl, apiBaseUrl), allowLan)}) {\n${script}\n}\ntrue;`;
}
