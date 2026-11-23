package fr.miage.collectivit.salesservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.miage.collectivit.salesservice.dto.PhysicalSaleRequest;
import fr.miage.collectivit.salesservice.dto.PhysicalSaleResponse;
import fr.miage.collectivit.salesservice.exception.GlobalExceptionHandler;
import fr.miage.collectivit.salesservice.security.JwtAuthInterceptor;
import fr.miage.collectivit.salesservice.service.PhysicalSaleService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
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
@DisplayName("PhysicalSaleController WebMvc Tests")
class PhysicalSaleControllerTest {

  @Mock private PhysicalSaleService physicalSaleService;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new PhysicalSaleController(physicalSaleService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
  }

  @Test
  @DisplayName("POST /v1/sales/physical should return 201 Created for MERCHANT")
  void shouldRecordPhysicalSale() throws Exception {
    UUID storeUid = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID saleUid = UUID.fromString("99999999-9999-9999-9999-999999999999");
    PhysicalSaleRequest request =
        new PhysicalSaleRequest(
            storeUid, "CLI-9999", "cust-1", new BigDecimal("25.00"), "EUR", "CARD");

    PhysicalSaleResponse response =
        new PhysicalSaleResponse(
            saleUid,
            "TX-PHYS-1234",
            storeUid,
            "merchant-1",
            "CLI-9999",
            "cust-1",
            new BigDecimal("25.00"),
            "EUR",
            new BigDecimal("5.0"),
            "5 EUR = 1 PTS",
            "CARD",
            "COMPLETED",
            Instant.now().toString());

    when(physicalSaleService.recordPhysicalSale(any(PhysicalSaleRequest.class), eq("merchant-1")))
        .thenReturn(response);

    mockMvc
        .perform(
            post("/v1/sales/physical")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ROLE, "MERCHANT")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ID, "merchant-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.uid").value(saleUid.toString()))
        .andExpect(jsonPath("$.storeUid").value(storeUid.toString()))
        .andExpect(jsonPath("$.transactionReference").value("TX-PHYS-1234"));
  }

  @Test
  @DisplayName("GET /v1/sales/physical/{ref} should return 200 OK for owner merchant")
  void shouldAllowOwnerMerchantToViewPhysicalSale() throws Exception {
    UUID storeUid = UUID.fromString("11111111-1111-1111-1111-111111111111");
    PhysicalSaleResponse response =
        new PhysicalSaleResponse(
            UUID.randomUUID(),
            "TX-PHYS-1234",
            storeUid,
            "merchant-1",
            "CLI-9999",
            "cust-1",
            new BigDecimal("25.00"),
            "EUR",
            new BigDecimal("5.0"),
            "5 EUR = 1 PTS",
            "CARD",
            "COMPLETED",
            Instant.now().toString());

    when(physicalSaleService.getByReference("TX-PHYS-1234")).thenReturn(response);

    mockMvc
        .perform(
            get("/v1/sales/physical/TX-PHYS-1234")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ROLE, "MERCHANT")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ID, "merchant-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactionReference").value("TX-PHYS-1234"));
  }

  @Test
  @DisplayName("GET /v1/sales/physical/{ref} should return 403 Forbidden for other merchant")
  void shouldDenyOtherMerchantAccess() throws Exception {
    UUID storeUid = UUID.fromString("11111111-1111-1111-1111-111111111111");
    PhysicalSaleResponse response =
        new PhysicalSaleResponse(
            UUID.randomUUID(),
            "TX-PHYS-1234",
            storeUid,
            "merchant-1",
            "CLI-9999",
            "cust-1",
            new BigDecimal("25.00"),
            "EUR",
            new BigDecimal("5.0"),
            "5 EUR = 1 PTS",
            "CARD",
            "COMPLETED",
            Instant.now().toString());

    when(physicalSaleService.getByReference("TX-PHYS-1234")).thenReturn(response);

    mockMvc
        .perform(
            get("/v1/sales/physical/TX-PHYS-1234")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ROLE, "MERCHANT")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ID, "other-merchant"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "GET /v1/sales/physical/{ref} should return 403 when customerId is null and caller is CLIENT")
  void shouldDenyClientWhenCustomerIdNull() throws Exception {
    UUID storeUid = UUID.fromString("11111111-1111-1111-1111-111111111111");
    PhysicalSaleResponse response =
        new PhysicalSaleResponse(
            UUID.randomUUID(),
            "TX-PHYS-1234",
            storeUid,
            "merchant-1",
            "CLI-9999",
            null,
            new BigDecimal("25.00"),
            "EUR",
            new BigDecimal("5.0"),
            "5 EUR = 1 PTS",
            "CARD",
            "COMPLETED",
            Instant.now().toString());

    when(physicalSaleService.getByReference("TX-PHYS-1234")).thenReturn(response);

    mockMvc
        .perform(
            get("/v1/sales/physical/TX-PHYS-1234")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ROLE, "CLIENT")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ID, "cust-1"))
        .andExpect(status().isForbidden());
  }
}
