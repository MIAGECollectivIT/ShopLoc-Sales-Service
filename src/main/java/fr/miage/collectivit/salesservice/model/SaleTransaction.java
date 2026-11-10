package fr.miage.collectivit.salesservice.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Generic base entity for all sales transactions across ShopLoc (in-store physical sales, online
 * mobile orders, and click & collect purchases).
 */
@Entity
@Table(name = "sales_transactions")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(
    name = "transaction_type",
    discriminatorType = DiscriminatorType.STRING,
    length = 32)
@DiscriminatorValue("GENERIC")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SaleTransaction {

  public static final String DEFAULT_STATUS = "COMPLETED";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "transaction_reference", nullable = false, unique = true, length = 64)
  private String transactionReference;

  @Column(name = "store_id", nullable = false)
  private Long storeId;

  @Column(name = "client_code", length = 32)
  private String clientCode;

  @Column(name = "customer_id", length = 64)
  private String customerId;

  @Column(name = "amount", nullable = false, precision = 10, scale = 2)
  private BigDecimal amount;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "points_earned", nullable = false, precision = 10, scale = 1)
  private BigDecimal pointsEarned;

  @Column(name = "points_rate_applied", nullable = false, length = 128)
  private String pointsRateApplied;

  @Column(name = "status", nullable = false, length = 32)
  private String status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @OneToMany(mappedBy = "saleTransaction", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<SaleTransactionItem> items = new ArrayList<>();

  @PrePersist
  protected void onCreate() {
    if (this.createdAt == null) {
      this.createdAt = Instant.now();
    }
    if (this.status == null) {
      this.status = DEFAULT_STATUS;
    }
    if (this.currency == null) {
      this.currency = "EUR";
    }
    if (this.pointsEarned == null) {
      this.pointsEarned = BigDecimal.ZERO;
    }
  }

  public void addItem(SaleTransactionItem item) {
    if (this.items == null) {
      this.items = new ArrayList<>();
    }
    this.items.add(item);
    item.setSaleTransaction(this);
  }

  public String getCreatedAtIso() {
    Instant effectiveTime = this.createdAt != null ? this.createdAt : Instant.now();
    return effectiveTime.toString();
  }
}
