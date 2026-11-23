package fr.miage.collectivit.salesservice.service.receipt;

import fr.miage.collectivit.salesservice.event.OrderPaymentEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Event listener executing strictly AFTER the database transaction committing the order payment.
 * Ensures the PostgreSQL database connection is fully closed before any PDF rendering, SMTP
 * transmission, or Kafka loyalty event publication starts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaidEventListener {

  private final ReceiptEmailService receiptEmailService;
  private final OrderPaymentEventPublisher orderPaymentEventPublisher;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void handleOrderPaidEvent(OrderPaidEvent event) {
    if (log.isInfoEnabled()) {
      log.info(
          "OrderPaidEvent received for order {}; triggering receipt dispatch and Kafka publication",
          event.orderIdentifier());
    }
    receiptEmailService.sendReceiptEmailAsync(event.orderIdentifier());
    if (event.kafkaEvent() != null && orderPaymentEventPublisher != null) {
      orderPaymentEventPublisher.publishOrderPaid(event.kafkaEvent());
    }
  }
}
