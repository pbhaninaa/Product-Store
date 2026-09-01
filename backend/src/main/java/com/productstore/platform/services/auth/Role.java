package com.productstore.platform.services.auth;

public enum Role {
  MERCHANT_OWNER,
  MERCHANT_STAFF,
  SUPPORT_USER,
  PLATFORM_ADMIN,
  /** Shopper account (Wheel Hub CLIENT) — history, referrals, realtime alerts. */
  CLIENT
}

