package fr.miage.collectivit.salesservice.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

@DisplayName("GlobalExceptionHandler RFC 7807 ProblemDetail Tests")
class GlobalExceptionHandlerProblemDetailTest {

  private GlobalExceptionHandler handler;

  @BeforeEach
  void setUp() {
    handler = new GlobalExceptionHandler();
  }

  @Test
  @DisplayName("Should format PaymentNotFoundException as RFC 7807 404 ProblemDetail")
  void shouldFormatNotFoundAsProblemDetail() {
    PaymentNotFoundException ex = new PaymentNotFoundException("REF-404");

    ProblemDetail problem = handler.handleResourceNotFound(ex);

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    assertThat(problem.getDetail()).contains("REF-404");
    assertThat(problem.getType().toString()).contains("resource-not-found");
    assertThat(problem.getProperties()).containsKey("timestamp");
    assertThat(problem.getProperties()).containsEntry("status", 404);
    assertThat(problem.getProperties()).containsEntry("error", "Not Found");
  }

  @Test
  @DisplayName("Should format ForbiddenException as RFC 7807 403 ProblemDetail")
  void shouldFormatForbiddenAsProblemDetail() {
    ForbiddenException ex = new ForbiddenException("Access denied");

    ProblemDetail problem = handler.handleForbidden(ex);

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    assertThat(problem.getDetail()).isEqualTo("Access denied");
    assertThat(problem.getType().toString()).contains("forbidden");
    assertThat(problem.getProperties()).containsEntry("error", "Forbidden");
  }

  @Test
  @DisplayName("Should format InsufficientWalletBalanceException as RFC 7807 400 ProblemDetail")
  void shouldFormatInsufficientFundsAsProblemDetail() {
    InsufficientWalletBalanceException ex =
        new InsufficientWalletBalanceException("Solde insuffisant");

    ProblemDetail problem = handler.handleInsufficientFundsOrStock(ex);

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(problem.getDetail()).isEqualTo("Solde insuffisant");
    assertThat(problem.getType().toString()).contains("insufficient-funds-or-stock");
  }

  @Test
  @DisplayName("Should format PaymentGatewayException as RFC 7807 502 Bad Gateway ProblemDetail")
  void shouldFormatPaymentGatewayExceptionAsProblemDetail() {
    PaymentGatewayException ex = new PaymentGatewayException("Gateway timeout");

    ProblemDetail problem = handler.handlePaymentGatewayFailure(ex);

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
    assertThat(problem.getDetail()).isEqualTo("Gateway timeout");
    assertThat(problem.getType().toString()).contains("payment-gateway-failure");
  }
}
