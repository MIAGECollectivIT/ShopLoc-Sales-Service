package fr.miage.collectivit.salesservice.service.receipt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Event listener executing strictly AFTER the database transaction committing the order payment.
 * Ensures the PostgreSQL database connection is fully closed before any PDF rendering or SMTP
 * transmission starts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaidEventListener {

  private final ReceiptEmailService receiptEmailService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void handleOrderPaidEvent(OrderPaidEvent event) {
    if (log.isInfoEnabled()) {
      log.info(
          "OrderPaidEvent received for order {}; triggering asynchronous receipt generation and email dispatch",
          event.orderIdentifier());
    }
    receiptEmailService.sendReceiptEmailAsync(event.orderIdentifier());
  }
}
