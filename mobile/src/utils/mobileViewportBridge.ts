import type { MobileNavMode } from '../navigation/navigationMode';
import { resolveMobileNavMode } from '../navigation/navigationMode';

export const MOBILE_VIEWPORT_BRIDGE = `
(function () {
  if (window.__wheelHubViewportBridgeInstalled) return;
  window.__wheelHubViewportBridgeInstalled = true;
  window.__wheelHubNativeShell = true;

  var SHELL = 'servicehub-native-shell';
  var MODES = ['servicehub-nav-bottom', 'servicehub-nav-hamburger', 'servicehub-nav-none'];

  function markShell() {
    document.documentElement.classList.add(SHELL);
    if (document.body) document.body.classList.add(SHELL);
  }

  function readLoggedIn() {
    try {
      return !!(localStorage.getItem('ps_token') || '');
    } catch (e) {
      return false;
    }
  }

  function resolveMode(role, loggedIn) {
    var path = window.location.pathname || '/';
    var auth = ['/signup','/forgot-password','/reset-password'];
    if (auth.indexOf(path) >= 0) return 'none';
    if (path.indexOf('/support') === 0 || path.indexOf('/admin') >= 0) return 'hamburger';
    if (loggedIn) return 'hamburger';
    if (path.indexOf('/m/') === 0) return 'bottom';
    var first = (path.split('/').filter(Boolean)[0] || '');
    var reserved = { signup:1, 'forgot-password':1, 'reset-password':1, support:1 };
    if (first && !reserved[first]) return 'bottom';
    return 'none';
  }

  function setNavMode(mode) {
    var root = document.documentElement;
    for (var i = 0; i < MODES.length; i++) {
      root.classList.remove(MODES[i]);
    }
    if (mode === 'bottom') root.classList.add('servicehub-nav-bottom');
    if (mode === 'hamburger') root.classList.add('servicehub-nav-hamburger');
    if (mode === 'none') root.classList.add('servicehub-nav-none');
    syncDomNav(mode);
  }

  function syncDomNav(mode) {
    var hideWebNav = mode === 'bottom';
    var nodes = document.querySelectorAll('.v-app-bar, .app-bar, header.v-toolbar, .v-navigation-drawer, .v-navigation-drawer__scrim');
    for (var i = 0; i < nodes.length; i++) {
      nodes[i].style.setProperty('display', hideWebNav ? 'none' : '', 'important');
    }
  }

  function applyRoleLayout(role, loggedIn) {
    setNavMode(resolveMode(role, loggedIn !== false && (loggedIn || readLoggedIn())));
  }

  window.__wheelHubApplyRoleLayout = applyRoleLayout;

  function shellCss() {
    return [
      'html.' + SHELL + ', html.' + SHELL + ' body { width:100%!important; min-height:100%!important; margin:0!important; overflow-x:hidden!important; }',
      'html.servicehub-nav-bottom .v-app-bar, html.servicehub-nav-bottom .app-bar, html.servicehub-nav-bottom header.v-toolbar { display:none!important; }',
      'html.' + SHELL + '.servicehub-nav-bottom .v-main { padding-top:12px!important; padding-bottom:calc(72px + env(safe-area-inset-bottom,0px))!important; }',
    ].join('\\n');
  }

  function applyViewport() {
    markShell();
    var meta = document.querySelector('meta[name="viewport"]');
    if (!meta) {
      meta = document.createElement('meta');
      meta.setAttribute('name', 'viewport');
      document.head.appendChild(meta);
    }
    meta.setAttribute('content', 'width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no');
    var style = document.getElementById('servicehub-mobile-shell-style');
    if (!style) {
      style = document.createElement('style');
      style.id = 'servicehub-mobile-shell-style';
      document.head.appendChild(style);
    }
    style.textContent = shellCss();
    applyRoleLayout(null, readLoggedIn());
  }

  applyViewport();
  document.addEventListener('DOMContentLoaded', applyViewport);
  setInterval(function () { applyRoleLayout(null, readLoggedIn()); }, 1500);
})();
true;
`;

export function buildRoleLayoutScript(
  role: string | null | undefined,
  loggedIn: boolean,
): string {
  const safeRole = JSON.stringify(role || '');
  const safeLoggedIn = loggedIn ? 'true' : 'false';
  return `
(function () {
  if (typeof window.__wheelHubApplyRoleLayout === 'function') {
    window.__wheelHubApplyRoleLayout(${safeRole}, ${safeLoggedIn});
  }
})();
true;
`;
}

export const APPLY_ROLE_LAYOUT_FROM_STORAGE = `
(function () {
  if (typeof window.__wheelHubApplyRoleLayout === 'function') {
    try {
      var token = localStorage.getItem('ps_token') || '';
      window.__wheelHubApplyRoleLayout('', !!token);
    } catch (e) {}
  }
})();
true;
`;

export function navModeFromState(
  role: string | null | undefined,
  loggedIn: boolean,
  path: string,
): MobileNavMode {
  return resolveMobileNavMode(role, loggedIn, path);
}
