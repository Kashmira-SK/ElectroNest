package lk.sliit.electronest.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DemoPaymentStatusMigrationTest {
    private DriverManagerDataSource database() {
        return new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    }

    @Test void oldEnumIsExpandedWithoutChangingRowsAndRestartIsSafe() throws Exception {
        var source = database();
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE orders(id bigint PRIMARY KEY, payment_status ENUM('FAILED','PAID','PENDING_PAYMENT','REFUNDED') NOT NULL)");
        jdbc.execute("INSERT INTO orders VALUES (1,'FAILED'),(2,'PAID'),(3,'PENDING_PAYMENT'),(4,'REFUNDED')");
        var before = jdbc.queryForList("SELECT * FROM orders ORDER BY id");
        var migration = new DemoPaymentStatusMigration(source);
        migration.run();
        assertEquals(before, jdbc.queryForList("SELECT * FROM orders ORDER BY id"));
        jdbc.execute("INSERT INTO orders VALUES (5,'CANCELLED')");
        var migrated = jdbc.queryForList("SELECT * FROM orders ORDER BY id");
        migration.run();
        assertEquals(migrated, jdbc.queryForList("SELECT * FROM orders ORDER BY id"));
    }

    @Test void freshSchemaAlreadySupportsCancelled() throws Exception {
        var source = database();
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE orders(id bigint PRIMARY KEY, payment_status ENUM('CANCELLED','FAILED','PAID','PENDING_PAYMENT','REFUNDED') NOT NULL)");
        new DemoPaymentStatusMigration(source).run();
        jdbc.execute("INSERT INTO orders VALUES (1,'CANCELLED')");
        assertEquals("CANCELLED", jdbc.queryForObject("SELECT payment_status FROM orders WHERE id=1", String.class));
    }

    @Test void unexpectedSchemaFailsVisibly() {
        var source = database();
        new JdbcTemplate(source).execute("CREATE TABLE orders(payment_status varchar(30))");
        assertThrows(IllegalStateException.class, () -> new DemoPaymentStatusMigration(source).run());
    }

    @Test void postgresNeverReceivesH2SqlEvenIfDemoIsEnabled() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        new DemoPaymentStatusMigration(source).run();
        verify(connection, never()).prepareStatement(anyString());
        verify(connection, never()).createStatement();
        verify(connection).close();
    }

    @Test void migrationIsAbsentOutsideDemoProfile() {
        for (String profile : List.of("local", "test")) {
            try (var context = new AnnotationConfigApplicationContext()) {
                context.getEnvironment().setActiveProfiles(profile);
                context.register(DemoPaymentStatusMigration.class);
                context.refresh();
                assertTrue(context.getBeansOfType(DemoPaymentStatusMigration.class).isEmpty());
            }
        }
    }
}
