package fr.miage.collectivit.salesservice.controller;

import fr.miage.collectivit.salesservice.dto.CreateOrderRequest;
import fr.miage.collectivit.salesservice.dto.OrderResponse;
import fr.miage.collectivit.salesservice.dto.UpdateOrderStatusRequest;
import fr.miage.collectivit.salesservice.exception.ForbiddenException;
import fr.miage.collectivit.salesservice.security.JwtAuthInterceptor;
import fr.miage.collectivit.salesservice.security.RequiresRole;
import fr.miage.collectivit.salesservice.security.UserRole;
import fr.miage.collectivit.salesservice.service.OrderProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
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

  private static final String CODE_401 = "401";
  private static final String MSG_UNAUTHORIZED = "Unauthorized - Bearer token required";

  private final OrderProcessingService orderProcessingService;

  @PostMapping
  @RequiresRole({UserRole.CLIENT, UserRole.MERCHANT, UserRole.ADMIN})
  @Operation(
      summary = "Create multi-store click & collect order",
      description =
          "Validates cart line items, calculates amounts per merchant store, locks the order under PENDING_PAYMENT status and initializes the expiration window.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "201",
            description = "Order created and locked under PENDING_PAYMENT"),
        @ApiResponse(responseCode = "400", description = "Invalid order payload or empty items"),
        @ApiResponse(responseCode = CODE_401, description = MSG_UNAUTHORIZED)
      })
  public ResponseEntity<OrderResponse> createOrder(
      @Valid @RequestBody CreateOrderRequest requestDto, HttpServletRequest request) {
    String userId = (String) request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID);
    String userEmail = (String) request.getAttribute(JwtAuthInterceptor.ATTR_USER_EMAIL);

    OrderResponse response = orderProcessingService.createOrder(requestDto, userId, userEmail);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping
  @RequiresRole({UserRole.CLIENT, UserRole.MERCHANT, UserRole.ADMIN})
  @Operation(
      summary = "List orders for authenticated caller",
      description =
          "Returns the order history for the caller (or all orders if caller is an administrator).")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Orders retrieved successfully"),
        @ApiResponse(responseCode = CODE_401, description = MSG_UNAUTHORIZED)
      })
  public ResponseEntity<List<OrderResponse>> getOrders(HttpServletRequest request) {
    String role = (String) request.getAttribute(JwtAuthInterceptor.ATTR_USER_ROLE);
    String userId = (String) request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID);

    return ResponseEntity.ok(orderProcessingService.getOrders(userId, role));
  }

  @GetMapping("/{reference}")
  @RequiresRole({UserRole.CLIENT, UserRole.MERCHANT, UserRole.ADMIN})
  @Operation(
      summary = "Retrieve order by reference",
      description = "Returns order details, items and merchant breakdowns for the given reference.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Order found"),
        @ApiResponse(responseCode = CODE_401, description = MSG_UNAUTHORIZED),
        @ApiResponse(
            responseCode = "403",
            description =
                "Forbidden - Caller cannot access an order belonging to another customer"),
        @ApiResponse(responseCode = "404", description = "Order not found")
      })
  public ResponseEntity<OrderResponse> getOrderByReference(
      @PathVariable String reference, HttpServletRequest request) {
    String role = (String) request.getAttribute(JwtAuthInterceptor.ATTR_USER_ROLE);
    String userId = (String) request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID);

    OrderResponse order = orderProcessingService.getOrderByReference(reference);
    verifyOrderReadAccess(order, role, userId);
    return ResponseEntity.ok(order);
  }

  @PatchMapping("/{reference}/status")
  @RequiresRole({UserRole.MERCHANT, UserRole.ADMIN})
  @Operation(
      summary = "Update order status",
      description =
          "Transitions the order status (e.g. to PAID once payment is authorized, or CANCELLED). Only merchants and administrators are allowed.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Order status updated"),
        @ApiResponse(responseCode = "400", description = "Invalid status value"),
        @ApiResponse(responseCode = CODE_401, description = MSG_UNAUTHORIZED),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Role MERCHANT or ADMIN required"),
        @ApiResponse(responseCode = "404", description = "Order not found")
      })
  public ResponseEntity<OrderResponse> updateOrderStatus(
      @PathVariable String reference, @Valid @RequestBody UpdateOrderStatusRequest requestDto) {
    return ResponseEntity.ok(
        orderProcessingService.updateOrderStatus(reference, requestDto.status()));
  }

  private void verifyOrderReadAccess(OrderResponse order, String role, String userId) {
    if (UserRole.ADMIN.name().equalsIgnoreCase(role)
        || UserRole.MERCHANT.name().equalsIgnoreCase(role)) {
      return;
    }

    boolean isOwner = userId != null && userId.equals(order.customerId());
    if (isOwner) {
      return;
    }

    if (UserRole.CLIENT.name().equalsIgnoreCase(role)) {
      throw new ForbiddenException("Access denied: you can only consult your own orders");
    }

    throw new ForbiddenException("Access denied: you do not have permission to view this order");
  }
}
