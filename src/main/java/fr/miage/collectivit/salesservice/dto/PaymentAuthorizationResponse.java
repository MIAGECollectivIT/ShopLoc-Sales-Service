package fr.miage.collectivit.salesservice.dto;

import fr.miage.collectivit.salesservice.model.PaymentTransaction;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Payment authorization response compliant with RG001")
public record PaymentAuthorizationResponse(
    @Schema(description = "Authorization status", example = "TRANSACTION_APPROVED") String status,
    @Schema(
            description = "Unique bank authorization code",
            example = "AUTH-123e4567-e89b-12d3-a456-426614174000")
        String authorizationCode,
    @Schema(
            description = "Unique transaction identifier",
            example = "TXN-123e4567-e89b-12d3-a456-426614174000")
        String transactionId,
    @Schema(description = "Authorized amount", example = "42.50") BigDecimal amount,
    @Schema(description = "ISO currency code", example = "EUR") String currency,
    @Schema(description = "Order or session reference", example = "ORD-2026-001") String reference,
    @Schema(description = "Authorization timestamp in UTC", example = "2026-11-08T10:30:00Z")
        String timestamp,
    @Schema(description = "Applied business rule", example = "RG001") String rule) {

  public static PaymentAuthorizationResponse fromEntity(PaymentTransaction entity) {
    return new PaymentAuthorizationResponse(
        entity.getStatus(),
        entity.getAuthorizationCode(),
        entity.getTransactionId(),
        entity.getAmount(),
        entity.getCurrency(),
        entity.getReference(),
        entity.getCreatedAtIso(),
        PaymentTransaction.RG001_RULE_CODE);
  }
}
