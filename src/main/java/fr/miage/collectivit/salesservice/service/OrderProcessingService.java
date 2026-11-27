package fr.miage.collectivit.salesservice.service;

import fr.miage.collectivit.salesservice.client.LoyaltyHoldClient;
import fr.miage.collectivit.salesservice.client.StoreClient;
import fr.miage.collectivit.salesservice.dto.CreateOrderItemRequest;
import fr.miage.collectivit.salesservice.dto.CreateOrderRequest;
import fr.miage.collectivit.salesservice.dto.OrderItemResponse;
import fr.miage.collectivit.salesservice.dto.OrderResponse;
import fr.miage.collectivit.salesservice.dto.OrderStoreSummaryResponse;
import fr.miage.collectivit.salesservice.dto.PageResponse;
import fr.miage.collectivit.salesservice.dto.event.OrderPaidKafkaEvent;
import fr.miage.collectivit.salesservice.dto.event.StoreOrderBreakdown;
import fr.miage.collectivit.salesservice.exception.ForbiddenException;
import fr.miage.collectivit.salesservice.exception.OrderNotFoundException;
import fr.miage.collectivit.salesservice.exception.StoreServiceUnavailableException;
import fr.miage.collectivit.salesservice.model.Order;
import fr.miage.collectivit.salesservice.model.OrderItem;
import fr.miage.collectivit.salesservice.model.OrderItemType;
import fr.miage.collectivit.salesservice.model.OrderStatus;
import fr.miage.collectivit.salesservice.model.WalletTransactionType;
import fr.miage.collectivit.salesservice.repository.OrderRepository;
import fr.miage.collectivit.salesservice.repository.PaymentTransactionRepository;
import fr.miage.collectivit.salesservice.repository.WalletTransactionRepository;
import fr.miage.collectivit.salesservice.security.UserRole;
import fr.miage.collectivit.salesservice.service.receipt.OrderPaidEvent;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
  "PMD.GodClass",
  "PMD.TooManyMethods",
  "PMD.CyclomaticComplexity",
  "PMD.CognitiveComplexity",
  "PMD.AvoidCatchingGenericException"
})
public class OrderProcessingService {

  public static final Duration DEFAULT_LOCK_DURATION = Duration.ofMinutes(30);
  private static final int SINGLE_ITEM_COUNT = 1;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final OrderRepository orderRepository;
  private final StoreClient storeClient;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final PaymentTransactionRepository paymentTransactionRepository;
  private final WalletTransactionRepository walletTransactionRepository;

  @Autowired(required = false)
  private TransactionTemplate transactionTemplate;

  @Autowired(required = false)
  private LoyaltyHoldClient loyaltyHoldClient;

  public OrderResponse createOrder(
      CreateOrderRequest request, String customerId, String customerEmail) {
    return createOrder(request, customerId, customerEmail, null);
  }

  public OrderResponse createOrder(
      CreateOrderRequest request,
      String customerId,
      String customerEmail,
      String authenticatedClientCode) {
    if (request.items() == null || request.items().isEmpty()) {
      throw new IllegalArgumentException("Order must contain at least one item");
    }

    validateItemPrices(request.items());
    Order order = buildOrderEntity(request, customerId, customerEmail, authenticatedClientCode);

    List<CreateOrderItemRequest> productItems =
        request.items().stream()
            .filter(i -> i.resolvedItemType() == OrderItemType.PRODUCT)
            .toList();
    List<CreateOrderItemRequest> giftItems =
        request.items().stream().filter(i -> i.resolvedItemType() == OrderItemType.GIFT).toList();

    List<ReservedItem> reservedItems = reserveStock(productItems);
    boolean holdCreated = false;
    try {
      if (!giftItems.isEmpty() && loyaltyHoldClient != null) {
        holdCreated = reserveGiftHold(order, giftItems, customerId);
      }
      return transactionTemplate != null
          ? transactionTemplate.execute(status -> persistAndMapOrder(order))
          : persistAndMapOrder(order);
    } catch (RuntimeException ex) {
      rollbackReservedStock(reservedItems);
      if (holdCreated && loyaltyHoldClient != null) {
        loyaltyHoldClient.releaseHold(order.getOrderReference());
      }
      throw ex;
    }
  }

  private boolean reserveGiftHold(
      Order order, List<CreateOrderItemRequest> giftItems, String customerId) {
    if (customerId == null || customerId.isBlank()) {
      throw new ForbiddenException("Authentification requise pour commander un cadeau fidélité.");
    }
    List<LoyaltyHoldClient.HoldItemRequest> holdItems =
        giftItems.stream()
            .map(
                i ->
                    new LoyaltyHoldClient.HoldItemRequest(
                        i.storeUid(), i.productUid(), i.quantity()))
            .toList();
    LoyaltyHoldClient.HoldResponse hold =
        loyaltyHoldClient.createHold(
            new LoyaltyHoldClient.CreateHoldRequest(
                order.getOrderReference(), customerId, order.getExpiresAt(), holdItems));
    if (hold != null && hold.items() != null) {
      Map<UUID, Integer> unitCostByGift = new LinkedHashMap<>();
      for (LoyaltyHoldClient.HoldItemResponse hItem : hold.items()) {
        unitCostByGift.put(hItem.giftUid(), hItem.unitPointsCost());
      }
      int totalPoints = 0;
      for (OrderItem item : order.getItems()) {
        if (item.getItemType() == OrderItemType.GIFT) {
          int unitPts = unitCostByGift.getOrDefault(item.getProductUid(), 0);
          int linePts = unitPts * item.getQuantity();
          item.setPointsCost(linePts);
          totalPoints += linePts;
        }
      }
      order.setTotalPointsCost(totalPoints);
    }
    return true;
  }

  @Transactional(readOnly = true)
  public boolean hasCustomerPurchasedInStore(String userId, UUID storeUid) {
    if (userId == null || userId.isBlank() || storeUid == null) {
      return false;
    }
    return orderRepository.existsSettledOrderForCustomerAndStore(userId.trim(), storeUid);
  }

  @Transactional(readOnly = true)
  public List<UUID> getCustomerPurchasedStoreUids(String userId) {
    if (userId == null || userId.isBlank()) {
      return List.of();
    }
    return orderRepository.findSettledStoreUidsByCustomerId(userId.trim());
  }

  private Order buildOrderEntity(
      CreateOrderRequest request,
      String customerId,
      String customerEmail,
      String authenticatedClientCode) {
    String effectiveEmail =
        (customerEmail != null && !customerEmail.isBlank())
            ? customerEmail
            : request.customerEmail();
    String effectiveClientCode =
        (authenticatedClientCode != null && !authenticatedClientCode.isBlank())
            ? authenticatedClientCode.trim()
            : null;

    Instant now = Instant.now();
    Instant expiresAt = now.plus(DEFAULT_LOCK_DURATION);
    Order order =
        Order.builder()
            .uid(UUID.randomUUID())
            .orderReference(generateOrderReference())
            .customerId(customerId)
            .customerEmail(effectiveEmail)
            .clientCode(effectiveClientCode)
            .currency("EUR")
            .status(OrderStatus.PENDING_PAYMENT)
            .expiresAt(expiresAt)
            .createdAt(now)
            .updatedAt(now)
            .items(new ArrayList<>())
            .build();

    BigDecimal totalOrderAmount = BigDecimal.ZERO;
    for (CreateOrderItemRequest itemReq : request.items()) {
      BigDecimal lineTotal = itemReq.unitPrice().multiply(BigDecimal.valueOf(itemReq.quantity()));
      totalOrderAmount = totalOrderAmount.add(lineTotal);
      order.addItem(
          OrderItem.builder()
              .uid(UUID.randomUUID())
              .order(order)
              .storeUid(itemReq.storeUid())
              .storeName(itemReq.storeName())
              .productUid(itemReq.productUid())
              .productName(itemReq.productName())
              .unitPrice(itemReq.unitPrice())
              .quantity(itemReq.quantity())
              .totalPrice(lineTotal)
              .itemType(itemReq.resolvedItemType())
              .pointsCost(0)
              .build());
    }
    order.setTotalAmount(totalOrderAmount);
    order.setTotalPointsCost(0);
    return order;
  }

  private OrderResponse persistAndMapOrder(Order order) {
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
  public PageResponse<OrderResponse> getOrders(
      String callerUserId, String callerRole, Pageable pageable) {
    Page<Order> orders;
    if (UserRole.ADMIN.name().equalsIgnoreCase(callerRole)) {
      orders = orderRepository.findAll(pageable);
    } else if (callerUserId != null && !callerUserId.isBlank()) {
      orders = orderRepository.findByCustomerIdOrderByCreatedAtDesc(callerUserId, pageable);
    } else {
      orders = Page.empty(pageable);
    }
    return PageResponse.from(orders.map(this::mapToResponse));
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
      // Not a UUID, lookup by order reference
    }
    Optional<Order> byRef = orderRepository.findByOrderReference(trimmed);
    if (byRef.isPresent()) {
      return byRef;
    }
    return Optional.empty();
  }

  @Transactional(readOnly = true)
  public void verifyOrderPaymentSettled(String orderReference) {
    Order order =
        resolveOrder(orderReference)
            .orElseThrow(
                () ->
                    new OrderNotFoundException(
                        "Order with identifier " + orderReference + " not found"));

    List<String> orderIdentifiers = new ArrayList<>();
    if (order.getOrderReference() != null && !order.getOrderReference().isBlank()) {
      orderIdentifiers.add(order.getOrderReference());
    }
    if (order.getUid() != null) {
      orderIdentifiers.add(order.getUid().toString());
    }

    BigDecimal approvedCardAmount =
        orderIdentifiers.isEmpty()
            ? BigDecimal.ZERO
            : Objects.requireNonNullElse(
                paymentTransactionRepository.sumApprovedAmountByReferences(orderIdentifiers),
                BigDecimal.ZERO);
    BigDecimal walletDebitedAmount =
        Objects.requireNonNullElse(
            walletTransactionRepository.sumAmountByOrderReferenceAndType(
                order.getOrderReference(), WalletTransactionType.DEBIT),
            BigDecimal.ZERO);
    BigDecimal totalSettled = approvedCardAmount.add(walletDebitedAmount);

    if (order.getTotalAmount() == null || totalSettled.compareTo(order.getTotalAmount()) < 0) {
      throw new ForbiddenException(
          "Payment proof required: settled amount does not cover the order total");
    }
  }

  public void verifyMerchantOwnsOrderStore(OrderResponse order, String rawBearerToken) {
    Set<UUID> merchantStoreUids = storeClient.getMerchantStoreUids(rawBearerToken);
    if (merchantStoreUids.isEmpty()) {
      throw new ForbiddenException(
          "Access denied: unable to verify merchant store ownership for this order");
    }
    boolean ownsAnyStore =
        order.storeSummaries() != null
            && order.storeSummaries().stream()
                .anyMatch(s -> s.storeUid() != null && merchantStoreUids.contains(s.storeUid()));
    if (!ownsAnyStore && order.items() != null) {
      ownsAnyStore =
          order.items().stream()
              .anyMatch(i -> i.storeUid() != null && merchantStoreUids.contains(i.storeUid()));
    }
    if (!ownsAnyStore) {
      throw new ForbiddenException(
          "Access denied: merchants can only access orders containing their own stores");
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

  /**
   * Resolves an order by reference, UID, or ID and maps it to {@link OrderResponse} within a
   * read-only transaction so lazy associations (such as {@code items}) are initialized safely.
   */
  @Transactional(readOnly = true)
  public Optional<OrderResponse> findOrderResponse(String identifier) {
    return resolveOrder(identifier).map(this::mapToResponse);
  }

  /** Marks the receipt email as sent for the given order reference inside a transaction. */
  @Transactional
  public void markReceiptSent(String orderReference) {
    resolveOrder(orderReference)
        .ifPresent(
            order -> {
              order.setReceiptSentAt(Instant.now());
              orderRepository.save(order);
            });
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

    if (order.getStatus() == status) {
      if (log.isInfoEnabled()) {
        log.info(
            "Order {} is already in status {}, skipping status update and event publication",
            identifier,
            status);
      }
      return mapToResponse(order);
    }

    if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.COMPLETED) {
      throw new IllegalStateException(
          "Cannot change status of an order that is already " + order.getStatus());
    }

    releaseStockIfCancelledOrExpired(order, status);

    order.setStatus(status);
    order.setUpdatedAt(Instant.now());
    Order updated = orderRepository.save(order);

    if (status == OrderStatus.PAID) {
      OrderPaidKafkaEvent kafkaEvent = buildKafkaOrderPaidEvent(updated);
      applicationEventPublisher.publishEvent(
          new OrderPaidEvent(updated.getOrderReference(), kafkaEvent));
    }

    if (log.isInfoEnabled()) {
      log.info("Order status updated: identifier={}, newStatus={}", identifier, status);
    }
    return mapToResponse(updated);
  }

  /**
   * Periodically expires PENDING_PAYMENT orders whose lock window has elapsed and releases their
   * reserved stock in Store Service.
   */
  @Scheduled(fixedDelay = 60_000L)
  @Transactional
  public void expirePendingOrders() {
    Instant now = Instant.now();
    List<Order> expiredOrders =
        orderRepository.findByStatusAndExpiresAtBefore(OrderStatus.PENDING_PAYMENT, now);
    if (expiredOrders.isEmpty()) {
      return;
    }

    for (Order order : expiredOrders) {
      releaseStockIfCancelledOrExpired(order, OrderStatus.EXPIRED);
      order.setStatus(OrderStatus.EXPIRED);
      order.setUpdatedAt(now);
    }
    orderRepository.saveAll(expiredOrders);
    if (log.isInfoEnabled()) {
      log.info("Expired {} stale PENDING_PAYMENT order(s).", expiredOrders.size());
    }
  }

  @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
  public OrderResponse mapToResponse(Order order) {
    List<OrderItemResponse> itemResponses = new ArrayList<>();
    Map<UUID, List<OrderItemResponse>> storeItemsMap = new LinkedHashMap<>();
    Map<UUID, String> storeNamesMap = new LinkedHashMap<>();

    for (OrderItem item : order.getItems()) {
      OrderItemResponse itemResp =
          new OrderItemResponse(
              item.getUid(),
              item.getStoreUid(),
              item.getStoreName(),
              item.getProductUid(),
              item.getProductName(),
              item.getUnitPrice(),
              item.getQuantity(),
              item.getTotalPrice(),
              item.getItemType() != null ? item.getItemType() : OrderItemType.PRODUCT,
              item.getPointsCost() != null ? item.getPointsCost() : 0);
      itemResponses.add(itemResp);

      storeItemsMap.computeIfAbsent(item.getStoreUid(), k -> new ArrayList<>()).add(itemResp);
      if (item.getStoreName() != null && !item.getStoreName().isBlank()) {
        storeNamesMap.put(item.getStoreUid(), item.getStoreName());
      }
    }

    List<OrderStoreSummaryResponse> storeSummaries = new ArrayList<>();
    for (Map.Entry<UUID, List<OrderItemResponse>> entry : storeItemsMap.entrySet()) {
      UUID storeUid = entry.getKey();
      List<OrderItemResponse> storeItems = entry.getValue();

      BigDecimal subtotal =
          storeItems.stream()
              .map(OrderItemResponse::totalPrice)
              .reduce(BigDecimal.ZERO, BigDecimal::add);

      int count = storeItems.stream().mapToInt(OrderItemResponse::quantity).sum();

      String storeName = storeNamesMap.getOrDefault(storeUid, "Commerce " + storeUid);

      storeSummaries.add(
          new OrderStoreSummaryResponse(storeUid, storeName, subtotal, count, storeItems));
    }

    return new OrderResponse(
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
        order.getReceiptSentAt(),
        order.getUpdatedAt(),
        order.getTotalPointsCost() != null ? order.getTotalPointsCost() : 0);
  }

  private List<ReservedItem> reserveStock(List<CreateOrderItemRequest> items) {
    List<ReservedItem> reservedItems = new ArrayList<>();
    try {
      for (CreateOrderItemRequest itemReq : items) {
        storeClient.updateStock(itemReq.storeUid(), itemReq.productUid(), -itemReq.quantity());
        reservedItems.add(
            new ReservedItem(itemReq.storeUid(), itemReq.productUid(), itemReq.quantity()));
      }
      return reservedItems;
    } catch (RuntimeException ex) {
      rollbackReservedStock(reservedItems);
      throw ex;
    }
  }

  private void rollbackReservedStock(List<ReservedItem> reservedItems) {
    if (log.isWarnEnabled()) {
      log.warn("Stock reservation failed. Rolling back {} items.", reservedItems.size());
    }
    for (ReservedItem reserved : reservedItems) {
      try {
        storeClient.updateStock(reserved.storeUid(), reserved.productUid(), reserved.quantity());
      } catch (RuntimeException rollbackEx) {
        if (log.isErrorEnabled()) {
          log.error(
              "Rollback failed for store {} product {}: {}",
              reserved.storeUid(),
              reserved.productUid(),
              rollbackEx.getMessage());
        }
      }
    }
  }

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
    boolean hasGifts = false;
    for (OrderItem item : order.getItems()) {
      if (item.getItemType() == OrderItemType.GIFT) {
        hasGifts = true;
        continue;
      }
      try {
        storeClient.updateStock(item.getStoreUid(), item.getProductUid(), item.getQuantity());
      } catch (RuntimeException ex) {
        if (log.isErrorEnabled()) {
          log.error(
              "Failed to release stock for order {} store {} product {}: {}",
              order.getOrderReference(),
              item.getStoreUid(),
              item.getProductUid(),
              ex.getMessage());
        }
      }
    }
    if (hasGifts && loyaltyHoldClient != null) {
      loyaltyHoldClient.releaseHold(order.getOrderReference());
    }
  }

  @SuppressWarnings("PMD.PreserveStackTrace")
  private void validateItemPrices(List<CreateOrderItemRequest> items) {
    if (items.size() <= SINGLE_ITEM_COUNT) {
      validateSingleItemPrice(items.get(0));
      return;
    }
    List<CompletableFuture<Void>> futures =
        items.stream()
            .map(item -> CompletableFuture.runAsync(() -> validateSingleItemPrice(item)))
            .toList();
    try {
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    } catch (CompletionException ex) {
      if (ex.getCause() instanceof RuntimeException runtimeEx) {
        throw runtimeEx;
      }
      throw ex;
    }
  }

  private void validateSingleItemPrice(CreateOrderItemRequest item) {
    if (item.resolvedItemType() == OrderItemType.GIFT) {
      if (item.unitPrice() == null || item.unitPrice().compareTo(BigDecimal.ZERO) != 0) {
        throw new IllegalArgumentException(
            "Gift item unit price must be 0.00 EUR (RG002) for gift " + item.productUid());
      }
      return;
    }
    if (item.unitPrice() == null || item.unitPrice().compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException(
          "Unit price must be strictly positive for product " + item.productUid());
    }
    Optional<BigDecimal> verifiedPrice =
        storeClient.getProductPrice(item.storeUid(), item.productUid());
    if (verifiedPrice.isEmpty()) {
      throw new StoreServiceUnavailableException(
          "Unable to verify price for product "
              + item.productUid()
              + " in store "
              + item.storeUid());
    }
    if (verifiedPrice.get().compareTo(item.unitPrice()) != 0) {
      throw new IllegalArgumentException(
          "Price mismatch for product "
              + item.productUid()
              + ": expected "
              + verifiedPrice.get()
              + " EUR but received "
              + item.unitPrice()
              + " EUR");
    }
  }

  private String generateOrderReference() {
    String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    String randomSuffix =
        UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    return "CMD-CC-" + datePart + "-" + randomSuffix;
  }

  private OrderPaidKafkaEvent buildKafkaOrderPaidEvent(Order order) {
    List<StoreOrderBreakdown> breakdowns = computeStoreBreakdowns(order.getItems());
    return new OrderPaidKafkaEvent(
        order.getOrderReference(),
        order.getCustomerId(),
        order.getClientCode(),
        order.getTotalAmount(),
        order.getCurrency(),
        breakdowns,
        Instant.now());
  }

  private List<StoreOrderBreakdown> computeStoreBreakdowns(List<OrderItem> items) {
    if (items == null || items.isEmpty()) {
      return Collections.emptyList();
    }
    Map<UUID, BigDecimal> storeAmounts = new LinkedHashMap<>();
    Map<UUID, String> storeNames = new LinkedHashMap<>();
    for (OrderItem item : items) {
      UUID storeUid = item.getStoreUid();
      if (storeUid == null) {
        continue;
      }
      BigDecimal itemTotal = Objects.requireNonNullElse(item.getTotalPrice(), BigDecimal.ZERO);
      storeAmounts.merge(storeUid, itemTotal, BigDecimal::add);
      if (item.getStoreName() != null) {
        storeNames.putIfAbsent(storeUid, item.getStoreName());
      }
    }
    List<StoreOrderBreakdown> breakdowns = new ArrayList<>();
    storeAmounts.forEach(
        (storeUid, amount) -> {
          if (amount.compareTo(BigDecimal.ZERO) > 0) {
            breakdowns.add(new StoreOrderBreakdown(storeUid, storeNames.get(storeUid), amount));
          }
        });
    return breakdowns;
  }

  private record ReservedItem(UUID storeUid, UUID productUid, int quantity) {}
}
