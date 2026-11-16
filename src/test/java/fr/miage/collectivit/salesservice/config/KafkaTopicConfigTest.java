package fr.miage.collectivit.salesservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Config - KafkaTopicConfig")
class KafkaTopicConfigTest {

  private final KafkaTopicConfig config = new KafkaTopicConfig();

  @Test
  @DisplayName("Should create NewTopic bean for physical payments with 4 partitions and replica factor 1")
  void shouldCreatePhysicalPaymentsTopic() {
    NewTopic topic = config.physicalPaymentsTopic();

    assertThat(topic).isNotNull();
    assertThat(topic.name()).isEqualTo(KafkaTopicConfig.PHYSICAL_PAYMENTS_TOPIC);
    assertThat(topic.numPartitions()).isEqualTo(4);
    assertThat(topic.replicationFactor()).isEqualTo((short) 1);
  }

  @Test
  @DisplayName("Should create NewTopic bean for order payments with 4 partitions and replica factor 1")
  void shouldCreateOrderPaymentsTopic() {
    NewTopic topic = config.orderPaymentsTopic();

    assertThat(topic).isNotNull();
    assertThat(topic.name()).isEqualTo(KafkaTopicConfig.ORDER_PAYMENTS_TOPIC);
    assertThat(topic.numPartitions()).isEqualTo(4);
    assertThat(topic.replicationFactor()).isEqualTo((short) 1);
  }
}

