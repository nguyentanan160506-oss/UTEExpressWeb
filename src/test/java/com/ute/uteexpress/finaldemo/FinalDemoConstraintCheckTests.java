package com.ute.uteexpress.finaldemo;

import java.sql.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** JDBC protocol regression, offline. No SQL Server connection or altered constraint is needed. */
class FinalDemoConstraintCheckTests {
    @Test void noResultSetIsSuccessfulCompletion() throws Exception {
        var statement = mock(Statement.class);
        when(statement.execute(FinalDemoConstraintCheck.SQL)).thenReturn(false);
        when(statement.getUpdateCount()).thenReturn(-1);
        assertEquals(new FinalDemoConstraintCheck.Summary(0, 0, 0), FinalDemoConstraintCheck.consume(statement));
        verify(statement, never()).executeQuery(anyString());
        verify(statement, never()).getResultSet();
        verify(statement, never()).getMoreResults(anyInt());
    }

    @Test void emptyResultSetIsClosedAndAccepted() throws Exception {
        var statement = mock(Statement.class); var rows = mock(ResultSet.class);
        when(statement.execute(anyString())).thenReturn(true);
        when(statement.getResultSet()).thenReturn(rows);
        when(statement.getUpdateCount()).thenReturn(-1);
        assertEquals(new FinalDemoConstraintCheck.Summary(1, 0, 0), FinalDemoConstraintCheck.consume(statement));
        verify(rows).close();
    }

    @Test void updateCountsDoNotHideLaterEmptyResultSets() throws Exception {
        var statement = mock(Statement.class); var first = mock(ResultSet.class); var second = mock(ResultSet.class);
        when(statement.execute(anyString())).thenReturn(false);
        when(statement.getUpdateCount()).thenReturn(0, 7, -1);
        when(statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT)).thenReturn(true, false, true, false);
        when(statement.getResultSet()).thenReturn(first, second);
        assertEquals(new FinalDemoConstraintCheck.Summary(2, 0, 2), FinalDemoConstraintCheck.consume(statement));
        verify(first).close(); verify(second).close();
    }

    @Test void violationsInLaterResultSetAreNeverIgnored() throws Exception {
        var statement = mock(Statement.class); var first = mock(ResultSet.class); var second = mock(ResultSet.class);
        when(statement.execute(anyString())).thenReturn(true);
        when(statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT)).thenReturn(false, true, false);
        when(statement.getUpdateCount()).thenReturn(0, -1);
        when(statement.getResultSet()).thenReturn(first, second);
        when(second.next()).thenReturn(true, true, false);
        var error = assertThrows(IllegalStateException.class, () -> FinalDemoConstraintCheck.consume(statement));
        assertEquals("DBCC constraint violation rows=2", error.getMessage());
        verify(first).close(); verify(second).close();
    }

    @Test void violationsInFirstResultSetDoNotSkipRemainingResults() throws Exception {
        var statement = mock(Statement.class); var first = mock(ResultSet.class); var second = mock(ResultSet.class);
        when(statement.execute(anyString())).thenReturn(true);
        when(statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT)).thenReturn(true, false);
        when(statement.getUpdateCount()).thenReturn(-1);
        when(statement.getResultSet()).thenReturn(first, second);
        when(first.next()).thenReturn(true, false);
        assertThrows(IllegalStateException.class, () -> FinalDemoConstraintCheck.consume(statement));
        verify(second).next(); verify(second).close();
    }

    @Test void sqlFailureOnExecutePropagates() throws Exception {
        var statement = mock(Statement.class); var failure = new SQLException("Synthetic provider failure", "S0001", 229);
        when(statement.execute(anyString())).thenThrow(failure);
        assertSame(failure, assertThrows(SQLException.class, () -> FinalDemoConstraintCheck.consume(statement)));
    }

    @Test void trailingSqlFailureIsNotConvertedToPass() throws Exception {
        var statement = mock(Statement.class); var rows = mock(ResultSet.class); var failure = new SQLException("Synthetic trailing error");
        when(statement.execute(anyString())).thenReturn(true);
        when(statement.getResultSet()).thenReturn(rows);
        when(statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT)).thenThrow(failure);
        assertSame(failure, assertThrows(SQLException.class, () -> FinalDemoConstraintCheck.consume(statement)));
        verify(rows).close();
    }

    @Test void warningsAndMissingResultSetsFailClosed() throws Exception {
        var statement = mock(Statement.class);
        when(statement.getUpdateCount()).thenReturn(-1);
        when(statement.getWarnings()).thenReturn(new SQLWarning("Synthetic incomplete check", "01000"));
        assertThrows(SQLException.class, () -> FinalDemoConstraintCheck.consume(statement));
        var missing = mock(Statement.class);
        when(missing.execute(anyString())).thenReturn(true);
        assertThrows(SQLException.class, () -> FinalDemoConstraintCheck.consume(missing));
    }

    @Test void validationClosesStatementAndSetsTimeout() throws Exception {
        var connection = mock(Connection.class); var statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.getUpdateCount()).thenReturn(-1);
        FinalDemoConstraintCheck.validate(connection);
        verify(statement).setQueryTimeout(60); verify(statement).close();
    }
}
