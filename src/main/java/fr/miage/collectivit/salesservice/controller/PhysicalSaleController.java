package fr.miage.collectivit.salesservice.controller;

import fr.miage.collectivit.salesservice.dto.PhysicalSaleRequest;
import fr.miage.collectivit.salesservice.dto.PhysicalSaleResponse;
import fr.miage.collectivit.salesservice.exception.ForbiddenException;
import fr.miage.collectivit.salesservice.security.JwtAuthInterceptor;
import fr.miage.collectivit.salesservice.service.PhysicalSaleService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/sales/physical")
@Tag(
    name = "Physical Sales",
    description = "In-store cashier physical checkout and loyalty points attribution")
@RequiredArgsConstructor
public class PhysicalSaleController {

  private final PhysicalSaleService physicalSaleService;

  @PostMapping
  @Operation(
      summary = "Register in-store physical sale checkout",
      description =
          "Records an in-store physical checkout transaction for an identified customer and attributes loyalty points according to the merchant store rate.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "201",
            description = "Physical sale recorded and loyalty points attributed"),
        @ApiResponse(responseCode = "400", description = "Invalid sale request payload"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Bearer token required"),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Role MERCHANT or ADMIN required")
      })
  public ResponseEntity<PhysicalSaleResponse> recordPhysicalSale(
      @Valid @RequestBody PhysicalSaleRequest requestDto, HttpServletRequest request) {
    String role = (String) request.getAttribute(JwtAuthInterceptor.ATTR_USER_ROLE);
    if (role == null || (!"MERCHANT".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role))) {
      throw new ForbiddenException(
          "Access denied: only merchants and administrators can register in-store physical sales");
    }

    String userId = (String) request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID);
    PhysicalSaleResponse response = physicalSaleService.recordPhysicalSale(requestDto, userId);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/{reference}")
  @Operation(
      summary = "Retrieve physical sale transaction by reference",
      description = "Returns the physical sale transaction audit record matching the reference.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Physical sale transaction found"),
        @ApiResponse(responseCode = "404", description = "Physical sale transaction not found")
      })
  public ResponseEntity<PhysicalSaleResponse> getPhysicalSaleByReference(
      @PathVariable String reference) {
    return ResponseEntity.ok(physicalSaleService.getByReference(reference));
  }
}
