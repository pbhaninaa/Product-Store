package com.productstore.platform.controllers;

import java.util.Map;
import java.util.UUID;

import com.productstore.platform.services.SupportUserDirectoryService;
import com.productstore.platform.services.auth.ApiUserPrincipal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform-wide user directory (Support console "Users" page). Listing needs VIEW_USERS; suspend,
 * reactivate and password reset need MANAGE_USERS (PLATFORM_ADMIN always has both). Permission and
 * safety checks live in {@link SupportUserDirectoryService}; every action is audited.
 */
@RestController
@RequestMapping("/api/support/users")
public class SupportUserController {
  private final SupportUserDirectoryService directory;

  public SupportUserController(SupportUserDirectoryService directory) {
    this.directory = directory;
  }

  @GetMapping
  public Map<String, Object> list(
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestParam(name = "q", required = false) String q,
      @RequestParam(name = "role", required = false) String role,
      @RequestParam(name = "store", required = false) String store,
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "page", required = false) Integer page,
      @RequestParam(name = "size", required = false) Integer size) {
    return directory.search(
        principal, new SupportUserDirectoryService.Filter(q, role, store, status, page, size));
  }

  @GetMapping("/stores")
  public Map<String, Object> stores(@AuthenticationPrincipal ApiUserPrincipal principal) {
    return Map.of("stores", directory.storeOptions(principal));
  }

  @PostMapping("/{userId}/suspend")
  public Map<String, Object> suspend(
      @PathVariable UUID userId, @AuthenticationPrincipal ApiUserPrincipal principal) {
    return directory.setSuspended(principal, userId, true);
  }

  @PostMapping("/{userId}/reactivate")
  public Map<String, Object> reactivate(
      @PathVariable UUID userId, @AuthenticationPrincipal ApiUserPrincipal principal) {
    return directory.setSuspended(principal, userId, false);
  }

  @PostMapping("/{userId}/send-password-reset")
  public Map<String, Object> sendPasswordReset(
      @PathVariable UUID userId, @AuthenticationPrincipal ApiUserPrincipal principal) {
    return directory.sendPasswordReset(principal, userId);
  }
}
