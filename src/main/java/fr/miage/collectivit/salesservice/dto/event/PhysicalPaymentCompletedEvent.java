package fr.miage.collectivit.salesservice.dto.event;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public record PhysicalPaymentCompletedEvent(
    @NotBlank String transactionReference,
    @NotNull Long storeId,
    @NotBlank String clientCode,
    String customerId,
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    String currency,
    @NotNull Instant timestamp) {}

