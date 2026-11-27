package fr.miage.collectivit.salesservice.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "uid", nullable = false, unique = true, updatable = false)
  private UUID uid;

  @JsonIgnore
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @Column(name = "store_uid")
  private UUID storeUid;

  @Column(name = "store_name")
  private String storeName;

  @Column(name = "product_uid")
  private UUID productUid;

  @Column(name = "product_name", nullable = false)
  private String productName;

  @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
  private BigDecimal unitPrice;

  @Column(name = "quantity", nullable = false)
  private Integer quantity;

  @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
  private BigDecimal totalPrice;

  @Enumerated(EnumType.STRING)
  @Column(name = "item_type", nullable = false, length = 16)
  @Builder.Default
  private OrderItemType itemType = OrderItemType.PRODUCT;

  @Column(name = "points_cost", nullable = false)
  @Builder.Default
  private Integer pointsCost = 0;

  @PrePersist
  protected void onPersist() {
    if (this.uid == null) {
      this.uid = UUID.randomUUID();
    }
    if (this.itemType == null) {
      this.itemType = OrderItemType.PRODUCT;
    }
    if (this.pointsCost == null) {
      this.pointsCost = 0;
    }
    if (this.totalPrice == null && this.unitPrice != null && this.quantity != null) {
      this.totalPrice = this.unitPrice.multiply(BigDecimal.valueOf(this.quantity));
    }
  }
}
