package fr.miage.collectivit.salesservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.miage.collectivit.salesservice.client.LoyaltyHoldClient;
import fr.miage.collectivit.salesservice.client.StoreClient;
import fr.miage.collectivit.salesservice.dto.CreateOrderItemRequest;
import fr.miage.collectivit.salesservice.dto.CreateOrderRequest;
import fr.miage.collectivit.salesservice.dto.OrderResponse;
import fr.miage.collectivit.salesservice.model.OrderItemType;
import fr.miage.collectivit.salesservice.repository.OrderRepository;
import fr.miage.collectivit.salesservice.repository.PaymentTransactionRepository;
import fr.miage.collectivit.salesservice.repository.WalletTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderProcessingGiftTest {

  @Mock private OrderRepository orderRepository;
  @Mock private StoreClient storeClient;
  @Mock private ApplicationEventPublisher applicationEventPublisher;
  @Mock private PaymentTransactionRepository paymentTransactionRepository;
  @Mock private WalletTransactionRepository walletTransactionRepository;
  @Mock private LoyaltyHoldClient loyaltyHoldClient;

  private OrderProcessingService service;

  private final UUID storeUid = UUID.randomUUID();
  private final UUID giftUid = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new OrderProcessingService(
            orderRepository,
            storeClient,
            applicationEventPublisher,
            paymentTransactionRepository,
            walletTransactionRepository);
    ReflectionTestUtils.setField(service, "loyaltyHoldClient", loyaltyHoldClient);
  }

  @Test
  void createOrderWithZeroEuroGiftCreatesHoldAndZeroTotal() {
    when(loyaltyHoldClient.createHold(any()))
        .thenReturn(
            new LoyaltyHoldClient.HoldResponse(
                UUID.randomUUID(),
                "CMD-1",
                "user-1",
                new BigDecimal("150.0"),
                "ACTIVE",
                Instant.now().plusSeconds(1800),
                List.of(new LoyaltyHoldClient.HoldItemResponse(storeUid, giftUid, "Mug", 150, 1))));
    when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    OrderResponse response =
        service.createOrder(
            new CreateOrderRequest(
                List.of(
                    new CreateOrderItemRequest(
                        storeUid,
                        "Boutique",
                        giftUid,
                        "Mug",
                        BigDecimal.ZERO,
                        1,
                        OrderItemType.GIFT)),
                null,
                "client@test.fr"),
            "user-1",
            "client@test.fr");

    assertThat(response.totalAmount()).isEqualByComparingTo("0.00");
    assertThat(response.totalPointsCost()).isEqualTo(150);
    assertThat(response.items()).hasSize(1);
    assertThat(response.items().get(0).itemType()).isEqualTo(OrderItemType.GIFT);
    verify(loyaltyHoldClient).createHold(any());
  }

  @Test
  void createOrderRejectsGiftWithNonZeroPrice() {
    assertThatThrownBy(
            () ->
                service.createOrder(
                    new CreateOrderRequest(
                        List.of(
                            new CreateOrderItemRequest(
                                storeUid,
                                "Boutique",
                                giftUid,
                                "Mug",
                                new BigDecimal("5.00"),
                                1,
                                OrderItemType.GIFT)),
                        null,
                        "client@test.fr"),
                    "user-1",
                    "client@test.fr"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("0.00 EUR");
  }
}
