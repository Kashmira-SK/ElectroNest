package lk.sliit.electronest.admin.service;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Recognizes the existing email unique index without depending on generated constraint names. */
@Component
public class RegistrationEmailConflict {
    private final DataSource dataSource;

    public RegistrationEmailConflict(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean matches(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && "23505".equals(violation.getSQLState())
                    && violation.getConstraintName() != null) {
                return isEmailIndex(violation.getConstraintName());
            }
        }
        return false;
    }

    private boolean isEmailIndex(String constraintName) {
        // H2 reports a qualified index followed by " ON ..."; PostgreSQL reports its name.
        String indexName = constraintName.split(" ON ", 2)[0].replace("\"", "");
        indexName = indexName.substring(indexName.lastIndexOf('.') + 1);
        try (var connection = dataSource.getConnection();
             var indexes = connection.getMetaData().getIndexInfo(
                     connection.getCatalog(), connection.getSchema(), "users", true, false)) {
            Map<String, List<String>> columnsByIndex = new HashMap<>();
            while (indexes.next()) {
                String name = indexes.getString("INDEX_NAME");
                if (name != null) {
                    columnsByIndex.computeIfAbsent(name, ignored -> new ArrayList<>())
                            .add(indexes.getString("COLUMN_NAME"));
                }
            }
            for (var index : columnsByIndex.entrySet()) {
                if (index.getKey().equals(indexName) && index.getValue().size() == 1
                        && "email".equalsIgnoreCase(index.getValue().getFirst())) {
                    return true;
                }
            }
        } catch (SQLException ignored) {
            // Unverified persistence failures must not be labelled as duplicate emails.
        }
        return false;
    }
}
