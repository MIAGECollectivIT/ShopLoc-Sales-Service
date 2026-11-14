package fr.miage.collectivit.salesservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(description = "Wallet debit request payload")
public record WalletDebitRequest(
    @Schema(description = "Amount to debit", example = "12.50")
        @NotNull(message = "Debit amount is required")
        @DecimalMin(value = "0.01", message = "Le montant doit être supérieur à zéro")
        @Digits(integer = 8, fraction = 2, message = "Le montant ne doit pas dépasser 2 décimales")
        BigDecimal amount,
    @Schema(description = "Merchant partner store ID", example = "1") Long storeId,
    @Schema(description = "Merchant partner store name", example = "Boulangerie Le Moulin Doré")
        @Size(max = 255)
        String storeName,
    @Schema(description = "Order reference if linked to an order", example = "ORD-20261114-0001")
        @Size(max = 64)
        String orderReference,
    @Schema(
            description = "Human-readable description",
            example = "Achat chez Boulangerie Le Moulin Doré")
        @Size(max = 255)
        String description) {}
