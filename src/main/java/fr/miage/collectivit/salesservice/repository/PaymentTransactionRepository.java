package fr.miage.collectivit.salesservice.repository;

import fr.miage.collectivit.salesservice.model.PaymentTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

  Optional<PaymentTransaction> findFirstByReferenceOrderByCreatedAtDesc(String reference);

  List<PaymentTransaction> findByUserIdAndStatusAndCreatedAtBetweenOrderByCreatedAtDesc(
      String userId, String status, Instant from, Instant to);

  @Query(
      "SELECT COALESCE(SUM(p.amount), 0) FROM PaymentTransaction p "
          + "WHERE p.reference = :reference AND p.status = 'TRANSACTION_APPROVED'")
  BigDecimal sumApprovedAmountByReference(@Param("reference") String reference);
}
