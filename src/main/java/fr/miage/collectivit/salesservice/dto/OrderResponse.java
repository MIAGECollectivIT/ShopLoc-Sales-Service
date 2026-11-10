package fr.miage.collectivit.salesservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Order response representation")
public record OrderResponse(
    @Schema(description = "Order database identifier", example = "1") Long id,
    @Schema(description = "Unique human-readable order reference", example = "CMD-CC-20261110-8472")
        String orderReference,
    @Schema(description = "Customer user identifier if authenticated", example = "user-123")
        String customerId,
    @Schema(description = "Customer email address", example = "client@example.com")
        String customerEmail,
    @Schema(description = "Customer client code", example = "CLI-99482") String clientCode,
    @Schema(description = "Locked total amount in euros", example = "42.50") BigDecimal totalAmount,
    @Schema(description = "Currency ISO code", example = "EUR") String currency,
    @Schema(description = "Current order status", example = "PENDING_PAYMENT") String status,
    @Schema(description = "Lock expiration timestamp") Instant expiresAt,
    @Schema(description = "Creation timestamp") Instant createdAt,
    @Schema(description = "Breakdown summaries per merchant store")
        List<OrderStoreSummaryResponse> storeSummaries,
    @Schema(description = "All order items") List<OrderItemResponse> items) {}
