package fr.miage.collectivit.salesservice.dto.event;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record StoreOrderBreakdown(
    @NotNull UUID storeUid, String storeName, @NotNull @DecimalMin("0.01") BigDecimal amount) {}
