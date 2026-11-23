package fr.miage.collectivit.salesservice.service.receipt;

import fr.miage.collectivit.salesservice.dto.event.OrderPaidKafkaEvent;

/**
 * Domain event dispatched after an order payment has successfully been committed to the database.
 * Used to trigger asynchronous PDF generation, email transmission, and Kafka loyalty event
 * publication without blocking or desynchronizing the DB transaction.
 */
public record OrderPaidEvent(String orderIdentifier, OrderPaidKafkaEvent kafkaEvent) {

  public OrderPaidEvent {
    if (orderIdentifier == null || orderIdentifier.isBlank()) {
      throw new IllegalArgumentException("orderIdentifier must not be null or blank");
    }
  }

  public OrderPaidEvent(String orderIdentifier) {
    this(orderIdentifier, null);
  }
}
