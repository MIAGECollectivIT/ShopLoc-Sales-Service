package fr.miage.collectivit.salesservice.service;

import fr.miage.collectivit.salesservice.client.LoyaltyRateClient;
import fr.miage.collectivit.salesservice.dto.MerchantLoyaltyRateDto;
import fr.miage.collectivit.salesservice.dto.PhysicalSaleRequest;
import fr.miage.collectivit.salesservice.dto.PhysicalSaleResponse;
import fr.miage.collectivit.salesservice.event.TransactionApprovedKafkaEvent;
import fr.miage.collectivit.salesservice.event.TransactionEventPublisher;
import fr.miage.collectivit.salesservice.exception.PhysicalSaleNotFoundException;
import fr.miage.collectivit.salesservice.model.PhysicalSaleTransaction;
import fr.miage.collectivit.salesservice.repository.PhysicalSaleTransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class PhysicalSaleService {

  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

  private final PhysicalSaleTransactionRepository physicalSaleTransactionRepository;
  private final LoyaltyRateClient loyaltyRateClient;
  private final TransactionEventPublisher transactionEventPublisher;

  public PhysicalSaleService(
      PhysicalSaleTransactionRepository physicalSaleTransactionRepository,
      LoyaltyRateClient loyaltyRateClient) {
    this(physicalSaleTransactionRepository, loyaltyRateClient, null);
  }

  @Autowired
  public PhysicalSaleService(
      PhysicalSaleTransactionRepository physicalSaleTransactionRepository,
      LoyaltyRateClient loyaltyRateClient,
      @Autowired(required = false) TransactionEventPublisher transactionEventPublisher) {
    this.physicalSaleTransactionRepository = physicalSaleTransactionRepository;
    this.loyaltyRateClient = loyaltyRateClient;
    this.transactionEventPublisher = transactionEventPublisher;
  }

  @Transactional
  @SuppressWarnings("PMD.LawOfDemeter")
  public PhysicalSaleResponse recordPhysicalSale(PhysicalSaleRequest request, String callerUserId) {
    MerchantLoyaltyRateDto loyaltyRate = loyaltyRateClient.getStoreLoyaltyRate(request.storeId());

    BigDecimal normalizedAmount = request.amount().setScale(2, RoundingMode.HALF_UP);
    BigDecimal pointsEarned = calculatePoints(normalizedAmount, loyaltyRate);
    String rateApplied =
        String.format(
            Locale.ROOT,
            "%.2f EUR = %.1f PTS",
            loyaltyRate.euroAmount(),
            loyaltyRate.pointsReward());

    String transactionReference = generateTransactionReference();
    String effectiveMerchantId = hasText(callerUserId) ? callerUserId.trim() : "ANONYMOUS_MERCHANT";
    String currency =
        hasText(request.currency()) ? request.currency().trim().toUpperCase(Locale.ROOT) : "EUR";
    String paymentMethod =
        hasText(request.paymentMethod())
            ? request.paymentMethod().trim().toUpperCase(Locale.ROOT)
            : "PHYSICAL";

    PhysicalSaleTransaction transaction =
        PhysicalSaleTransaction.builder()
            .transactionReference(transactionReference)
            .storeId(request.storeId())
            .merchantId(effectiveMerchantId)
            .clientCode(request.clientCode().trim().toUpperCase(Locale.ROOT))
            .customerId(hasText(request.customerId()) ? request.customerId().trim() : null)
            .amount(normalizedAmount)
            .currency(currency)
            .pointsEarned(pointsEarned)
            .pointsRateApplied(rateApplied)
            .paymentMethod(paymentMethod)
            .status(PhysicalSaleTransaction.DEFAULT_STATUS)
            .createdAt(Instant.now())
            .build();

    PhysicalSaleTransaction saved = physicalSaleTransactionRepository.save(transaction);

    if (transactionEventPublisher != null && hasText(saved.getCustomerId())) {
      Double amountValue = saved.getAmount() != null ? saved.getAmount().doubleValue() : null;
      transactionEventPublisher.publishTransactionApproved(
          new TransactionApprovedKafkaEvent(
              saved.getCustomerId().trim(),
              saved.getTransactionReference(),
              saved.getTransactionReference(),
              amountValue,
              saved.getCurrency(),
              saved.getCreatedAt()));
    }

    if (log.isInfoEnabled()) {
      log.info(
          "Physical in-store sale recorded: reference={}, storeId={}, clientCode={}, amount={} {}, pointsEarned={} ({})",
          saved.getTransactionReference(),
          saved.getStoreId(),
          saved.getClientCode(),
          saved.getAmount(),
          saved.getCurrency(),
          saved.getPointsEarned(),
          saved.getPointsRateApplied());
    }

    return PhysicalSaleResponse.fromEntity(saved);
  }

  @Transactional(readOnly = true)
  public PhysicalSaleResponse getByReference(String reference) {
    return physicalSaleTransactionRepository
        .findByTransactionReference(reference)
        .map(PhysicalSaleResponse::fromEntity)
        .orElseThrow(() -> new PhysicalSaleNotFoundException(reference));
  }

  public BigDecimal calculatePoints(BigDecimal amount, MerchantLoyaltyRateDto rate) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ZERO.setScale(1, RoundingMode.UNNECESSARY);
    }
    BigDecimal euroAmount = rate.euroAmount();
    BigDecimal pointsReward = rate.pointsReward();
    if (euroAmount == null
        || euroAmount.compareTo(BigDecimal.ZERO) <= 0
        || pointsReward == null
        || pointsReward.compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ZERO.setScale(1, RoundingMode.UNNECESSARY);
    }

    BigDecimal rawPoints =
        amount.multiply(pointsReward).divide(euroAmount, 4, RoundingMode.HALF_UP);
    BigDecimal halfSteps =
        rawPoints.multiply(BigDecimal.valueOf(2)).setScale(0, RoundingMode.FLOOR);
    return halfSteps.divide(BigDecimal.valueOf(2), 1, RoundingMode.UNNECESSARY);
  }

  private String generateTransactionReference() {
    String datePart = DATE_FORMATTER.format(Instant.now());
    String randomSuffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    return "PHYS-" + datePart + "-" + randomSuffix;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
