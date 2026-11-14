package fr.miage.collectivit.salesservice.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Publishes domain events to Kafka topics for inter-service communication (SLE-45). */
@Slf4j
@Component
public class TransactionEventPublisher {

  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final String topic;

  @Autowired
  public TransactionEventPublisher(
      @Autowired(required = false) KafkaTemplate<String, Object> kafkaTemplate,
      @Value("${application.kafka.topics.transaction-approved:shoploc.transactions.approved}")
          String topic) {
    this.kafkaTemplate = kafkaTemplate;
    this.topic = topic;
  }

  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  public void publishTransactionApproved(TransactionApprovedKafkaEvent event) {
    if (event == null || event.userId() == null || event.userId().isBlank()) {
      return;
    }

    if (kafkaTemplate == null) {
      if (log.isDebugEnabled()) {
        log.debug(
            "KafkaTemplate not available, skipping event publication for user {}", event.userId());
      }
      return;
    }

    try {
      kafkaTemplate.send(topic, event.userId(), event);
      if (log.isInfoEnabled()) {
        log.info(
            "Published transaction approved event to Kafka topic {}: userId={}, reference={}",
            topic,
            event.userId(),
            event.reference());
      }
    } catch (Exception ex) {
      if (log.isWarnEnabled()) {
        log.warn(
            "Failed to publish transaction approved event to Kafka for user {}: {}",
            event.userId(),
            ex.getMessage());
      }
    }
  }
}
