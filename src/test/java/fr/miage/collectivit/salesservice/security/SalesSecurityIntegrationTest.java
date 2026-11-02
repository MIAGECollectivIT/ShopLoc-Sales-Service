package fr.miage.collectivit.salesservice.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
  @DisplayName("GET /v1/sales/status sans token -> doit renvoyer 401 Unauthorized")
  void shouldDenySalesStatusWithoutToken() throws Exception {
    mockMvc.perform(get("/v1/sales/status")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("GET /v1/sales/status avec JWT valide -> doit renvoyer 200 OK")
  void shouldAllowSalesStatusWithValidToken() throws Exception {
    String token = generateTestToken("CLIENT", "client@shoploc.fr", UUID.randomUUID().toString());
    mockMvc
        .perform(get("/v1/sales/status").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.service").value("sales-service"))
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  @DisplayName("GET /v1/sales/status avec token révoqué -> doit renvoyer 401 Unauthorized")
  void shouldDenySalesStatusWithRevokedToken() throws Exception {
    String revokedJti = UUID.randomUUID().toString();
    jwtValidatorService.addRevokedJtiForTest(revokedJti);

    String token = generateTestToken("CLIENT", "client@shoploc.fr", revokedJti);
    mockMvc
        .perform(get("/v1/sales/status").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }
}
