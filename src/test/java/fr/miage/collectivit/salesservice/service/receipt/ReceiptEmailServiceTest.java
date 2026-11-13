package fr.miage.collectivit.salesservice.service.receipt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.miage.collectivit.salesservice.dto.OrderResponse;
import fr.miage.collectivit.salesservice.model.Order;
import fr.miage.collectivit.salesservice.model.OrderStatus;
import fr.miage.collectivit.salesservice.repository.OrderRepository;
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
  @Mock private OrderRepository orderRepository;
  @Mock private OrderProcessingService orderProcessingService;

  private ReceiptEmailService receiptEmailService;

  @BeforeEach
  void setUp() {
    receiptEmailService =
        new ReceiptEmailService(
            mailSenderProvider,
            receiptPdfService,
            orderRepository,
            orderProcessingService,
            "contact@shop-loc.fr");
  }

  @Test
  @DisplayName("Should send email with attached PDF when mail sender is configured")
  void shouldSendEmailWithPdfAttachment() {
    String orderRef = "CMD-CC-20261113-1234";
    Order order =
        Order.builder()
            .id(1L)
            .uid(UUID.randomUUID())
            .orderReference(orderRef)
            .customerId("user-1")
            .customerEmail("client@example.com")
            .clientCode("CLT-12345")
            .currency("EUR")
            .status(OrderStatus.PAID)
            .totalAmount(new BigDecimal("25.00"))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(1800))
            .build();

    OrderResponse orderResponse =
        new OrderResponse(
            1L,
            order.getUid(),
            orderRef,
            "user-1",
            "client@example.com",
            "CLT-12345",
            new BigDecimal("25.00"),
            "EUR",
            "PAID",
            order.getExpiresAt(),
            order.getCreatedAt(),
            List.of(),
            List.of(),
            null);

    when(orderProcessingService.resolveOrder(orderRef)).thenReturn(Optional.of(order));
    when(orderProcessingService.mapToResponse(order)).thenReturn(orderResponse);
    when(receiptPdfService.generateReceiptPdf(orderResponse))
        .thenReturn("%PDF-1.4 dummy".getBytes());

    when(mailSenderProvider.getIfAvailable()).thenReturn(javaMailSender);
    MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
    when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

    receiptEmailService.sendReceiptEmailAsync(orderRef);

    verify(javaMailSender).send(any(MimeMessage.class));
    verify(orderRepository).save(order);
    assertThat(order.getReceiptSentAt()).isNotNull();
  }

  @Test
  @DisplayName("Should simulate email delivery when JavaMailSender is unavailable without crashing")
  void shouldSimulateDeliveryWhenMailSenderNotConfigured() {
    String orderRef = "CMD-CC-20261113-5678";
    Order order =
        Order.builder()
            .id(2L)
            .uid(UUID.randomUUID())
            .orderReference(orderRef)
            .customerId("user-2")
            .customerEmail("client2@example.com")
            .status(OrderStatus.PAID)
            .totalAmount(new BigDecimal("10.00"))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(1800))
            .build();

    OrderResponse orderResponse =
        new OrderResponse(
            2L,
            order.getUid(),
            orderRef,
            "user-2",
            "client2@example.com",
            "CLT-99999",
            new BigDecimal("10.00"),
            "EUR",
            "PAID",
            order.getExpiresAt(),
            order.getCreatedAt(),
            List.of(),
            List.of(),
            null);

    when(orderProcessingService.resolveOrder(orderRef)).thenReturn(Optional.of(order));
    when(orderProcessingService.mapToResponse(order)).thenReturn(orderResponse);
    when(receiptPdfService.generateReceiptPdf(orderResponse))
        .thenReturn("%PDF-1.4 dummy".getBytes());
    when(mailSenderProvider.getIfAvailable()).thenReturn(null);

    receiptEmailService.sendReceiptEmailAsync(orderRef);

    verify(orderRepository).save(order);
    assertThat(order.getReceiptSentAt()).isNotNull();
  }

  @Test
  @DisplayName(
      "Should reject resending receipt email when cooldown is active (429 Too Many Requests)")
  void shouldRejectResendingReceiptEmailWhenCooldownActive() {
    String orderRef = "CMD-CC-20261113-9999";
    Order order =
        Order.builder()
            .id(3L)
            .uid(UUID.randomUUID())
            .orderReference(orderRef)
            .customerId("user-3")
            .customerEmail("client3@example.com")
            .status(OrderStatus.PAID)
            .totalAmount(new BigDecimal("15.00"))
            .createdAt(Instant.now().minusSeconds(100))
            .updatedAt(Instant.now())
            .receiptSentAt(Instant.now().minusSeconds(10)) // sent 10s ago (< 60s cooldown)
            .expiresAt(Instant.now().plusSeconds(1800))
            .build();

    OrderResponse orderResponse =
        new OrderResponse(
            3L,
            order.getUid(),
            orderRef,
            "user-3",
            "client3@example.com",
            "CLT-3333",
            new BigDecimal("15.00"),
            "EUR",
            "PAID",
            order.getExpiresAt(),
            order.getCreatedAt(),
            List.of(),
            List.of(),
            order.getReceiptSentAt());

    when(orderProcessingService.resolveOrder(orderRef)).thenReturn(Optional.of(order));
    when(orderProcessingService.mapToResponse(order)).thenReturn(orderResponse);

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> receiptEmailService.resendReceiptEmail(orderRef))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
        .hasMessageContaining("Veuillez patienter");
  }
}
