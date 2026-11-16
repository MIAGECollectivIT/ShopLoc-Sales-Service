package fr.miage.collectivit.salesservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import fr.miage.collectivit.salesservice.model.PhysicalSaleTransaction;
import fr.miage.collectivit.salesservice.model.SaleTransaction;
import fr.miage.collectivit.salesservice.model.SaleTransactionItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@DisplayName("SaleTransaction Joined Table Hierarchy & Items Integration Test")
class SaleTransactionHierarchyIntegrationTest {

  @Autowired private SaleTransactionRepository saleTransactionRepository;
  @Autowired private PhysicalSaleTransactionRepository physicalSaleTransactionRepository;
  @Autowired private SaleTransactionItemRepository saleTransactionItemRepository;

  @Test
  @DisplayName(
      "Should persist and retrieve PhysicalSaleTransaction through generic and specific repositories")
  void shouldPersistAndRetrievePhysicalSaleThroughJoinedHierarchy() {
    PhysicalSaleTransaction physicalSale =
        PhysicalSaleTransaction.builder()
            .transactionReference("PHYS-TEST-001")
            .storeId(10L)
            .merchantId("merchant_42")
            .clientCode("CL10001")
            .amount(new BigDecimal("35.00"))
            .currency("EUR")
            .pointsEarned(new BigDecimal("35.0"))
            .pointsRateApplied("1.00 EUR = 1.0 PTS")
            .paymentMethod("CASH")
            .status("COMPLETED")
            .build();

    PhysicalSaleTransaction saved = physicalSaleTransactionRepository.save(physicalSale);
    assertThat(saved.getId()).isNotNull();

    // Query via specific physical repository
    Optional<PhysicalSaleTransaction> foundPhysical =
        physicalSaleTransactionRepository.findByTransactionReference("PHYS-TEST-001");
    assertThat(foundPhysical).isPresent();
    assertThat(foundPhysical.get().getMerchantId()).isEqualTo("merchant_42");
    assertThat(foundPhysical.get().getPaymentMethod()).isEqualTo("CASH");

    // Query via generic parent repository
    Optional<SaleTransaction> foundGeneric =
        saleTransactionRepository.findByTransactionReference("PHYS-TEST-001");
    assertThat(foundGeneric).isPresent();
    assertThat(foundGeneric.get()).isInstanceOf(PhysicalSaleTransaction.class);
    assertThat(foundGeneric.get().getAmount()).isEqualByComparingTo(new BigDecimal("35.00"));
  }

  @Test
  @DisplayName(
      "Should support attaching product items to a sale transaction for future in-app orders")
  void shouldSupportAttachingProductItemsToSaleTransaction() {
    SaleTransaction inAppSale =
        SaleTransaction.builder()
            .transactionReference("APP-ORDER-002")
            .storeId(15L)
            .clientCode("CL20002")
            .customerId("cust-uuid-999")
            .amount(new BigDecimal("18.50"))
            .currency("EUR")
            .pointsEarned(new BigDecimal("18.5"))
            .pointsRateApplied("1.00 EUR = 1.0 PTS")
            .status("COMPLETED")
            .build();

    SaleTransactionItem item1 =
        SaleTransactionItem.builder()
            .productId(101L)
            .productName("Pain de campagne Bio")
            .unitPrice(new BigDecimal("3.50"))
            .quantity(2)
            .totalPrice(new BigDecimal("7.00"))
            .build();

    SaleTransactionItem item2 =
        SaleTransactionItem.builder()
            .productId(102L)
            .productName("Fromage de chèvre")
            .unitPrice(new BigDecimal("5.75"))
            .quantity(2)
            .totalPrice(new BigDecimal("11.50"))
            .build();

    inAppSale.addItem(item1);
    inAppSale.addItem(item2);

    SaleTransaction saved = saleTransactionRepository.save(inAppSale);
    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getItems()).hasSize(2);

    List<SaleTransactionItem> persistedItems =
        saleTransactionItemRepository.findBySaleTransactionId(saved.getId());
    assertThat(persistedItems).hasSize(2);
    assertThat(persistedItems)
        .extracting(SaleTransactionItem::getProductName)
        .containsExactlyInAnyOrder("Pain de campagne Bio", "Fromage de chèvre");
  }
}
