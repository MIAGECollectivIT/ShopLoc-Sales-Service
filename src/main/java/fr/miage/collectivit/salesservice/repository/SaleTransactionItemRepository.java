package fr.miage.collectivit.salesservice.repository;

import fr.miage.collectivit.salesservice.model.SaleTransactionItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SaleTransactionItemRepository extends JpaRepository<SaleTransactionItem, Long> {

  List<SaleTransactionItem> findBySaleTransactionId(Long saleId);
}
