package fr.miage.collectivit.salesservice.exception;

public class StoreNotFoundException extends RuntimeException {
  public StoreNotFoundException(String message) {
    super(message);
  }
}
