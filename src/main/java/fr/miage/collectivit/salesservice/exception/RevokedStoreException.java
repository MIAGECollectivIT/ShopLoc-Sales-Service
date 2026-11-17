package fr.miage.collectivit.salesservice.exception;

public class RevokedStoreException extends RuntimeException {
  private final Long storeId;

  public RevokedStoreException(Long storeId) {
    super(
        "Le commerce partenaire #"
            + storeId
            + " est révoqué et ne peut plus effectuer d'opération de vente ou de commande");
    this.storeId = storeId;
  }

  public RevokedStoreException(String message, Long storeId) {
    super(message);
    this.storeId = storeId;
  }

  public Long getStoreId() {
    return storeId;
  }
}
