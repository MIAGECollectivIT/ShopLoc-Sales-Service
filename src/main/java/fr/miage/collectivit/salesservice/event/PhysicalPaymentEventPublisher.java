package fr.miage.collectivit.salesservice.event;

import fr.miage.collectivit.salesservice.config.KafkaTopicConfig;
import fr.miage.collectivit.salesservice.dto.event.PhysicalPaymentCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
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
      kafkaTemplate.send(topic, key, event);
      if (log.isInfoEnabled()) {
        log.info(
            "Published physical payment event to Kafka topic {}: key={}, reference={}, storeId={}, amount={}",
            topic,
            key,
            event.transactionReference(),
            event.storeId(),
            event.amount());
      }
    } catch (Exception ex) {
      if (log.isWarnEnabled()) {
        log.warn(
            "Failed to publish physical payment event to Kafka for reference {}: {}",
            event.transactionReference(),
            ex.getMessage());
      }
    }
  }
}
