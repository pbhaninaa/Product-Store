package com.productstore.platform.models;

import java.util.Map;

public record PayFastCheckoutResponse(String paymentId, String processUrl, Map<String, String> fields) {}
