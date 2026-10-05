package lk.sliit.electronest.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashSet;
import java.util.Set;

/** Compatibility for persistent H2 demo databases created before CANCELLED was added. */
@Component
@Profile("demo")
@DependsOn("entityManagerFactory")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DemoPaymentStatusMigration implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoPaymentStatusMigration.class);
    private final DataSource dataSource;

    public DemoPaymentStatusMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) throws Exception {
        // CommandLineRunner executes after Hibernate schema initialization and before readiness.
        try (Connection connection = dataSource.getConnection()) {
            if (!"H2".equals(connection.getMetaData().getDatabaseProductName())) {
                log.info("Skipping demo payment-status migration: database is not H2");
                return;
            }
            Set<String> values = new HashSet<>();
            try (var query = connection.prepareStatement("""
                    SELECT value_name FROM information_schema.enum_values
                    WHERE object_schema = ? AND object_name = 'orders'
                      AND enum_identifier = (
                        SELECT dtd_identifier FROM information_schema.columns
                        WHERE table_schema = ? AND table_name = 'orders' AND column_name = 'payment_status')
                    """)) {
                query.setString(1, connection.getSchema());
                query.setString(2, connection.getSchema());
                try (var result = query.executeQuery()) {
                    while (result.next()) values.add(result.getString(1));
                }
            }
            if (values.contains("CANCELLED")) return;
            if (!values.equals(Set.of("FAILED", "PAID", "PENDING_PAYMENT", "REFUNDED"))) {
                throw new IllegalStateException("Cannot migrate demo orders.payment_status: unexpected enum values " + values);
            }
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE orders ALTER COLUMN payment_status
                        ENUM('CANCELLED','FAILED','PAID','PENDING_PAYMENT','REFUNDED') NOT NULL
                        """);
            }
            log.info("Migrated demo orders.payment_status to support CANCELLED; existing rows preserved");
        }
    }
}
