package fr.miage.collectivit.salesservice.model;

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
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Entity representing an audit record of a wallet credit or debit transaction (SLE-15, SLE-18). */
@Entity
@Table(name = "wallet_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletTransaction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "wallet_id", nullable = false)
  private Wallet wallet;

  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  @Column(name = "transaction_reference", nullable = false, unique = true, length = 64)
  private String transactionReference;

  @Column(name = "amount", nullable = false, precision = 10, scale = 2)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 32)
  private WalletTransactionType type;

  @Column(name = "status", nullable = false, length = 32)
  private String status;

  @Column(name = "payment_reference", length = 128)
  private String paymentReference;

  @Column(name = "idempotency_key", length = 128)
  private String idempotencyKey;

  @Column(name = "store_id")
  private Long storeId;

  @Column(name = "store_name", length = 255)
  private String storeName;

  @Column(name = "order_reference", length = 64)
  private String orderReference;

  @Column(name = "description", length = 255)
  private String description;

  @Column(name = "balance_after", nullable = false, precision = 10, scale = 2)
  private BigDecimal balanceAfter;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  public void prePersist() {
    if (this.createdAt == null) {
      this.createdAt = Instant.now();
    }
    if (this.status == null) {
      this.status = "SUCCESS";
    }
  }
}
