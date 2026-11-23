package fr.miage.collectivit.salesservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.miage.collectivit.salesservice.client.BankGatewayClient;
import fr.miage.collectivit.salesservice.dto.PaymentAuthorizationRequest;
import fr.miage.collectivit.salesservice.dto.PaymentAuthorizationResponse;
import fr.miage.collectivit.salesservice.event.TransactionApprovedKafkaEvent;
import fr.miage.collectivit.salesservice.event.TransactionEventPublisher;
import fr.miage.collectivit.salesservice.model.PaymentTransaction;
import fr.miage.collectivit.salesservice.repository.PaymentTransactionRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Unit Tests")
class PaymentServiceTest {

  @Mock private BankGatewayClient bankGatewayClient;
  @Mock private PaymentTransactionRepository paymentTransactionRepository;
  @Mock private TransactionEventPublisher transactionEventPublisher;

  private PaymentService paymentService;

  @BeforeEach
  void setUp() {
    paymentService =
        new PaymentService(
            bankGatewayClient, paymentTransactionRepository, transactionEventPublisher);
  }

  @Test
  @DisplayName(
      "Should publish TransactionApprovedKafkaEvent with PaymentTransaction uid UUID as transactionId")
  void shouldPublishTransactionApprovedWithPaymentUid() {
    String userUuid = UUID.randomUUID().toString();
    PaymentAuthorizationRequest request =
        new PaymentAuthorizationRequest(
            new BigDecimal("42.50"), "EUR", "CMD-CC-20261123-0001", "tok_visa_4242");

    PaymentAuthorizationResponse gatewayResponse =
        new PaymentAuthorizationResponse(
            PaymentTransaction.RG001_APPROVED_STATUS,
            "AUTH-1234",
            "TXN-GATEWAY-999",
            new BigDecimal("42.50"),
            "EUR",
            "CMD-CC-20261123-0001",
            "2026-11-23T14:00:00Z",
            PaymentTransaction.RG001_RULE_CODE);

    when(bankGatewayClient.authorize(request)).thenReturn(gatewayResponse);
    when(paymentTransactionRepository.save(any(PaymentTransaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    paymentService.authorizePayment(request, userUuid);

    ArgumentCaptor<PaymentTransaction> savedCaptor =
        ArgumentCaptor.forClass(PaymentTransaction.class);
    verify(paymentTransactionRepository).save(savedCaptor.capture());
    PaymentTransaction saved = savedCaptor.getValue();
    assertThat(saved.getUid()).isNotNull();

    ArgumentCaptor<TransactionApprovedKafkaEvent> eventCaptor =
        ArgumentCaptor.forClass(TransactionApprovedKafkaEvent.class);
    verify(transactionEventPublisher).publishTransactionApproved(eventCaptor.capture());
    TransactionApprovedKafkaEvent published = eventCaptor.getValue();

    assertThat(published.userId()).isEqualTo(userUuid);
    assertThat(published.transactionId()).isEqualTo(saved.getUid().toString());
    assertThat(UUID.fromString(published.transactionId())).isEqualTo(saved.getUid());
    assertThat(published.transactionId()).doesNotStartWith("TXN-");
  }
}
