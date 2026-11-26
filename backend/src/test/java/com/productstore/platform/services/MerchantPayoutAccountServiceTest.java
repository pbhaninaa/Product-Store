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
    when(paystack.resolveBankAccount("62000000001", "250655")).thenReturn("STORE OWNER");

    assertThatThrownBy(() -> service.save(tenantId, "250655", "62000000001", "someone else"))
        .hasMessage("payout_account_name_mismatch");

    MerchantPayoutAccountService.AccountView view =
        service.save(tenantId, "250655", "62000000001", "Store Owner");
    assertThat(view.verified()).isTrue();
    assertThat(view.accountNumberMasked()).isEqualTo("•••• 0001");
    assertThat(s.bankAccountNumber).isEqualTo("62000000001");
    assertThat(s.payoutAccountVerifiedName).isEqualTo("STORE OWNER");
  }

  @Test
  void maskNeverShowsTheFullNumber() {
    assertThat(MerchantPayoutAccountService.mask("98765432109")).isEqualTo("•••• 2109");
    assertThat(MerchantPayoutAccountService.last4("98765432109")).isEqualTo("2109");
  }
}
