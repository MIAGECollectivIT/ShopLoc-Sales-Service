package fr.miage.collectivit.salesservice.service;

import fr.miage.collectivit.salesservice.model.Wallet;
import fr.miage.collectivit.salesservice.model.WalletTransaction;
import fr.miage.collectivit.salesservice.model.WalletTransactionType;
import fr.miage.collectivit.salesservice.repository.WalletRepository;
import fr.miage.collectivit.salesservice.repository.WalletTransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audit service recording transaction attempts and failures (SLE-15, SLE-18) in an isolated
 * transaction boundary (REQUIRES_NEW) to preserve audit trails even during rollbacks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({
  "PMD.LawOfDemeter",
  "PMD.AvoidDuplicateLiterals",
  "PMD.AvoidCatchingGenericException"
})
public class WalletAuditService {

  private static final int MAX_REASON_LENGTH = 200;

  private final WalletRepository walletRepository;
  private final WalletTransactionRepository walletTransactionRepository;

  /** Records a failed reload attempt with status 'FAILED' in an independent transaction. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordFailedReload(String userId, BigDecimal amount, String failureReason) {
    try {
      String trimmedUserId = userId.trim();
      Wallet wallet =
          walletRepository
              .findByUserId(trimmedUserId)
              .orElseGet(
                  () ->
                      walletRepository.save(
                          Wallet.builder()
                              .userId(trimmedUserId)
                              .balance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                              .currency("EUR")
                              .createdAt(Instant.now())
                              .updatedAt(Instant.now())
                              .build()));

      String txRef =
          "WTX-FAIL-"
              + UUID.randomUUID()
                  .toString()
                  .replace("-", "")
                  .substring(0, 10)
                  .toUpperCase(Locale.ROOT);

      String cleanReason =
          (failureReason != null && !failureReason.isBlank())
              ? failureReason.trim()
              : "Refus autorisation bancaire";

      if (cleanReason.length() > MAX_REASON_LENGTH) {
        cleanReason = cleanReason.substring(0, MAX_REASON_LENGTH - 3) + "...";
      }

      WalletTransaction failedTx =
          WalletTransaction.builder()
              .wallet(wallet)
              .userId(trimmedUserId)
              .transactionReference(txRef)
              .amount(amount)
              .type(WalletTransactionType.TOPUP)
              .status("FAILED")
              .description("Échec rechargement carte bancaire (" + cleanReason + ")")
              .balanceAfter(wallet.getBalance())
              .createdAt(Instant.now())
              .build();

      walletTransactionRepository.save(failedTx);

      if (log.isWarnEnabled()) {
        log.warn(
            "Recorded failed wallet reload: userId={}, amount={} EUR, txRef={}, reason='{}'",
            trimmedUserId,
            amount,
            txRef,
            cleanReason);
      }
    } catch (Exception ex) {
      if (log.isErrorEnabled()) {
        log.error("Failed to record wallet reload failure audit log: {}", ex.getMessage(), ex);
      }
    }
  }
}
