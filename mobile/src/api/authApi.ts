import AsyncStorage from '@react-native-async-storage/async-storage';
import { apiFetch } from './client';
import { normalizeShopType, type ShopType } from '../utils/shopType';

export const TOKEN_KEY = 'ps_token';
export const TENANT_CTX_KEY = 'ps_merchant_tenant_context';
export const LAST_GUEST_SLUG_KEY = 'ps_last_guest_merchant_slug';
export const SUPPORT_TOKEN_STASH = 'ps_support_token_stash';

export type MerchantTenantContext = {
  id: string;
  slug: string;
  name: string;
  shopType: ShopType;
};

export type SessionUser = {
  id: string;
  email: string;
  roles: string[];
  tenant: string;
  tenantId: string;
  tenantDetail: MerchantTenantContext | null;
  shadowSupport: boolean;
};

function decodeJwtPayload(token: string): Record<string, any> | null {
  const parts = String(token || '').split('.');
  if (parts.length !== 3) return null;
  try {
    const b64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const pad = b64.length % 4 === 0 ? '' : '='.repeat(4 - (b64.length % 4));
    // atob is available in Hermes / RN JS runtime
    const atobFn = (globalThis as any).atob as (data: string) => string;
    const json = atobFn(b64 + pad);
    return JSON.parse(json);
  } catch {
    return null;
  }
}

function isJwtExpired(payload: Record<string, any> | null): boolean {
  if (!payload || payload.exp == null) return false;
  const expMs = Number(payload.exp) * 1000;
  if (!Number.isFinite(expMs)) return false;
  return expMs <= Date.now() + 5_000;
}

function isMerchantRoles(roles: unknown): boolean {
  if (!Array.isArray(roles)) return false;
  return roles.some((r) => r === 'MERCHANT_OWNER' || r === 'MERCHANT_STAFF');
}

export function isSupportOrPlatformOnlyUser(u: SessionUser | null): boolean {
  if (!u || !Array.isArray(u.roles)) return false;
  const merchant =
    u.roles.includes('MERCHANT_OWNER') || u.roles.includes('MERCHANT_STAFF');
  const elevated =
    u.roles.includes('SUPPORT_USER') || u.roles.includes('PLATFORM_ADMIN');
  return elevated && !merchant;
}

export async function getStoredToken(): Promise<string> {
  try {
    return (await AsyncStorage.getItem(TOKEN_KEY)) || '';
  } catch {
    return '';
  }
}

export async function setStoredToken(token: string): Promise<void> {
  const t = String(token || '').trim();
  if (!t) await AsyncStorage.removeItem(TOKEN_KEY);
  else await AsyncStorage.setItem(TOKEN_KEY, t);
}

export async function clearAuthStorage(): Promise<void> {
  const keys = await AsyncStorage.getAllKeys();
  const toRemove = keys.filter((k) => k === TOKEN_KEY || k.startsWith('ps_'));
  if (toRemove.length) await AsyncStorage.multiRemove(toRemove);
}

export async function getMerchantTenantContext(): Promise<MerchantTenantContext | null> {
  try {
    const raw = await AsyncStorage.getItem(TENANT_CTX_KEY);
    if (!raw) return null;
    const o = JSON.parse(raw);
    if (!o || !o.slug || typeof o.slug !== 'string') return null;
    return {
      id: String(o.id || ''),
      slug: String(o.slug).trim(),
      name: String(o.name || ''),
      shopType: normalizeShopType(o.shopType),
    };
  } catch {
    return null;
  }
}

export async function clearMerchantTenantContext(): Promise<void> {
  await AsyncStorage.removeItem(TENANT_CTX_KEY);
}

async function persistMerchantTenantFromLoginResponse(
  res: any,
  jwtPayload: Record<string, any> | null,
): Promise<void> {
  const roles = Array.isArray(res?.roles)
    ? res.roles
    : Array.isArray(jwtPayload?.roles)
      ? jwtPayload!.roles
      : [];
  const t = res?.tenant;
  if (!isMerchantRoles(roles) || !t || typeof t !== 'object') {
    await clearMerchantTenantContext();
    return;
  }
  const slug = String(t.slug || '').trim();
  if (!slug) {
    await clearMerchantTenantContext();
    return;
  }
  const payload: MerchantTenantContext = {
    id: String(t.id || ''),
    slug,
    name: String(t.name || ''),
    shopType: normalizeShopType(t.shopType),
  };
  await AsyncStorage.setItem(TENANT_CTX_KEY, JSON.stringify(payload));
  await AsyncStorage.setItem(LAST_GUEST_SLUG_KEY, slug);
}

export async function getSessionUser(): Promise<SessionUser | null> {
  const token = await getStoredToken();
  if (!token) return null;
  const payload = decodeJwtPayload(token);
  if (!payload || !payload.sub) {
    await clearAuthStorage();
    return null;
  }
  if (isJwtExpired(payload)) {
    await clearAuthStorage();
    return null;
  }
  const roles = Array.isArray(payload.roles) ? payload.roles : [];
  const jwtTenantSlug = String(payload.tenant || '').trim();
  const jwtTenantId = String(payload.tenantId || '').trim();

  let ctx = await getMerchantTenantContext();
  if (isMerchantRoles(roles) && jwtTenantSlug && ctx && ctx.slug !== jwtTenantSlug) {
    await clearMerchantTenantContext();
    ctx = null;
  }
  if (!isMerchantRoles(roles)) ctx = null;

  let tenantSlug = jwtTenantSlug;
  let tenantDetail: MerchantTenantContext | null = null;
  if (isMerchantRoles(roles)) {
    if (jwtTenantSlug) {
      if (ctx && ctx.slug === jwtTenantSlug) {
        tenantDetail = { ...ctx, id: jwtTenantId || ctx.id };
      } else {
        tenantDetail = {
          id: jwtTenantId,
          slug: jwtTenantSlug,
          name: '',
          shopType: normalizeShopType(null),
        };
      }
    } else if (ctx?.slug) {
      tenantSlug = ctx.slug;
      tenantDetail = { ...ctx, id: jwtTenantId || ctx.id };
    }
  }

  return {
    id: String(payload.sub),
    email: String(payload.email || ''),
    roles,
    tenant: tenantSlug,
    tenantId: jwtTenantId || (tenantDetail && tenantDetail.id) || '',
    tenantDetail,
    shadowSupport: Boolean(payload.shadowSupport),
  };
}

export async function loginWithEmailPassword(email: string, password: string) {
  const e = String(email || '').trim();
  const p = String(password || '');
  if (!e || !p) throw new Error('Email and password are required.');
  const res = await apiFetch<any>('/api/auth/login', {
    method: 'POST',
    json: { email: e, password: p },
  });
  if (!res?.token) throw new Error('Login failed.');
  await setStoredToken(res.token);
  await persistMerchantTenantFromLoginResponse(res, decodeJwtPayload(res.token));
  return res;
}

export async function registerMerchant(params: {
  merchantName: string;
  ownerEmail: string;
  ownerPassword: string;
  merchantSlug?: string;
}) {
  const body: Record<string, string> = {
    merchantName: params.merchantName,
    ownerEmail: params.ownerEmail,
    ownerPassword: params.ownerPassword,
  };
  if (params.merchantSlug != null && String(params.merchantSlug).trim()) {
    body.merchantSlug = String(params.merchantSlug).trim();
  }
  const res = await apiFetch<any>('/api/auth/register-merchant', {
    method: 'POST',
    json: body,
  });
  if (!res?.token) throw new Error('Registration failed.');
  await setStoredToken(res.token);
  await persistMerchantTenantFromLoginResponse(res, decodeJwtPayload(res.token));
  return res;
}

export async function requestPasswordReset(email: string) {
  const e = String(email || '').trim();
  if (!e) throw new Error('Email is required.');
  return apiFetch('/api/auth/forgot-password', {
    method: 'POST',
    json: { email: e },
  });
}

export async function resetPassword(token: string, newPassword: string) {
  const t = String(token || '').trim();
  const next = String(newPassword || '');
  if (!t) throw new Error('Reset token is missing.');
  if (next.length < 8) throw new Error('New password must be at least 8 characters.');
  return apiFetch('/api/auth/reset-password', {
    method: 'POST',
    json: { token: t, newPassword: next },
  });
}

export async function logout(): Promise<void> {
  await clearAuthStorage();
}

export async function getLastGuestMerchantSlug(fallback: string): Promise<string> {
  try {
    const last = String((await AsyncStorage.getItem(LAST_GUEST_SLUG_KEY)) || '').trim();
    if (last) return last;
  } catch {
    // ignore
  }
  return fallback;
}

export async function setLastGuestMerchantSlug(slug: string): Promise<void> {
  const s = String(slug || '').trim();
  if (!s) return;
  await AsyncStorage.setItem(LAST_GUEST_SLUG_KEY, s);
}
