package fr.miage.collectivit.salesservice.service;

import fr.miage.collectivit.salesservice.client.BankGatewayClient;
import fr.miage.collectivit.salesservice.dto.PaymentAuthorizationRequest;
import fr.miage.collectivit.salesservice.dto.PaymentAuthorizationResponse;
import fr.miage.collectivit.salesservice.dto.UserPaymentTransactionResponse;
import fr.miage.collectivit.salesservice.event.TransactionApprovedKafkaEvent;
import fr.miage.collectivit.salesservice.event.TransactionEventPublisher;
import fr.miage.collectivit.salesservice.exception.PaymentGatewayException;
import fr.miage.collectivit.salesservice.exception.PaymentNotFoundException;
import fr.miage.collectivit.salesservice.model.PaymentTransaction;
import fr.miage.collectivit.salesservice.repository.PaymentTransactionRepository;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class PaymentService {

  private final BankGatewayClient bankGatewayClient;
  private final PaymentTransactionRepository paymentTransactionRepository;
  private final TransactionEventPublisher transactionEventPublisher;

  public PaymentService(
      BankGatewayClient bankGatewayClient,
      PaymentTransactionRepository paymentTransactionRepository) {
    this(bankGatewayClient, paymentTransactionRepository, null);
  }

  @Autowired
  public PaymentService(
      BankGatewayClient bankGatewayClient,
      PaymentTransactionRepository paymentTransactionRepository,
      @Autowired(required = false) TransactionEventPublisher transactionEventPublisher) {
    this.bankGatewayClient = bankGatewayClient;
    this.paymentTransactionRepository = paymentTransactionRepository;
    this.transactionEventPublisher = transactionEventPublisher;
  }

  @SuppressWarnings("PMD.LawOfDemeter")
  public PaymentAuthorizationResponse authorizePayment(
      PaymentAuthorizationRequest request, String userId) {
    PaymentAuthorizationResponse gatewayResponse = bankGatewayClient.authorize(request);

    if (!PaymentTransaction.RG001_APPROVED_STATUS.equals(gatewayResponse.status())) {
      throw new PaymentGatewayException(
          "Bank authorization rejected under RG001 with status: " + gatewayResponse.status());
    }

    String authorizationCode =
        hasText(gatewayResponse.authorizationCode())
            ? gatewayResponse.authorizationCode()
            : "AUTH-" + UUID.randomUUID();
    String transactionId =
        hasText(gatewayResponse.transactionId())
            ? gatewayResponse.transactionId()
            : "TXN-" + UUID.randomUUID();

    PaymentTransaction transaction =
        PaymentTransaction.builder()
            .transactionId(transactionId)
            .reference(request.reference().trim())
            .amount(request.amount())
            .currency(request.currency().trim().toUpperCase(Locale.ROOT))
            .status(PaymentTransaction.RG001_APPROVED_STATUS)
            .authorizationCode(authorizationCode)
            .paymentMethodToken(request.paymentToken().trim())
            .userId(userId)
            .createdAt(parseInstantOrDefault(gatewayResponse.timestamp()))
            .build();

    PaymentTransaction saved = paymentTransactionRepository.save(transaction);

    if (transactionEventPublisher != null) {
      Double amountValue = saved.getAmount() != null ? saved.getAmount().doubleValue() : null;
      transactionEventPublisher.publishTransactionApproved(
          new TransactionApprovedKafkaEvent(
              saved.getUserId(),
              saved.getTransactionId(),
              saved.getReference(),
              amountValue,
              saved.getCurrency(),
              saved.getCreatedAt()));
    }

    if (log.isInfoEnabled()) {
      log.info(
          "Payment authorized under RG001: reference={}, transactionId={}, authorizationCode={}, amount={} {}",
          saved.getReference(),
          saved.getTransactionId(),
          saved.getAuthorizationCode(),
          saved.getAmount(),
          saved.getCurrency());
    }

    return PaymentAuthorizationResponse.fromEntity(saved);
  }

  @Transactional(readOnly = true)
  public PaymentAuthorizationResponse getByReference(String reference) {
    return paymentTransactionRepository
        .findFirstByReferenceOrderByCreatedAtDesc(reference)
        .map(PaymentAuthorizationResponse::fromEntity)
        .orElseThrow(() -> new PaymentNotFoundException(reference));
  }

  @Transactional(readOnly = true)
  public List<UserPaymentTransactionResponse> getUserTransactions(
      String userId, Instant from, Instant to) {
    if (!hasText(userId)) {
      throw new IllegalArgumentException("User ID must not be blank");
    }
    if (from == null || to == null) {
      throw new IllegalArgumentException("Both 'from' and 'to' date parameters are required");
    }
    if (from.isAfter(to)) {
      throw new IllegalArgumentException(
          "The 'from' timestamp must be before or equal to 'to' timestamp");
    }

    return paymentTransactionRepository
        .findByUserIdAndStatusAndCreatedAtBetweenOrderByCreatedAtDesc(
            userId.trim(), PaymentTransaction.RG001_APPROVED_STATUS, from, to)
        .stream()
        .map(UserPaymentTransactionResponse::fromEntity)
        .toList();
  }

  @Transactional(readOnly = true)
  public String getUserIdByReference(String reference) {
    return paymentTransactionRepository
        .findFirstByReferenceOrderByCreatedAtDesc(reference)
        .map(PaymentTransaction::getUserId)
        .orElse(null);
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private Instant parseInstantOrDefault(String timestamp) {
    if (!hasText(timestamp)) {
      return Instant.now();
    }
    try {
      return Instant.parse(timestamp);
    } catch (DateTimeParseException ex) {
      if (log.isInfoEnabled()) {
        log.info(
            "Invalid gateway timestamp '{}', falling back to current UTC time: {}",
            timestamp,
            ex.getMessage());
      }
      return Instant.now();
    }
  }
}
