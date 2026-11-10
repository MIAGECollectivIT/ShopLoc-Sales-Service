package fr.miage.collectivit.salesservice.exception;

import java.io.Serial;

public class PhysicalSaleNotFoundException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public PhysicalSaleNotFoundException(String reference) {
    super("No physical sale transaction found for reference: " + reference);
  }
}
