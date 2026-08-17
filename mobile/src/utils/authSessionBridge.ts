/** Sync native AsyncStorage session into the WebView SPA localStorage. */
export function buildAuthSessionBridgeScript(opts: {
  token: string;
  tenantContextJson?: string | null;
  /** After writing session, navigate here (admin/support) so Vue guards see the token. */
  redirectPath?: string | null;
}): string {
  const token = JSON.stringify(String(opts.token || ''));
  const ctx = JSON.stringify(
    opts.tenantContextJson != null ? String(opts.tenantContextJson) : '',
  );
  const redirect = JSON.stringify(
    opts.redirectPath != null ? String(opts.redirectPath) : '',
  );
  return `
(function () {
  try {
    var token = ${token};
    var ctx = ${ctx};
    var redirect = ${redirect};
    if (token) {
      localStorage.setItem('ps_token', token);
    } else {
      localStorage.removeItem('ps_token');
    }
    if (ctx) {
      localStorage.setItem('ps_merchant_tenant_context', ctx);
    } else {
      localStorage.removeItem('ps_merchant_tenant_context');
    }
    try {
      window.dispatchEvent(new Event('ps-auth-change'));
    } catch (e) {}

    if (token && redirect) {
      try {
        var path = window.location.pathname || '/';
        var onTarget = path === redirect || (redirect.indexOf('/admin') >= 0 && path.indexOf('/admin') >= 0);
        if (!onTarget) {
          var el = document.getElementById('app');
          var router = el && el.__vue__ && el.__vue__.$router;
          if (router && typeof router.replace === 'function') {
            router.replace(redirect).catch(function () {
              window.location.replace(redirect);
            });
          } else if (path !== redirect) {
            window.location.replace(redirect);
          }
        }
      } catch (eNav) {}
    }
  } catch (e2) {}
})();
true;
`;
}

export function resolveStaffWebPath(user: {
  roles?: string[];
  tenant?: string;
} | null): string {
  if (!user) return '/login';
  const roles = Array.isArray(user.roles) ? user.roles : [];
  const merchant =
    roles.includes('MERCHANT_OWNER') || roles.includes('MERCHANT_STAFF');
  const support =
    roles.includes('SUPPORT_USER') || roles.includes('PLATFORM_ADMIN');
  if (support && !merchant) return '/support';
  const slug = String(user.tenant || '').trim();
  if (merchant && slug) return `/m/${encodeURIComponent(slug)}/admin`;
  if (support) return '/support';
  return '/login';
}
