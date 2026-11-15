import { resolveEnvironmentEndpoints } from '../environments';

describe('resolveEnvironmentEndpoints', () => {
  it('points SIT at the local Vue dev server and local API', () => {
    const sit = resolveEnvironmentEndpoints('sit');
    expect(sit.webAppUrl).toMatch(/^http:\/\/[^/]+:8085$/);
    expect(sit.apiBaseUrl).toMatch(/^http:\/\/[^/]+:8080$/);
  });

  it.each(['uat', 'prod'] as const)('uses https hosted URLs for %s', (env) => {
    const e = resolveEnvironmentEndpoints(env);
    expect(e.webAppUrl).toMatch(/^https:\/\//);
    expect(e.apiBaseUrl).toMatch(/^https:\/\//);
  });

  // Release blocker: UAT/PROD APKs load this URL in the WebView. Set HOSTED_WEB_APP_URL in
  // src/config/environments.ts to the real Vercel frontend before shipping.
  it.each(['uat', 'prod'] as const)('has a real (non-placeholder) web app URL for %s', (env) => {
    expect(resolveEnvironmentEndpoints(env).webAppUrl).not.toMatch(/YOUR-|your-/);
  });
});
