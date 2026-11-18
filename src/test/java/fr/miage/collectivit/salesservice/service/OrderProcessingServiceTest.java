package fr.miage.collectivit.salesservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.miage.collectivit.salesservice.client.StoreClient;
import fr.miage.collectivit.salesservice.dto.CreateOrderItemRequest;
import fr.miage.collectivit.salesservice.dto.CreateOrderRequest;
import fr.miage.collectivit.salesservice.dto.OrderResponse;
import fr.miage.collectivit.salesservice.dto.event.OrderPaidKafkaEvent;
import fr.miage.collectivit.salesservice.event.OrderPaymentEventPublisher;
import fr.miage.collectivit.salesservice.exception.OrderNotFoundException;
import fr.miage.collectivit.salesservice.model.Order;
import fr.miage.collectivit.salesservice.model.OrderItem;
import fr.miage.collectivit.salesservice.model.OrderStatus;
import fr.miage.collectivit.salesservice.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderProcessingService Unit Tests (SLE-186)")
class OrderProcessingServiceTest {

  @Mock private OrderRepository orderRepository;
  @Mock private StoreClient storeClient;
  @Mock private ApplicationEventPublisher applicationEventPublisher;
  @Mock private OrderPaymentEventPublisher orderPaymentEventPublisher;

  private OrderProcessingService orderProcessingService;

  @BeforeEach
  void setUp() {
    orderProcessingService =
        new OrderProcessingService(
            orderRepository, storeClient, applicationEventPublisher, orderPaymentEventPublisher);
  }

  @Test
  @DisplayName(
      "Should create multi-store order with calculated amounts, breakdown and PENDING_PAYMENT status")
  void shouldCreateOrderSuccessfully() {
    CreateOrderItemRequest item1 =
        new CreateOrderItemRequest(
            1L, "Boulangerie Le Moulin", 2L, "Baguette de Tradition", new BigDecimal("1.35"), 2);
    CreateOrderItemRequest item2 =
        new CreateOrderItemRequest(
            1L, "Boulangerie Le Moulin", 3L, "Croissant Pur Beurre", new BigDecimal("1.60"), 1);
    CreateOrderItemRequest item3 =
        new CreateOrderItemRequest(
            2L, "Librairie Le Furet", 6L, "Roman Best-Seller", new BigDecimal("22.00"), 1);

    CreateOrderRequest request =
        new CreateOrderRequest(List.of(item1, item2, item3), "CLI-99482", "client@example.com");

    when(orderRepository.save(any(Order.class)))
        .thenAnswer(
            invocation -> {
              Order o = invocation.getArgument(0);
              o.setId(101L);
              return o;
            });

    OrderResponse response =
        orderProcessingService.createOrder(request, "user-42", "auth@example.com");

    assertThat(response).isNotNull();
    assertThat(response.id()).isEqualTo(101L);
    assertThat(response.orderReference()).startsWith("CMD-CC-");
    assertThat(response.status()).isEqualTo(OrderStatus.PENDING_PAYMENT.name());
    // Total calculation: (1.35 * 2) + (1.60 * 1) + (22.00 * 1) = 2.70 + 1.60 + 22.00 = 26.30
    assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("26.30"));
    assertThat(response.currency()).isEqualTo("EUR");
    assertThat(response.expiresAt()).isAfter(Instant.now());
    assertThat(response.customerId()).isEqualTo("user-42");
    assertThat(response.customerEmail()).isEqualTo("auth@example.com");

    // Store summaries check
    assertThat(response.storeSummaries()).hasSize(2);

    // Store 1 breakdown
    var store1 =
        response.storeSummaries().stream()
            .filter(s -> s.storeId().equals(1L))
            .findFirst()
            .orElseThrow();
    assertThat(store1.storeName()).isEqualTo("Boulangerie Le Moulin");
    assertThat(store1.subtotal()).isEqualByComparingTo(new BigDecimal("4.30"));
    assertThat(store1.itemCount()).isEqualTo(3);
    assertThat(store1.items()).hasSize(2);

    // Store 2 breakdown
    var store2 =
        response.storeSummaries().stream()
            .filter(s -> s.storeId().equals(2L))
            .findFirst()
            .orElseThrow();
    assertThat(store2.storeName()).isEqualTo("Librairie Le Furet");
    assertThat(store2.subtotal()).isEqualByComparingTo(new BigDecimal("22.00"));
    assertThat(store2.itemCount()).isEqualTo(1);
    assertThat(store2.items()).hasSize(1);

    ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
    verify(orderRepository).save(orderCaptor.capture());
    Order captured = orderCaptor.getValue();
    assertThat(captured.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    assertThat(captured.getItems()).hasSize(3);

    verify(storeClient).updateStock(1L, 2L, -2);
    verify(storeClient).updateStock(1L, 3L, -1);
    verify(storeClient).updateStock(2L, 6L, -1);
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException when items list is empty or null")
  void shouldRejectOrderWithEmptyItems() {
    CreateOrderRequest request = new CreateOrderRequest(List.of(), null, null);

    assertThatThrownBy(() -> orderProcessingService.createOrder(request, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one item");
  }

  @Test
  @DisplayName("Should retrieve order by reference")
  void shouldGetOrderByReference() {
    Order order =
        Order.builder()
            .id(50L)
            .orderReference("CMD-CC-20261110-1234")
            .customerId("user-1")
            .customerEmail("client@example.com")
            .totalAmount(new BigDecimal("15.50"))
            .currency("EUR")
            .status(OrderStatus.PENDING_PAYMENT)
            .expiresAt(Instant.now().plusSeconds(1800))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .items(new ArrayList<>())
            .build();

    OrderItem item =
        OrderItem.builder()
            .id(1L)
            .storeId(1L)
            .storeName("Le Moulin")
            .productId(2L)
            .productName("Pain")
            .unitPrice(new BigDecimal("15.50"))
            .quantity(1)
            .totalPrice(new BigDecimal("15.50"))
            .build();
    order.addItem(item);

    when(orderRepository.findByOrderReference("CMD-CC-20261110-1234"))
        .thenReturn(Optional.of(order));

    OrderResponse response = orderProcessingService.getOrderByReference("CMD-CC-20261110-1234");

    assertThat(response.orderReference()).isEqualTo("CMD-CC-20261110-1234");
    assertThat(response.status()).isEqualTo("PENDING_PAYMENT");
    assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("15.50"));
    assertThat(response.items()).hasSize(1);
  }

  @Test
  @DisplayName("Should throw OrderNotFoundException when order reference does not exist")
  void shouldThrowWhenOrderNotFound() {
    when(orderRepository.findByOrderReference("UNKNOWN")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderProcessingService.getOrderByReference("UNKNOWN"))
        .isInstanceOf(OrderNotFoundException.class)
        .hasMessageContaining("UNKNOWN");
  }

  @Test
  @DisplayName("Should update order status to PAID")
  void shouldUpdateOrderStatusToPaid() {
    Order order =
        Order.builder()
            .id(50L)
            .orderReference("CMD-CC-20261110-1234")
            .status(OrderStatus.PENDING_PAYMENT)
            .totalAmount(new BigDecimal("15.50"))
            .currency("EUR")
            .expiresAt(Instant.now().plusSeconds(1800))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .items(new ArrayList<>())
            .build();

    when(orderRepository.findByOrderReference("CMD-CC-20261110-1234"))
        .thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

    OrderResponse response =
        orderProcessingService.updateOrderStatus("CMD-CC-20261110-1234", "PAID");

    assertThat(response.status()).isEqualTo("PAID");
    verify(orderRepository).save(order);
  }

  @Test
  @DisplayName(
      "Should publish Kafka order paid event with store breakdowns when status changes to PAID (SLE-220)")
  void shouldPublishKafkaOrderPaidEventWhenStatusChangesToPaid() {
    OrderItem item1 =
        OrderItem.builder()
            .storeId(10L)
            .storeName("Boulangerie")
            .productId(1L)
            .productName("Pain")
            .quantity(2)
            .unitPrice(new BigDecimal("1.50"))
            .totalPrice(new BigDecimal("3.00"))
            .build();
    OrderItem item2 =
        OrderItem.builder()
            .storeId(20L)
            .storeName("Boucherie")
            .productId(2L)
            .productName("Steak")
            .quantity(1)
            .unitPrice(new BigDecimal("12.00"))
            .totalPrice(new BigDecimal("12.00"))
            .build();
    OrderItem item3 =
        OrderItem.builder()
            .storeId(10L)
            .storeName("Boulangerie")
            .productId(3L)
            .productName("Croissant")
            .quantity(2)
            .unitPrice(new BigDecimal("1.00"))
            .totalPrice(new BigDecimal("2.00"))
            .build();

    Order order =
        Order.builder()
            .orderReference("CMD-CC-PAID-001")
            .customerId("user-777")
            .clientCode("k7P9xQ2")
            .status(OrderStatus.PENDING_PAYMENT)
            .totalAmount(new BigDecimal("17.00"))
            .currency("EUR")
            .items(new ArrayList<>(List.of(item1, item2, item3)))
            .build();

    when(orderRepository.findByOrderReference("CMD-CC-PAID-001")).thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

    orderProcessingService.updateOrderStatus("CMD-CC-PAID-001", "PAID");

    ArgumentCaptor<OrderPaidKafkaEvent> captor = ArgumentCaptor.forClass(OrderPaidKafkaEvent.class);
    verify(orderPaymentEventPublisher).publishOrderPaid(captor.capture());

    OrderPaidKafkaEvent captured = captor.getValue();
    assertThat(captured.orderReference()).isEqualTo("CMD-CC-PAID-001");
    assertThat(captured.customerId()).isEqualTo("user-777");
    assertThat(captured.clientCode()).isEqualTo("k7P9xQ2");
    assertThat(captured.totalAmount()).isEqualByComparingTo(new BigDecimal("17.00"));
    assertThat(captured.currency()).isEqualTo("EUR");
    assertThat(captured.stores()).hasSize(2);

    var store10 =
        captured.stores().stream().filter(s -> s.storeId().equals(10L)).findFirst().orElseThrow();
    assertThat(store10.storeName()).isEqualTo("Boulangerie");
    assertThat(store10.amount()).isEqualByComparingTo(new BigDecimal("5.00"));

    var store20 =
        captured.stores().stream().filter(s -> s.storeId().equals(20L)).findFirst().orElseThrow();
    assertThat(store20.storeName()).isEqualTo("Boucherie");
    assertThat(store20.amount()).isEqualByComparingTo(new BigDecimal("12.00"));
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException when updating status with unknown value")
  void shouldThrowWhenUpdatingInvalidStatus() {
    Order order =
        Order.builder().orderReference("CMD-CC-1").status(OrderStatus.PENDING_PAYMENT).build();

    when(orderRepository.findByOrderReference("CMD-CC-1")).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderProcessingService.updateOrderStatus("CMD-CC-1", "INVALID_STATUS"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid order status");
  }

  @Test
  @DisplayName("Should update order status to CANCELLED and EXPIRED and release stock")
  void shouldTransitionOrderStatusToCancelledAndExpired() {
    OrderItem item =
        OrderItem.builder()
            .storeId(1L)
            .productId(10L)
            .productName("Item")
            .quantity(3)
            .unitPrice(new BigDecimal("5.00"))
            .totalPrice(new BigDecimal("15.00"))
            .build();
    Order order =
        Order.builder()
            .orderReference("CMD-CC-2")
            .status(OrderStatus.PENDING_PAYMENT)
            .items(new ArrayList<>(List.of(item)))
            .build();

    when(orderRepository.findByOrderReference("CMD-CC-2")).thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

    OrderResponse cancelled = orderProcessingService.updateOrderStatus("CMD-CC-2", "CANCELLED");
    assertThat(cancelled.status()).isEqualTo("CANCELLED");
    verify(storeClient).updateStock(1L, 10L, 3);

    Order order2 =
        Order.builder()
            .orderReference("CMD-CC-3")
            .status(OrderStatus.PENDING_PAYMENT)
            .items(new ArrayList<>(List.of(item)))
            .build();
    when(orderRepository.findByOrderReference("CMD-CC-3")).thenReturn(Optional.of(order2));

    OrderResponse expired = orderProcessingService.updateOrderStatus("CMD-CC-3", "EXPIRED");
    assertThat(expired.status()).isEqualTo("EXPIRED");
  }

  @Test
  @DisplayName(
      "Should throw IllegalStateException when transitioning from terminal CANCELLED status")
  void shouldThrowWhenTransitioningFromTerminalStatus() {
    Order cancelledOrder =
        Order.builder()
            .orderReference("CMD-CC-TERMINAL")
            .status(OrderStatus.CANCELLED)
            .items(new ArrayList<>())
            .build();

    when(orderRepository.findByOrderReference("CMD-CC-TERMINAL"))
        .thenReturn(Optional.of(cancelledOrder));

    assertThatThrownBy(() -> orderProcessingService.updateOrderStatus("CMD-CC-TERMINAL", "PAID"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Cannot change status of an order");
  }

  @Test
  @DisplayName("Should reject order creation when unit price does not match store price")
  void shouldRejectOrderCreationWhenPriceMismatched() {
    CreateOrderItemRequest tamperedItem =
        new CreateOrderItemRequest(
            1L, "Store 1", 10L, "Expensive Product", new BigDecimal("0.01"), 1);
    CreateOrderRequest request =
        new CreateOrderRequest(List.of(tamperedItem), "CLI-1", "test@test.fr");

    when(storeClient.getProductPrice(1L, 10L)).thenReturn(Optional.of(new BigDecimal("59.99")));

    assertThatThrownBy(() -> orderProcessingService.createOrder(request, "cust-1", "test@test.fr"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Price mismatch");
  }

  @Test
  @DisplayName("Should reject order creation when unit price is null or non-positive")
  void shouldRejectOrderCreationWhenUnitPriceNonPositive() {
    CreateOrderItemRequest zeroPriceItem =
        new CreateOrderItemRequest(1L, "Store 1", 10L, "Free Product", BigDecimal.ZERO, 1);
    CreateOrderRequest request =
        new CreateOrderRequest(List.of(zeroPriceItem), "CLI-1", "test@test.fr");

    assertThatThrownBy(() -> orderProcessingService.createOrder(request, "cust-1", "test@test.fr"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unit price must be strictly positive");
  }

  @Test
  @DisplayName("Should rollback previously reserved items when subsequent reservation fails")
  void shouldRollbackStockWhenReservationFails() {
    CreateOrderItemRequest item1 =
        new CreateOrderItemRequest(1L, "Store 1", 101L, "P1", new BigDecimal("5.00"), 2);
    CreateOrderItemRequest item2 =
        new CreateOrderItemRequest(1L, "Store 1", 102L, "P2", new BigDecimal("10.00"), 1);

    CreateOrderRequest request = new CreateOrderRequest(List.of(item1, item2), "CLI-1", null);

    doNothing().when(storeClient).updateStock(1L, 101L, -2);
    doNothing().when(storeClient).updateStock(1L, 101L, 2);
    doThrow(new RuntimeException("Insufficient stock on P2"))
        .when(storeClient)
        .updateStock(1L, 102L, -1);

    assertThatThrownBy(() -> orderProcessingService.createOrder(request, "user-1", "user@test.fr"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Insufficient stock on P2");

    // item1 was reserved (-2), then rolled back (+2)
    verify(storeClient).updateStock(1L, 101L, -2);
    verify(storeClient).updateStock(1L, 101L, 2);
  }

  @Test
  @DisplayName("Should fallback to payload customerEmail when authenticated email is null or blank")
  void shouldFallbackToPayloadEmail() {
    CreateOrderItemRequest item =
        new CreateOrderItemRequest(1L, "Store", 2L, "Product", new BigDecimal("10.00"), 1);
    CreateOrderRequest request = new CreateOrderRequest(List.of(item), null, "guest@fallback.com");

    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

    OrderResponse response = orderProcessingService.createOrder(request, null, "   ");

    assertThat(response.customerId()).isNull();
    assertThat(response.customerEmail()).isEqualTo("guest@fallback.com");
  }
}
