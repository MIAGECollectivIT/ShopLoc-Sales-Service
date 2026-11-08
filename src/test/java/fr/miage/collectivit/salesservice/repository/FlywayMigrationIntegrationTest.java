package fr.miage.collectivit.salesservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.DataSourceBuilder;

@DisplayName("Flyway Migration V1 Integration Test")
class FlywayMigrationIntegrationTest {

  @Test
  @DisplayName("Should execute V1 payment_transactions migration successfully")
  void shouldExecuteFlywayMigrationSuccessfully() {
    DataSource dataSource =
        DataSourceBuilder.create()
            .url("jdbc:h2:mem:flywaysalesdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
            .driverClassName("org.h2.Driver")
            .username("sa")
            .password("")
            .build();

    Flyway flyway =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load();

    MigrateResult result = flyway.migrate();

    assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(1);
    assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
  }
}
