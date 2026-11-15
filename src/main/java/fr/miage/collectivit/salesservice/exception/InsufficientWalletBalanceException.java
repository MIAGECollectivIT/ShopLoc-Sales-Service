package fr.miage.collectivit.salesservice.exception;

/** Exception thrown when requested wallet balance is insufficient for a debit operation. */
public class InsufficientWalletBalanceException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InsufficientWalletBalanceException(String message) {
    super(message);
  }
}
