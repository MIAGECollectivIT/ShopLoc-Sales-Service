package fr.miage.collectivit.salesservice.controller;

import fr.miage.collectivit.salesservice.dto.CreateOrderRequest;
import fr.miage.collectivit.salesservice.dto.OrderResponse;
import fr.miage.collectivit.salesservice.dto.UpdateOrderStatusRequest;
import fr.miage.collectivit.salesservice.security.JwtAuthInterceptor;
import fr.miage.collectivit.salesservice.service.OrderProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/sales/orders")
@Tag(
    name = "Orders",
    description = "Multi-store click & collect order lifecycle and lock management (SLE-186)")
@RequiredArgsConstructor
public class OrderController {

  private final OrderProcessingService orderProcessingService;

  @PostMapping
  @Operation(
      summary = "Create multi-store click & collect order",
      description =
          "Validates cart line items, calculates amounts per merchant store, locks the order under PENDING_PAYMENT status and initializes the expiration window.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "201",
            description = "Order created and locked under PENDING_PAYMENT"),
        @ApiResponse(responseCode = "400", description = "Invalid order payload or empty items")
      })
  public ResponseEntity<OrderResponse> createOrder(
      @Valid @RequestBody CreateOrderRequest requestDto, HttpServletRequest request) {
    String userId = (String) request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID);
    String userEmail = (String) request.getAttribute(JwtAuthInterceptor.ATTR_USER_EMAIL);

    OrderResponse response = orderProcessingService.createOrder(requestDto, userId, userEmail);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/{reference}")
  @Operation(
      summary = "Retrieve order by reference",
      description = "Returns order details, items and merchant breakdowns for the given reference.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Order found"),
        @ApiResponse(responseCode = "404", description = "Order not found")
      })
  public ResponseEntity<OrderResponse> getOrderByReference(@PathVariable String reference) {
    return ResponseEntity.ok(orderProcessingService.getOrderByReference(reference));
  }

  @PatchMapping("/{reference}/status")
  @Operation(
      summary = "Update order status",
      description =
          "Transitions the order status (e.g. to PAID once payment is authorized, or CANCELLED).")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Order status updated"),
        @ApiResponse(responseCode = "400", description = "Invalid status value"),
        @ApiResponse(responseCode = "404", description = "Order not found")
      })
  public ResponseEntity<OrderResponse> updateOrderStatus(
      @PathVariable String reference, @Valid @RequestBody UpdateOrderStatusRequest requestDto) {
    return ResponseEntity.ok(
        orderProcessingService.updateOrderStatus(reference, requestDto.status()));
  }
}
