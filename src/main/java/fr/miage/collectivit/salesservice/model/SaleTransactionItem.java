package fr.miage.collectivit.salesservice.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Line item entity representing an individual product purchased within a sale transaction. Supports
 * future itemized in-app orders and itemized in-store checkouts.
 */
@Entity
@Table(name = "sale_transaction_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleTransactionItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @JsonIgnore
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "sale_id", nullable = false)
  private SaleTransaction saleTransaction;

  @Column(name = "product_id")
  private Long productId;

  @Column(name = "product_name", nullable = false)
  private String productName;

  @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
  private BigDecimal unitPrice;

  @Column(name = "quantity", nullable = false)
  private Integer quantity;

  @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
  private BigDecimal totalPrice;

  @PrePersist
  protected void onPersist() {
    if (this.totalPrice == null && this.unitPrice != null && this.quantity != null) {
      this.totalPrice = this.unitPrice.multiply(BigDecimal.valueOf(this.quantity));
    }
  }
}
