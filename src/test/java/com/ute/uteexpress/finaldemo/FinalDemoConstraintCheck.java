package com.ute.uteexpress.finaldemo;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** SQL Server DBCC may return no result set. Consume every result before accepting validation. */
final class FinalDemoConstraintCheck {
    private FinalDemoConstraintCheck() { }
    static final String SQL = "DBCC CHECKCONSTRAINTS WITH ALL_CONSTRAINTS, NO_INFOMSGS";
    record Summary(int resultSets, long violationRows, int updateCounts) { }

    static Summary validate(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(60);
            return consume(statement);
        }
    }

    static Summary consume(Statement statement) throws SQLException {
        int resultSets = 0, updateCounts = 0;
        long violations = 0;
        boolean hasResultSet = statement.execute(SQL);
        while (true) {
            if (hasResultSet) {
                try (var rows = statement.getResultSet()) {
                    if (rows == null) { throw new SQLException("DBCC reported a missing result set"); }
                    resultSets++;
                    while (rows.next()) { violations = Math.incrementExact(violations); }
                }
            } else {
                if (statement.getUpdateCount() == -1) { break; }
                updateCounts++;
            }
            hasResultSet = statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT);
        }
        // Do not silently accept partial/skipped checks signalled by the provider.
        var warning = statement.getWarnings();
        if (warning != null) {
            throw new SQLException("DBCC warning; validation not accepted",
                    warning.getSQLState(), warning.getErrorCode(), warning);
        }
        if (violations != 0) {
            // Never expose table rows, keys, addresses or constraint expressions to operator logs.
            throw new IllegalStateException("DBCC constraint violation rows=" + violations);
        }
        return new Summary(resultSets, violations, updateCounts);
    }
}
