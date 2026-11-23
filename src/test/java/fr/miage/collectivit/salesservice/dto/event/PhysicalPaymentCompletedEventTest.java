package fr.miage.collectivit.salesservice.dto.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DTO Event - PhysicalPaymentCompletedEvent")
class PhysicalPaymentCompletedEventTest {

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  @DisplayName("Should serialize and deserialize PhysicalPaymentCompletedEvent correctly")
  void shouldSerializeAndDeserializeEvent() throws Exception {
    Instant now = Instant.parse("2026-11-11T12:00:00Z");
    UUID storeUid = UUID.fromString("11111111-1111-1111-1111-111111111010");
    PhysicalPaymentCompletedEvent event =
        new PhysicalPaymentCompletedEvent(
            "TX-PHY-20261111-A1B2C3",
            storeUid,
            "k7P9xQ2",
            "3fa85f64-5717-4562-b3fc-2c963f66afa6",
            new BigDecimal("25.50"),
            "EUR",
            now);

    String json = objectMapper.writeValueAsString(event);
    PhysicalPaymentCompletedEvent deserialized =
        objectMapper.readValue(json, PhysicalPaymentCompletedEvent.class);

    assertThat(deserialized.transactionReference()).isEqualTo("TX-PHY-20261111-A1B2C3");
    assertThat(deserialized.storeUid()).isEqualTo(storeUid);
    assertThat(deserialized.clientCode()).isEqualTo("k7P9xQ2");
    assertThat(deserialized.customerId()).isEqualTo("3fa85f64-5717-4562-b3fc-2c963f66afa6");
    assertThat(deserialized.amount()).isEqualByComparingTo(new BigDecimal("25.50"));
    assertThat(deserialized.currency()).isEqualTo("EUR");
    assertThat(deserialized.timestamp()).isEqualTo(now);
  }
}
