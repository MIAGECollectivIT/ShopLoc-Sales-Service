package fr.miage.collectivit.salesservice.exception;

import java.io.Serial;

public class PaymentNotFoundException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public PaymentNotFoundException(String reference) {
    super("No payment transaction found for reference: " + reference);
  }
}
