package fr.miage.collectivit.salesservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import fr.miage.collectivit.salesservice.service.OrderProcessingService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderController WebMvc Tests (SLE-186)")
class OrderControllerTest {

  @Mock private OrderProcessingService orderProcessingService;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new OrderController(orderProcessingService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
  }

  @Test
  @DisplayName("POST /v1/sales/orders should create order and return 201 Created")
  void shouldCreateOrderSuccessfully() throws Exception {
    CreateOrderItemRequest item1 =
        new CreateOrderItemRequest(1L, "Le Moulin", 2L, "Baguette", new BigDecimal("1.35"), 2);
    CreateOrderRequest request =
        new CreateOrderRequest(List.of(item1), "CLI-123", "client@test.fr");

    OrderItemResponse itemResp =
        new OrderItemResponse(
            1L, 1L, "Le Moulin", 2L, "Baguette", new BigDecimal("1.35"), 2, new BigDecimal("2.70"));
    OrderStoreSummaryResponse storeSummary =
        new OrderStoreSummaryResponse(
            1L, "Le Moulin", new BigDecimal("2.70"), 2, List.of(itemResp));

    OrderResponse response =
        new OrderResponse(
            10L,
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

    when(orderProcessingService.createOrder(any(), any(), any())).thenReturn(response);

    mockMvc
        .perform(
            post("/v1/sales/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.orderReference").value("CMD-CC-20261110-9999"))
        .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
        .andExpect(jsonPath("$.totalAmount").value(2.70))
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
        new CreateOrderItemRequest(1L, "Store", 2L, "Product", new BigDecimal("-1.00"), 1);
    CreateOrderRequest request = new CreateOrderRequest(List.of(invalidItem), null, null);

    mockMvc
        .perform(
            post("/v1/sales/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("GET /v1/sales/orders/{ref} should return 200 OK with order payload")
  void shouldGetOrderByReference() throws Exception {
    OrderResponse response =
        new OrderResponse(
            10L,
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
        .perform(get("/v1/sales/orders/CMD-CC-20261110-9999"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orderReference").value("CMD-CC-20261110-9999"))
        .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
  }

  @Test
  @DisplayName("GET /v1/sales/orders/{ref} should return 404 Not Found when order does not exist")
  void shouldReturn404WhenNotFound() throws Exception {
    when(orderProcessingService.getOrderByReference("UNKNOWN"))
        .thenThrow(new OrderNotFoundException("Order with reference UNKNOWN not found"));

    mockMvc
        .perform(get("/v1/sales/orders/UNKNOWN"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Order with reference UNKNOWN not found"));
  }

  @Test
  @DisplayName("PATCH /v1/sales/orders/{ref}/status should return 200 OK with updated order")
  void shouldUpdateOrderStatus() throws Exception {
    UpdateOrderStatusRequest patchRequest = new UpdateOrderStatusRequest("PAID");

    OrderResponse response =
        new OrderResponse(
            10L,
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
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(patchRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAID"));
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
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(patchRequest)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Invalid order status: INVALID_STATUS"));
  }

  @Test
  @DisplayName("POST /v1/sales/orders should return 400 Bad Request when storeId is null")
  void shouldReturn400WhenStoreIdNull() throws Exception {
    CreateOrderItemRequest invalidItem =
        new CreateOrderItemRequest(null, "Store", 2L, "Product", new BigDecimal("5.00"), 1);
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
  @DisplayName("POST /v1/sales/orders should return 400 Bad Request when productId is null")
  void shouldReturn400WhenProductIdNull() throws Exception {
    CreateOrderItemRequest invalidItem =
        new CreateOrderItemRequest(1L, "Store", null, "Product", new BigDecimal("5.00"), 1);
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
        new CreateOrderItemRequest(1L, "Store", 2L, "   ", new BigDecimal("5.00"), 1);
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
}
