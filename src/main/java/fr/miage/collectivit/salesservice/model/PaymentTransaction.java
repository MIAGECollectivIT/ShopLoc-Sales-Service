package fr.miage.collectivit.salesservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payment_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {

  public static final String RG001_APPROVED_STATUS = "TRANSACTION_APPROVED";
  public static final String RG001_RULE_CODE = "RG001";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "transaction_id", nullable = false, unique = true, length = 64)
  private String transactionId;

  @Column(name = "reference", nullable = false, length = 128)
  private String reference;

  @Column(name = "amount", nullable = false, precision = 10, scale = 2)
  private BigDecimal amount;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "status", nullable = false, length = 32)
  private String status;

  @Column(name = "authorization_code", nullable = false, length = 64)
  private String authorizationCode;

  @Column(name = "payment_method_token", nullable = false, length = 128)
  private String paymentMethodToken;

  @Column(name = "user_id", length = 64)
  private String userId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  protected void onCreate() {
    if (this.createdAt == null) {
      this.createdAt = Instant.now();
    }
  }

  public boolean isApprovedUnderRg001() {
    return RG001_APPROVED_STATUS.equals(this.status);
  }

  public String getCreatedAtIso() {
    Instant effectiveTime = this.createdAt != null ? this.createdAt : Instant.now();
    return effectiveTime.toString();
  }
}
