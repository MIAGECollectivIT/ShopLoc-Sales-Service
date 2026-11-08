package fr.miage.collectivit.salesservice.exception;

import java.io.Serial;

public class PaymentGatewayException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public PaymentGatewayException(String message) {
    super(message);
  }

  public PaymentGatewayException(String message, Throwable cause) {
    super(message, cause);
  }
}
