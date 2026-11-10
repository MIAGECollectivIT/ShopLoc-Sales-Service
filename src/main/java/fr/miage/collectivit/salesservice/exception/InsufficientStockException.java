package fr.miage.collectivit.salesservice.exception;

/** Exception thrown when requested product stock is insufficient in Store Service. */
public class InsufficientStockException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InsufficientStockException(String message) {
    super(message);
  }

  public InsufficientStockException(String message, Throwable cause) {
    super(message, cause);
  }
}
