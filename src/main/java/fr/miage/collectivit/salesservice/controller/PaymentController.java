package fr.miage.collectivit.salesservice.controller;

import fr.miage.collectivit.salesservice.dto.PaymentAuthorizationRequest;
import fr.miage.collectivit.salesservice.dto.PaymentAuthorizationResponse;
import fr.miage.collectivit.salesservice.security.JwtAuthInterceptor;
import fr.miage.collectivit.salesservice.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/sales/payments")
@Tag(
    name = "Payments",
    description = "Bank payment authorization and transaction audit endpoints (RG001)")
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentService paymentService;

  @PostMapping("/authorize")
  @Operation(
      summary = "Authorize a payment transaction",
      description =
          "Forwards the payment request to the external bank gateway emulator, enforces RG001 approval, and persists the transaction audit record.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Payment authorized under RG001"),
        @ApiResponse(responseCode = "400", description = "Invalid payment request payload"),
        @ApiResponse(responseCode = "502", description = "External bank gateway unavailable")
      })
  public ResponseEntity<PaymentAuthorizationResponse> authorizePayment(
      @Valid @RequestBody PaymentAuthorizationRequest requestDto, HttpServletRequest request) {
    String userId = (String) request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID);
    return ResponseEntity.ok(paymentService.authorizePayment(requestDto, userId));
  }

  @GetMapping("/{reference}")
  @Operation(
      summary = "Retrieve a payment transaction by reference",
      description = "Returns the most recent payment authorization record matching the reference.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Payment transaction found"),
        @ApiResponse(responseCode = "404", description = "Payment transaction not found")
      })
  public ResponseEntity<PaymentAuthorizationResponse> getPaymentByReference(
      @PathVariable String reference) {
    return ResponseEntity.ok(paymentService.getByReference(reference));
  }
}
