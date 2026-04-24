/**
 * Store Type-Based Color Themes
 * 
 * Themes are automatically selected based on store type
 * Default store type: 'colognes'
 */

// ============================================================================
// THEME: Colognes & Perfumes (Luxury, Elegant)
// DEFAULT THEME - Warm gold accents with sophisticated dark base
// ============================================================================
export const themeColognes = {
  primary: '#1a1a1a',      // Deep black
  secondary: '#8b7355',    // Warm taupe
  accent: '#d4af37',       // Gold
  info: '#9370db',         // Lavender
  success: '#2d5a2d',      // Deep green
  warning: '#d4a574',      // Bronze
  error: '#c41e3a',        // Crimson red
  anchor: '#daa520',       // Goldenrod
  background: '#faf7f2',   // Cream white
  surface: '#ffffff'       // Pure white
}

// ============================================================================
// THEME: Beauty & Cosmetics (Vibrant, Modern, Playful)
// Pinks, purples, and accent colors for makeup/beauty products
// ============================================================================
export const themeBeauty = {
  primary: '#2d1b3d',      // Deep plum
  secondary: '#d4669d',    // Dusty rose
  accent: '#ff1493',       // Deep pink/Magenta
  info: '#b86ed8',         // Orchid
  success: '#ff69b4',      // Hot pink
  warning: '#ffa07a',      // Light salmon
  error: '#e63946',        // Rose red
  anchor: '#ff69b4',       // Hot pink link
  background: '#fef4f8',   // Light pink
  surface: '#ffffff'       // White
}

// ============================================================================
// THEME: Hardware & Tools (Industrial, Professional, Trustworthy)
// Dark grays with steel blue accents
// ============================================================================
export const themeHardware = {
  primary: '#1a2332',      // Dark steel blue
  secondary: '#546e7a',    // Blue-gray
  accent: '#0097a7',       // Cyan blue
  info: '#01579b',         // Dark blue
  success: '#00897b',      // Teal
  warning: '#ff6f00',      // Orange
  error: '#d32f2f',        // Red
  anchor: '#0288d1',       // Light blue
  background: '#eceff1',   // Light blue-gray
  surface: '#ffffff'       // White
}

// ============================================================================
// THEME: Fashion & Apparel (Trendy, Bold, Sophisticated)
// Black with vibrant neon accents
// ============================================================================
export const themeFashion = {
  primary: '#0a0e27',      // Almost black
  secondary: '#6c757d',    // Medium gray
  accent: '#ff006e',       // Hot pink/Neon
  info: '#00b4d8',         // Cyan
  success: '#06ffa5',      // Neon green
  warning: '#ffd60a',      // Neon yellow
  error: '#ff006e',        // Neon pink
  anchor: '#ff006e',       // Neon pink link
  background: '#f5f5f5',   // Light gray
  surface: '#ffffff'       // White
}

// ============================================================================
// THEME: Jewelry & Luxury (Premium, Elegant, Refined)
// Deep jewel tones with metallic accents
// ============================================================================
export const themeJewelry = {
  primary: '#1a1033',      // Deep plum/navy
  secondary: '#4a3f7f',    // Purple-gray
  accent: '#ffd700',       // Gold
  info: '#c0ffee',         // Platinum-like cyan
  success: '#228b22',      // Forest green
  warning: '#ff6347',      // Tomato red
  error: '#8b0000',        // Dark red
  anchor: '#ffd700',       // Gold link
  background: '#f5f1e8',   // Champagne
  surface: '#ffffff'       // White
}

// ============================================================================
// THEME: Electronics & Tech (Modern, Clean, Futuristic)
// Bright blue with silver accents
// ============================================================================
export const themeElectronics = {
  primary: '#0d47a1',      // Bright blue
  secondary: '#757575',    // Gray
  accent: '#00bcd4',       // Cyan
  info: '#2196f3',         // Bright blue
  success: '#4caf50',      // Green
  warning: '#ff9800',      // Orange
  error: '#f44336',        // Red
  anchor: '#1976d2',       // Light blue link
  background: '#f3f3f3',   // Light gray
  surface: '#ffffff'       // White
}

// ============================================================================
// THEME: Home & Kitchen (Warm, Inviting, Cozy)
// Warm earth tones with warm orange accents
// ============================================================================
export const themeHomeKitchen = {
  primary: '#3e2723',      // Dark brown
  secondary: '#8d6e63',    // Brown-gray
  accent: '#ff6f00',       // Deep orange
  info: '#d2691e',         // Chocolate
  success: '#558b2f',      // Olive green
  warning: '#ffa000',      // Amber
  error: '#e53935',        // Red
  anchor: '#ff6f00',       // Orange link
  background: '#fbe9e7',   // Light beige
  surface: '#ffffff'       // White
}

// ============================================================================
// THEME: Sports & Fitness (Energetic, Dynamic, Athletic)
// Bold primary color with athletic accents
// ============================================================================
export const themeSportsFitness = {
  primary: '#1b1b1b',      // Dark gray
  secondary: '#424242',    // Medium gray
  accent: '#00e676',       // Neon green
  info: '#1e88e5',         // Electric blue
  success: '#00c853',      // Green
  warning: '#ff6d00',      // Orange
  error: '#ff3d00',        // Red-orange
  anchor: '#00e676',       // Neon green link
  background: '#f5f5f5',   // Light gray
  surface: '#ffffff'       // White
}

// ============================================================================
// STORE TYPE TO THEME MAPPING
// ============================================================================
const themeMap = {
  colognes: themeColognes,
  beauty: themeBeauty,
  hardware: themeHardware,
  fashion: themeFashion,
  jewelry: themeJewelry,
  electronics: themeElectronics,
  home_kitchen: themeHomeKitchen,
  sports_fitness: themeSportsFitness
}

/**
 * Get theme based on store type
 * @param {string} storeType - Type of store (e.g., 'colognes', 'beauty', 'hardware')
 * @returns {Object} Theme object with color properties
 */
export function getThemeByStoreType(storeType = 'colognes') {
  const type = String(storeType || 'colognes').toLowerCase().trim()
  return themeMap[type] || themeColognes // Default to colognes if type not found
}

/**
 * Get list of all available store types
 * @returns {Array} Array of store type objects with label and value
 */
export function getAvailableStoreTypes() {
  return [
    { label: '💐 Colognes & Perfumes', value: 'colognes' },
    { label: '💄 Beauty & Cosmetics', value: 'beauty' },
    { label: '🔧 Hardware & Tools', value: 'hardware' },
    { label: '👗 Fashion & Apparel', value: 'fashion' },
    { label: '💎 Jewelry & Luxury', value: 'jewelry' },
    { label: '📱 Electronics & Tech', value: 'electronics' },
    { label: '🏠 Home & Kitchen', value: 'home_kitchen' },
    { label: '🏃 Sports & Fitness', value: 'sports_fitness' }
  ]
}
