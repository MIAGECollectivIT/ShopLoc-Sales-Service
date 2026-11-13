package fr.miage.collectivit.salesservice.service;

import fr.miage.collectivit.salesservice.client.StoreClient;
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
import fr.miage.collectivit.salesservice.service.receipt.OrderPaidEvent;
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
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for processing and managing multi-store click & collect orders, calculating
 * amounts, locking items under PENDING_PAYMENT status, and tracking expiration.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings({
  "PMD.LawOfDemeter",
  "PMD.CouplingBetweenObjects",
  "PMD.ExcessiveImports",
  "PMD.GodClass"
})
public class OrderProcessingService {

  public static final Duration DEFAULT_LOCK_DURATION = Duration.ofMinutes(30);
  private static final SecureRandom RANDOM = new SecureRandom();

  private final OrderRepository orderRepository;
  private final StoreClient storeClient;
  private final ApplicationEventPublisher applicationEventPublisher;

  @Transactional
  public OrderResponse createOrder(
      CreateOrderRequest request, String customerId, String customerEmail) {
    if (request.items() == null || request.items().isEmpty()) {
      throw new IllegalArgumentException("Order must contain at least one item");
    }

    reserveStock(request.items());

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
            .uid(UUID.randomUUID())
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

  public Optional<Order> resolveOrder(String identifier) {
    if (identifier == null || identifier.isBlank()) {
      return Optional.empty();
    }
    String trimmed = identifier.trim();
    try {
      UUID uid = UUID.fromString(trimmed);
      Optional<Order> byUid = orderRepository.findByUid(uid);
      if (byUid.isPresent()) {
        return byUid;
      }
    } catch (IllegalArgumentException ignored) {
      // Not a UUID, fallback to reference or ID
    }
    Optional<Order> byRef = orderRepository.findByOrderReference(trimmed);
    if (byRef.isPresent()) {
      return byRef;
    }
    try {
      Long id = Long.parseLong(trimmed);
      return orderRepository.findById(id);
    } catch (NumberFormatException ignored) {
      return Optional.empty();
    }
  }

  @Transactional(readOnly = true)
  public OrderResponse getOrderByReference(String identifier) {
    Order order =
        resolveOrder(identifier)
            .orElseThrow(
                () ->
                    new OrderNotFoundException(
                        "Order with identifier " + identifier + " not found"));
    return mapToResponse(order);
  }

  @Transactional
  public OrderResponse updateOrderStatus(String identifier, String newStatus) {
    Order order =
        resolveOrder(identifier)
            .orElseThrow(
                () ->
                    new OrderNotFoundException(
                        "Order with identifier " + identifier + " not found"));

    OrderStatus status;
    try {
      status = OrderStatus.valueOf(newStatus.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid order status: " + newStatus, ex);
    }

    releaseStockIfCancelledOrExpired(order, status);

    order.setStatus(status);
    order.setUpdatedAt(Instant.now());
    Order updated = orderRepository.save(order);

    if (status == OrderStatus.PAID) {
      applicationEventPublisher.publishEvent(new OrderPaidEvent(updated.getOrderReference()));
    }

    if (log.isInfoEnabled()) {
      log.info("Order status updated: identifier={}, newStatus={}", identifier, status);
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
        order.getUid(),
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
        itemResponses,
        order.getReceiptSentAt());
  }

  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  private void reserveStock(List<CreateOrderItemRequest> items) {
    List<ReservedItem> reservedItems = new ArrayList<>();
    try {
      for (CreateOrderItemRequest itemReq : items) {
        storeClient.updateStock(itemReq.storeId(), itemReq.productId(), -itemReq.quantity());
        reservedItems.add(
            new ReservedItem(itemReq.storeId(), itemReq.productId(), itemReq.quantity()));
      }
    } catch (RuntimeException ex) {
      rollbackReservedStock(reservedItems);
      throw ex;
    }
  }

  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  private void rollbackReservedStock(List<ReservedItem> reservedItems) {
    if (log.isWarnEnabled()) {
      log.warn("Stock reservation failed. Rolling back {} items.", reservedItems.size());
    }
    for (ReservedItem reserved : reservedItems) {
      try {
        storeClient.updateStock(reserved.storeId(), reserved.productId(), reserved.quantity());
      } catch (RuntimeException rollbackEx) {
        if (log.isErrorEnabled()) {
          log.error(
              "Rollback failed for store {} product {}: {}",
              reserved.storeId(),
              reserved.productId(),
              rollbackEx.getMessage());
        }
      }
    }
  }

  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  private void releaseStockIfCancelledOrExpired(Order order, OrderStatus newStatus) {
    OrderStatus previousStatus = order.getStatus();
    boolean isCancelledOrExpired =
        newStatus == OrderStatus.CANCELLED || newStatus == OrderStatus.EXPIRED;
    boolean wasPendingOrPaid =
        previousStatus == OrderStatus.PENDING_PAYMENT || previousStatus == OrderStatus.PAID;

    if (!wasPendingOrPaid || !isCancelledOrExpired) {
      return;
    }

    if (log.isInfoEnabled()) {
      log.info(
          "Order {} transitioning from {} to {}; releasing reserved stock.",
          order.getOrderReference(),
          previousStatus,
          newStatus);
    }
    for (OrderItem item : order.getItems()) {
      try {
        storeClient.updateStock(item.getStoreId(), item.getProductId(), item.getQuantity());
      } catch (RuntimeException ex) {
        if (log.isErrorEnabled()) {
          log.error(
              "Failed to release stock for order {} store {} product {}: {}",
              order.getOrderReference(),
              item.getStoreId(),
              item.getProductId(),
              ex.getMessage());
        }
      }
    }
  }

  private String generateOrderReference() {
    String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    int randomPart = 1000 + RANDOM.nextInt(9000);
    return "CMD-CC-" + datePart + "-" + randomPart;
  }

  private record ReservedItem(Long storeId, Long productId, int quantity) {}
}
