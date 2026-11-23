import { appHostsFor, buildAppOnlyScript } from '../appOnlyScript';
import { buildApiBaseUrlBridgeScript } from '../apiBaseUrlBridge';
import { DRAWER_STATE_BRIDGE } from '../drawerStateBridge';
import { APPLY_ROLE_LAYOUT_FROM_STORAGE, MOBILE_VIEWPORT_BRIDGE } from '../mobileViewportBridge';
import { NATIVE_GEOCODE_BRIDGE } from '../nativeGeocodeBridge';
import { NATIVE_GEOLOCATION_BRIDGE } from '../nativeGeolocationBridge';
import { PULL_TO_REFRESH_BRIDGE } from '../pullToRefreshBridge';
import { WEB_APP_STATE_BRIDGE } from '../webNavigationBridge';

/** Runs an injected script as if the page were at `pageUrl`; reports whether the guarded body ran. */
function runOn(pageUrl: string, script: string): { ran: boolean; result: unknown } {
  const fakeWindow: Record<string, unknown> = { location: new URL(pageUrl) };
  // eslint-disable-next-line no-new-func
  const result = new Function('window', `return eval(${JSON.stringify(script)});`)(fakeWindow);
  return { ran: fakeWindow.__ran === true, result };
}

describe('appOnlyScript', () => {
  const web = 'https://app.example.com';
  const api = 'https://api.example.com';
  const body = 'window.__ran = true;';

  it('lists the app and API hosts once', () => {
    expect(appHostsFor(web, api)).toEqual(['app.example.com', 'api.example.com']);
    expect(appHostsFor(web, web)).toEqual(['app.example.com']);
    expect(appHostsFor(web)).toEqual(['app.example.com']);
  });

  it('runs on the web app, its subdomains and the API host', () => {
    const script = buildAppOnlyScript(body, web, api, false);
    expect(runOn('https://app.example.com/m/shop', script).ran).toBe(true);
    expect(runOn('https://www.app.example.com/', script).ran).toBe(true);
    expect(runOn('https://api.example.com/x', script).ran).toBe(true);
  });

  it('does nothing on payment, bank or look alike pages, and still evaluates to true', () => {
    const script = buildAppOnlyScript(body, web, api, false);
    for (const url of [
      'https://checkout.paystack.com/abc',
      'https://standard.paystack.co/close',
      'https://acs.issuer-bank.example/challenge',
      'https://evilapp.example.com/',
      'https://app.example.com.evil.com/',
    ]) {
      const { ran, result } = runOn(url, script);
      expect(ran).toBe(false);
      expect(result).toBe(true);
    }
  });

  it('runs on LAN pages only when allowed', () => {
    expect(runOn('http://192.168.1.20:8080/', buildAppOnlyScript(body, web, api, true)).ran).toBe(true);
    expect(runOn('http://192.168.1.20:8080/', buildAppOnlyScript(body, web, api, false)).ran).toBe(false);
  });

  it('keeps a bridge style IIFE working inside the guard', () => {
    const script = buildAppOnlyScript('(function () { window.__ran = true; })();\ntrue;', web, api, false);
    expect(runOn('https://app.example.com/', script)).toEqual({ ran: true, result: true });
  });
});

describe('the real bridge scripts inside the guard', () => {
  it('still compile as one script', () => {
    const scripts = [
      MOBILE_VIEWPORT_BRIDGE,
      APPLY_ROLE_LAYOUT_FROM_STORAGE,
      buildApiBaseUrlBridgeScript('https://api.example.com'),
      NATIVE_GEOLOCATION_BRIDGE,
      NATIVE_GEOCODE_BRIDGE,
      PULL_TO_REFRESH_BRIDGE,
      WEB_APP_STATE_BRIDGE,
      DRAWER_STATE_BRIDGE,
    ];
    const guarded = buildAppOnlyScript(scripts.join('\n'), 'https://app.example.com', 'https://api.example.com', false);
    // eslint-disable-next-line no-new-func
    expect(() => new Function(guarded)).not.toThrow();
  });
});
