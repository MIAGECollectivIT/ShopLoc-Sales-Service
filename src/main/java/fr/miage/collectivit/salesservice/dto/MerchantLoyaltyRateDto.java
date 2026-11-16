package fr.miage.collectivit.salesservice.dto;

import java.math.BigDecimal;

public record MerchantLoyaltyRateDto(
    Long storeId, BigDecimal euroAmount, BigDecimal pointsReward, boolean isCustom) {

  public static MerchantLoyaltyRateDto defaultForStore(Long storeId) {
    return new MerchantLoyaltyRateDto(
        storeId, new BigDecimal("1.00"), new BigDecimal("1.0"), false);
  }
}
