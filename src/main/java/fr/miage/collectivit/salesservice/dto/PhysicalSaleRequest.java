package fr.miage.collectivit.salesservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(description = "In-store physical checkout transaction registration request")
public record PhysicalSaleRequest(
    @Schema(description = "Store identifier", example = "10")
        @NotNull(message = "Store identifier is mandatory")
        @Positive(message = "Store identifier must be positive")
        Long storeId,
    @Schema(description = "Customer 7-character loyalty code", example = "k7P9xQ2")
        @NotBlank(message = "Client loyalty code is mandatory")
        @Size(min = 3, max = 32, message = "Client code must be between 3 and 32 characters")
        String clientCode,
    @Schema(
            description = "Optional public customer identifier UUID",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        String customerId,
    @Schema(
            description = "Checkout purchase amount (positive with max 2 decimals)",
            example = "25.50")
        @NotNull(message = "Amount is mandatory")
        @DecimalMin(value = "0.01", message = "Amount must be strictly positive")
        @Digits(integer = 8, fraction = 2, message = "Amount must have at most 2 decimal places")
        BigDecimal amount,
    @Schema(description = "Currency code (default EUR)", example = "EUR")
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid 3-letter ISO code")
        String currency,
    @Schema(description = "Physical payment method (e.g. CASH, CARD)", example = "CASH")
        String paymentMethod) {}
