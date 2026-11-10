package fr.miage.collectivit.salesservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Schema(description = "Multi-store order creation request payload")
public record CreateOrderRequest(
    @Schema(description = "List of items across one or multiple partner stores")
        @NotEmpty(message = "Order must contain at least one item")
        List<@Valid CreateOrderItemRequest> items,
    @Schema(description = "Optional client loyalty or barcode", example = "CLI-99482")
        String clientCode,
    @Schema(description = "Optional customer contact email", example = "client@example.com")
        String customerEmail) {}
