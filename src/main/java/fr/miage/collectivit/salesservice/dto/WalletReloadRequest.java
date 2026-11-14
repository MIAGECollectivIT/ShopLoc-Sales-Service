package fr.miage.collectivit.salesservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(description = "Wallet online reload request payload (SLE-15)")
public record WalletReloadRequest(
    @Schema(
            description = "Amount to reload (strict floor 5.00 €, ceiling 100.00 €)",
            example = "20.00")
        @NotNull(message = "Reload amount is required")
        @DecimalMin(value = "5.00", message = "Le montant minimum de rechargement est de 5.00 €")
        @DecimalMax(
            value = "100.00",
            message = "Le montant maximum de rechargement est de 100.00 €")
        @Digits(integer = 3, fraction = 2, message = "Le montant ne doit pas dépasser 2 décimales")
        BigDecimal amount,
    @Schema(
            description = "Payment method token for bank gateway authorization",
            example = "tok_visa_4242")
        @Size(max = 128, message = "Payment token must not exceed 128 characters")
        String paymentToken,
    @Schema(
            description = "Unique client-generated idempotency key to prevent duplicate reloads",
            example = "c1a2b3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
        @Size(max = 128, message = "Idempotency key must not exceed 128 characters")
        String idempotencyKey) {

  public WalletReloadRequest(BigDecimal amount, String paymentToken) {
    this(amount, paymentToken, null);
  }
}
