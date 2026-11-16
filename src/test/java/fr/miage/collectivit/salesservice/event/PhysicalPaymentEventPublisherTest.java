package fr.miage.collectivit.salesservice.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.miage.collectivit.salesservice.dto.event.PhysicalPaymentCompletedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

@DisplayName("PhysicalPaymentEventPublisher Unit Tests (SLE-172)")
class PhysicalPaymentEventPublisherTest {

  private static final String TOPIC = "shoploc.physical-payments";
  private KafkaTemplate<String, Object> kafkaTemplate;
  private PhysicalPaymentEventPublisher publisher;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    kafkaTemplate = mock(KafkaTemplate.class);
    publisher = new PhysicalPaymentEventPublisher(kafkaTemplate, TOPIC);
  }

  @Test
  @DisplayName("Should publish physical payment completed event with clientCode as partition key")
  void shouldPublishEventWithClientCodeKey() {
    PhysicalPaymentCompletedEvent event =
        new PhysicalPaymentCompletedEvent(
            "TX-PHY-001",
            10L,
            "k7P9xQ2",
            "user-123",
            new BigDecimal("25.50"),
            "EUR",
            Instant.now());

    publisher.publishPhysicalPaymentCompleted(event);

    verify(kafkaTemplate).send(eq(TOPIC), eq("k7P9xQ2"), eq(event));
  }

  @Test
  @DisplayName("Should fallback to transactionReference as key when clientCode is blank")
  void shouldFallbackToTransactionReferenceWhenClientCodeIsBlank() {
    PhysicalPaymentCompletedEvent event =
        new PhysicalPaymentCompletedEvent(
            "TX-PHY-NO-CODE",
            10L,
            "   ",
            "user-123",
            new BigDecimal("15.00"),
            "EUR",
            Instant.now());

    publisher.publishPhysicalPaymentCompleted(event);

    verify(kafkaTemplate).send(eq(TOPIC), eq("TX-PHY-NO-CODE"), eq(event));
  }

  @Test
  @DisplayName("Should ignore event when event or transactionReference is null or blank")
  void shouldIgnoreInvalidEvent() {
    publisher.publishPhysicalPaymentCompleted(null);
    publisher.publishPhysicalPaymentCompleted(
        new PhysicalPaymentCompletedEvent(
            null, 10L, "CODE", "user-1", new BigDecimal("10.00"), "EUR", Instant.now()));
    publisher.publishPhysicalPaymentCompleted(
        new PhysicalPaymentCompletedEvent(
            "   ", 10L, "CODE", "user-2", new BigDecimal("10.00"), "EUR", Instant.now()));

    verify(kafkaTemplate, never()).send(any(), any(), any());
  }

  @Test
  @DisplayName("Should handle null KafkaTemplate gracefully without throwing")
  void shouldHandleNullKafkaTemplateGracefully() {
    PhysicalPaymentEventPublisher noKafkaPublisher = new PhysicalPaymentEventPublisher(null, TOPIC);
    PhysicalPaymentCompletedEvent event =
        new PhysicalPaymentCompletedEvent(
            "TX-PHY-002",
            10L,
            "k7P9xQ2",
            "user-123",
            new BigDecimal("30.00"),
            "EUR",
            Instant.now());

    assertThatCode(() -> noKafkaPublisher.publishPhysicalPaymentCompleted(event))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Should catch and log exceptions when KafkaTemplate send throws")
  void shouldCatchAndLogExceptionsWhenKafkaFails() {
    PhysicalPaymentCompletedEvent event =
        new PhysicalPaymentCompletedEvent(
            "TX-PHY-FAIL",
            10L,
            "k7P9xQ2",
            "user-123",
            new BigDecimal("40.00"),
            "EUR",
            Instant.now());

    when(kafkaTemplate.send(any(), any(), any()))
        .thenThrow(new RuntimeException("Kafka broker unreachable"));

    assertThatCode(() -> publisher.publishPhysicalPaymentCompleted(event))
        .doesNotThrowAnyException();
  }
}
