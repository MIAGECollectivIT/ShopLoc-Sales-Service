package fr.miage.collectivit.salesservice.repository;

import fr.miage.collectivit.salesservice.model.SaleTransaction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Generic repository querying across all sales transactions regardless of channel. */
@Repository
public interface SaleTransactionRepository extends JpaRepository<SaleTransaction, Long> {

  Optional<SaleTransaction> findByTransactionReference(String transactionReference);

  List<SaleTransaction> findByStoreIdOrderByCreatedAtDesc(Long storeId);

  List<SaleTransaction> findByClientCodeOrderByCreatedAtDesc(String clientCode);
}
