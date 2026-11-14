package fr.miage.collectivit.salesservice.controller;

import fr.miage.collectivit.salesservice.dto.WalletDebitRequest;
import fr.miage.collectivit.salesservice.dto.WalletReloadRequest;
import fr.miage.collectivit.salesservice.dto.WalletResponse;
import fr.miage.collectivit.salesservice.dto.WalletTransactionResponse;
import fr.miage.collectivit.salesservice.exception.ForbiddenException;
import fr.miage.collectivit.salesservice.security.JwtAuthInterceptor;
import fr.miage.collectivit.salesservice.security.RequiresRole;
import fr.miage.collectivit.salesservice.security.UserRole;
import fr.miage.collectivit.salesservice.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/sales/wallet")
@Tag(
    name = "Wallet",
    description =
        "Digital citizen wallet, real-time balance, online bank reloads, and transaction history (SLE-15, SLE-17, SLE-18)")
@RequiredArgsConstructor
@SuppressWarnings({"PMD.AvoidDuplicateLiterals"})
public class WalletController {

  private static final String CODE_401 = "401";
  private static final String MSG_UNAUTHORIZED = "Unauthorized - Bearer token required";

  private final WalletService walletService;

  @GetMapping
  @RequiresRole({UserRole.CLIENT, UserRole.MERCHANT, UserRole.ADMIN})
  @Operation(
      summary = "Consult digital wallet balance in real time (SLE-17)",
      description =
          "Retrieves the current euro balance and details of the authenticated citizen's wallet.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Wallet retrieved successfully"),
    @ApiResponse(responseCode = CODE_401, description = MSG_UNAUTHORIZED),
    @ApiResponse(responseCode = "403", description = "Forbidden")
  })
  public ResponseEntity<WalletResponse> getWallet(
      @RequestParam(value = "userId", required = false) String requestedUserId,
      HttpServletRequest request) {
    String authenticatedUserId = resolveUserId(requestedUserId, request);
    return ResponseEntity.ok(walletService.getWallet(authenticatedUserId));
  }

  @PostMapping("/reload")
  @RequiresRole({UserRole.CLIENT, UserRole.MERCHANT, UserRole.ADMIN})
  @Operation(
      summary = "Reload wallet online by credit card (SLE-15)",
      description =
          "Recharges the digital wallet with a custom amount between 5 € and 100 € through the bank payment gateway.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Wallet reloaded successfully"),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid reload amount (outside 5€ - 100€ range or weekly limit exceeded)"),
    @ApiResponse(responseCode = CODE_401, description = MSG_UNAUTHORIZED),
    @ApiResponse(responseCode = "403", description = "Forbidden"),
    @ApiResponse(responseCode = "502", description = "Bank payment gateway rejected authorization")
  })
  public ResponseEntity<WalletResponse> reloadWallet(
      @Valid @RequestBody WalletReloadRequest reloadRequest,
      @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyHeader,
      @RequestParam(value = "userId", required = false) String requestedUserId,
      HttpServletRequest request) {
    String authenticatedUserId = resolveUserId(requestedUserId, request);
    String idempotencyKey =
        (idempotencyHeader != null && !idempotencyHeader.isBlank())
            ? idempotencyHeader.trim()
            : reloadRequest.idempotencyKey();
    return ResponseEntity.ok(
        walletService.reload(authenticatedUserId, reloadRequest, idempotencyKey));
  }

  @GetMapping("/transactions")
  @RequiresRole({UserRole.CLIENT, UserRole.MERCHANT, UserRole.ADMIN})
  @Operation(
      summary = "Consult transaction history in real time (SLE-18)",
      description =
          "Returns the ordered list of credit and debit transactions with amounts and partner store names.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Transaction history retrieved successfully"),
    @ApiResponse(responseCode = CODE_401, description = MSG_UNAUTHORIZED),
    @ApiResponse(responseCode = "403", description = "Forbidden")
  })
  public ResponseEntity<List<WalletTransactionResponse>> getTransactions(
      @RequestParam(value = "userId", required = false) String requestedUserId,
      HttpServletRequest request) {
    String authenticatedUserId = resolveUserId(requestedUserId, request);
    return ResponseEntity.ok(walletService.getTransactions(authenticatedUserId));
  }

  @PostMapping("/debit")
  @RequiresRole({UserRole.CLIENT, UserRole.MERCHANT, UserRole.ADMIN})
  @Operation(
      summary = "Debit wallet for a purchase (SLE-18)",
      description =
          "Deducts an amount from the wallet for a store purchase and records the transaction.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Wallet debited successfully"),
    @ApiResponse(responseCode = "400", description = "Insufficient funds or invalid amount"),
    @ApiResponse(responseCode = CODE_401, description = MSG_UNAUTHORIZED),
    @ApiResponse(responseCode = "403", description = "Forbidden")
  })
  public ResponseEntity<WalletResponse> debitWallet(
      @Valid @RequestBody WalletDebitRequest debitRequest,
      @RequestParam(value = "userId", required = false) String requestedUserId,
      HttpServletRequest request) {
    String authenticatedUserId = resolveUserId(requestedUserId, request);
    return ResponseEntity.ok(walletService.debit(authenticatedUserId, debitRequest));
  }

  private String resolveUserId(String requestedUserId, HttpServletRequest request) {
    String authUserId = (String) request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID);
    String authRole = (String) request.getAttribute(JwtAuthInterceptor.ATTR_USER_ROLE);

    // Only administrators are permitted to query or operate on behalf of another user ID
    if (UserRole.ADMIN.name().equalsIgnoreCase(authRole)
        && requestedUserId != null
        && !requestedUserId.isBlank()) {
      return requestedUserId.trim();
    }
    // All other users are strictly bound to their authenticated token subject
    if (authUserId != null && !authUserId.isBlank()) {
      return authUserId.trim();
    }
    throw new ForbiddenException("Missing authenticated user context");
  }
}
