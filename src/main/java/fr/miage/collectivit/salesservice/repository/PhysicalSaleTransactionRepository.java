package fr.miage.collectivit.salesservice.repository;

import fr.miage.collectivit.salesservice.model.PhysicalSaleTransaction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PhysicalSaleTransactionRepository
    extends JpaRepository<PhysicalSaleTransaction, Long> {

  Optional<PhysicalSaleTransaction> findByTransactionReference(String transactionReference);

  List<PhysicalSaleTransaction> findByStoreIdOrderByCreatedAtDesc(Long storeId);

  List<PhysicalSaleTransaction> findByClientCodeOrderByCreatedAtDesc(String clientCode);
}
