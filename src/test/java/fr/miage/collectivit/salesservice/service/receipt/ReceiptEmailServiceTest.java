package fr.miage.collectivit.salesservice.service.receipt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.miage.collectivit.salesservice.dto.OrderResponse;
import fr.miage.collectivit.salesservice.service.OrderProcessingService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReceiptEmailService Tests (SLE-21)")
class ReceiptEmailServiceTest {

  @Mock private ObjectProvider<JavaMailSender> mailSenderProvider;
  @Mock private JavaMailSender javaMailSender;
  @Mock private ReceiptPdfService receiptPdfService;
  @Mock private OrderProcessingService orderProcessingService;

  private ReceiptEmailService receiptEmailService;

  @BeforeEach
  void setUp() {
    receiptEmailService =
        new ReceiptEmailService(
            mailSenderProvider, receiptPdfService, orderProcessingService, "contact@shop-loc.fr");
  }

  @Test
  @DisplayName("Should send email with attached PDF when mail sender is configured")
  void shouldSendEmailWithPdfAttachment() {
    String orderRef = "CMD-CC-20261113-1234";
    Instant expiresAt = Instant.now().plusSeconds(1800);
    Instant createdAt = Instant.now();

    OrderResponse orderResponse =
        new OrderResponse(
            UUID.randomUUID(),
            orderRef,
            "user-1",
            "client@example.com",
            "CLT-12345",
            new BigDecimal("25.00"),
            "EUR",
            "PAID",
            expiresAt,
            createdAt,
            List.of(),
            List.of(),
            null);

    when(orderProcessingService.findOrderResponse(orderRef)).thenReturn(Optional.of(orderResponse));
    when(receiptPdfService.generateReceiptPdf(orderResponse))
        .thenReturn("%PDF-1.4 dummy".getBytes());

    when(mailSenderProvider.getIfAvailable()).thenReturn(javaMailSender);
    MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
    when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

    receiptEmailService.sendReceiptEmailAsync(orderRef);

    verify(javaMailSender).send(any(MimeMessage.class));
    verify(orderProcessingService).markReceiptSent(orderRef);
  }

  @Test
  @DisplayName("Should simulate email delivery when JavaMailSender is unavailable without crashing")
  void shouldSimulateDeliveryWhenMailSenderNotConfigured() {
    String orderRef = "CMD-CC-20261113-5678";
    Instant expiresAt = Instant.now().plusSeconds(1800);
    Instant createdAt = Instant.now();

    OrderResponse orderResponse =
        new OrderResponse(
            UUID.randomUUID(),
            orderRef,
            "user-2",
            "client2@example.com",
            "CLT-99999",
            new BigDecimal("10.00"),
            "EUR",
            "PAID",
            expiresAt,
            createdAt,
            List.of(),
            List.of(),
            null);

    when(orderProcessingService.findOrderResponse(orderRef)).thenReturn(Optional.of(orderResponse));
    when(receiptPdfService.generateReceiptPdf(orderResponse))
        .thenReturn("%PDF-1.4 dummy".getBytes());
    when(mailSenderProvider.getIfAvailable()).thenReturn(null);

    receiptEmailService.sendReceiptEmailAsync(orderRef);

    verify(orderProcessingService).markReceiptSent(orderRef);
  }

  @Test
  @DisplayName(
      "Should reject resending receipt email when cooldown is active (429 Too Many Requests)")
  void shouldRejectResendingReceiptEmailWhenCooldownActive() {
    String orderRef = "CMD-CC-20261113-9999";
    Instant createdAt = Instant.now().minusSeconds(100);
    Instant expiresAt = Instant.now().plusSeconds(1800);
    Instant receiptSentAt = Instant.now().minusSeconds(10); // sent 10s ago (< 60s cooldown)

    OrderResponse orderResponse =
        new OrderResponse(
            UUID.randomUUID(),
            orderRef,
            "user-3",
            "client3@example.com",
            "CLT-3333",
            new BigDecimal("15.00"),
            "EUR",
            "PAID",
            expiresAt,
            createdAt,
            List.of(),
            List.of(),
            receiptSentAt);

    when(orderProcessingService.findOrderResponse(orderRef)).thenReturn(Optional.of(orderResponse));

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> receiptEmailService.resendReceiptEmail(orderRef))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
        .hasMessageContaining("Veuillez patienter");
  }
}
