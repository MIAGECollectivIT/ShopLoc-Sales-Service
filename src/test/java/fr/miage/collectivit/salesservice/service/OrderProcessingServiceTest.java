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

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderProcessingService Unit Tests (SLE-186)")
class OrderProcessingServiceTest {

  @Mock private OrderRepository orderRepository;
  @Mock private StoreClient storeClient;

  private OrderProcessingService orderProcessingService;

  @BeforeEach
  void setUp() {
    orderProcessingService = new OrderProcessingService(orderRepository, storeClient);
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

    OrderResponse expired = orderProcessingService.updateOrderStatus("CMD-CC-2", "EXPIRED");
    assertThat(expired.status()).isEqualTo("EXPIRED");
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
