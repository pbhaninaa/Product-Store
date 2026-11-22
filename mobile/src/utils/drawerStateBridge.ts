/**
 * Tells the native shell when the web app's slide-in side menu (a Vuetify temporary navigation drawer)
 * opens or closes, so the shell can hide the phone's navigation bar and its own bottom tabs meanwhile.
 * Posts only on change.
 */
export const DRAWER_STATE_BRIDGE = `
(function () {
  if (window.__productStoreDrawerBridgeInstalled) return;
  window.__productStoreDrawerBridgeInstalled = true;

  var last = null;
  function isOpen() {
    return !!document.querySelector('.v-navigation-drawer--temporary.v-navigation-drawer--open');
  }
  function report() {
    var open = isOpen();
    if (open === last) return;
    last = open;
    if (window.ReactNativeWebView && window.ReactNativeWebView.postMessage) {
      window.ReactNativeWebView.postMessage(JSON.stringify({ type: 'WEB_DRAWER', open: open }));
    }
  }
  function start() {
    report();
    new MutationObserver(report).observe(document.body, {
      attributes: true,
      attributeFilter: ['class'],
      childList: true,
      subtree: true
    });
  }
  if (document.body) start();
  else document.addEventListener('DOMContentLoaded', start);
})();
true;
`;

export type DrawerStateMessage = { type: 'WEB_DRAWER'; open: boolean };

export function isDrawerStateMessage(value: unknown): value is DrawerStateMessage {
  return (
    !!value &&
    typeof value === 'object' &&
    (value as { type?: unknown }).type === 'WEB_DRAWER' &&
    typeof (value as { open?: unknown }).open === 'boolean'
  );
}
