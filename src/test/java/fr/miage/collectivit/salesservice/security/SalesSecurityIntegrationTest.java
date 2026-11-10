package fr.miage.collectivit.salesservice.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class SalesSecurityIntegrationTest {

  private static final String TEST_SECRET =
      "dGhpc2lzYXZlcnlzZWNyZXRqd3R1c2Vyc3NlcnZpY2VrZXkxMjM0NTY=";

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JwtValidatorService jwtValidatorService;

  private MockMvc mockMvc;
  private SecretKey signingKey;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    byte[] keyBytes = Base64.getDecoder().decode(TEST_SECRET);
    signingKey = Keys.hmacShaKeyFor(keyBytes);
  }

  private String generateTestToken(String role, String email, String jti) {
    long now = System.currentTimeMillis();
    return Jwts.builder()
        .id(jti)
        .subject(UUID.randomUUID().toString())
        .issuer("shoploc-auth-service")
        .issuedAt(new Date(now))
        .expiration(new Date(now + 3600000))
        .claim("userId", UUID.randomUUID().toString())
        .claim("email", email)
        .claim("role", role)
        .claim("clientCode", "CL99999")
        .signWith(signingKey)
        .compact();
  }

  @Test
  @DisplayName("GET /v1/sales/status without token -> should return 401 Unauthorized")
  void shouldDenySalesStatusWithoutToken() throws Exception {
    mockMvc.perform(get("/v1/sales/status")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("GET /v1/sales/status with valid JWT -> should return 200 OK")
  void shouldAllowSalesStatusWithValidToken() throws Exception {
    String token = generateTestToken("CLIENT", "client@shoploc.fr", UUID.randomUUID().toString());
    mockMvc
        .perform(get("/v1/sales/status").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.service").value("sales-service"))
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  @DisplayName("GET /v1/sales/status with revoked token -> should return 401 Unauthorized")
  void shouldDenySalesStatusWithRevokedToken() throws Exception {
    String revokedJti = UUID.randomUUID().toString();
    jwtValidatorService.addRevokedJtiForTest(revokedJti);

    String token = generateTestToken("CLIENT", "client@shoploc.fr", revokedJti);
    mockMvc
        .perform(get("/v1/sales/status").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("POST /v1/sales/orders without token -> should return 401 Unauthorized")
  void shouldDenyOrderCreationWithoutToken() throws Exception {
    mockMvc
        .perform(post("/v1/sales/orders").contentType("application/json").content("{\"items\":[]}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName(
      "PATCH /v1/sales/orders/{ref}/status with CLIENT role -> should pass security interceptor")
  void shouldAllowOrderStatusUpdateForClientRole() throws Exception {
    String token = generateTestToken("CLIENT", "client@shoploc.fr", UUID.randomUUID().toString());
    mockMvc
        .perform(
            patch("/v1/sales/orders/CMD-TEST-1234/status")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"status\":\"PAID\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("PATCH /v1/sales/orders/{ref}/status with MERCHANT role -> should pass security")
  void shouldAllowOrderStatusUpdateForMerchantRole() throws Exception {
    String token =
        generateTestToken("MERCHANT", "merchant@shoploc.fr", UUID.randomUUID().toString());
    mockMvc
        .perform(
            patch("/v1/sales/orders/CMD-TEST-1234/status")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"status\":\"PAID\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("POST /v1/sales/physical with CLIENT role -> should return 403 Forbidden")
  void shouldDenyPhysicalSaleForClientRole() throws Exception {
    String token = generateTestToken("CLIENT", "client@shoploc.fr", UUID.randomUUID().toString());
    mockMvc
        .perform(
            post("/v1/sales/physical")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"storeId\":1,\"amount\":10.0}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("POST /v1/sales/payments/authorize without token -> should return 401 Unauthorized")
  void shouldDenyPaymentAuthorizeWithoutToken() throws Exception {
    mockMvc
        .perform(
            post("/v1/sales/payments/authorize")
                .contentType("application/json")
                .content(
                    "{\"amount\":10.00,\"currency\":\"EUR\",\"reference\":\"REF-1\",\"paymentToken\":\"tok_1\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("GET /v1/sales/payments/{ref} without token -> should return 401 Unauthorized")
  void shouldDenyGetPaymentWithoutToken() throws Exception {
    mockMvc.perform(get("/v1/sales/payments/REF-1234")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("GET /v1/sales/physical/{ref} without token -> should return 401 Unauthorized")
  void shouldDenyGetPhysicalSaleWithoutToken() throws Exception {
    mockMvc.perform(get("/v1/sales/physical/PHYS-1234")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("GET /v1/sales/orders without token -> should return 401 Unauthorized")
  void shouldDenyGetOrdersWithoutToken() throws Exception {
    mockMvc.perform(get("/v1/sales/orders")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("GET /v1/sales/orders/{ref} without token -> should return 401 Unauthorized")
  void shouldDenyGetOrderByReferenceWithoutToken() throws Exception {
    mockMvc.perform(get("/v1/sales/orders/CMD-1234")).andExpect(status().isUnauthorized());
  }
}
