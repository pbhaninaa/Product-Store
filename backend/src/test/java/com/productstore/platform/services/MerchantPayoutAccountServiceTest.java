package com.productstore.platform.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.config.PaystackProperties;
import com.productstore.platform.entities.ShopSettingsEntity;
import com.productstore.platform.repositories.ShopSettingsRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MerchantPayoutAccountServiceTest {
  private ShopSettingsRepository shops;
  private PaystackClient paystack;
  private MerchantPayoutAccountService service;
  private UUID tenantId;

  @BeforeEach
  void setUp() {
    shops = mock(ShopSettingsRepository.class);
    paystack = mock(PaystackClient.class);
    PaystackProperties properties = new PaystackProperties();
    ReflectionTestUtils.setField(properties, "enabled", "true");
    ReflectionTestUtils.setField(properties, "secretKey", "sk_test_x");
    ReflectionTestUtils.setField(properties, "baseUrl", "https://api.paystack.co");
    service = new MerchantPayoutAccountService(shops, paystack, properties);
    tenantId = UUID.randomUUID();
    when(paystack.listSouthAfricanBanks()).thenReturn(List.of(new PaystackClient.Bank("FNB", "250655")));
  }

  @Test
  void saveRequiresTheResolvedNameToBeConfirmed() {
    ShopSettingsEntity s = new ShopSettingsEntity();
    s.tenantId = tenantId;
    when(shops.findByTenantId(tenantId)).thenReturn(Optional.of(s));

    PaystackClient.ValidationRequest request1 = new PaystackClient.ValidationRequest(
        "62000000001", "250655", "someone else", "personal", "identityNumber", "8001015009087");
    when(paystack.validateAccount(request1))
        .thenReturn(new PaystackClient.ValidationResult("STORE OWNER", true));

    assertThatThrownBy(() -> service.save(tenantId, "250655", "62000000001", "someone else",
        "personal", "identityNumber", "8001015009087"))
        .hasMessage("payout_account_name_mismatch");

    PaystackClient.ValidationRequest request2 = new PaystackClient.ValidationRequest(
        "62000000001", "250655", "Store Owner", "personal", "identityNumber", "8001015009087");
    when(paystack.validateAccount(request2))
        .thenReturn(new PaystackClient.ValidationResult("STORE OWNER", true));

    MerchantPayoutAccountService.AccountView view =
        service.save(tenantId, "250655", "62000000001", "Store Owner",
            "personal", "identityNumber", "8001015009087");
    assertThat(view.verified()).isTrue();
    assertThat(view.accountNumberMasked()).isEqualTo("•••• 0001");
    assertThat(s.bankAccountNumber).isEqualTo("62000000001");
    assertThat(s.payoutAccountVerifiedName).isEqualTo("STORE OWNER");
    assertThat(s.payoutAccountType).isEqualTo("personal");
  }

  @Test
  void maskNeverShowsTheFullNumber() {
    assertThat(MerchantPayoutAccountService.mask("98765432109")).isEqualTo("•••• 2109");
    assertThat(MerchantPayoutAccountService.last4("98765432109")).isEqualTo("2109");
  }
}
