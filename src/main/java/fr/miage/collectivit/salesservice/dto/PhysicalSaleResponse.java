package fr.miage.collectivit.salesservice.dto;

import fr.miage.collectivit.salesservice.model.PhysicalSaleTransaction;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "In-store physical checkout transaction confirmation response")
public record PhysicalSaleResponse(
    @Schema(description = "Unique transaction reference", example = "PHYS-20261110-A1B2C3")
        String transactionReference,
    @Schema(description = "Store identifier", example = "10") Long storeId,
    @Schema(description = "Merchant user identifier", example = "merchant_user_123")
        String merchantId,
    @Schema(description = "Customer loyalty code", example = "k7P9xQ2") String clientCode,
    @Schema(
            description = "Customer UUID identifier",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        String customerId,
    @Schema(description = "Validated checkout amount", example = "25.50") BigDecimal amount,
    @Schema(description = "Currency", example = "EUR") String currency,
    @Schema(
            description = "Awarded loyalty points calculated according to store rate",
            example = "25.5")
        BigDecimal pointsEarned,
    @Schema(description = "Store loyalty rate applied", example = "1.00 EUR = 1.0 PTS")
        String pointsRateApplied,
    @Schema(description = "Payment method", example = "CASH") String paymentMethod,
    @Schema(description = "Transaction status", example = "COMPLETED") String status,
    @Schema(description = "Transaction timestamp in UTC", example = "2026-11-10T14:30:00Z")
        String timestamp) {

  public static PhysicalSaleResponse fromEntity(PhysicalSaleTransaction entity) {
    return new PhysicalSaleResponse(
        entity.getTransactionReference(),
        entity.getStoreId(),
        entity.getMerchantId(),
        entity.getClientCode(),
        entity.getCustomerId(),
        entity.getAmount(),
        entity.getCurrency(),
        entity.getPointsEarned(),
        entity.getPointsRateApplied(),
        entity.getPaymentMethod(),
        entity.getStatus(),
        entity.getCreatedAtIso());
  }
}
