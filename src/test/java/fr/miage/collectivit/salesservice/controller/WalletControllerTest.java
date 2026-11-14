package fr.miage.collectivit.salesservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.miage.collectivit.salesservice.dto.WalletDebitRequest;
import fr.miage.collectivit.salesservice.dto.WalletReloadRequest;
import fr.miage.collectivit.salesservice.dto.WalletResponse;
import fr.miage.collectivit.salesservice.dto.WalletTransactionResponse;
import fr.miage.collectivit.salesservice.exception.GlobalExceptionHandler;
import fr.miage.collectivit.salesservice.model.WalletTransactionType;
import fr.miage.collectivit.salesservice.security.JwtAuthInterceptor;
import fr.miage.collectivit.salesservice.security.UserRole;
import fr.miage.collectivit.salesservice.service.WalletService;
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
@DisplayName("WalletController WebMvc Tests (SLE-15, SLE-17, SLE-18)")
class WalletControllerTest {

  @Mock private WalletService walletService;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  private static final String USER_ID = "a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d";

  @BeforeEach
  void setUp() {
    WalletController controller = new WalletController(walletService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    objectMapper = new ObjectMapper();
  }

  @Test
  @DisplayName("GET /v1/sales/wallet - 200 OK: Consult real-time balance (SLE-17)")
  void shouldReturnWalletBalance() throws Exception {
    WalletResponse response =
        new WalletResponse(
            1L, USER_ID, new BigDecimal("45.50"), "EUR", Instant.now(), Instant.now());
    when(walletService.getWallet(USER_ID)).thenReturn(response);

    mockMvc
        .perform(
            get("/v1/sales/wallet")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ID, USER_ID)
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ROLE, UserRole.CLIENT.name())
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(USER_ID))
        .andExpect(jsonPath("$.balance").value(45.50))
        .andExpect(jsonPath("$.currency").value("EUR"));
  }

  @Test
  @DisplayName("POST /v1/sales/wallet/reload - 200 OK: Reload wallet between 5€ and 100€ (SLE-15)")
  void shouldReloadWalletSuccessfully() throws Exception {
    WalletReloadRequest request = new WalletReloadRequest(new BigDecimal("25.00"), "tok_visa");
    WalletResponse response =
        new WalletResponse(
            1L, USER_ID, new BigDecimal("70.50"), "EUR", Instant.now(), Instant.now());
    when(walletService.reload(eq(USER_ID), any(WalletReloadRequest.class))).thenReturn(response);

    mockMvc
        .perform(
            post("/v1/sales/wallet/reload")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ID, USER_ID)
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ROLE, UserRole.CLIENT.name())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balance").value(70.50));
  }

  @Test
  @DisplayName(
      "POST /v1/sales/wallet/reload - 400 Bad Request: Below 5.00 € minimum floor (SLE-15)")
  void shouldRejectReloadBelow5Euros() throws Exception {
    WalletReloadRequest request = new WalletReloadRequest(new BigDecimal("4.00"), "tok_visa");

    mockMvc
        .perform(
            post("/v1/sales/wallet/reload")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ID, USER_ID)
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ROLE, UserRole.CLIENT.name())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.amount").exists());
  }

  @Test
  @DisplayName(
      "POST /v1/sales/wallet/reload - 400 Bad Request: Above 100.00 € maximum ceiling (SLE-15)")
  void shouldRejectReloadAbove100Euros() throws Exception {
    WalletReloadRequest request = new WalletReloadRequest(new BigDecimal("105.00"), "tok_visa");

    mockMvc
        .perform(
            post("/v1/sales/wallet/reload")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ID, USER_ID)
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ROLE, UserRole.CLIENT.name())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.amount").exists());
  }

  @Test
  @DisplayName("GET /v1/sales/wallet/transactions - 200 OK: Retrieve transaction history (SLE-18)")
  void shouldReturnTransactionHistory() throws Exception {
    WalletTransactionResponse tx1 =
        new WalletTransactionResponse(
            1L,
            "WTX-001",
            new BigDecimal("20.00"),
            WalletTransactionType.TOPUP,
            "SUCCESS",
            "PAY-1",
            null,
            null,
            null,
            "Rechargement CB",
            new BigDecimal("20.00"),
            Instant.now());

    WalletTransactionResponse tx2 =
        new WalletTransactionResponse(
            2L,
            "WTX-002",
            new BigDecimal("8.50"),
            WalletTransactionType.DEBIT,
            "SUCCESS",
            null,
            1L,
            "Boulangerie Le Moulin Doré",
            "ORD-001",
            "Achat pain",
            new BigDecimal("11.50"),
            Instant.now());

    when(walletService.getTransactions(USER_ID)).thenReturn(List.of(tx2, tx1));

    mockMvc
        .perform(
            get("/v1/sales/wallet/transactions")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ID, USER_ID)
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ROLE, UserRole.CLIENT.name())
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].storeName").value("Boulangerie Le Moulin Doré"))
        .andExpect(jsonPath("$[0].type").value("DEBIT"))
        .andExpect(jsonPath("$[1].type").value("TOPUP"));
  }

  @Test
  @DisplayName("POST /v1/sales/wallet/debit - 200 OK: Debit wallet for store purchase (SLE-18)")
  void shouldDebitWalletSuccessfully() throws Exception {
    WalletDebitRequest request =
        new WalletDebitRequest(
            new BigDecimal("10.00"), 2L, "Boucherie des Halles", "ORD-002", "Achat viande");
    WalletResponse response =
        new WalletResponse(
            1L, USER_ID, new BigDecimal("35.50"), "EUR", Instant.now(), Instant.now());

    when(walletService.debit(eq(USER_ID), any(WalletDebitRequest.class))).thenReturn(response);

    mockMvc
        .perform(
            post("/v1/sales/wallet/debit")
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ID, USER_ID)
                .requestAttr(JwtAuthInterceptor.ATTR_USER_ROLE, UserRole.CLIENT.name())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balance").value(35.50));
  }
}
