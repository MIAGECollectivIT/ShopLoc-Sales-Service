package fr.miage.collectivit.salesservice.repository;

import fr.miage.collectivit.salesservice.model.Wallet;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {
  Optional<Wallet> findByUserId(String userId);
}
