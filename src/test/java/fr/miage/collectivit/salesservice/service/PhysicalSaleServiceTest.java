package fr.miage.collectivit.salesservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.miage.collectivit.salesservice.client.LoyaltyRateClient;
import fr.miage.collectivit.salesservice.dto.MerchantLoyaltyRateDto;
import fr.miage.collectivit.salesservice.dto.PhysicalSaleRequest;
import fr.miage.collectivit.salesservice.dto.PhysicalSaleResponse;
import fr.miage.collectivit.salesservice.dto.event.PhysicalPaymentCompletedEvent;
import fr.miage.collectivit.salesservice.event.PhysicalPaymentEventPublisher;
import fr.miage.collectivit.salesservice.event.TransactionApprovedKafkaEvent;
import fr.miage.collectivit.salesservice.event.TransactionEventPublisher;
import fr.miage.collectivit.salesservice.exception.PhysicalSaleNotFoundException;
import fr.miage.collectivit.salesservice.model.PhysicalSaleTransaction;
import fr.miage.collectivit.salesservice.repository.PhysicalSaleTransactionRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PhysicalSaleService Unit Tests")
class PhysicalSaleServiceTest {

  @Mock private PhysicalSaleTransactionRepository repository;
  @Mock private LoyaltyRateClient loyaltyRateClient;
  @Mock private TransactionEventPublisher transactionEventPublisher;
  @Mock private PhysicalPaymentEventPublisher physicalPaymentEventPublisher;

  private PhysicalSaleService physicalSaleService;

  @BeforeEach
  void setUp() {
    physicalSaleService =
        new PhysicalSaleService(
            repository,
            loyaltyRateClient,
            transactionEventPublisher,
            physicalPaymentEventPublisher);
  }

  @Test
  @DisplayName("Should record physical sale and calculate points accurately with custom rate")
  void shouldRecordPhysicalSaleWithCustomRate() {
    UUID storeUid = UUID.fromString("11111111-1111-1111-1111-111111111010");
    MerchantLoyaltyRateDto customRate =
        new MerchantLoyaltyRateDto(storeUid, new BigDecimal("2.00"), new BigDecimal("1.0"), true);
    when(loyaltyRateClient.getStoreLoyaltyRate(storeUid)).thenReturn(customRate);

    PhysicalSaleRequest request =
        new PhysicalSaleRequest(
            storeUid, "k7P9xQ2", "cust-123", new BigDecimal("25.50"), "EUR", "CASH");

    when(repository.save(any(PhysicalSaleTransaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    PhysicalSaleResponse response =
        physicalSaleService.recordPhysicalSale(request, "merchant-user-456");

    assertThat(response).isNotNull();
    assertThat(response.uid()).isNotNull();
    assertThat(response.storeUid()).isEqualTo(storeUid);
    assertThat(response.clientCode()).isEqualTo("K7P9XQ2");
    assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("25.50"));
    // 25.50 * 1.0 / 2.00 = 12.75 -> floor to nearest 0.5 step is 12.5 points
    assertThat(response.pointsEarned()).isEqualByComparingTo(new BigDecimal("12.5"));
    assertThat(response.status()).isEqualTo("COMPLETED");
    assertThat(response.transactionReference()).startsWith("PHYS-");

    ArgumentCaptor<PhysicalSaleTransaction> captor =
        ArgumentCaptor.forClass(PhysicalSaleTransaction.class);
    verify(repository).save(captor.capture());
    PhysicalSaleTransaction saved = captor.getValue();
    assertThat(saved.getMerchantId()).isEqualTo("merchant-user-456");
    assertThat(saved.getPointsRateApplied()).isEqualTo("2.00 EUR = 1.0 PTS");

    ArgumentCaptor<PhysicalPaymentCompletedEvent> eventCaptor =
        ArgumentCaptor.forClass(PhysicalPaymentCompletedEvent.class);
    verify(physicalPaymentEventPublisher).publishPhysicalPaymentCompleted(eventCaptor.capture());
    PhysicalPaymentCompletedEvent publishedEvent = eventCaptor.getValue();
    assertThat(publishedEvent.transactionReference()).isEqualTo(response.transactionReference());
    assertThat(publishedEvent.storeUid()).isEqualTo(storeUid);
    assertThat(publishedEvent.clientCode()).isEqualTo("K7P9XQ2");
    assertThat(publishedEvent.customerId()).isEqualTo("cust-123");
    assertThat(publishedEvent.amount()).isEqualByComparingTo(new BigDecimal("25.50"));
    assertThat(publishedEvent.currency()).isEqualTo("EUR");

    ArgumentCaptor<TransactionApprovedKafkaEvent> txCaptor =
        ArgumentCaptor.forClass(TransactionApprovedKafkaEvent.class);
    verify(transactionEventPublisher).publishTransactionApproved(txCaptor.capture());
    TransactionApprovedKafkaEvent txEvent = txCaptor.getValue();
    assertThat(txEvent.transactionId()).isEqualTo(saved.getUid().toString());
    assertThat(UUID.fromString(txEvent.transactionId())).isEqualTo(saved.getUid());
  }

  @Test
  @DisplayName("Should record physical sale using default loyalty rate (1 EUR = 1 PT)")
  void shouldRecordPhysicalSaleWithDefaultRate() {
    UUID storeUid = UUID.fromString("11111111-1111-1111-1111-111111111005");
    MerchantLoyaltyRateDto defaultRate = MerchantLoyaltyRateDto.defaultForStore(storeUid);
    when(loyaltyRateClient.getStoreLoyaltyRate(storeUid)).thenReturn(defaultRate);

    PhysicalSaleRequest request =
        new PhysicalSaleRequest(storeUid, "ABC1234", null, new BigDecimal("42.00"), "EUR", "CARD");

    when(repository.save(any(PhysicalSaleTransaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    PhysicalSaleResponse response = physicalSaleService.recordPhysicalSale(request, null);

    assertThat(response).isNotNull();
    assertThat(response.pointsEarned()).isEqualByComparingTo(new BigDecimal("42.0"));
    assertThat(response.merchantId()).isEqualTo("ANONYMOUS_MERCHANT");
  }

  @Test
  @DisplayName("Should calculate points with half-point granularity (0.5 steps)")
  void shouldCalculatePointsWithHalfPointGranularity() {
    MerchantLoyaltyRateDto rate =
        new MerchantLoyaltyRateDto(
            UUID.randomUUID(), new BigDecimal("1.00"), new BigDecimal("0.5"), true);

    // 10.00 EUR * 0.5 = 5.0 points
    BigDecimal points1 = physicalSaleService.calculatePoints(new BigDecimal("10.00"), rate);
    assertThat(points1).isEqualByComparingTo(new BigDecimal("5.0"));

    // 3.00 EUR * 0.5 = 1.5 points
    BigDecimal points2 = physicalSaleService.calculatePoints(new BigDecimal("3.00"), rate);
    assertThat(points2).isEqualByComparingTo(new BigDecimal("1.5"));

    // 3.90 EUR * 0.5 = 1.95 -> floor(1.95 * 2)/2 = floor(3.9)/2 = 3/2 = 1.5 points
    BigDecimal points3 = physicalSaleService.calculatePoints(new BigDecimal("3.90"), rate);
    assertThat(points3).isEqualByComparingTo(new BigDecimal("1.5"));
  }

  @Test
  @DisplayName("Should retrieve physical sale transaction by reference")
  void shouldRetrieveByReference() {
    String ref = "PHYS-20261110-XYZ123";
    PhysicalSaleTransaction entity =
        PhysicalSaleTransaction.builder()
            .uid(UUID.randomUUID())
            .transactionReference(ref)
            .storeUid(UUID.randomUUID())
            .merchantId("m1")
            .clientCode("CL123")
            .amount(new BigDecimal("15.00"))
            .currency("EUR")
            .pointsEarned(new BigDecimal("15.0"))
            .pointsRateApplied("1.00 EUR = 1.0 PTS")
            .status("COMPLETED")
            .build();

    when(repository.findByTransactionReference(ref)).thenReturn(Optional.of(entity));

    PhysicalSaleResponse response = physicalSaleService.getByReference(ref);
    assertThat(response.transactionReference()).isEqualTo(ref);
    assertThat(response.pointsEarned()).isEqualByComparingTo("15.0");
  }

  @Test
  @DisplayName("Should throw PhysicalSaleNotFoundException when transaction does not exist")
  void shouldThrowWhenNotFound() {
    when(repository.findByTransactionReference("UNKNOWN")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> physicalSaleService.getByReference("UNKNOWN"))
        .isInstanceOf(PhysicalSaleNotFoundException.class)
        .hasMessageContaining("UNKNOWN");
  }
}
