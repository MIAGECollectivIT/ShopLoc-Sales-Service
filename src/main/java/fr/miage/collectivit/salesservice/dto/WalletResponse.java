package fr.miage.collectivit.salesservice.dto;

import fr.miage.collectivit.salesservice.model.Wallet;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Citizen digital wallet balance response (SLE-17)")
public record WalletResponse(
    @Schema(description = "Wallet ID", example = "1") Long id,
    @Schema(
            description = "User unique public identifier (UUID)",
            example = "a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d")
        String userId,
    @Schema(description = "Available real-time balance in euros", example = "35.50")
        BigDecimal balance,
    @Schema(description = "ISO 4217 currency code", example = "EUR") String currency,
    @Schema(description = "Creation timestamp") Instant createdAt,
    @Schema(description = "Last update timestamp") Instant updatedAt,
    @Schema(
            description = "Remaining available reload limit on rolling 7-day window",
            example = "215.00")
        BigDecimal weeklyReloadRemaining) {

  public WalletResponse(
      Long id,
      String userId,
      BigDecimal balance,
      String currency,
      Instant createdAt,
      Instant updatedAt) {
    this(id, userId, balance, currency, createdAt, updatedAt, null);
  }

  public static WalletResponse fromEntity(Wallet wallet) {
    return fromEntity(wallet, null);
  }

  public static WalletResponse fromEntity(Wallet wallet, BigDecimal weeklyReloadRemaining) {
    return new WalletResponse(
        wallet.getId(),
        wallet.getUserId(),
        wallet.getBalance(),
        wallet.getCurrency(),
        wallet.getCreatedAt(),
        wallet.getUpdatedAt(),
        weeklyReloadRemaining);
  }
}
