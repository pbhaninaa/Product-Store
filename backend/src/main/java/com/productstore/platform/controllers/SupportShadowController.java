package com.productstore.platform.controllers;

import java.util.List;
import java.util.Map;

import com.productstore.platform.constants.SupportPermission;
import com.productstore.platform.services.ShadowSupportService;
import com.productstore.platform.services.SupportAccessService;
import com.productstore.platform.services.auth.ApiUserPrincipal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/support/shadow")
public class SupportShadowController {
  private final ShadowSupportService shadow;
  private final SupportAccessService access;

  public SupportShadowController(ShadowSupportService shadow, SupportAccessService access) {
    this.shadow = shadow;
    this.access = access;
  }

  @GetMapping("/merchants")
  public Map<String, Object> merchants(
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestParam(name = "q", required = false) String q) {
    access.requirePermission(principal, SupportPermission.USE_SHADOW);
    return Map.of("merchants", shadow.listCandidates(q));
  }

  @PostMapping("/token")
  public Map<String, Object> token(
      @AuthenticationPrincipal ApiUserPrincipal principal, @RequestBody Map<String, Object> body) {
    String slug = body != null && body.get("slug") != null ? String.valueOf(body.get("slug")) : "";
    return shadow.mintShadowToken(principal, slug);
  }
}
