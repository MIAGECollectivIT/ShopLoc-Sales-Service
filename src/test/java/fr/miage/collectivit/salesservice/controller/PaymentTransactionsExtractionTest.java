package fr.miage.collectivit.salesservice.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.miage.collectivit.salesservice.model.PaymentTransaction;
import fr.miage.collectivit.salesservice.repository.PaymentTransactionRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class PaymentTransactionsExtractionTest {

  private static final String TEST_SECRET =
      "dGhpc2lzYXZlcnlzZWNyZXRqd3R1c2Vyc3NlcnZpY2VrZXkxMjM0NTY=";

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private PaymentTransactionRepository paymentTransactionRepository;

  private MockMvc mockMvc;
  private SecretKey signingKey;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    byte[] keyBytes = Base64.getDecoder().decode(TEST_SECRET);
    signingKey = Keys.hmacShaKeyFor(keyBytes);
    paymentTransactionRepository.deleteAll();
  }

  private String generateTestToken(String userId, String role) {
    long now = System.currentTimeMillis();
    return Jwts.builder()
        .id(UUID.randomUUID().toString())
        .subject(userId)
        .issuer("shoploc-auth-service")
        .issuedAt(new Date(now))
        .expiration(new Date(now + 3600000))
        .claim("userId", userId)
        .claim("email", userId + "@example.com")
        .claim("role", role)
        .signWith(signingKey)
        .compact();
  }

  @Test
  @DisplayName("SLE-160: Missing Authorization header should return 401 Unauthorized")
  void testExtractTransactionsWithoutToken() throws Exception {
    mockMvc
        .perform(
            get("/v1/sales/payments/users/USER-1/transactions")
                .param("from", Instant.now().minus(15, ChronoUnit.DAYS).toString())
                .param("to", Instant.now().toString()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("SLE-160: Client querying another user's transactions should return 403 Forbidden")
  void testExtractTransactionsForbiddenForOtherUser() throws Exception {
    String token = generateTestToken("USER-1", "CLIENT");
    mockMvc
        .perform(
            get("/v1/sales/payments/users/USER-2/transactions")
                .header("Authorization", "Bearer " + token)
                .param("from", Instant.now().minus(15, ChronoUnit.DAYS).toString())
                .param("to", Instant.now().toString()))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("SLE-160: Missing date parameters should return 400 Bad Request")
  void testExtractTransactionsMissingParams() throws Exception {
    String token = generateTestToken("USER-1", "CLIENT");
    mockMvc
        .perform(
            get("/v1/sales/payments/users/USER-1/transactions")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("SLE-160: Invalid date range (from after to) should return 400 Bad Request")
  void testExtractTransactionsInvalidDateRange() throws Exception {
    String token = generateTestToken("USER-1", "CLIENT");
    Instant now = Instant.now();
    mockMvc
        .perform(
            get("/v1/sales/payments/users/USER-1/transactions")
                .header("Authorization", "Bearer " + token)
                .param("from", now.toString())
                .param("to", now.minus(5, ChronoUnit.DAYS).toString()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "SLE-160: Client querying own transactions extracts only approved transactions in interval")
  void testExtractTransactionsNominal() throws Exception {
    String userId = "USER-42";
    Instant now = Instant.now();
    Instant windowStart = now.minus(15, ChronoUnit.DAYS);

    // 1. Transaction approved within window
    paymentTransactionRepository.save(
        PaymentTransaction.builder()
            .transactionId("TXN-1")
            .reference("REF-1")
            .amount(BigDecimal.valueOf(15.50))
            .currency("EUR")
            .status(PaymentTransaction.RG001_APPROVED_STATUS)
            .authorizationCode("AUTH-1")
            .paymentMethodToken("TOK-1")
            .userId(userId)
            .createdAt(now.minus(2, ChronoUnit.DAYS))
            .build());

    // 2. Transaction approved within window (same day, different hour)
    paymentTransactionRepository.save(
        PaymentTransaction.builder()
            .transactionId("TXN-2")
            .reference("REF-2")
            .amount(BigDecimal.valueOf(25.00))
            .currency("EUR")
            .status(PaymentTransaction.RG001_APPROVED_STATUS)
            .authorizationCode("AUTH-2")
            .paymentMethodToken("TOK-2")
            .userId(userId)
            .createdAt(now.minus(2, ChronoUnit.DAYS).plusSeconds(3600))
            .build());

    // 3. Transaction rejected (REJECTED) within window -> should be EXCLUDED
    paymentTransactionRepository.save(
        PaymentTransaction.builder()
            .transactionId("TXN-REJECTED")
            .reference("REF-REJECTED")
            .amount(BigDecimal.valueOf(50.00))
            .currency("EUR")
            .status("TRANSACTION_REJECTED")
            .authorizationCode("AUTH-REJ")
            .paymentMethodToken("TOK-REJ")
            .userId(userId)
            .createdAt(now.minus(3, ChronoUnit.DAYS))
            .build());

    // 4. Transaction approved but BEFORE window (> 15 days ago) -> should be EXCLUDED
    paymentTransactionRepository.save(
        PaymentTransaction.builder()
            .transactionId("TXN-OLD")
            .reference("REF-OLD")
            .amount(BigDecimal.valueOf(10.00))
            .currency("EUR")
            .status(PaymentTransaction.RG001_APPROVED_STATUS)
            .authorizationCode("AUTH-OLD")
            .paymentMethodToken("TOK-OLD")
            .userId(userId)
            .createdAt(now.minus(20, ChronoUnit.DAYS))
            .build());

    // 5. Transaction for ANOTHER user -> should be EXCLUDED
    paymentTransactionRepository.save(
        PaymentTransaction.builder()
            .transactionId("TXN-OTHER")
            .reference("REF-OTHER")
            .amount(BigDecimal.valueOf(10.00))
            .currency("EUR")
            .status(PaymentTransaction.RG001_APPROVED_STATUS)
            .authorizationCode("AUTH-OTHER")
            .paymentMethodToken("TOK-OTHER")
            .userId("OTHER-USER")
            .createdAt(now.minus(1, ChronoUnit.DAYS))
            .build());

    String token = generateTestToken(userId, "CLIENT");

    // Nominal query via /v1/sales/payments/users/...
    mockMvc
        .perform(
            get("/v1/sales/payments/users/" + userId + "/transactions")
                .header("Authorization", "Bearer " + token)
                .param("from", windowStart.toString())
                .param("to", now.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].status").value(PaymentTransaction.RG001_APPROVED_STATUS))
        .andExpect(jsonPath("$[1].status").value(PaymentTransaction.RG001_APPROVED_STATUS));

    // Also verify alias /api/v1/payments/users/...
    mockMvc
        .perform(
            get("/api/v1/payments/users/" + userId + "/transactions")
                .header("Authorization", "Bearer " + token)
                .param("from", windowStart.toString())
                .param("to", now.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)));
  }

  @Test
  @DisplayName("SLE-160: Admin can query any user's transactions")
  void testExtractTransactionsAdminAccess() throws Exception {
    String userId = "USER-99";
    Instant now = Instant.now();

    paymentTransactionRepository.save(
        PaymentTransaction.builder()
            .transactionId("TXN-ADMIN-TEST")
            .reference("REF-ADM")
            .amount(BigDecimal.valueOf(100.00))
            .currency("EUR")
            .status(PaymentTransaction.RG001_APPROVED_STATUS)
            .authorizationCode("AUTH-ADM")
            .paymentMethodToken("TOK-ADM")
            .userId(userId)
            .createdAt(now.minus(1, ChronoUnit.DAYS))
            .build());

    String adminToken = generateTestToken("ADMIN-1", "ADMIN");

    mockMvc
        .perform(
            get("/v1/sales/payments/users/" + userId + "/transactions")
                .header("Authorization", "Bearer " + adminToken)
                .param("from", now.minus(5, ChronoUnit.DAYS).toString())
                .param("to", now.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].reference").value("REF-ADM"));
  }
}
