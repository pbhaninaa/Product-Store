import { getAppEnvironment } from './appEnv';
import { resolveEnvironmentEndpoints } from './environments';

const endpoints = resolveEnvironmentEndpoints(getAppEnvironment());

export const CONFIG = {
  APP_ENV: endpoints.env,
  WEB_APP_URL: endpoints.webAppUrl,
  API_BASE_URL: endpoints.apiBaseUrl,
  DEFAULT_MERCHANT_SLUG: endpoints.defaultMerchantSlug,
  /** Guest storefront entry (root `/` redirects to merchant signup on web). */
  INITIAL_WEB_PATH: `/m/${endpoints.defaultMerchantSlug}`,
  API_TIMEOUT: 15000,

  DEFAULT_PHONE_COUNTRY_CODE: '',

  APP_NAME: 'Product Store',
  APP_VERSION: '1.0.3',

  STORAGE_KEYS: {
    AUTH_TOKEN: 'authToken',
    USER_DATA: 'user',
    SETTINGS: 'settings',
  },

  COLORS: {
    PRIMARY: '#0f172a',
    SECONDARY: '#5856D6',
    SUCCESS: '#34C759',
    WARNING: '#FF9500',
    ERROR: '#FF3B30',
    INFO: '#5AC8FA',
    LIGHT_GRAY: '#F2F2F7',
    GRAY: '#8E8E93',
    DARK_GRAY: '#1C1C1E',
    WHITE: '#FFFFFF',
    BLACK: '#000000',
  },
  
  // Dimensions
  DIMENSIONS: {
    PADDING: 16,
    MARGIN: 16,
    BORDER_RADIUS: 8,
    BUTTON_HEIGHT: 48,
    INPUT_HEIGHT: 48,
  },
  
  // Font Sizes
  FONT_SIZES: {
    SMALL: 12,
    MEDIUM: 14,
    LARGE: 16,
    XLARGE: 18,
    XXLARGE: 20,
    TITLE: 24,
    HEADER: 28,
  },
  
  // Map Configuration (Johannesburg default — matches storefront geocode)
  MAP: {
    DEFAULT_LATITUDE: -26.2041,
    DEFAULT_LONGITUDE: 28.0473,
    DEFAULT_ZOOM: 13,
    SEARCH_RADIUS: 5000,
  },
  
  // Notification Configuration
  NOTIFICATIONS: {
    CHANNEL_ID: 'productstore_app',
    CHANNEL_NAME: 'Product Store Notifications',
    CHANNEL_DESCRIPTION: 'Notifications for Product Store',
  },
  
  // File Upload
  UPLOAD: {
    MAX_FILE_SIZE: 10 * 1024 * 1024, // 10MB
    ALLOWED_TYPES: ['image/jpeg', 'image/png', 'image/jpg'],
    MAX_FILES: 5,
  },
  
  // Validation
  VALIDATION: {
    PASSWORD_MIN_LENGTH: 6,
    PHONE_REGEX: /^\+?[\d\s\-()]+$/,
    EMAIL_REGEX: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
  },
};

export default CONFIG; 