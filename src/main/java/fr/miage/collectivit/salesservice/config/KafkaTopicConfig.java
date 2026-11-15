package fr.miage.collectivit.salesservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

  public static final String PHYSICAL_PAYMENTS_TOPIC = "shoploc.physical-payments";
  public static final String ORDER_PAYMENTS_TOPIC = "shoploc.order-payments";
  public static final int DEFAULT_PARTITIONS = 4;
  public static final short DEFAULT_REPLICATION_FACTOR = 1;

  @Bean
  public NewTopic physicalPaymentsTopic() {
    return TopicBuilder.name(PHYSICAL_PAYMENTS_TOPIC)
        .partitions(DEFAULT_PARTITIONS)
        .replicas(DEFAULT_REPLICATION_FACTOR)
        .build();
  }

  @Bean
  public NewTopic orderPaymentsTopic() {
    return TopicBuilder.name(ORDER_PAYMENTS_TOPIC)
        .partitions(DEFAULT_PARTITIONS)
        .replicas(DEFAULT_REPLICATION_FACTOR)
        .build();
  }
}

