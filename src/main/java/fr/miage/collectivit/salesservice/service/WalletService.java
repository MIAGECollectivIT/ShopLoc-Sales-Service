package fr.miage.collectivit.salesservice.service;

import fr.miage.collectivit.salesservice.dto.PaymentAuthorizationRequest;
import fr.miage.collectivit.salesservice.dto.PaymentAuthorizationResponse;
import fr.miage.collectivit.salesservice.dto.WalletDebitRequest;
import fr.miage.collectivit.salesservice.dto.WalletReloadRequest;
import fr.miage.collectivit.salesservice.dto.WalletResponse;
import fr.miage.collectivit.salesservice.dto.WalletTransactionResponse;
import fr.miage.collectivit.salesservice.exception.InsufficientWalletBalanceException;
import fr.miage.collectivit.salesservice.model.Wallet;
import fr.miage.collectivit.salesservice.model.WalletTransaction;
import fr.miage.collectivit.salesservice.model.WalletTransactionType;
import fr.miage.collectivit.salesservice.repository.WalletRepository;
import fr.miage.collectivit.salesservice.repository.WalletTransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing citizen digital wallet balance, online bank reloads (SLE-15), real-time balance
 * queries (SLE-17), and transaction history auditing (SLE-18).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({
  "PMD.LawOfDemeter",
  "PMD.AvoidDuplicateLiterals",
  "PMD.AvoidCatchingGenericException"
})
public class WalletService {

  public static final BigDecimal MIN_RELOAD_AMOUNT = new BigDecimal("5.00");
  public static final BigDecimal MAX_RELOAD_AMOUNT = new BigDecimal("100.00");
  public static final BigDecimal MAX_WEEKLY_RELOAD_AMOUNT = new BigDecimal("250.00");
  public static final int ROLLING_LIMIT_DAYS = 7;
  public static final int IDEMPOTENCY_TTL_HOURS = 24;
  private static final String DEFAULT_CURRENCY = "EUR";
  private static final String DEFAULT_PAYMENT_TOKEN = "tok_visa_4242";

  private final WalletRepository walletRepository;
  private final WalletTransactionRepository walletTransactionRepository;
  private final PaymentService paymentService;
  private final WalletAuditService walletAuditService;

  /** Retrieves or provisions the wallet for the given citizen user (SLE-17). */
  @Transactional
  public Wallet getOrCreateWalletEntity(String userId) {
    String trimmedUserId = userId.trim();
    return walletRepository
        .findByUserId(trimmedUserId)
        .orElseGet(
            () ->
                walletRepository.save(
                    Wallet.builder()
                        .userId(trimmedUserId)
                        .balance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                        .currency(DEFAULT_CURRENCY)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build()));
  }

  /**
   * Retrieves or provisions the wallet with a pessimistic write lock (SELECT FOR UPDATE) to
   * strictly serialize concurrent modifications and prevent double-spending race conditions.
   */
  @Transactional
  public Wallet getOrCreateWalletEntityForUpdate(String userId) {
    String trimmedUserId = userId.trim();
    return walletRepository
        .findByUserIdForUpdate(trimmedUserId)
        .orElseGet(() -> getOrCreateWalletEntity(trimmedUserId));
  }

  /**
   * Calculates the remaining available reload amount for the citizen over a rolling 7-day window.
   */
  @Transactional(readOnly = true)
  public BigDecimal getWeeklyReloadRemaining(String userId) {
    Instant sevenDaysAgo = Instant.now().minus(ROLLING_LIMIT_DAYS, ChronoUnit.DAYS);
    BigDecimal sumRecent =
        walletTransactionRepository.sumAmountByUserIdAndTypeAndCreatedAtAfter(
            userId.trim(), WalletTransactionType.TOPUP, sevenDaysAgo);
    if (sumRecent == null) {
      sumRecent = BigDecimal.ZERO;
    }
    BigDecimal remaining = MAX_WEEKLY_RELOAD_AMOUNT.subtract(sumRecent);
    return remaining.compareTo(BigDecimal.ZERO) < 0
        ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        : remaining.setScale(2, RoundingMode.HALF_UP);
  }

  /** Retrieves the current real-time balance of the citizen's wallet (SLE-17). */
  @Transactional(readOnly = true)
  public WalletResponse getWallet(String userId) {
    String trimmedUserId = userId.trim();
    BigDecimal weeklyRemaining = getWeeklyReloadRemaining(trimmedUserId);
    return walletRepository
        .findByUserId(trimmedUserId)
        .map(wallet -> WalletResponse.fromEntity(wallet, weeklyRemaining))
        .orElseGet(
            () ->
                new WalletResponse(
                    null,
                    trimmedUserId,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    DEFAULT_CURRENCY,
                    Instant.now(),
                    Instant.now(),
                    weeklyRemaining));
  }

  /**
   * Reloads the citizen wallet with a custom amount between 5.00 € and 100.00 € through the secure
   * bank payment gateway (SLE-15).
   */
  @Transactional
  public WalletResponse reload(String userId, WalletReloadRequest request) {
    return reload(userId, request, request != null ? request.idempotencyKey() : null);
  }

  /**
   * Reloads the citizen wallet with idempotency replay protection and 7-day rolling limit check.
   */
  @Transactional
  public WalletResponse reload(String userId, WalletReloadRequest request, String idempotencyKey) {
    String trimmedUserId = userId.trim();
    String effectiveKey =
        (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey.trim() : null;

    Optional<WalletResponse> replayed = checkIdempotentReload(trimmedUserId, effectiveKey);
    if (replayed.isPresent()) {
      return replayed.get();
    }

    BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
    validateReloadAmount(amount);
    verifyWeeklyReloadLimit(trimmedUserId, amount);

    String paymentToken =
        (request.paymentToken() != null && !request.paymentToken().isBlank())
            ? request.paymentToken().trim()
            : DEFAULT_PAYMENT_TOKEN;

    String paymentReference = "WRL-" + System.currentTimeMillis();
    PaymentAuthorizationRequest payRequest =
        new PaymentAuthorizationRequest(amount, DEFAULT_CURRENCY, paymentReference, paymentToken);

    // Call external bank gateway through PaymentService (RG001 verification)
    PaymentAuthorizationResponse payResponse;
    try {
      payResponse = paymentService.authorizePayment(payRequest, trimmedUserId);
    } catch (RuntimeException ex) {
      walletAuditService.recordFailedReload(trimmedUserId, amount, ex.getMessage());
      throw ex;
    }

    // Credit the wallet with row-level pessimistic locking
    Wallet wallet = getOrCreateWalletEntityForUpdate(trimmedUserId);
    BigDecimal newBalance = wallet.getBalance().add(amount).setScale(2, RoundingMode.HALF_UP);
    wallet.setBalance(newBalance);
    Wallet savedWallet = walletRepository.save(wallet);

    // Record the audit transaction (SLE-18)
    String txRef =
        "WTX-"
            + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase(Locale.ROOT);
    WalletTransaction tx =
        WalletTransaction.builder()
            .wallet(savedWallet)
            .userId(trimmedUserId)
            .transactionReference(txRef)
            .amount(amount)
            .type(WalletTransactionType.TOPUP)
            .status("SUCCESS")
            .paymentReference(payResponse.reference())
            .description("Rechargement porte-monnaie par carte bancaire")
            .balanceAfter(newBalance)
            .idempotencyKey(effectiveKey)
            .createdAt(Instant.now())
            .build();
    walletTransactionRepository.save(tx);

    if (log.isInfoEnabled()) {
      log.info(
          "Wallet successfully reloaded: userId={}, amount={} EUR, newBalance={} EUR, txRef={}, idempotencyKey={}",
          trimmedUserId,
          amount,
          newBalance,
          txRef,
          effectiveKey);
    }

    return WalletResponse.fromEntity(savedWallet, getWeeklyReloadRemaining(trimmedUserId));
  }

  private Optional<WalletResponse> checkIdempotentReload(String userId, String effectiveKey) {
    if (effectiveKey == null) {
      return Optional.empty();
    }
    Optional<WalletTransaction> existingTx =
        walletTransactionRepository.findByUserIdAndIdempotencyKey(userId, effectiveKey);
    if (existingTx.isEmpty()) {
      return Optional.empty();
    }

    WalletTransaction tx = existingTx.get();
    Instant cutoff = Instant.now().minus(IDEMPOTENCY_TTL_HOURS, ChronoUnit.HOURS);
    if (!tx.getCreatedAt().isAfter(cutoff)) {
      throw new IllegalArgumentException(
          "La clé d'idempotence a expiré (durée de validité : 24 heures). Veuillez renouveler l'opération avec une nouvelle clé.");
    }

    if (log.isInfoEnabled()) {
      log.info(
          "Idempotent reload replayed for user {} with key {}: txRef={}",
          userId,
          effectiveKey,
          tx.getTransactionReference());
    }
    return Optional.of(getWallet(userId));
  }

  private void verifyWeeklyReloadLimit(String userId, BigDecimal amount) {
    BigDecimal remainingLimit = getWeeklyReloadRemaining(userId);
    if (amount.compareTo(remainingLimit) > 0) {
      throw new IllegalArgumentException(
          "Plafond hebdomadaire de rechargement dépassé. Montant disponible sur 7 jours glissants : "
              + remainingLimit
              + " € (maximum : "
              + MAX_WEEKLY_RELOAD_AMOUNT
              + " €).");
    }
  }

  /** Debits the citizen wallet for in-store or click & collect purchases (SLE-18). */
  @Transactional
  public WalletResponse debit(String userId, WalletDebitRequest request) {
    BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Le montant du débit doit être supérieur à zéro.");
    }

    // Debit the wallet with row-level pessimistic locking
    Wallet wallet = getOrCreateWalletEntityForUpdate(userId);
    if (wallet.getBalance().compareTo(amount) < 0) {
      throw new InsufficientWalletBalanceException(
          "Solde insuffisant sur votre porte-monnaie (solde disponible : "
              + wallet.getBalance()
              + " €)");
    }

    BigDecimal newBalance = wallet.getBalance().subtract(amount).setScale(2, RoundingMode.HALF_UP);
    wallet.setBalance(newBalance);
    Wallet savedWallet = walletRepository.save(wallet);

    String txRef =
        "WTX-"
            + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase(Locale.ROOT);
    String description =
        (request.description() != null && !request.description().isBlank())
            ? request.description().trim()
            : ("Achat chez " + (request.storeName() != null ? request.storeName() : "commerçant"));

    WalletTransaction tx =
        WalletTransaction.builder()
            .wallet(savedWallet)
            .userId(userId.trim())
            .transactionReference(txRef)
            .amount(amount)
            .type(WalletTransactionType.DEBIT)
            .status("SUCCESS")
            .storeId(request.storeId())
            .storeName(request.storeName())
            .orderReference(request.orderReference())
            .description(description)
            .balanceAfter(newBalance)
            .createdAt(Instant.now())
            .build();
    walletTransactionRepository.save(tx);

    if (log.isInfoEnabled()) {
      log.info(
          "Wallet successfully debited: userId={}, amount={} EUR, store='{}', newBalance={} EUR, txRef={}",
          userId,
          amount,
          request.storeName(),
          newBalance,
          txRef);
    }

    return WalletResponse.fromEntity(savedWallet);
  }

  /** Returns the ordered transaction history (newest to oldest) for the citizen (SLE-18). */
  @Transactional(readOnly = true)
  public List<WalletTransactionResponse> getTransactions(String userId) {
    return walletTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId.trim()).stream()
        .map(WalletTransactionResponse::fromEntity)
        .toList();
  }

  private void validateReloadAmount(BigDecimal amount) {
    if (amount == null) {
      throw new IllegalArgumentException("Le montant de rechargement est obligatoire.");
    }
    if (amount.compareTo(MIN_RELOAD_AMOUNT) < 0) {
      throw new IllegalArgumentException(
          "Le montant minimum de rechargement est de " + MIN_RELOAD_AMOUNT + " €.");
    }
    if (amount.compareTo(MAX_RELOAD_AMOUNT) > 0) {
      throw new IllegalArgumentException(
          "Le montant maximum de rechargement est de " + MAX_RELOAD_AMOUNT + " €.");
    }
  }
}
