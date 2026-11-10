package fr.miage.collectivit.salesservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Detailed line item response")
public record OrderItemResponse(
    @Schema(description = "Line item primary key", example = "10") Long id,
    @Schema(description = "Store identifier", example = "1") Long storeId,
    @Schema(description = "Store name", example = "Boulangerie Le Moulin de Lille")
        String storeName,
    @Schema(description = "Product identifier", example = "2") Long productId,
    @Schema(description = "Product name", example = "Baguette de Tradition Française")
        String productName,
    @Schema(description = "Unit price in euros", example = "1.35") BigDecimal unitPrice,
    @Schema(description = "Quantity ordered", example = "2") Integer quantity,
    @Schema(description = "Total price in euros", example = "2.70") BigDecimal totalPrice) {}
