package fr.miage.collectivit.salesservice.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration
public class KafkaTopicConfig {

  public static final String PHYSICAL_PAYMENTS_TOPIC = "shoploc.physical-payments";
  public static final String ORDER_PAYMENTS_TOPIC = "shoploc.order-payments";
  public static final int DEFAULT_PARTITIONS = 4;
  public static final short DEFAULT_REPLICATION_FACTOR = 1;

  @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
  private String bootstrapServers;

  @Value("${spring.kafka.admin.auto-create:true}")
  private boolean autoCreate;

  @Bean
  @ConditionalOnMissingBean
  public KafkaAdmin kafkaAdmin() {
    Map<String, Object> configs = new HashMap<>();
    configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    KafkaAdmin admin = new KafkaAdmin(configs);
    admin.setFatalIfBrokerNotAvailable(false);
    admin.setAutoCreate(autoCreate);
    return admin;
  }

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
