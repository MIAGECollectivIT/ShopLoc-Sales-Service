package fr.miage.collectivit.salesservice.repository;

import fr.miage.collectivit.salesservice.model.Order;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

  Optional<Order> findByOrderReference(String orderReference);

  List<Order> findByCustomerIdOrderByCreatedAtDesc(String customerId);
}
