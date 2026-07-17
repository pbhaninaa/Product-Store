/** Sync native bottom nav with Vue 2/3 router + report login state. */
export const WEB_APP_STATE_BRIDGE = `
(function () {
  if (window.__wheelHubAppStateBridgeInstalled) return;
  window.__wheelHubAppStateBridgeInstalled = true;

  function decodeJwtRoles(token) {
    try {
      var parts = String(token || '').split('.');
      if (parts.length !== 3) return [];
      var b64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
      while (b64.length % 4) b64 += '=';
      var json = JSON.parse(atob(b64));
      return Array.isArray(json.roles) ? json.roles : [];
    } catch (e) {
      return [];
    }
  }

  function sendState() {
    if (!window.ReactNativeWebView || !window.ReactNativeWebView.postMessage) return;
    try {
      var path = window.location.pathname || '/';
      var token = localStorage.getItem('ps_token') || '';
      var roles = decodeJwtRoles(token);
      var role = roles[0] || '';
      var loggedIn = !!token;
      var slugMatch = path.match(/^\\/m\\/([^/]+)/);
      var merchantSlug = slugMatch ? slugMatch[1] : '';
      try {
        var ctx = localStorage.getItem('ps_merchant_tenant_context');
        if (ctx) {
          var parsed = JSON.parse(ctx);
          if (parsed && parsed.slug) merchantSlug = parsed.slug;
        }
      } catch (e2) {}
      window.ReactNativeWebView.postMessage(JSON.stringify({
        type: 'WEB_APP_STATE',
        path: path,
        role: role,
        roles: roles,
        loggedIn: loggedIn,
        merchantSlug: merchantSlug
      }));
      if (typeof window.__wheelHubApplyRoleLayout === 'function') {
        window.__wheelHubApplyRoleLayout(role, loggedIn);
      }
    } catch (e) {}
  }

  window.__wheelHubSendAppState = sendState;

  function getRouter() {
    try {
      var el = document.getElementById('app');
      // Vue 2
      if (el && el.__vue__ && el.__vue__.$router) return el.__vue__.$router;
      // Vue 3 fallback
      var app = el && el.__vue_app__;
      if (!app) return null;
      var router = app.config && app.config.globalProperties && app.config.globalProperties.$router;
      if (router) return router;
      var provides = app._context && app._context.provides;
      if (!provides) return null;
      var symbols = Object.getOwnPropertySymbols(provides);
      for (var i = 0; i < symbols.length; i++) {
        var candidate = provides[symbols[i]];
        if (candidate && typeof candidate.push === 'function' && candidate.currentRoute) {
          return candidate;
        }
      }
    } catch (e) {}
    return null;
  }

  function hookRouter() {
    var router = getRouter();
    if (!router || router.__wheelHubHooked) return;
    router.__wheelHubHooked = true;
    if (typeof router.afterEach === 'function') {
      router.afterEach(function () { sendState(); });
    }
    sendState();
  }

  sendState();
  window.addEventListener('popstate', sendState);
  window.addEventListener('hashchange', sendState);
  setInterval(function () {
    hookRouter();
    sendState();
  }, 800);
})();
true;
`;

export function buildWebNavigateScript(path: string, baseUrl: string): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  const url = `${baseUrl.replace(/\/+$/, '')}${normalizedPath}`;
  const safeUrl = JSON.stringify(url);
  const safePath = JSON.stringify(normalizedPath);
  return `
(function () {
  var path = ${safePath};
  var target = ${safeUrl};
  var current = (window.location.pathname || '/') + (window.location.search || '');
  if (current === path) return;

  function notifyNative() {
    if (typeof window.__wheelHubSendAppState === 'function') {
      window.__wheelHubSendAppState();
    }
  }

  function getRouter() {
    try {
      var el = document.getElementById('app');
      if (el && el.__vue__ && el.__vue__.$router) return el.__vue__.$router;
      var app = el && el.__vue_app__;
      if (!app) return null;
      var router = app.config && app.config.globalProperties && app.config.globalProperties.$router;
      if (router) return router;
    } catch (e) {}
    return null;
  }

  var router = getRouter();
  if (router && typeof router.push === 'function') {
    var result = router.push(path);
    if (result && typeof result.then === 'function') {
      result.then(notifyNative).catch(function () { window.location.assign(target); });
    } else {
      notifyNative();
    }
    return;
  }

  try {
    window.history.pushState({ productStoreNav: true }, '', path);
    window.dispatchEvent(new PopStateEvent('popstate', { state: { productStoreNav: true } }));
    notifyNative();
    if ((window.location.pathname || '/') === path) return;
  } catch (e) {}

  window.location.assign(target);
})();
true;
`;
}

export type WebAppStateMessage = {
  type: 'WEB_APP_STATE';
  path: string;
  role: string;
  loggedIn: boolean;
  merchantSlug?: string;
  roles?: string[];
};

export function isWebAppStateMessage(raw: unknown): raw is WebAppStateMessage {
  if (!raw || typeof raw !== 'object') return false;
  const msg = raw as WebAppStateMessage;
  return msg.type === 'WEB_APP_STATE' && typeof msg.path === 'string';
}

export function buildWebAppUrl(baseUrl: string, path: string): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return `${baseUrl.replace(/\/+$/, '')}${normalizedPath}`;
}
