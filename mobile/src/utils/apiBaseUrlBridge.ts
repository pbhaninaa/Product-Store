/** Bakes the native shell API base URL into the WebView so the SPA hits Railway. */
export function buildApiBaseUrlBridgeScript(apiBaseUrl: string): string {
  const safe = JSON.stringify(apiBaseUrl);
  return (
    `window.__productStoreApiBaseUrl = ${safe};` +
    `window.__wheelHubApiBaseUrl = ${safe};` +
    'true;'
  );
}
