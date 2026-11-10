package fr.miage.collectivit.salesservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Store level breakdown summary for multi-store click and collect orders")
public record OrderStoreSummaryResponse(
    @Schema(description = "Store identifier", example = "1") Long storeId,
    @Schema(description = "Store name", example = "Boulangerie Le Moulin de Lille")
        String storeName,
    @Schema(description = "Subtotal for this store in euros", example = "4.50") BigDecimal subtotal,
    @Schema(description = "Number of items ordered for this store", example = "2")
        Integer itemCount,
    @Schema(description = "Line items belonging to this store") List<OrderItemResponse> items) {}
