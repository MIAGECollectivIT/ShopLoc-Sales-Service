package fr.miage.collectivit.salesservice.model;

/**
 * Type of digital wallet transaction: - TOPUP: Credit / recharge operation (SLE-15) - DEBIT:
 * Purchase / payment debit operation (SLE-18)
 */
public enum WalletTransactionType {
  TOPUP,
  DEBIT
}
