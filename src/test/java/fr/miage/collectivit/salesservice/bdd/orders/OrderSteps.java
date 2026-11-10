package fr.miage.collectivit.salesservice.bdd.orders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.miage.collectivit.salesservice.bdd.common.AbstractScenario;
import fr.miage.collectivit.salesservice.bdd.common.ScenarioContext;
import fr.miage.collectivit.salesservice.dto.CreateOrderItemRequest;
import fr.miage.collectivit.salesservice.dto.CreateOrderRequest;
import fr.miage.collectivit.salesservice.dto.UpdateOrderStatusRequest;
import fr.miage.collectivit.salesservice.model.Order;
import fr.miage.collectivit.salesservice.model.OrderItem;
import fr.miage.collectivit.salesservice.model.OrderStatus;
import fr.miage.collectivit.salesservice.repository.OrderRepository;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@RequiredArgsConstructor
public class OrderSteps extends AbstractScenario {

  private static final String ORDERS_ENDPOINT = "/v1/sales/orders";

  private final WebApplicationContext webApplicationContext;
  private final ScenarioContext scenarioContext;
  private final OrderRepository orderRepository;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;
  private String currentOrderReference;

  @Before
  public void setUpScenario() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  }

  @Given(
      "a customer creates a multi-store order with {int} items from store {long} at {string} EUR and {int} item from store {long} at {string} EUR")
  public void givenCustomerCreatesMultiStoreOrder(
      int qty1, Long storeId1, String price1, int qty2, Long storeId2, String price2)
      throws Exception {
    CreateOrderItemRequest item1 =
        new CreateOrderItemRequest(
            storeId1, "Store #" + storeId1, 101L, "Product Store 1", new BigDecimal(price1), qty1);
    CreateOrderItemRequest item2 =
        new CreateOrderItemRequest(
            storeId2, "Store #" + storeId2, 201L, "Product Store 2", new BigDecimal(price2), qty2);

    CreateOrderRequest request =
        new CreateOrderRequest(List.of(item1, item2), "CLI-BDD-01", "bdd-customer@example.com");

    ResultActions result =
        mockMvc.perform(
            post(ORDERS_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

    scenarioContext.setLastResult(result);
  }

  @When("an order is created with an empty items list")
  public void whenOrderCreatedWithEmptyItems() throws Exception {
    CreateOrderRequest request = new CreateOrderRequest(List.of(), null, null);

    ResultActions result =
        mockMvc.perform(
            post(ORDERS_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

    scenarioContext.setLastResult(result);
  }

  @Given("an existing order with status {string}")
  public void givenExistingOrderWithStatus(String initialStatus) {
    Order order =
        Order.builder()
            .orderReference("CMD-CC-BDD-12345")
            .customerId("cust-bdd")
            .customerEmail("client@bdd.fr")
            .totalAmount(new BigDecimal("15.00"))
            .currency("EUR")
            .status(OrderStatus.valueOf(initialStatus))
            .expiresAt(Instant.now().plusSeconds(1800))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .items(new ArrayList<>())
            .build();

    OrderItem item =
        OrderItem.builder()
            .order(order)
            .storeId(1L)
            .storeName("Store 1")
            .productId(10L)
            .productName("Item")
            .unitPrice(new BigDecimal("15.00"))
            .quantity(1)
            .totalPrice(new BigDecimal("15.00"))
            .build();
    order.addItem(item);

    Order saved = orderRepository.save(order);
    currentOrderReference = saved.getOrderReference();
  }

  @When("the order status is updated to {string}")
  public void whenOrderStatusIsUpdated(String targetStatus) throws Exception {
    UpdateOrderStatusRequest patchRequest = new UpdateOrderStatusRequest(targetStatus);

    ResultActions result =
        mockMvc.perform(
            patch(ORDERS_ENDPOINT + "/" + currentOrderReference + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(patchRequest)));

    scenarioContext.setLastResult(result);
  }
}
