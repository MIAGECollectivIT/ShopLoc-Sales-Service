package fr.miage.collectivit.salesservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Order status update payload")
public record UpdateOrderStatusRequest(
    @Schema(description = "New order status", example = "PAID")
        @NotBlank(message = "Status is required")
        String status) {}
