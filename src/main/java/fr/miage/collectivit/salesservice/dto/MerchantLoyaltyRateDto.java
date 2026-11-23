package fr.miage.collectivit.salesservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record MerchantLoyaltyRateDto(
    UUID storeUid, BigDecimal euroAmount, BigDecimal pointsReward, boolean isCustom) {

  public static MerchantLoyaltyRateDto defaultForStore(UUID storeUid) {
    return new MerchantLoyaltyRateDto(
        storeUid, new BigDecimal("1.00"), new BigDecimal("1.0"), false);
  }
}
