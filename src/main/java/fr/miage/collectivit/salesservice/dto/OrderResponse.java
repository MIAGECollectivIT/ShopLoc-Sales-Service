package fr.miage.collectivit.salesservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Order response representation")
@SuppressWarnings("PMD.ExcessiveParameterList")
public record OrderResponse(
    @Schema(description = "Order database identifier", example = "1") Long id,
    @Schema(
            description = "Order public unique UUID identifier",
            example = "668aacbd-d4bb-44e1-a23f-bbc2186ffef1")
        UUID uid,
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
    @Schema(description = "All order items") List<OrderItemResponse> items,
    @Schema(description = "Receipt email transmission timestamp if sent") Instant receiptSentAt) {

  public OrderResponse(
      Long id,
      UUID uid,
      String orderReference,
      String customerId,
      String customerEmail,
      String clientCode,
      BigDecimal totalAmount,
      String currency,
      String status,
      Instant expiresAt,
      Instant createdAt,
      List<OrderStoreSummaryResponse> storeSummaries,
      List<OrderItemResponse> items) {
    this(
        id,
        uid,
        orderReference,
        customerId,
        customerEmail,
        clientCode,
        totalAmount,
        currency,
        status,
        expiresAt,
        createdAt,
        storeSummaries,
        items,
        null);
  }

  public OrderResponse(
      Long id,
      String orderReference,
      String customerId,
      String customerEmail,
      String clientCode,
      BigDecimal totalAmount,
      String currency,
      String status,
      Instant expiresAt,
      Instant createdAt,
      List<OrderStoreSummaryResponse> storeSummaries,
      List<OrderItemResponse> items) {
    this(
        id,
        UUID.randomUUID(),
        orderReference,
        customerId,
        customerEmail,
        clientCode,
        totalAmount,
        currency,
        status,
        expiresAt,
        createdAt,
        storeSummaries,
        items,
        null);
  }
}
