package fr.miage.collectivit.salesservice.service.receipt;

/**
 * Domain event dispatched after an order payment has successfully been committed to the database.
 * Used to trigger asynchronous PDF generation and email transmission without blocking the DB
 * transaction.
 */
public record OrderPaidEvent(String orderIdentifier) {

  public OrderPaidEvent {
    if (orderIdentifier == null || orderIdentifier.isBlank()) {
      throw new IllegalArgumentException("orderIdentifier must not be null or blank");
    }
  }
}
