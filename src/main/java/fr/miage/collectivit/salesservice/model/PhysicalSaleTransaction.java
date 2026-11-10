package fr.miage.collectivit.salesservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Specialized sub-entity for in-store cashier physical sales transactions. Joins with the generic
 * sales_transactions parent table.
 */
@Entity
@Table(name = "physical_sales_transactions")
@DiscriminatorValue("PHYSICAL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PhysicalSaleTransaction extends SaleTransaction {

  @Column(name = "merchant_id", nullable = false, length = 64)
  private String merchantId;

  @Column(name = "payment_method", nullable = false, length = 32)
  private String paymentMethod;
}
