/** Fallback product name when VUE_APP_SITE_NAME is not set for the environment. */
export const DEFAULT_SITE_NAME = 'Product Store'

/**
 * The product name shown across the web app (top bar, tab title, copy). Set VUE_APP_SITE_NAME per environment
 * (Vercel project settings) to rename the app without a code change; vue.config.js uses the same value for the
 * page title served before the app loads.
 */
export function siteName(env = process.env) {
  const configured = String((env && env.VUE_APP_SITE_NAME) || '').trim()
  return configured || DEFAULT_SITE_NAME
}
