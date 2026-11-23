package fr.miage.collectivit.salesservice.dto.event;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PhysicalPaymentCompletedEvent(
    @NotBlank String transactionReference,
    @NotNull UUID storeUid,
    @NotBlank String clientCode,
    String customerId,
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    String currency,
    @NotNull Instant timestamp) {}
