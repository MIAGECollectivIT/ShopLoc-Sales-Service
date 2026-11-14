package fr.miage.collectivit.salesservice.repository;

import fr.miage.collectivit.salesservice.model.WalletTransaction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

  List<WalletTransaction> findByUserIdOrderByCreatedAtDesc(String userId);

  Page<WalletTransaction> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

  Optional<WalletTransaction> findByTransactionReference(String transactionReference);
}
