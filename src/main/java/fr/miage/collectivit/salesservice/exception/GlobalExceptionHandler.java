package fr.miage.collectivit.salesservice.exception;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Global exception handler conforming to RFC 7807 (ProblemDetail). Maintains backward compatibility
 * with legacy clients and tests through custom properties.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final String PROBLEM_BASE_URL = "https://errors.shoploc.com/";
  private static final String KEY_TIMESTAMP = "timestamp";
  private static final String KEY_STATUS = "status";
  private static final String KEY_MESSAGE = "message";
  private static final String KEY_ERROR = "error";

  private ProblemDetail createProblem(HttpStatus status, String type, String message) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
    problem.setType(URI.create(PROBLEM_BASE_URL + type));
    problem.setTitle(status.getReasonPhrase());
    problem.setProperty(KEY_TIMESTAMP, Instant.now().toString());
    problem.setProperty(KEY_STATUS, status.value());
    problem.setProperty(KEY_ERROR, status.getReasonPhrase());
    problem.setProperty(KEY_MESSAGE, message);
    return problem;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidationExceptions(MethodArgumentNotValidException ex) {
    Map<String, String> errors = new HashMap<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      errors.put(error.getField(), error.getDefaultMessage());
    }
    ProblemDetail problem =
        createProblem(HttpStatus.BAD_REQUEST, "validation-error", "Validation Failed");
    problem.setProperty("error", "Validation Failed");
    problem.setProperty("details", errors);
    problem.setProperty("invalid_params", errors);
    return problem;
  }

  @SuppressWarnings("PMD.LawOfDemeter")
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    String message =
        UUID.class.equals(ex.getRequiredType())
            ? "Invalid UUID format for parameter '" + ex.getName() + "'"
            : "Invalid parameter '" + ex.getName() + "': " + ex.getValue();
    return createProblem(HttpStatus.BAD_REQUEST, "type-mismatch", message);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ProblemDetail handleMalformedJson(HttpMessageNotReadableException ex) {
    return createProblem(
        HttpStatus.BAD_REQUEST, "malformed-json", "Malformed JSON request payload");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
    return createProblem(HttpStatus.BAD_REQUEST, "illegal-argument", ex.getMessage());
  }

  @ExceptionHandler(ForbiddenException.class)
  public ProblemDetail handleForbidden(ForbiddenException ex) {
    return createProblem(HttpStatus.FORBIDDEN, "forbidden", ex.getMessage());
  }

  @ExceptionHandler({
    PaymentNotFoundException.class,
    PhysicalSaleNotFoundException.class,
    OrderNotFoundException.class
  })
  public ProblemDetail handleResourceNotFound(RuntimeException ex) {
    return createProblem(HttpStatus.NOT_FOUND, "resource-not-found", ex.getMessage());
  }

  @ExceptionHandler({InsufficientStockException.class, InsufficientWalletBalanceException.class})
  public ProblemDetail handleInsufficientFundsOrStock(RuntimeException ex) {
    return createProblem(HttpStatus.BAD_REQUEST, "insufficient-funds-or-stock", ex.getMessage());
  }

  @ExceptionHandler(StoreServiceUnavailableException.class)
  public ProblemDetail handleStoreServiceUnavailable(StoreServiceUnavailableException ex) {
    return createProblem(
        HttpStatus.SERVICE_UNAVAILABLE, "store-service-unavailable", ex.getMessage());
  }

  @ExceptionHandler(PaymentGatewayException.class)
  public ProblemDetail handlePaymentGatewayFailure(PaymentGatewayException ex) {
    return createProblem(HttpStatus.BAD_GATEWAY, "payment-gateway-failure", ex.getMessage());
  }
}
