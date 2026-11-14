package fr.miage.collectivit.salesservice.dto;

import fr.miage.collectivit.salesservice.model.PaymentTransaction;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(
    description =
        "Représentation d'une transaction de paiement utilisateur pour l'historique et l'audit")
public record UserPaymentTransactionResponse(
    @Schema(
            description = "Identifiant unique de la transaction",
            example = "TXN-842e4312-32a1-432d")
        String transactionId,
    @Schema(
            description = "Référence métier du paiement ou de la commande",
            example = "ORD-20261114-001")
        String reference,
    @Schema(description = "Montant de la transaction", example = "24.50") BigDecimal amount,
    @Schema(description = "Devise (ISO 4217)", example = "EUR") String currency,
    @Schema(description = "Statut de la transaction", example = "TRANSACTION_APPROVED")
        String status,
    @Schema(description = "Code d'autorisation bancaire", example = "AUTH-987654")
        String authorizationCode,
    @Schema(description = "Horodatage de création de la transaction (UTC)") Instant createdAt) {
  public static UserPaymentTransactionResponse fromEntity(PaymentTransaction entity) {
    return new UserPaymentTransactionResponse(
        entity.getTransactionId(),
        entity.getReference(),
        entity.getAmount(),
        entity.getCurrency(),
        entity.getStatus(),
        entity.getAuthorizationCode(),
        entity.getCreatedAt());
  }
}
