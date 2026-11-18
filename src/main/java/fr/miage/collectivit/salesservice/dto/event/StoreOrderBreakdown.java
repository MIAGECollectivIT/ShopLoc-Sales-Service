package fr.miage.collectivit.salesservice.dto.event;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record StoreOrderBreakdown(
    @NotNull Long storeId, String storeName, @NotNull @DecimalMin("0.01") BigDecimal amount) {}
