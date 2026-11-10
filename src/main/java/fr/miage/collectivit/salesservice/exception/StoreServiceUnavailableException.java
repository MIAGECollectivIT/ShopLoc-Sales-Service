package fr.miage.collectivit.salesservice.exception;

/** Exception thrown when Store Service is unreachable or unavailable during stock operations. */
public class StoreServiceUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public StoreServiceUnavailableException(String message) {
    super(message);
  }

  public StoreServiceUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
