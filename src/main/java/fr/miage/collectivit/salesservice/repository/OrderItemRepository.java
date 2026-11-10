package fr.miage.collectivit.salesservice.repository;

import fr.miage.collectivit.salesservice.model.OrderItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

  List<OrderItem> findByStoreId(Long storeId);
}
