package fr.miage.collectivit.salesservice.repository;

import fr.miage.collectivit.salesservice.model.WalletTransaction;
import fr.miage.collectivit.salesservice.model.WalletTransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

  List<WalletTransaction> findByUserIdOrderByCreatedAtDesc(String userId);

  Optional<WalletTransaction> findByTransactionReference(String transactionReference);

  Optional<WalletTransaction> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);

  @Query(
      "SELECT COALESCE(SUM(t.amount), 0) FROM WalletTransaction t "
          + "WHERE t.userId = :userId AND t.type = :type AND t.status = 'SUCCESS' AND t.createdAt >= :since")
  BigDecimal sumAmountByUserIdAndTypeAndCreatedAtAfter(
      @Param("userId") String userId,
      @Param("type") WalletTransactionType type,
      @Param("since") Instant since);
}
