package fr.miage.collectivit.salesservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(description = "Payment authorization request payload")
public record PaymentAuthorizationRequest(
    @Schema(description = "Transaction amount", example = "42.50")
        @NotNull(message = "Payment amount is required")
        @DecimalMin(value = "0.01", message = "Payment amount must be strictly greater than zero")
        @Digits(
            integer = 8,
            fraction = 2,
            message = "Payment amount must have at most 2 decimal places")
        BigDecimal amount,
    @Schema(description = "ISO 4217 currency code", example = "EUR")
        @NotBlank(message = "Currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid 3-letter ISO code")
        String currency,
    @Schema(description = "Order or parking session reference", example = "ORD-2026-001")
        @NotBlank(message = "Transaction reference is required")
        @Size(max = 128, message = "Transaction reference must not exceed 128 characters")
        String reference,
    @Schema(description = "Simulated card or payment method token", example = "tok_visa_4242")
        @NotBlank(message = "Payment method token is required")
        @Size(max = 128, message = "Payment method token must not exceed 128 characters")
        String paymentToken) {}
