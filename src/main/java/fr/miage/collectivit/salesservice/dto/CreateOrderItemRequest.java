package fr.miage.collectivit.salesservice.dto;

import fr.miage.collectivit.salesservice.model.OrderItemType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Item included in the order creation payload")
public record CreateOrderItemRequest(
    @Schema(
            description = "Store public UUID identifier",
            example = "11111111-1111-1111-1111-111111111111")
        @NotNull(message = "Store UID is required")
        UUID storeUid,
    @Schema(description = "Store name", example = "Boulangerie Le Moulin de Lille")
        String storeName,
    @Schema(
            description = "Product public UUID identifier",
            example = "22222222-2222-2222-2222-222222222222")
        @NotNull(message = "Product UID is required")
        UUID productUid,
    @Schema(description = "Product name", example = "Baguette de Tradition Française")
        @NotBlank(message = "Product name is required")
        String productName,
    @Schema(description = "Unit price in euros", example = "1.35")
        @NotNull(message = "Unit price is required")
        @DecimalMin(value = "0.00", message = "Unit price must be zero or positive")
        @Digits(
            integer = 8,
            fraction = 2,
            message = "Unit price must have at most 2 decimal places")
        BigDecimal unitPrice,
    @Schema(description = "Quantity ordered", example = "2")
        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        Integer quantity,
    @Schema(description = "Line item type (PRODUCT or GIFT)", example = "PRODUCT")
        OrderItemType itemType) {

  public CreateOrderItemRequest(
      UUID storeUid,
      String storeName,
      UUID productUid,
      String productName,
      BigDecimal unitPrice,
      Integer quantity) {
    this(storeUid, storeName, productUid, productName, unitPrice, quantity, OrderItemType.PRODUCT);
  }

  public OrderItemType resolvedItemType() {
    return itemType != null ? itemType : OrderItemType.PRODUCT;
  }
}
