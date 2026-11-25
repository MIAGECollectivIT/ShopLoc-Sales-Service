package fr.miage.collectivit.salesservice.event;

import fr.miage.collectivit.salesservice.config.KafkaTopicConfig;
import fr.miage.collectivit.salesservice.dto.event.OrderPaidKafkaEvent;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

/**
 * Publishes order paid events to Kafka topics for inter-service loyalty points crediting (SLE-220).
 */
@Slf4j
@Component
public class OrderPaymentEventPublisher {

  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final String topic;

  @Autowired
  public OrderPaymentEventPublisher(
      @Autowired(required = false) KafkaTemplate<String, Object> kafkaTemplate,
      @Value(
              "${application.kafka.topics.order-payments:"
                  + KafkaTopicConfig.ORDER_PAYMENTS_TOPIC
                  + "}")
          String topic) {
    this.kafkaTemplate = kafkaTemplate;
    this.topic = topic;
  }

  public void publishOrderPaid(OrderPaidKafkaEvent event) {
    if (!isValidEvent(event)) {
      return;
    }

    if (kafkaTemplate == null) {
      if (log.isDebugEnabled()) {
        log.debug(
            "KafkaTemplate not available, skipping order paid event publication for reference {}",
            event.orderReference());
      }
      return;
    }

    sendEvent(event);
  }

  private boolean isValidEvent(OrderPaidKafkaEvent event) {
    return event != null
        && event.orderReference() != null
        && !event.orderReference().isBlank()
        && event.totalAmount() != null
        && event.totalAmount().compareTo(BigDecimal.ZERO) > 0;
  }

  private String resolvePartitionKey(OrderPaidKafkaEvent event) {
    return event.clientCode() != null && !event.clientCode().isBlank()
        ? event.clientCode().trim()
        : event.orderReference().trim();
  }

  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  private void sendEvent(OrderPaidKafkaEvent event) {
    try {
      String key = resolvePartitionKey(event);
      CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, event);
      if (future != null) {
        future.whenComplete((result, ex) -> logDelivery(event, ex));
      }
    } catch (Exception ex) {
      if (log.isErrorEnabled()) {
        log.error(
            "Failed to initiate order paid event publish for reference {}: {}",
            event.orderReference(),
            ex.getMessage(),
            ex);
      }
    }
  }

  private void logDelivery(OrderPaidKafkaEvent event, Throwable ex) {
    if (ex != null) {
      if (log.isErrorEnabled()) {
        log.error(
            "Failed to asynchronously deliver order paid event to Kafka for reference {}: {}",
            event.orderReference(),
            ex.getMessage(),
            ex);
      }
      return;
    }
    if (log.isInfoEnabled()) {
      log.info(
          "Published order paid event to Kafka topic {}: reference={}, totalAmount={}",
          topic,
          event.orderReference(),
          event.totalAmount());
    }
  }
}
