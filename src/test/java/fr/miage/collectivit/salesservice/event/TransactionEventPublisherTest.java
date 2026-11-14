package fr.miage.collectivit.salesservice.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

@DisplayName("TransactionEventPublisher Unit Tests")
class TransactionEventPublisherTest {

  private static final String TOPIC = "shoploc.transactions.approved";
  private KafkaTemplate<String, Object> kafkaTemplate;
  private TransactionEventPublisher publisher;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    kafkaTemplate = mock(KafkaTemplate.class);
    publisher = new TransactionEventPublisher(kafkaTemplate, TOPIC);
  }

  @Test
  @DisplayName("Should publish transaction approved event with userId key")
  void shouldPublishEventSuccessfully() {
    TransactionApprovedKafkaEvent event =
        new TransactionApprovedKafkaEvent(
            "user-123", "txn-456", "REF-789", 42.50, "EUR", Instant.now());

    publisher.publishTransactionApproved(event);

    verify(kafkaTemplate).send(eq(TOPIC), eq("user-123"), eq(event));
  }

  @Test
  @DisplayName("Should ignore event when event or userId is null or blank")
  void shouldIgnoreInvalidEvent() {
    publisher.publishTransactionApproved(null);
    publisher.publishTransactionApproved(
        new TransactionApprovedKafkaEvent(null, "txn-1", "ref-1", 10.0, "EUR", Instant.now()));
    publisher.publishTransactionApproved(
        new TransactionApprovedKafkaEvent("   ", "txn-2", "ref-2", 10.0, "EUR", Instant.now()));

    verify(kafkaTemplate, never()).send(any(), any(), any());
  }

  @Test
  @DisplayName("Should not throw when kafkaTemplate is null")
  void shouldHandleNullKafkaTemplateGracefully() {
    TransactionEventPublisher noKafkaPublisher = new TransactionEventPublisher(null, TOPIC);
    TransactionApprovedKafkaEvent event =
        new TransactionApprovedKafkaEvent(
            "user-123", "txn-456", "REF-789", 42.50, "EUR", Instant.now());

    assertThatCode(() -> noKafkaPublisher.publishTransactionApproved(event))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Should catch and handle kafka send exception gracefully")
  void shouldCatchAndLogKafkaException() {
    TransactionApprovedKafkaEvent event =
        new TransactionApprovedKafkaEvent(
            "user-123", "txn-456", "REF-789", 42.50, "EUR", Instant.now());

    when(kafkaTemplate.send(eq(TOPIC), eq("user-123"), eq(event)))
        .thenThrow(new RuntimeException("Kafka broker down"));

    assertThatCode(() -> publisher.publishTransactionApproved(event)).doesNotThrowAnyException();
  }
}
