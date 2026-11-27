package fr.miage.collectivit.salesservice.repository;

import fr.miage.collectivit.salesservice.model.Order;
import fr.miage.collectivit.salesservice.model.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

  String GRAPH_ITEMS = "items";

  @EntityGraph(attributePaths = GRAPH_ITEMS)
  Optional<Order> findByUid(UUID uid);

  @EntityGraph(attributePaths = GRAPH_ITEMS)
  Optional<Order> findByOrderReference(String orderReference);

  @EntityGraph(attributePaths = GRAPH_ITEMS)
  List<Order> findByCustomerIdOrderByCreatedAtDesc(String customerId);

  @Override
  @EntityGraph(attributePaths = GRAPH_ITEMS)
  List<Order> findAll();

  @EntityGraph(attributePaths = GRAPH_ITEMS)
  List<Order> findByStatusAndExpiresAtBefore(OrderStatus status, Instant expiresAt);

  Page<Order> findByCustomerIdOrderByCreatedAtDesc(String customerId, Pageable pageable);

  @Query(
      "SELECT COUNT(o) > 0 FROM Order o JOIN o.items i "
          + "WHERE o.customerId = :customerId AND i.storeUid = :storeUid "
          + "AND o.status IN ('PAID', 'DELIVERED', 'COMPLETED')")
  boolean existsSettledOrderForCustomerAndStore(
      @Param("customerId") String customerId, @Param("storeUid") UUID storeUid);

  @Query(
      "SELECT DISTINCT i.storeUid FROM Order o JOIN o.items i "
          + "WHERE o.customerId = :customerId AND i.storeUid IS NOT NULL "
          + "AND o.status IN ('PAID', 'DELIVERED', 'COMPLETED')")
  List<UUID> findSettledStoreUidsByCustomerId(@Param("customerId") String customerId);
}
