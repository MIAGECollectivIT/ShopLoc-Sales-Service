package fr.miage.collectivit.salesservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.miage.collectivit.salesservice.dto.CreateOrderItemRequest;
import fr.miage.collectivit.salesservice.dto.CreateOrderRequest;
import fr.miage.collectivit.salesservice.dto.OrderItemResponse;
import fr.miage.collectivit.salesservice.dto.OrderResponse;
import fr.miage.collectivit.salesservice.dto.OrderStoreSummaryResponse;
import fr.miage.collectivit.salesservice.dto.UpdateOrderStatusRequest;
import fr.miage.collectivit.salesservice.exception.GlobalExceptionHandler;
import fr.miage.collectivit.salesservice.exception.OrderNotFoundException;
import fr.miage.collectivit.salesservice.model.OrderStatus;
import fr.miage.collectivit.salesservice.security.JwtAuthInterceptor;
import fr.miage.collectivit.salesservice.security.UserRole;
import fr.miage.collectivit.salesservice.service.OrderProcessingService;
import fr.miage.collectivit.salesservice.service.receipt.ReceiptEmailService;
import fr.miage.collectivit.salesservice.service.receipt.ReceiptPdfService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderController WebMvc Tests (SLE-186)")
class OrderControllerTest {

  @Mock private OrderProcessingService orderProcessingService;
  @Mock private ReceiptPdfService receiptPdfService;
  @Mock private ReceiptEmailService receiptEmailService;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  private static final UUID STORE_UID_1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID PRODUCT_UID_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new OrderController(orderProcessingService, receiptPdfService, receiptEmailService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
  }

  @Test
  @DisplayName("POST /v1/sales/orders should create order and return 201 Created")
  void shouldCreateOrderSuccessfully() throws Exception {
    CreateOrderItemRequest item1 =
        new CreateOrderItemRequest(
            STORE_UID_1, "Le Moulin", PRODUCT_UID_2, "Baguette", new BigDecimal("1.35"), 2);
    CreateOrderRequest request =
        new CreateOrderRequest(List.of(item1), "CLI-123", "client@test.fr");

    OrderItemResponse itemResp =
        new OrderItemResponse(
            UUID.randomUUID(),
            STORE_UID_1,
            "Le Moulin",
            PRODUCT_UID_2,
            "Baguette",
            new BigDecimal("1.35"),
            2,
            new BigDecimal("2.70"));
    OrderStoreSummaryResponse storeSummary =
        new OrderStoreSummaryResponse(
            STORE_UID_1, "Le Moulin", new BigDecimal("2.70"), 2, List.of(itemResp));

    OrderResponse response =
        new OrderResponse(
            UUID.randomUUID(),
            "CMD-CC-20261110-9999",
            "cust-1",
            "client@test.fr",
            "CLI-123",
            new BigDecimal("2.70"),
            "EUR",
            "PENDING_PAYMENT",
            Instant.now().plusSeconds(1800),
            Instant.now(),
            List.of(storeSummary),
            List.of(itemResp));

    when(orderProcessingService.createOrder(any(), any(), any(), any())).thenReturn(response);

    mockMvc
        .perform(
            post("/v1/sales/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.uid").isNotEmpty())
        .andExpect(jsonPath("$.orderReference").value("CMD-CC-20261110-9999"))
        .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
        .andExpect(jsonPath("$.totalAmount").value(2.70))
        .andExpect(jsonPath("$.storeSummaries[0].storeUid").value(STORE_UID_1.toString()))
        .andExpect(jsonPath("$.storeSummaries[0].storeName").value("Le Moulin"))
        .andExpect(jsonPath("$.storeSummaries[0].subtotal").value(2.70));
  }

  @Test
  @DisplayName("POST /v1/sales/orders should return 400 Bad Request when items list is empty")
  void shouldReturn400WhenItemsEmpty() throws Exception {
    CreateOrderRequest request = new CreateOrderRequest(List.of(), null, null);

    mockMvc
        .perform(
            post("/v1/sales/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Validation Failed"));
  }

  @Test
  @DisplayName("POST /v1/sales/orders should return 400 Bad Request when unit price is invalid")
  void shouldReturn400WhenPriceInvalid() throws Exception {
    CreateOrderItemRequest invalidItem =
        new CreateOrderItemRequest(
            STORE_UID_1, "Store", PRODUCT_UID_2, "Product", new BigDecimal("-1.00"), 1);
    CreateOrderRequest request = new CreateOrderRequest(List.of(invalidItem), null, null);

    mockMvc
        .perform(
            post("/v1/sales/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("GET /v1/sales/orders/{ref} should return 200 OK for owner CLIENT")
  void shouldGetOrderByReference() throws Exception {
    OrderResponse response =
        new OrderResponse(
            UUID.randomUUID(),
            "CMD-CC-20261110-9999",
            "cust-1",
            "client@test.fr",
            null,
            new BigDecimal("10.00"),
            "EUR",
            "PENDING_PAYMENT",
            Instant.now().plusSeconds(1800),
            Instant.now(),
            List.of(),
            List.of());

    when(orderProcessingService.getOrderByReference("CMD-CC-20261110-9999")).thenReturn(response);

    mockMvc
        .perform(
            get("/v1/sales/orders/CMD-CC-20261110-9999")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ROLE, "CLIENT")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ID, "cust-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orderReference").value("CMD-CC-20261110-9999"))
        .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
  }

  @Test
  @DisplayName(
      "GET /v1/sales/orders/{ref} should return 403 Forbidden when CLIENT accesses another's order")
  void shouldReturn403WhenClientAccessesAnotherOrder() throws Exception {
    OrderResponse response =
        new OrderResponse(
            UUID.randomUUID(),
            "CMD-CC-20261110-9999",
            "cust-other",
            "other@test.fr",
            null,
            new BigDecimal("10.00"),
            "EUR",
            "PENDING_PAYMENT",
            Instant.now().plusSeconds(1800),
            Instant.now(),
            List.of(),
            List.of());

    when(orderProcessingService.getOrderByReference("CMD-CC-20261110-9999")).thenReturn(response);

    mockMvc
        .perform(
            get("/v1/sales/orders/CMD-CC-20261110-9999")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ROLE, "CLIENT")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ID, "cust-1"))
        .andExpect(status().isForbidden())
        .andExpect(
            jsonPath("$.message").value("Access denied: you can only consult your own orders"));
  }

  @Test
  @DisplayName("GET /v1/sales/orders/{ref} should return 200 OK for ADMIN regardless of customerId")
  void shouldGetOrderByReferenceForAdmin() throws Exception {
    OrderResponse response =
        new OrderResponse(
            UUID.randomUUID(),
            "CMD-CC-20261110-9999",
            "cust-other",
            "other@test.fr",
            null,
            new BigDecimal("10.00"),
            "EUR",
            "PENDING_PAYMENT",
            Instant.now().plusSeconds(1800),
            Instant.now(),
            List.of(),
            List.of());

    when(orderProcessingService.getOrderByReference("CMD-CC-20261110-9999")).thenReturn(response);

    mockMvc
        .perform(
            get("/v1/sales/orders/CMD-CC-20261110-9999")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ROLE, "ADMIN")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ID, "admin-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orderReference").value("CMD-CC-20261110-9999"));
  }

  @Test
  @DisplayName("GET /v1/sales/orders should return order list for authenticated caller")
  void shouldListOrdersForCaller() throws Exception {
    OrderResponse response =
        new OrderResponse(
            UUID.randomUUID(),
            "CMD-CC-20261110-9999",
            "cust-1",
            "client@test.fr",
            null,
            new BigDecimal("10.00"),
            "EUR",
            "PENDING_PAYMENT",
            Instant.now().plusSeconds(1800),
            Instant.now(),
            List.of(),
            List.of());

    when(orderProcessingService.getOrders("cust-1", "CLIENT")).thenReturn(List.of(response));

    mockMvc
        .perform(
            get("/v1/sales/orders")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ROLE, "CLIENT")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ID, "cust-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].orderReference").value("CMD-CC-20261110-9999"));
  }

  @Test
  @DisplayName("GET /v1/sales/orders/{ref} should return 404 Not Found when order does not exist")
  void shouldReturn404WhenNotFound() throws Exception {
    when(orderProcessingService.getOrderByReference("UNKNOWN"))
        .thenThrow(new OrderNotFoundException("Order with reference UNKNOWN not found"));

    mockMvc
        .perform(
            get("/v1/sales/orders/UNKNOWN").requestAttr(JwtAuthInterceptor.ATTR_USER_ROLE, "ADMIN"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Order with reference UNKNOWN not found"));
  }

  @Test
  @DisplayName("PATCH /v1/sales/orders/{ref}/status should return 200 OK for MERCHANT")
  void shouldUpdateOrderStatus() throws Exception {
    UpdateOrderStatusRequest patchRequest = new UpdateOrderStatusRequest("PAID");

    OrderResponse response =
        new OrderResponse(
            UUID.randomUUID(),
            "CMD-CC-20261110-9999",
            "cust-1",
            "client@test.fr",
            null,
            new BigDecimal("10.00"),
            "EUR",
            "PAID",
            Instant.now().plusSeconds(1800),
            Instant.now(),
            List.of(),
            List.of());

    when(orderProcessingService.updateOrderStatus(eq("CMD-CC-20261110-9999"), eq("PAID")))
        .thenReturn(response);

    mockMvc
        .perform(
            patch("/v1/sales/orders/CMD-CC-20261110-9999/status")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ROLE, "MERCHANT")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ID, "merchant-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(patchRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAID"));
  }

  @Test
  @DisplayName(
      "PATCH /v1/sales/orders/{ref}/status should return 200 OK when CLIENT sets status to PAID upon checkout settlement")
  void shouldAllowClientToSetStatusPaidForTheirPendingOrder() throws Exception {
    UpdateOrderStatusRequest patchRequest = new UpdateOrderStatusRequest("PAID");

    OrderResponse existingOrder =
        new OrderResponse(
            UUID.randomUUID(),
            "CMD-CC-20261110-9999",
            "cust-1",
            "client@test.fr",
            null,
            new BigDecimal("10.00"),
            "EUR",
            "PENDING_PAYMENT",
            Instant.now().plusSeconds(1800),
            Instant.now(),
            List.of(),
            List.of());

    OrderResponse paidOrder =
        new OrderResponse(
            UUID.randomUUID(),
            "CMD-CC-20261110-9999",
            "cust-1",
            "client@test.fr",
            null,
            new BigDecimal("10.00"),
            "EUR",
            "PAID",
            Instant.now().plusSeconds(1800),
            Instant.now(),
            List.of(),
            List.of());

    when(orderProcessingService.getOrderByReference("CMD-CC-20261110-9999"))
        .thenReturn(existingOrder);
    when(orderProcessingService.updateOrderStatus(eq("CMD-CC-20261110-9999"), eq("PAID")))
        .thenReturn(paidOrder);

    mockMvc
        .perform(
            patch("/v1/sales/orders/CMD-CC-20261110-9999/status")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ROLE, "CLIENT")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ID, "cust-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(patchRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAID"));
  }

  @Test
  @DisplayName(
      "PATCH /v1/sales/orders/{ref}/status should return 403 Forbidden when CLIENT attempts to set status to COMPLETED")
  void shouldReturn403WhenClientAttemptsToSetStatusCompleted() throws Exception {
    UpdateOrderStatusRequest patchRequest = new UpdateOrderStatusRequest("COMPLETED");

    OrderResponse existingOrder =
        new OrderResponse(
            UUID.randomUUID(),
            "CMD-CC-20261110-9999",
            "cust-1",
            "client@test.fr",
            null,
            new BigDecimal("10.00"),
            "EUR",
            "PENDING_PAYMENT",
            Instant.now().plusSeconds(1800),
            Instant.now(),
            List.of(),
            List.of());

    when(orderProcessingService.getOrderByReference("CMD-CC-20261110-9999"))
        .thenReturn(existingOrder);

    mockMvc
        .perform(
            patch("/v1/sales/orders/CMD-CC-20261110-9999/status")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ROLE, "CLIENT")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ID, "cust-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(patchRequest)))
        .andExpect(status().isForbidden())
        .andExpect(
            jsonPath("$.message")
                .value(
                    org.hamcrest.Matchers.containsString(
                        "Access denied: clients are only permitted to pay or cancel their own pending orders")));
  }

  @Test
  @DisplayName(
      "PATCH /v1/sales/orders/{ref}/status should return 200 OK when CLIENT cancels their own pending order")
  void shouldAllowClientToCancelTheirPendingOrder() throws Exception {
    UpdateOrderStatusRequest patchRequest = new UpdateOrderStatusRequest("CANCELLED");

    OrderResponse existingOrder =
        new OrderResponse(
            UUID.randomUUID(),
            "CMD-CC-20261110-9999",
            "cust-1",
            "client@test.fr",
            null,
            new BigDecimal("10.00"),
            "EUR",
            "PENDING_PAYMENT",
            Instant.now().plusSeconds(1800),
            Instant.now(),
            List.of(),
            List.of());

    OrderResponse cancelledOrder =
        new OrderResponse(
            UUID.randomUUID(),
            "CMD-CC-20261110-9999",
            "cust-1",
            "client@test.fr",
            null,
            new BigDecimal("10.00"),
            "EUR",
            "CANCELLED",
            Instant.now().plusSeconds(1800),
            Instant.now(),
            List.of(),
            List.of());

    when(orderProcessingService.getOrderByReference("CMD-CC-20261110-9999"))
        .thenReturn(existingOrder);
    when(orderProcessingService.updateOrderStatus(eq("CMD-CC-20261110-9999"), eq("CANCELLED")))
        .thenReturn(cancelledOrder);

    mockMvc
        .perform(
            patch("/v1/sales/orders/CMD-CC-20261110-9999/status")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ROLE, "CLIENT")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ID, "cust-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(patchRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  @DisplayName(
      "PATCH /v1/sales/orders/{ref}/status should return 403 Forbidden when CLIENT tries to cancel already PAID order")
  void shouldReturn403WhenClientTriesToCancelPaidOrder() throws Exception {
    UpdateOrderStatusRequest patchRequest = new UpdateOrderStatusRequest("CANCELLED");

    OrderResponse paidOrder =
        new OrderResponse(
            UUID.randomUUID(),
            "CMD-CC-20261110-9999",
            "cust-1",
            "client@test.fr",
            null,
            new BigDecimal("10.00"),
            "EUR",
            "PAID",
            Instant.now().plusSeconds(1800),
            Instant.now(),
            List.of(),
            List.of());

    when(orderProcessingService.getOrderByReference("CMD-CC-20261110-9999")).thenReturn(paidOrder);

    mockMvc
        .perform(
            patch("/v1/sales/orders/CMD-CC-20261110-9999/status")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ROLE, "CLIENT")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ID, "cust-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(patchRequest)))
        .andExpect(status().isForbidden())
        .andExpect(
            jsonPath("$.message")
                .value("Access denied: clients can only cancel orders in PENDING_PAYMENT status"));
  }

  @Test
  @DisplayName(
      "PATCH /v1/sales/orders/{ref}/status should return 400 Bad Request when status is invalid")
  void shouldReturn400WhenStatusInvalid() throws Exception {
    UpdateOrderStatusRequest patchRequest = new UpdateOrderStatusRequest("INVALID_STATUS");

    when(orderProcessingService.updateOrderStatus(eq("CMD-CC-20261110-9999"), eq("INVALID_STATUS")))
        .thenThrow(new IllegalArgumentException("Invalid order status: INVALID_STATUS"));

    mockMvc
        .perform(
            patch("/v1/sales/orders/CMD-CC-20261110-9999/status")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ROLE, "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(patchRequest)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Invalid order status: INVALID_STATUS"));
  }

  @Test
  @DisplayName("POST /v1/sales/orders should return 400 Bad Request when storeUid is null")
  void shouldReturn400WhenStoreUidNull() throws Exception {
    CreateOrderItemRequest invalidItem =
        new CreateOrderItemRequest(
            null, "Store", PRODUCT_UID_2, "Product", new BigDecimal("5.00"), 1);
    CreateOrderRequest request = new CreateOrderRequest(List.of(invalidItem), null, null);

    mockMvc
        .perform(
            post("/v1/sales/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Validation Failed"));
  }

  @Test
  @DisplayName("POST /v1/sales/orders should return 400 Bad Request when productUid is null")
  void shouldReturn400WhenProductUidNull() throws Exception {
    CreateOrderItemRequest invalidItem =
        new CreateOrderItemRequest(
            STORE_UID_1, "Store", null, "Product", new BigDecimal("5.00"), 1);
    CreateOrderRequest request = new CreateOrderRequest(List.of(invalidItem), null, null);

    mockMvc
        .perform(
            post("/v1/sales/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Validation Failed"));
  }

  @Test
  @DisplayName("POST /v1/sales/orders should return 400 Bad Request when productName is blank")
  void shouldReturn400WhenProductNameBlank() throws Exception {
    CreateOrderItemRequest invalidItem =
        new CreateOrderItemRequest(
            STORE_UID_1, "Store", PRODUCT_UID_2, "   ", new BigDecimal("5.00"), 1);
    CreateOrderRequest request = new CreateOrderRequest(List.of(invalidItem), null, null);

    mockMvc
        .perform(
            post("/v1/sales/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Validation Failed"));
  }

  @Test
  @DisplayName("POST /v1/sales/orders should return 400 Bad Request when payload is malformed JSON")
  void shouldReturn400WhenMalformedJson() throws Exception {
    mockMvc
        .perform(
            post("/v1/sales/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not-a-valid-json}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Malformed JSON request payload"));
  }

  @Test
  @DisplayName(
      "GET /v1/sales/orders/{identifier}/receipt should return 200 OK with PDF bytes when order is PAID")
  void shouldDownloadReceiptSuccessfullyWhenPaid() throws Exception {
    String ref = "CMD-CC-20261113-1111";
    OrderResponse paidOrder =
        new OrderResponse(
            UUID.randomUUID(),
            ref,
            "user-10",
            "client@example.com",
            "CLT-10",
            new BigDecimal("20.00"),
            "EUR",
            "PAID",
            Instant.now().plusSeconds(1800),
            Instant.now(),
            List.of(),
            List.of(),
            null);

    org.mockito.Mockito.when(orderProcessingService.getOrderByReference(ref)).thenReturn(paidOrder);
    org.mockito.Mockito.when(receiptPdfService.generateReceiptPdf(paidOrder))
        .thenReturn("%PDF-1.4 test bytes".getBytes());

    mockMvc
        .perform(
            get("/v1/sales/orders/" + ref + "/receipt")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ROLE, UserRole.CLIENT.name())
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ID, "user-10"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"bon-de-retrait-" + ref + ".pdf\""));
  }

  @Test
  @DisplayName(
      "GET /v1/sales/orders/{identifier}/receipt should return 200 OK when order is COMPLETED")
  void shouldAllowDownloadingReceiptWhenOrderIsCompleted() throws Exception {
    String ref = "CMD-CC-20261113-3333";
    OrderResponse completedOrder =
        new OrderResponse(
            UUID.randomUUID(),
            ref,
            "user-10",
            "client@example.com",
            "CLT-10",
            new BigDecimal("30.00"),
            "EUR",
            OrderStatus.COMPLETED.name(),
            Instant.now().plusSeconds(3600),
            Instant.now().minusSeconds(7200),
            List.of(),
            List.of(),
            null,
            Instant.now().minusSeconds(1800));

    when(orderProcessingService.getOrderByReference(ref)).thenReturn(completedOrder);
    when(receiptPdfService.generateReceiptPdf(completedOrder))
        .thenReturn("%PDF-1.4 test bytes".getBytes());

    mockMvc
        .perform(
            get("/v1/sales/orders/" + ref + "/receipt")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ROLE, UserRole.CLIENT.name())
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ID, "user-10"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"facture-" + ref + ".pdf\""));
  }

  @Test
  @DisplayName(
      "GET /v1/sales/orders/{identifier}/receipt should return 409 Conflict when order is not PAID")
  void shouldReturn409WhenDownloadingReceiptForUnpaidOrder() throws Exception {
    String ref = "CMD-CC-20261113-2222";
    OrderResponse pendingOrder =
        new OrderResponse(
            UUID.randomUUID(),
            ref,
            "user-10",
            "client@example.com",
            "CLT-10",
            new BigDecimal("20.00"),
            "EUR",
            "PENDING_PAYMENT",
            Instant.now().plusSeconds(1800),
            Instant.now(),
            List.of(),
            List.of(),
            null);

    org.mockito.Mockito.when(orderProcessingService.getOrderByReference(ref))
        .thenReturn(pendingOrder);

    mockMvc
        .perform(
            get("/v1/sales/orders/" + ref + "/receipt")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ROLE, UserRole.CLIENT.name())
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ID, "user-10"))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName(
      "GET /v1/sales/orders/{identifier}/receipt should return 403 Forbidden when caller is not owner")
  void shouldReturn403WhenCallerNotOwnerOfReceipt() throws Exception {
    String ref = "CMD-CC-20261113-3333";
    OrderResponse paidOrder =
        new OrderResponse(
            UUID.randomUUID(),
            ref,
            "user-10",
            "client@example.com",
            "CLT-10",
            new BigDecimal("20.00"),
            "EUR",
            "PAID",
            Instant.now().plusSeconds(1800),
            Instant.now(),
            List.of(),
            List.of(),
            null);

    org.mockito.Mockito.when(orderProcessingService.getOrderByReference(ref)).thenReturn(paidOrder);

    mockMvc
        .perform(
            get("/v1/sales/orders/" + ref + "/receipt")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ROLE, UserRole.CLIENT.name())
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ID, "user-intruder"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "POST /v1/sales/orders/{identifier}/resend-receipt should resend email when caller is owner")
  void shouldResendReceiptSuccessfullyWhenOwner() throws Exception {
    String ref = "CMD-CC-20261113-4444";
    OrderResponse paidOrder =
        new OrderResponse(
            UUID.randomUUID(),
            ref,
            "user-10",
            "client@example.com",
            "CLT-10",
            new BigDecimal("20.00"),
            "EUR",
            "PAID",
            Instant.now().plusSeconds(1800),
            Instant.now(),
            List.of(),
            List.of(),
            null);

    org.mockito.Mockito.when(orderProcessingService.getOrderByReference(ref)).thenReturn(paidOrder);
    org.mockito.Mockito.when(receiptEmailService.resendReceiptEmail(ref)).thenReturn(true);

    mockMvc
        .perform(
            post("/v1/sales/orders/" + ref + "/resend-receipt")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ROLE, UserRole.CLIENT.name())
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ID, "user-10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.orderReference").value(ref));
  }
}
