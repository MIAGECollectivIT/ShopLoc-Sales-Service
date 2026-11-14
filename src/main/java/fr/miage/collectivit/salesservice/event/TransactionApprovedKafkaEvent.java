package fr.miage.collectivit.salesservice.event;

import java.time.Instant;

/** Event published to Kafka when a transaction or payment is approved (SLE-45). */
public record TransactionApprovedKafkaEvent(
    String userId,
    String transactionId,
    String reference,
    Double amount,
    String currency,
    Instant timestamp) {

  public TransactionApprovedKafkaEvent {
    if (timestamp == null) {
      timestamp = Instant.now();
    }
  }
}
