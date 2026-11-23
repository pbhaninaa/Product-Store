import {
  hostOf,
  isAllowedWebViewUrl,
  isPaymentCheckoutUrl,
  isPaymentFlowActive,
  isPaymentProviderUrl,
  isTrustedAppPage,
  nextPaymentFlow,
  PAYMENT_FLOW_IDLE,
  PAYMENT_FLOW_MAX_MS,
  shouldLoadInWebView,
  type PaymentFlow,
} from '../allowedWebViewUrl';

describe('allowedWebViewUrl', () => {
  const web = 'https://app.example.com';
  const api = 'https://api.example.com';

  it('allows the SPA host', () => {
    expect(isAllowedWebViewUrl('https://app.example.com/checkout', web, api)).toBe(true);
  });

  it('allows the API host', () => {
    expect(isAllowedWebViewUrl('https://api.example.com/api/payments/paystack/configured', web, api)).toBe(true);
  });

  it('allows the Paystack hosted checkout and its 3DS close page', () => {
    expect(isAllowedWebViewUrl('https://checkout.paystack.com/0peioxfhpn', web, api)).toBe(true);
    expect(isAllowedWebViewUrl('https://standard.paystack.co/close', web, api)).toBe(true);
    expect(isPaymentProviderUrl('https://checkout.paystack.com/0peioxfhpn')).toBe(true);
    expect(isPaymentProviderUrl('https://app.example.com/payment/return')).toBe(false);
  });

  it('allows exact Paystack hosts over https only', () => {
    expect(isAllowedWebViewUrl('https://dashboard.paystack.com/', web, api)).toBe(false);
    expect(isAllowedWebViewUrl('https://paystack.com/', web, api)).toBe(false);
    expect(isAllowedWebViewUrl('http://checkout.paystack.com/abc', web, api)).toBe(false);
  });

  it('rejects unrelated external hosts, including other payment providers', () => {
    expect(isAllowedWebViewUrl('https://maps.google.com/dir', web, api)).toBe(false);
    expect(isAllowedWebViewUrl('https://checkout.other-provider.example/pay', web, api)).toBe(false);
  });

  it('parses hosts', () => {
    expect(hostOf('https://App.Example.com/path')).toBe('app.example.com');
    expect(hostOf('http://10.0.2.2:8080/m/shop')).toBe('10.0.2.2');
  });

  it('rejects URLs a browser would send somewhere else', () => {
    expect(isAllowedWebViewUrl('https://evil.com\\.paystack.com/', web, api)).toBe(false);
    expect(isAllowedWebViewUrl('https://app.example.com@evil.com/', web, api)).toBe(false);
    expect(isAllowedWebViewUrl('https://evil.com\t.paystack.com/', web, api)).toBe(false);
    expect(isAllowedWebViewUrl('https://evilpaystack.com/', web, api)).toBe(false);
    expect(isAllowedWebViewUrl('https://checkout.paystack.com.evil.com/', web, api)).toBe(false);
    expect(isAllowedWebViewUrl('javascript:alert(1)//checkout.paystack.com', web, api)).toBe(false);
  });

  it('allows LAN addresses only in development builds', () => {
    expect(isAllowedWebViewUrl('http://192.168.1.20:8080/', web, api, true)).toBe(true);
    expect(isAllowedWebViewUrl('http://192.168.1.20:8080/', web, api, false)).toBe(false);
    expect(isAllowedWebViewUrl('http://localhost:8080/', web, api, false)).toBe(false);
  });
});

describe('online payment flow in the WebView', () => {
  const web = 'https://app.example.com';
  const api = 'https://api.example.com';
  const checkout = 'https://checkout.paystack.com/abc';
  const bank3ds = 'https://acs.issuer-bank.example/challenge?id=1';
  const T0 = 1_000_000;

  function walk(urls: string[], start: PaymentFlow = PAYMENT_FLOW_IDLE, now = T0): PaymentFlow {
    return urls.reduce((flow, url) => nextPaymentFlow(flow, url, web, now), start);
  }

  it('enters only on checkout.paystack.com', () => {
    expect(isPaymentCheckoutUrl(checkout)).toBe(true);
    expect(isPaymentCheckoutUrl('https://standard.paystack.co/close')).toBe(false);
    expect(isPaymentCheckoutUrl('http://checkout.paystack.com/abc')).toBe(false);
    expect(walk(['https://standard.paystack.co/close']).active).toBe(false);
    expect(walk([bank3ds]).active).toBe(false);
    expect(walk(['https://maps.google.com/']).active).toBe(false);
    expect(walk([checkout])).toEqual({ active: true, startedAt: T0 });
  });

  it('stays active through 3DS and bank pages and ends on any web app page', () => {
    const during = walk([checkout, bank3ds, 'https://standard.paystack.co/close']);
    expect(isPaymentFlowActive(during, T0)).toBe(true);
    expect(walk(['https://app.example.com/m/shop/payment/return?kind=order'], during)).toEqual(PAYMENT_FLOW_IDLE);
    // Android back, or a tab press that loads any app page, ends it too.
    expect(walk(['https://app.example.com/m/shop'], during).active).toBe(false);
  });

  it('keeps its start time when the checkout page loads again', () => {
    const started = walk([checkout]);
    expect(nextPaymentFlow(started, checkout, web, T0 + 60_000)).toBe(started);
  });

  it('times out after 30 minutes', () => {
    const started = walk([checkout]);
    expect(isPaymentFlowActive(started, T0 + PAYMENT_FLOW_MAX_MS)).toBe(true);
    expect(isPaymentFlowActive(started, T0 + PAYMENT_FLOW_MAX_MS + 1)).toBe(false);
    expect(nextPaymentFlow(started, bank3ds, web, T0 + PAYMENT_FLOW_MAX_MS + 1)).toEqual(PAYMENT_FLOW_IDLE);
    expect(isPaymentFlowActive(started, T0 - 1)).toBe(false);
  });

  it('keeps card issuer and bank redirects in the WebView only while paying', () => {
    expect(shouldLoadInWebView(bank3ds, { webAppUrl: web, apiBaseUrl: api, paymentInProgress: true })).toBe(true);
    expect(shouldLoadInWebView(bank3ds, { webAppUrl: web, apiBaseUrl: api, paymentInProgress: false })).toBe(false);
  });

  it('never keeps plain http or malformed pages, even while paying', () => {
    const ctx = { webAppUrl: web, apiBaseUrl: api, paymentInProgress: true, allowLan: false };
    expect(shouldLoadInWebView('http://bank.example/', ctx)).toBe(false);
    expect(shouldLoadInWebView('https://evil.com\\.bank.example/', ctx)).toBe(false);
    expect(shouldLoadInWebView('javascript:alert(1)', ctx)).toBe(false);
  });
});

describe('isTrustedAppPage (bridge messages and native injection)', () => {
  const web = 'https://app.example.com';
  const api = 'https://api.example.com';

  it('trusts only the web app and API hosts', () => {
    expect(isTrustedAppPage('https://app.example.com/m/shop', web, api, false)).toBe(true);
    expect(isTrustedAppPage('https://api.example.com/x', web, api, false)).toBe(true);
    expect(isTrustedAppPage('https://checkout.paystack.com/abc', web, api, false)).toBe(false);
    expect(isTrustedAppPage('https://acs.issuer-bank.example/', web, api, false)).toBe(false);
  });

  it('fails closed without a usable source URL', () => {
    expect(isTrustedAppPage(undefined, web, api, false)).toBe(false);
    expect(isTrustedAppPage(null, web, api, false)).toBe(false);
    expect(isTrustedAppPage('', web, api, false)).toBe(false);
    expect(isTrustedAppPage('about:blank', web, api, false)).toBe(false);
    expect(isTrustedAppPage('data:text/html,hi', web, api, false)).toBe(false);
    expect(isTrustedAppPage('https://app.example.com@evil.com/', web, api, false)).toBe(false);
  });

  it('trusts LAN pages only in development builds', () => {
    expect(isTrustedAppPage('http://192.168.1.20:8080/', web, api, true)).toBe(true);
    expect(isTrustedAppPage('http://192.168.1.20:8080/', web, api, false)).toBe(false);
  });
});
