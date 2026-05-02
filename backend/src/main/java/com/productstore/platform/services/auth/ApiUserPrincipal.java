package com.productstore.platform.services.auth;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class ApiUserPrincipal implements UserDetails {
  private final UUID userId;
  private final String email;
  private final List<Role> roles;
  private final String tenantSlug;

  public ApiUserPrincipal(UUID userId, String email, List<Role> roles, String tenantSlug) {
    this.userId = userId;
    this.email = email;
    this.roles = roles;
    this.tenantSlug = tenantSlug;
  }

  public UUID userId() {
    return userId;
  }

  public List<Role> roles() {
    return roles;
  }

  public String tenantSlug() {
    return tenantSlug;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).toList();
  }

  @Override
  public String getPassword() {
    return "";
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}

