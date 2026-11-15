package fr.miage.collectivit.salesservice.exception;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final String KEY_TIMESTAMP = "timestamp";
  private static final String KEY_STATUS = "status";
  private static final String KEY_MESSAGE = "message";

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidationExceptions(
      MethodArgumentNotValidException ex) {
    Map<String, String> errors = new HashMap<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      errors.put(error.getField(), error.getDefaultMessage());
    }
    Map<String, Object> body = new HashMap<>();
    body.put(KEY_TIMESTAMP, Instant.now().toString());
    body.put(KEY_STATUS, HttpStatus.BAD_REQUEST.value());
    body.put("error", "Validation Failed");
    body.put("details", errors);
    return ResponseEntity.badRequest().body(body);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Map<String, Object>> handleMalformedJson(
      HttpMessageNotReadableException ex) {
    return buildResponse(HttpStatus.BAD_REQUEST, "Malformed JSON request payload");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
    return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex) {
    return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage());
  }

  @ExceptionHandler({
    PaymentNotFoundException.class,
    PhysicalSaleNotFoundException.class,
    OrderNotFoundException.class
  })
  public ResponseEntity<Map<String, Object>> handleResourceNotFound(RuntimeException ex) {
    return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler({InsufficientStockException.class, InsufficientWalletBalanceException.class})
  public ResponseEntity<Map<String, Object>> handleInsufficientFundsOrStock(RuntimeException ex) {
    return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(StoreServiceUnavailableException.class)
  public ResponseEntity<Map<String, Object>> handleStoreServiceUnavailable(
      StoreServiceUnavailableException ex) {
    return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
  }

  @ExceptionHandler(PaymentGatewayException.class)
  public ResponseEntity<Map<String, Object>> handlePaymentGatewayFailure(
      PaymentGatewayException ex) {
    return buildResponse(HttpStatus.BAD_GATEWAY, ex.getMessage());
  }

  private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
    Map<String, Object> body = new HashMap<>();
    body.put(KEY_TIMESTAMP, Instant.now().toString());
    body.put(KEY_STATUS, status.value());
    body.put(KEY_MESSAGE, message);
    return ResponseEntity.status(status).body(body);
  }
}
