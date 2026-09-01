import { apiFetch, apiFetchMultipart, resolveMediaUrl } from './client';
import { normalizeShopType, type ShopType } from '../utils/shopType';

export type Product = {
  id: string;
  name?: string;
  price?: number | string;
  stock?: number | string | null;
  category?: string;
  imageUrl?: string;
  [key: string]: unknown;
};

export type ShopSettings = {
  deliveryFeeZar: number;
  deliveryFeeMode: 'standard' | 'per_km';
  deliveryFeePerKmZar: number;
  storeLat: number | null;
  storeLng: number | null;
  bankName: string;
  bankAccountHolder: string;
  bankAccountNumber: string;
  bankBranchCode: string;
  eftBankInstructions: string;
  shopType: ShopType;
  salonEnabled: boolean;
  storeName: string;
  storeLogoUrl: string;
  storeHeroUrl: string;
  contactEmail: string;
  contactPhone: string;
  contactAddress: string;
  contactNotes: string;
  openingHoursJson: string;
  acceptCustomerPeach: boolean;
  acceptCustomerEft: boolean;
  acceptCustomerCash: boolean;
  peachConfigured: boolean;
};

export type PlaceOrderParams = {
  customerName: string;
  customerEmail: string;
  customerPhone?: string;
  deliveryType: 'delivery' | 'collection';
  deliveryAddress?: string;
  deliveryLat?: number | null;
  deliveryLng?: number | null;
  paymentMethod: 'cash_store' | 'eft' | 'peach';
  peachPaymentMethod?: 'CARD' | 'EFT' | null;
  items: { product_id: string; quantity: number }[];
};

export type PlaceOrderResult = {
  orderId: string;
  needsEftProof: boolean;
  needsPeachCheckout: boolean;
  peachRedirectUrl: string;
  peachCheckoutId: string;
  cashPaymentCode: string;
  needsCashPaymentCode: boolean;
};

const emptySettings = (): ShopSettings => ({
  deliveryFeeZar: 50,
  deliveryFeeMode: 'standard',
  deliveryFeePerKmZar: 8,
  storeLat: null,
  storeLng: null,
  bankName: '',
  bankAccountHolder: '',
  bankAccountNumber: '',
  bankBranchCode: '',
  eftBankInstructions: '',
  shopType: 'normal_store',
  salonEnabled: false,
  storeName: '',
  storeLogoUrl: '',
  storeHeroUrl: '',
  contactEmail: '',
  contactPhone: '',
  contactAddress: '',
  contactNotes: '',
  openingHoursJson: '[]',
  acceptCustomerPeach: true,
  acceptCustomerEft: true,
  acceptCustomerCash: true,
  peachConfigured: false,
});

export async function fetchCatalog(merchantSlug: string): Promise<Product[]> {
  const slug = String(merchantSlug || '').trim();
  if (!slug) return [];
  const res = await apiFetch<{ products?: Product[] }>(
    `/api/public/m/${encodeURIComponent(slug)}/catalog`,
  );
  return res?.products ? res.products : [];
}

export async function fetchShopSettings(merchantSlug: string): Promise<ShopSettings> {
  const slug = String(merchantSlug || '').trim();
  if (!slug) return emptySettings();
  const res = await apiFetch<any>(
    `/api/public/m/${encodeURIComponent(slug)}/shop-settings`,
  );
  return {
    deliveryFeeZar: Number(res.deliveryFeeZar),
    deliveryFeeMode: res.deliveryFeeMode === 'per_km' ? 'per_km' : 'standard',
    deliveryFeePerKmZar: Number(res.deliveryFeePerKmZar),
    storeLat: res.storeLat != null ? Number(res.storeLat) : null,
    storeLng: res.storeLng != null ? Number(res.storeLng) : null,
    bankName: String(res.bankName || ''),
    bankAccountHolder: String(res.bankAccountHolder || ''),
    bankAccountNumber: String(res.bankAccountNumber || ''),
    bankBranchCode: String(res.bankBranchCode || ''),
    eftBankInstructions: String(res.eftBankInstructions || ''),
    shopType: normalizeShopType(res.shopType),
    salonEnabled: Boolean(res.salonEnabled),
    storeName: String(res.storeName || ''),
    storeLogoUrl: resolveMediaUrl(String(res.storeLogoUrl || '')),
    storeHeroUrl: resolveMediaUrl(String(res.storeHeroUrl || '')),
    contactEmail: String(res.contactEmail || ''),
    contactPhone: String(res.contactPhone || ''),
    contactAddress: String(res.contactAddress || ''),
    contactNotes: String(res.contactNotes || ''),
    openingHoursJson: String(
      res.openingHoursJson != null && String(res.openingHoursJson).trim() !== ''
        ? res.openingHoursJson
        : '[]',
    ),
    acceptCustomerPeach: res.acceptCustomerPeach !== false,
    acceptCustomerEft: res.acceptCustomerEft !== false,
    acceptCustomerCash: res.acceptCustomerCash !== false,
    peachConfigured: Boolean(res.peachConfigured || res.payfastConfigured),
  };
}

export async function placeOrder(
  merchantSlug: string,
  params: PlaceOrderParams,
): Promise<PlaceOrderResult> {
  const slug = String(merchantSlug || '').trim();
  if (!slug) throw new Error('Missing merchant.');

  const payload = {
    customerName: params.customerName,
    customerEmail: params.customerEmail,
    customerPhone:
      params.customerPhone != null ? String(params.customerPhone).trim() : '',
    deliveryType: params.deliveryType,
    deliveryAddress: params.deliveryType === 'delivery' ? params.deliveryAddress : '',
    deliveryLat: params.deliveryType === 'delivery' ? params.deliveryLat : null,
    deliveryLng: params.deliveryType === 'delivery' ? params.deliveryLng : null,
    paymentMethod: params.paymentMethod,
    peachPaymentMethod:
      params.paymentMethod === 'peach' ? params.peachPaymentMethod : null,
    items: (params.items || []).map((r) => ({
      product_id: r.product_id,
      quantity: r.quantity,
    })),
  };

  const res = await apiFetch<any>(
    `/api/public/m/${encodeURIComponent(slug)}/checkout/orders`,
    { method: 'POST', json: payload },
  );

  const id = res?.orderId ? String(res.orderId) : '';
  if (!id) throw new Error('No order reference returned.');
  return {
    orderId: id,
    needsEftProof: Boolean(res?.needsEftProof),
    needsPeachCheckout: Boolean(res?.needsPeachCheckout),
    peachRedirectUrl:
      res?.peachRedirectUrl != null ? String(res.peachRedirectUrl) : '',
    peachCheckoutId:
      res?.peachCheckoutId != null ? String(res.peachCheckoutId) : '',
    cashPaymentCode:
      res?.cashPaymentCode != null ? String(res.cashPaymentCode) : '',
    needsCashPaymentCode: Boolean(res?.needsCashPaymentCode),
  };
}

export async function submitCheckoutOrderEftProof(
  merchantSlug: string,
  orderId: string,
  params: { customerEmail: string; bankReference: string; formData: FormData },
) {
  const slug = String(merchantSlug || '').trim();
  const id = String(orderId || '').trim();
  if (!slug || !id) throw new Error('Missing merchant or order.');
  return apiFetchMultipart(
    `/api/public/m/${encodeURIComponent(slug)}/checkout/orders/${encodeURIComponent(id)}/eft-proof`,
    { method: 'POST', formData: params.formData, auth: false },
  );
}

export async function fetchOrderPeachStatus(
  merchantSlug: string,
  orderId: string,
  customerEmail: string,
) {
  const slug = String(merchantSlug || '').trim();
  const id = String(orderId || '').trim();
  if (!slug || !id) throw new Error('Missing merchant or order.');
  const q = encodeURIComponent(String(customerEmail || '').trim());
  return apiFetch(
    `/api/public/m/${encodeURIComponent(slug)}/checkout/orders/${encodeURIComponent(id)}/peach-status?customerEmail=${q}`,
  );
}
