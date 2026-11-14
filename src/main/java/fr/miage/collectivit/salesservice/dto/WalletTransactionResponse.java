package fr.miage.collectivit.salesservice.dto;

import fr.miage.collectivit.salesservice.model.WalletTransaction;
import fr.miage.collectivit.salesservice.model.WalletTransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Wallet transaction audit entry (SLE-18)")
public record WalletTransactionResponse(
    @Schema(description = "Transaction ID", example = "1") Long id,
    @Schema(description = "Unique transaction reference", example = "WTX-20261114-123456")
        String transactionReference,
    @Schema(description = "Amount in euros", example = "20.00") BigDecimal amount,
    @Schema(description = "Operation type: TOPUP or DEBIT", example = "TOPUP")
        WalletTransactionType type,
    @Schema(description = "Transaction status", example = "SUCCESS") String status,
    @Schema(description = "Bank payment reference (for reloads)", example = "PAY-20261114-123")
        String paymentReference,
    @Schema(description = "Merchant partner store ID (for debits)", example = "1") Long storeId,
    @Schema(
            description = "Merchant partner store name (mandatory for debits under SLE-18)",
            example = "Boulangerie Le Moulin Doré")
        String storeName,
    @Schema(description = "Order reference if associated", example = "ORD-20261114-0001")
        String orderReference,
    @Schema(
            description = "Human-readable description",
            example = "Rechargement porte-monnaie par carte bancaire")
        String description,
    @Schema(description = "Balance after operation", example = "45.00") BigDecimal balanceAfter,
    @Schema(description = "Transaction timestamp") Instant createdAt) {

  public static WalletTransactionResponse fromEntity(WalletTransaction tx) {
    return new WalletTransactionResponse(
        tx.getId(),
        tx.getTransactionReference(),
        tx.getAmount(),
        tx.getType(),
        tx.getStatus(),
        tx.getPaymentReference(),
        tx.getStoreId(),
        tx.getStoreName(),
        tx.getOrderReference(),
        tx.getDescription(),
        tx.getBalanceAfter(),
        tx.getCreatedAt());
  }
}
