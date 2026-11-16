package fr.miage.collectivit.salesservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Schema(description = "Item included in the order creation payload")
public record CreateOrderItemRequest(
    @Schema(description = "Store identifier", example = "1")
        @NotNull(message = "Store ID is required")
        Long storeId,
    @Schema(description = "Store name", example = "Boulangerie Le Moulin de Lille")
        String storeName,
    @Schema(description = "Product identifier", example = "2")
        @NotNull(message = "Product ID is required")
        Long productId,
    @Schema(description = "Product name", example = "Baguette de Tradition Française")
        @NotBlank(message = "Product name is required")
        String productName,
    @Schema(description = "Unit price in euros", example = "1.35")
        @NotNull(message = "Unit price is required")
        @DecimalMin(value = "0.01", message = "Unit price must be strictly positive")
        @Digits(
            integer = 8,
            fraction = 2,
            message = "Unit price must have at most 2 decimal places")
        BigDecimal unitPrice,
    @Schema(description = "Quantity ordered", example = "2")
        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        Integer quantity) {}
