import { hostOf, isAllowedWebViewUrl } from '../allowedWebViewUrl';

describe('allowedWebViewUrl', () => {
  const web = 'https://app.example.com';
  const api = 'https://api.example.com';

  it('allows the SPA host', () => {
    expect(isAllowedWebViewUrl('https://app.example.com/checkout', web, api)).toBe(true);
  });

  it('allows the API host for PayFast return redirects', () => {
    expect(isAllowedWebViewUrl('https://api.example.com/api/payments/payfast/webhook', web, api)).toBe(true);
  });

  it('allows PayFast hosted checkout domains', () => {
    expect(isAllowedWebViewUrl('https://www.payfast.co.za/eng/process', web, api)).toBe(true);
    expect(isAllowedWebViewUrl('https://sandbox.payfast.co.za/eng/process', web, api)).toBe(true);
  });

  it('allows leftover Peach hosted checkout domains', () => {
    expect(isAllowedWebViewUrl('https://secure.peachpayments.com/checkout', web, api)).toBe(true);
    expect(isAllowedWebViewUrl('https://testsecure.peachpayments.com/checkout', web, api)).toBe(true);
  });

  it('rejects unrelated external hosts', () => {
    expect(isAllowedWebViewUrl('https://maps.google.com/dir', web, api)).toBe(false);
  });

  it('parses hosts', () => {
    expect(hostOf('https://App.Example.com/path')).toBe('app.example.com');
  });
});
