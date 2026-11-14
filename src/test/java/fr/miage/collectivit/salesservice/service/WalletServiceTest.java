package fr.miage.collectivit.salesservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletService Unit Tests (SLE-15, SLE-17, SLE-18)")
class WalletServiceTest {

  @Mock private WalletRepository walletRepository;
  @Mock private WalletTransactionRepository walletTransactionRepository;
  @Mock private PaymentService paymentService;
  @Mock private WalletAuditService walletAuditService;

  @InjectMocks private WalletService walletService;

  private static final String USER_ID = "a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d";
  private Wallet wallet;

  @BeforeEach
  void setUp() {
    wallet =
        Wallet.builder()
            .id(1L)
            .userId(USER_ID)
            .balance(new BigDecimal("20.00"))
            .currency("EUR")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
  }

  @Test
  @DisplayName("SLE-17: Consult wallet balance in real time")
  void shouldReturnExistingWalletBalance() {
    when(walletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(wallet));

    WalletResponse response = walletService.getWallet(USER_ID);

    assertThat(response.userId()).isEqualTo(USER_ID);
    assertThat(response.balance()).isEqualByComparingTo("20.00");
    assertThat(response.currency()).isEqualTo("EUR");
  }

  @Test
  @DisplayName("SLE-17: Return zero balance when wallet does not exist yet")
  void shouldReturnZeroBalanceForNewUser() {
    when(walletRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

    WalletResponse response = walletService.getWallet(USER_ID);

    assertThat(response.userId()).isEqualTo(USER_ID);
    assertThat(response.balance()).isEqualByComparingTo("0.00");
  }

  @Test
  @DisplayName("SLE-15: Reload wallet with valid amount between 5€ and 100€")
  void shouldReloadWalletSuccessfully() {
    when(walletRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(wallet));
    when(walletRepository.save(any(Wallet.class))).thenAnswer(i -> i.getArgument(0));

    PaymentAuthorizationResponse mockAuth =
        new PaymentAuthorizationResponse(
            "TRANSACTION_APPROVED",
            "AUTH-999",
            "TX-123",
            new BigDecimal("30.00"),
            "EUR",
            "WRL-12345",
            Instant.now().toString(),
            "RG001");
    when(paymentService.authorizePayment(any(PaymentAuthorizationRequest.class), eq(USER_ID)))
        .thenReturn(mockAuth);

    WalletReloadRequest reloadRequest =
        new WalletReloadRequest(new BigDecimal("30.00"), "tok_visa_4242");

    WalletResponse response = walletService.reload(USER_ID, reloadRequest);

    assertThat(response.balance()).isEqualByComparingTo("50.00");
    verify(walletTransactionRepository).save(any(WalletTransaction.class));
  }

  @Test
  @DisplayName(
      "SLE-15: Replay existing wallet without charging when idempotency key already processed")
  void shouldReplayExistingTransactionWhenIdempotencyKeyMatches() {
    WalletTransaction existingTx =
        WalletTransaction.builder()
            .id(99L)
            .userId(USER_ID)
            .transactionReference("WTX-EXISTING")
            .amount(new BigDecimal("30.00"))
            .type(WalletTransactionType.TOPUP)
            .status("SUCCESS")
            .idempotencyKey("idem-key-duplicate")
            .balanceAfter(new BigDecimal("20.00"))
            .createdAt(Instant.now())
            .build();

    when(walletTransactionRepository.findByUserIdAndIdempotencyKey(USER_ID, "idem-key-duplicate"))
        .thenReturn(Optional.of(existingTx));
    when(walletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(wallet));

    WalletReloadRequest reloadRequest =
        new WalletReloadRequest(new BigDecimal("30.00"), "tok_visa_4242", "idem-key-duplicate");

    WalletResponse response = walletService.reload(USER_ID, reloadRequest, "idem-key-duplicate");

    assertThat(response.balance()).isEqualByComparingTo("20.00");
    verify(paymentService, never()).authorizePayment(any(), any());
    verify(walletRepository, never()).save(any());
  }

  @Test
  @DisplayName("SLE-15: Reject reload when 7-day rolling limit (250 €) is exceeded")
  void shouldRejectReloadWhenWeeklyLimitExceeded() {
    when(walletTransactionRepository.sumAmountByUserIdAndTypeAndCreatedAtAfter(
            eq(USER_ID), eq(WalletTransactionType.TOPUP), any(Instant.class)))
        .thenReturn(new BigDecimal("230.00"));

    WalletReloadRequest reloadRequest =
        new WalletReloadRequest(new BigDecimal("30.00"), "tok_visa_4242");

    assertThatThrownBy(() -> walletService.reload(USER_ID, reloadRequest))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Plafond hebdomadaire de rechargement dépassé");

    verify(paymentService, never()).authorizePayment(any(), any());
  }

  @Test
  @DisplayName("SLE-15: Calculate remaining weekly reload limit accurately")
  void shouldCalculateRemainingWeeklyLimitCorrectly() {
    when(walletTransactionRepository.sumAmountByUserIdAndTypeAndCreatedAtAfter(
            eq(USER_ID), eq(WalletTransactionType.TOPUP), any(Instant.class)))
        .thenReturn(new BigDecimal("80.00"));

    BigDecimal remaining = walletService.getWeeklyReloadRemaining(USER_ID);

    assertThat(remaining).isEqualByComparingTo("170.00");
  }

  @Test
  @DisplayName("SLE-15: Reject reload when idempotency key is expired (> 24h)")
  void shouldRejectReloadWhenIdempotencyKeyExpired() {
    WalletTransaction oldTx =
        WalletTransaction.builder()
            .id(99L)
            .userId(USER_ID)
            .transactionReference("WTX-OLD")
            .amount(new BigDecimal("30.00"))
            .type(WalletTransactionType.TOPUP)
            .status("SUCCESS")
            .idempotencyKey("idem-expired")
            .balanceAfter(new BigDecimal("20.00"))
            .createdAt(Instant.now().minus(25, java.time.temporal.ChronoUnit.HOURS))
            .build();

    when(walletTransactionRepository.findByUserIdAndIdempotencyKey(USER_ID, "idem-expired"))
        .thenReturn(Optional.of(oldTx));

    WalletReloadRequest reloadRequest =
        new WalletReloadRequest(new BigDecimal("30.00"), "tok_visa_4242", "idem-expired");

    assertThatThrownBy(() -> walletService.reload(USER_ID, reloadRequest, "idem-expired"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("La clé d'idempotence a expiré");

    verify(paymentService, never()).authorizePayment(any(), any());
  }

  @Test
  @DisplayName(
      "SLE-15: Do not credit wallet when bank payment fails with exception and record audit failure")
  void shouldNotCreditWalletWhenBankFails() {
    when(paymentService.authorizePayment(any(PaymentAuthorizationRequest.class), eq(USER_ID)))
        .thenThrow(new RuntimeException("Bank gateway communication error"));

    WalletReloadRequest reloadRequest =
        new WalletReloadRequest(new BigDecimal("30.00"), "tok_visa_4242");

    assertThatThrownBy(() -> walletService.reload(USER_ID, reloadRequest))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Bank gateway communication error");

    verify(walletRepository, never()).save(any(Wallet.class));
    verify(walletTransactionRepository, never()).save(any(WalletTransaction.class));
    verify(walletAuditService)
        .recordFailedReload(
            eq(USER_ID), eq(new BigDecimal("30.00")), eq("Bank gateway communication error"));
  }

  @Test
  @DisplayName("SLE-17: Automatically provision wallet entity when it does not exist")
  void shouldProvisionNewWalletEntity() {
    when(walletRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
    when(walletRepository.save(any(Wallet.class))).thenAnswer(i -> i.getArgument(0));

    Wallet created = walletService.getOrCreateWalletEntity(USER_ID);

    assertThat(created.getUserId()).isEqualTo(USER_ID);
    assertThat(created.getBalance()).isEqualByComparingTo("0.00");
    assertThat(created.getCurrency()).isEqualTo("EUR");
    verify(walletRepository).save(any(Wallet.class));
  }

  @Test
  @DisplayName("SLE-15: Reject reload below strict floor 5.00 €")
  void shouldRejectReloadBelowMinimum() {
    WalletReloadRequest reloadRequest =
        new WalletReloadRequest(new BigDecimal("4.99"), "tok_visa_4242");

    assertThatThrownBy(() -> walletService.reload(USER_ID, reloadRequest))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Le montant minimum de rechargement est de 5.00 €");
  }

  @Test
  @DisplayName("SLE-15: Reject reload above strict ceiling 100.00 €")
  void shouldRejectReloadAboveMaximum() {
    WalletReloadRequest reloadRequest =
        new WalletReloadRequest(new BigDecimal("100.01"), "tok_visa_4242");

    assertThatThrownBy(() -> walletService.reload(USER_ID, reloadRequest))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Le montant maximum de rechargement est de 100.00 €");
  }

  @Test
  @DisplayName("SLE-18: Debit wallet for in-store purchase with partner store name")
  void shouldDebitWalletSuccessfully() {
    when(walletRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(wallet));
    when(walletRepository.save(any(Wallet.class))).thenAnswer(i -> i.getArgument(0));

    WalletDebitRequest debitRequest =
        new WalletDebitRequest(
            new BigDecimal("12.50"), 1L, "Boulangerie Le Moulin Doré", "ORD-123", "Achat pain");

    WalletResponse response = walletService.debit(USER_ID, debitRequest);

    assertThat(response.balance()).isEqualByComparingTo("7.50");
    verify(walletTransactionRepository).save(any(WalletTransaction.class));
  }

  @Test
  @DisplayName("SLE-18: Reject debit when wallet funds are insufficient")
  void shouldRejectDebitWhenInsufficientFunds() {
    when(walletRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(wallet));

    WalletDebitRequest debitRequest =
        new WalletDebitRequest(
            new BigDecimal("25.00"), 1L, "Boulangerie Le Moulin Doré", "ORD-123", "Achat pain");

    assertThatThrownBy(() -> walletService.debit(USER_ID, debitRequest))
        .isInstanceOf(InsufficientWalletBalanceException.class)
        .hasMessageContaining("Solde insuffisant");
  }

  @Test
  @DisplayName("SLE-18: Retrieve ordered transaction history (newest first)")
  void shouldReturnOrderedTransactionHistory() {
    WalletTransaction tx1 =
        WalletTransaction.builder()
            .id(1L)
            .userId(USER_ID)
            .transactionReference("WTX-001")
            .amount(new BigDecimal("20.00"))
            .type(WalletTransactionType.TOPUP)
            .status("SUCCESS")
            .balanceAfter(new BigDecimal("20.00"))
            .createdAt(Instant.now().minusSeconds(3600))
            .build();

    WalletTransaction tx2 =
        WalletTransaction.builder()
            .id(2L)
            .userId(USER_ID)
            .transactionReference("WTX-002")
            .amount(new BigDecimal("5.00"))
            .type(WalletTransactionType.DEBIT)
            .storeId(1L)
            .storeName("Boulangerie Le Moulin Doré")
            .status("SUCCESS")
            .balanceAfter(new BigDecimal("15.00"))
            .createdAt(Instant.now())
            .build();

    when(walletTransactionRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
        .thenReturn(List.of(tx2, tx1));

    List<WalletTransactionResponse> history = walletService.getTransactions(USER_ID);

    assertThat(history).hasSize(2);
    assertThat(history.get(0).transactionReference()).isEqualTo("WTX-002");
    assertThat(history.get(0).storeName()).isEqualTo("Boulangerie Le Moulin Doré");
    assertThat(history.get(0).type()).isEqualTo(WalletTransactionType.DEBIT);
    assertThat(history.get(1).transactionReference()).isEqualTo("WTX-001");
    assertThat(history.get(1).type()).isEqualTo(WalletTransactionType.TOPUP);
  }
}
