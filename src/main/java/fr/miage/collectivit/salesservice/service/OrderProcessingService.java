package fr.miage.collectivit.salesservice.service;

import fr.miage.collectivit.salesservice.dto.CreateOrderItemRequest;
import fr.miage.collectivit.salesservice.dto.CreateOrderRequest;
import fr.miage.collectivit.salesservice.dto.OrderItemResponse;
import fr.miage.collectivit.salesservice.dto.OrderResponse;
import fr.miage.collectivit.salesservice.dto.OrderStoreSummaryResponse;
import fr.miage.collectivit.salesservice.exception.OrderNotFoundException;
import fr.miage.collectivit.salesservice.model.Order;
import fr.miage.collectivit.salesservice.model.OrderItem;
import fr.miage.collectivit.salesservice.model.OrderStatus;
import fr.miage.collectivit.salesservice.repository.OrderRepository;
import fr.miage.collectivit.salesservice.security.UserRole;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for processing and managing multi-store click & collect orders, calculating
 * amounts, locking items under PENDING_PAYMENT status, and tracking expiration.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("PMD.LawOfDemeter")
public class OrderProcessingService {

  public static final Duration DEFAULT_LOCK_DURATION = Duration.ofMinutes(30);
  private static final SecureRandom RANDOM = new SecureRandom();

  private final OrderRepository orderRepository;

  @Transactional
  public OrderResponse createOrder(
      CreateOrderRequest request, String customerId, String customerEmail) {
    if (request.items() == null || request.items().isEmpty()) {
      throw new IllegalArgumentException("Order must contain at least one item");
    }

    String effectiveEmail =
        (customerEmail != null && !customerEmail.isBlank())
            ? customerEmail
            : request.customerEmail();

    Instant now = Instant.now();
    Instant expiresAt = now.plus(DEFAULT_LOCK_DURATION);
    String orderReference = generateOrderReference();

    BigDecimal totalOrderAmount = BigDecimal.ZERO;

    Order order =
        Order.builder()
            .orderReference(orderReference)
            .customerId(customerId)
            .customerEmail(effectiveEmail)
            .clientCode(request.clientCode())
            .currency("EUR")
            .status(OrderStatus.PENDING_PAYMENT)
            .expiresAt(expiresAt)
            .createdAt(now)
            .updatedAt(now)
            .items(new ArrayList<>())
            .build();

    for (CreateOrderItemRequest itemReq : request.items()) {
      BigDecimal lineTotal = itemReq.unitPrice().multiply(BigDecimal.valueOf(itemReq.quantity()));
      totalOrderAmount = totalOrderAmount.add(lineTotal);

      OrderItem item =
          OrderItem.builder()
              .order(order)
              .storeId(itemReq.storeId())
              .storeName(itemReq.storeName())
              .productId(itemReq.productId())
              .productName(itemReq.productName())
              .unitPrice(itemReq.unitPrice())
              .quantity(itemReq.quantity())
              .totalPrice(lineTotal)
              .build();

      order.addItem(item);
    }

    order.setTotalAmount(totalOrderAmount);

    Order saved = orderRepository.save(order);
    if (log.isInfoEnabled()) {
      log.info(
          "Order created successfully: reference={}, totalAmount={} EUR, itemsCount={}, status={}",
          saved.getOrderReference(),
          saved.getTotalAmount(),
          saved.getItems().size(),
          saved.getStatus());
    }

    return mapToResponse(saved);
  }

  @Transactional(readOnly = true)
  public List<OrderResponse> getOrders(String callerUserId, String callerRole) {
    List<Order> orders;
    if (UserRole.ADMIN.name().equalsIgnoreCase(callerRole)) {
      orders = orderRepository.findAll();
    } else if (callerUserId != null && !callerUserId.isBlank()) {
      orders = orderRepository.findByCustomerIdOrderByCreatedAtDesc(callerUserId);
    } else {
      orders = List.of();
    }
    return orders.stream().map(this::mapToResponse).toList();
  }

  @Transactional(readOnly = true)
  public OrderResponse getOrderByReference(String orderReference) {
    Order order =
        orderRepository
            .findByOrderReference(orderReference)
            .orElseThrow(
                () ->
                    new OrderNotFoundException(
                        "Order with reference " + orderReference + " not found"));
    return mapToResponse(order);
  }

  @Transactional
  public OrderResponse updateOrderStatus(String orderReference, String newStatus) {
    Order order =
        orderRepository
            .findByOrderReference(orderReference)
            .orElseThrow(
                () ->
                    new OrderNotFoundException(
                        "Order with reference " + orderReference + " not found"));

    OrderStatus status;
    try {
      status = OrderStatus.valueOf(newStatus.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid order status: " + newStatus, ex);
    }

    order.setStatus(status);
    order.setUpdatedAt(Instant.now());
    Order updated = orderRepository.save(order);

    if (log.isInfoEnabled()) {
      log.info("Order status updated: reference={}, newStatus={}", orderReference, status);
    }
    return mapToResponse(updated);
  }

  @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
  public OrderResponse mapToResponse(Order order) {
    List<OrderItemResponse> itemResponses = new ArrayList<>();
    Map<Long, List<OrderItemResponse>> storeItemsMap = new LinkedHashMap<>();
    Map<Long, String> storeNamesMap = new LinkedHashMap<>();

    for (OrderItem item : order.getItems()) {
      OrderItemResponse itemResp =
          new OrderItemResponse(
              item.getId(),
              item.getStoreId(),
              item.getStoreName(),
              item.getProductId(),
              item.getProductName(),
              item.getUnitPrice(),
              item.getQuantity(),
              item.getTotalPrice());
      itemResponses.add(itemResp);

      storeItemsMap.computeIfAbsent(item.getStoreId(), k -> new ArrayList<>()).add(itemResp);
      if (item.getStoreName() != null && !item.getStoreName().isBlank()) {
        storeNamesMap.put(item.getStoreId(), item.getStoreName());
      }
    }

    List<OrderStoreSummaryResponse> storeSummaries = new ArrayList<>();
    for (Map.Entry<Long, List<OrderItemResponse>> entry : storeItemsMap.entrySet()) {
      Long storeId = entry.getKey();
      List<OrderItemResponse> storeItems = entry.getValue();

      BigDecimal subtotal =
          storeItems.stream()
              .map(OrderItemResponse::totalPrice)
              .reduce(BigDecimal.ZERO, BigDecimal::add);

      int count = storeItems.stream().mapToInt(OrderItemResponse::quantity).sum();

      String storeName = storeNamesMap.getOrDefault(storeId, "Commerce #" + storeId);

      storeSummaries.add(
          new OrderStoreSummaryResponse(storeId, storeName, subtotal, count, storeItems));
    }

    return new OrderResponse(
        order.getId(),
        order.getOrderReference(),
        order.getCustomerId(),
        order.getCustomerEmail(),
        order.getClientCode(),
        order.getTotalAmount(),
        order.getCurrency(),
        order.getStatus().name(),
        order.getExpiresAt(),
        order.getCreatedAt(),
        storeSummaries,
        itemResponses);
  }

  private String generateOrderReference() {
    String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    int randomPart = 1000 + RANDOM.nextInt(9000);
    return "CMD-CC-" + datePart + "-" + randomPart;
  }
}
