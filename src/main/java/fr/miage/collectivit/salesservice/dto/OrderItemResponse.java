package fr.miage.collectivit.salesservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Detailed line item response")
public record OrderItemResponse(
    @Schema(
            description = "Line item public UUID identifier",
            example = "33333333-3333-3333-3333-333333333333")
        UUID uid,
    @Schema(
            description = "Store public UUID identifier",
            example = "11111111-1111-1111-1111-111111111111")
        UUID storeUid,
    @Schema(description = "Store name", example = "Boulangerie Le Moulin de Lille")
        String storeName,
    @Schema(
            description = "Product public UUID identifier",
            example = "22222222-2222-2222-2222-222222222222")
        UUID productUid,
    @Schema(description = "Product name", example = "Baguette de Tradition Française")
        String productName,
    @Schema(description = "Unit price in euros", example = "1.35") BigDecimal unitPrice,
    @Schema(description = "Quantity ordered", example = "2") Integer quantity,
    @Schema(description = "Total price in euros", example = "2.70") BigDecimal totalPrice) {}
