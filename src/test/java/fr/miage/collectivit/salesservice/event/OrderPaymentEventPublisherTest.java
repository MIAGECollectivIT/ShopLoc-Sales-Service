package fr.miage.collectivit.salesservice.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.miage.collectivit.salesservice.dto.event.OrderPaidKafkaEvent;
import fr.miage.collectivit.salesservice.dto.event.StoreOrderBreakdown;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

@DisplayName("OrderPaymentEventPublisher Unit Tests (SLE-220)")
class OrderPaymentEventPublisherTest {

  private static final String TOPIC = "shoploc.order-payments";
  private KafkaTemplate<String, Object> kafkaTemplate;
  private OrderPaymentEventPublisher publisher;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    kafkaTemplate = mock(KafkaTemplate.class);
    publisher = new OrderPaymentEventPublisher(kafkaTemplate, TOPIC);
  }

  @Test
  @DisplayName("Should publish order paid event with clientCode as partition key")
  void shouldPublishEventWithClientCodeKey() {
    StoreOrderBreakdown breakdown1 =
        new StoreOrderBreakdown(UUID.randomUUID(), "Boulangerie", new BigDecimal("15.00"));
    StoreOrderBreakdown breakdown2 =
        new StoreOrderBreakdown(UUID.randomUUID(), "Librairie", new BigDecimal("25.00"));

    OrderPaidKafkaEvent event =
        new OrderPaidKafkaEvent(
            "CMD-CC-20261118-001",
            "user-123",
            "k7P9xQ2",
            new BigDecimal("40.00"),
            "EUR",
            List.of(breakdown1, breakdown2),
            Instant.now());

    publisher.publishOrderPaid(event);

    verify(kafkaTemplate).send(eq(TOPIC), eq("k7P9xQ2"), eq(event));
  }

  @Test
  @DisplayName("Should fallback to orderReference as key when clientCode is blank")
  void shouldFallbackToOrderReferenceWhenClientCodeIsBlank() {
    StoreOrderBreakdown breakdown =
        new StoreOrderBreakdown(UUID.randomUUID(), "Boulangerie", new BigDecimal("15.00"));

    OrderPaidKafkaEvent event =
        new OrderPaidKafkaEvent(
            "CMD-CC-20261118-002",
            "user-123",
            "   ",
            new BigDecimal("15.00"),
            "EUR",
            List.of(breakdown),
            Instant.now());

    publisher.publishOrderPaid(event);

    verify(kafkaTemplate).send(eq(TOPIC), eq("CMD-CC-20261118-002"), eq(event));
  }

  @Test
  @DisplayName("Should not publish when event is null or orderReference is null or blank")
  void shouldNotPublishWhenEventOrReferenceIsInvalid() {
    publisher.publishOrderPaid(null);

    OrderPaidKafkaEvent blankRefEvent =
        new OrderPaidKafkaEvent(
            "   ",
            "user-123",
            "client-1",
            new BigDecimal("10.00"),
            "EUR",
            List.of(),
            Instant.now());
    publisher.publishOrderPaid(blankRefEvent);

    OrderPaidKafkaEvent nullRefEvent =
        new OrderPaidKafkaEvent(
            null, "user-123", "client-1", new BigDecimal("10.00"), "EUR", List.of(), Instant.now());
    publisher.publishOrderPaid(nullRefEvent);

    verify(kafkaTemplate, never()).send(any(), any(), any());
  }

  @Test
  @DisplayName("Should not fail if kafkaTemplate is null")
  void shouldHandleNullKafkaTemplateGracefully() {
    OrderPaymentEventPublisher nullPublisher = new OrderPaymentEventPublisher(null, TOPIC);
    OrderPaidKafkaEvent event =
        new OrderPaidKafkaEvent(
            "CMD-CC-20261118-003",
            "user-123",
            "k7P9xQ2",
            new BigDecimal("20.00"),
            "EUR",
            List.of(),
            Instant.now());

    assertThatCode(() -> nullPublisher.publishOrderPaid(event)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Should catch and handle Kafka exceptions gracefully")
  void shouldHandleKafkaSendException() {
    when(kafkaTemplate.send(any(), any(), any()))
        .thenThrow(new RuntimeException("Kafka connection refused"));

    OrderPaidKafkaEvent event =
        new OrderPaidKafkaEvent(
            "CMD-CC-20261118-004",
            "user-123",
            "k7P9xQ2",
            new BigDecimal("20.00"),
            "EUR",
            List.of(),
            Instant.now());

    assertThatCode(() -> publisher.publishOrderPaid(event)).doesNotThrowAnyException();
  }
}
