package fr.miage.collectivit.salesservice.service.receipt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.miage.collectivit.salesservice.dto.OrderItemResponse;
import fr.miage.collectivit.salesservice.dto.OrderResponse;
import fr.miage.collectivit.salesservice.dto.OrderStoreSummaryResponse;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ReceiptPdfService Tests (SLE-21)")
class ReceiptPdfServiceTest {

  private ReceiptPdfService receiptPdfService;

  @BeforeEach
  void setUp() {
    receiptPdfService = new ReceiptPdfService();
  }

  @Test
  @DisplayName("Should successfully generate a valid PDF document with %PDF magic header")
  void shouldGenerateValidPdfDocument() {
    OrderItemResponse item1 =
        new OrderItemResponse(
            10L,
            1L,
            "Boulangerie Le Moulin",
            101L,
            "Pain au Chocolat",
            new BigDecimal("1.20"),
            3,
            new BigDecimal("3.60"));
    OrderItemResponse item2 =
        new OrderItemResponse(
            11L,
            2L,
            "Boucherie Centrale",
            201L,
            "Steak Haché Bio",
            new BigDecimal("4.50"),
            2,
            new BigDecimal("9.00"));

    OrderStoreSummaryResponse store1 =
        new OrderStoreSummaryResponse(
            1L, "Boulangerie Le Moulin", new BigDecimal("3.60"), 3, List.of(item1));
    OrderStoreSummaryResponse store2 =
        new OrderStoreSummaryResponse(
            2L, "Boucherie Centrale", new BigDecimal("9.00"), 2, List.of(item2));

    OrderResponse order =
        new OrderResponse(
            1L,
            UUID.randomUUID(),
            "CMD-CC-20261113-4412",
            "user-123",
            "client@example.com",
            "CLT-849201",
            new BigDecimal("12.60"),
            "EUR",
            "PAID",
            Instant.now().plusSeconds(1800),
            Instant.now(),
            List.of(store1, store2),
            List.of(item1, item2),
            null);

    byte[] pdfBytes = receiptPdfService.generateReceiptPdf(order);

    assertThat(pdfBytes).isNotNull();
    assertThat(pdfBytes.length).isGreaterThan(500);

    // Verify PDF header magic bytes "%PDF-"
    String pdfHeader = new String(pdfBytes, 0, 8, StandardCharsets.US_ASCII);
    assertThat(pdfHeader).startsWith("%PDF-");
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException when order is null")
  void shouldThrowExceptionWhenOrderIsNull() {
    assertThatThrownBy(() -> receiptPdfService.generateReceiptPdf(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Order cannot be null");
  }

  @Test
  @DisplayName("Should handle orders with missing optional fields gracefully")
  void shouldHandleOrdersWithMissingFields() {
    OrderResponse minimalOrder =
        new OrderResponse(
            2L,
            UUID.randomUUID(),
            "CMD-CC-20261113-0001",
            "user-456",
            null, // no email
            null, // no client code
            new BigDecimal("5.00"),
            "EUR",
            "PAID",
            Instant.now().plusSeconds(1800),
            Instant.now(),
            List.of(),
            List.of(),
            null);

    byte[] pdfBytes = receiptPdfService.generateReceiptPdf(minimalOrder);
    assertThat(pdfBytes).isNotNull();
    assertThat(new String(pdfBytes, 0, 8, StandardCharsets.US_ASCII)).startsWith("%PDF-");
  }
}
