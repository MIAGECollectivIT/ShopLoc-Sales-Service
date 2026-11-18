package fr.miage.collectivit.salesservice.dto.event;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderPaidKafkaEvent(
    @NotBlank String orderReference,
    String customerId,
    String clientCode,
    @NotNull @DecimalMin("0.01") BigDecimal totalAmount,
    String currency,
    List<StoreOrderBreakdown> stores,
    @NotNull Instant timestamp) {}
