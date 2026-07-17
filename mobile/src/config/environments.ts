import { Platform } from 'react-native';
import { SIT_DEV_HOST } from './devWebHost';

export type ProductStoreAppEnv = 'sit' | 'uat' | 'prod';

export type EnvironmentEndpoints = {
  env: ProductStoreAppEnv;
  webAppUrl: string;
  apiBaseUrl: string;
  /** Default merchant slug for guest storefront tabs (override for your deploy). */
  defaultMerchantSlug: string;
};

const HOSTED_WEB_APP_URL = 'https://YOUR-PRODUCT-STORE-FRONTEND.vercel.app';
const HOSTED_API_BASE_URL = 'https://product-store-production-b8bf.up.railway.app';
const DEFAULT_MERCHANT_SLUG = 'demo';

function sitWebHost(): string {
  if (Platform.OS === 'android') {
    return SIT_DEV_HOST;
  }
  return 'localhost';
}

export function resolveEnvironmentEndpoints(env: ProductStoreAppEnv): EnvironmentEndpoints {
  switch (env) {
    case 'sit': {
      const host = sitWebHost();
      return {
        env: 'sit',
        // Vue CLI default serve port
        webAppUrl: `http://${host}:8085`,
        apiBaseUrl: `http://${host}:8080`,
        defaultMerchantSlug: DEFAULT_MERCHANT_SLUG,
      };
    }
    case 'uat':
      return {
        env: 'uat',
        webAppUrl: HOSTED_WEB_APP_URL,
        apiBaseUrl: HOSTED_API_BASE_URL,
        defaultMerchantSlug: DEFAULT_MERCHANT_SLUG,
      };
    case 'prod':
      return {
        env: 'prod',
        webAppUrl: HOSTED_WEB_APP_URL,
        apiBaseUrl: HOSTED_API_BASE_URL,
        defaultMerchantSlug: DEFAULT_MERCHANT_SLUG,
      };
    default: {
      const host = sitWebHost();
      return {
        env: 'sit',
        webAppUrl: `http://${host}:8085`,
        apiBaseUrl: `http://${host}:8080`,
        defaultMerchantSlug: DEFAULT_MERCHANT_SLUG,
      };
    }
  }
}
