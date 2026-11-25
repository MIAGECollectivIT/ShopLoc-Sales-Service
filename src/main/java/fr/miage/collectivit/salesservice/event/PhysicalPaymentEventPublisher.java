package fr.miage.collectivit.salesservice.event;

import fr.miage.collectivit.salesservice.config.KafkaTopicConfig;
import fr.miage.collectivit.salesservice.dto.event.PhysicalPaymentCompletedEvent;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

/**
 * Publishes physical payment completed events to Kafka topics for inter-service loyalty attribution
 * (SLE-172).
 */
@Slf4j
@Component
public class PhysicalPaymentEventPublisher {

  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final String topic;

  @Autowired
  public PhysicalPaymentEventPublisher(
      @Autowired(required = false) KafkaTemplate<String, Object> kafkaTemplate,
      @Value(
              "${application.kafka.topics.physical-payments:"
                  + KafkaTopicConfig.PHYSICAL_PAYMENTS_TOPIC
                  + "}")
          String topic) {
    this.kafkaTemplate = kafkaTemplate;
    this.topic = topic;
  }

  public void publishPhysicalPaymentCompleted(PhysicalPaymentCompletedEvent event) {
    if (!isValidEvent(event)) {
      return;
    }

    if (kafkaTemplate == null) {
      if (log.isDebugEnabled()) {
        log.debug(
            "KafkaTemplate not available, skipping physical payment event publication for reference {}",
            event.transactionReference());
      }
      return;
    }

    sendEvent(event);
  }

  private boolean isValidEvent(PhysicalPaymentCompletedEvent event) {
    return event != null
        && event.transactionReference() != null
        && !event.transactionReference().isBlank();
  }

  private String resolvePartitionKey(PhysicalPaymentCompletedEvent event) {
    return event.clientCode() != null && !event.clientCode().isBlank()
        ? event.clientCode().trim()
        : event.transactionReference().trim();
  }

  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  private void sendEvent(PhysicalPaymentCompletedEvent event) {
    try {
      String key = resolvePartitionKey(event);
      CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, event);
      if (future != null) {
        future.whenComplete((result, ex) -> logDelivery(event, key, ex));
      }
    } catch (Exception ex) {
      if (log.isErrorEnabled()) {
        log.error(
            "Failed to initiate physical payment event publish for reference {}: {}",
            event.transactionReference(),
            ex.getMessage(),
            ex);
      }
    }
  }

  private void logDelivery(PhysicalPaymentCompletedEvent event, String key, Throwable ex) {
    if (ex != null) {
      if (log.isErrorEnabled()) {
        log.error(
            "Failed to asynchronously deliver physical payment event to Kafka for reference {}: {}",
            event.transactionReference(),
            ex.getMessage(),
            ex);
      }
      return;
    }
    if (log.isInfoEnabled()) {
      log.info(
          "Published physical payment event to Kafka topic {}: key={}, reference={}, storeUid={}, amount={}",
          topic,
          key,
          event.transactionReference(),
          event.storeUid(),
          event.amount());
    }
  }
}
