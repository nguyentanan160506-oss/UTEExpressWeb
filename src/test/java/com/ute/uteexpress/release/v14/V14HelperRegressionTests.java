package com.ute.uteexpress.release.v14;

import java.sql.*;
import java.sql.Statement;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.api.*;
import org.flywaydb.core.api.callback.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Helper regression only. Uses JDBC mocks; never imports credentials or connects to SQL Server. */
class V14HelperRegressionTests {
    @Test void callbackRejectsAllNullContextsAndMissingMigrationMetadataWithoutTouchingJdbc() {
        var probe=new V14TransactionProbe("13",false);
        for(var event:Event.values())assertFalse(assertDoesNotThrow(()->probe.supports(event,null)),event.name());
        var context=mock(Context.class);
        assertFalse(probe.supports(Event.BEFORE_EACH_MIGRATE,context));
        var info=mock(MigrationInfo.class);when(context.getMigrationInfo()).thenReturn(info);
        assertFalse(probe.supports(Event.AFTER_EACH_MIGRATE,context));
        when(info.getVersion()).thenReturn(MigrationVersion.fromVersion("14"));assertFalse(probe.supports(Event.BEFORE_EACH_MIGRATE,context));
        when(info.getVersion()).thenReturn(MigrationVersion.fromVersion("13"));assertTrue(probe.supports(Event.AFTER_EACH_MIGRATE,context));
        verify(context,never()).getConnection();
    }
    @Test void inspectModeCannotEnableWritesEvenIfWriteApprovalWasLeftInEnvironment() {
        var env=new V14QaSafetyTests().env();var settings=V14QaTarget.settings("inspect",env);
        var source=new V14QaTarget.GuardedSource(settings);assertThrows(IllegalStateException.class,source::enableWrites);
        env.remove("UTE_V14_QA_READY_DATABASE");assertThrows(IllegalArgumentException.class,()->V14QaTarget.settings("inspect",env));
    }
    @Test void diagnosticsExposeFramesAndSqlCodesButNeverMessagesSettingsOrConnectionSecrets() {
        String secret="sensitive-"+UUID.randomUUID();
        var sql=new SQLException("jdbc:sqlserver://private;password="+secret,"S0001",207);
        sql.setNextException(new SQLException("token="+secret,"42000",229));
        var root=new IllegalStateException("credential="+secret,sql);
        root.setStackTrace(new StackTraceElement[]{new StackTraceElement("com.ute.uteexpress.release.v14.V14SqlServerQaTool","execute","V14SqlServerQaTool.java",42)});
        var text=String.join("\n",V14QaDiagnostics.lines("v13-migrate",root));
        assertTrue(text.contains("FAIL stage=v13-migrate type=IllegalStateException SQLState=S0001 code=207"));
        assertTrue(text.contains("file=V14SqlServerQaTool.java line=42"));assertTrue(text.contains("SQLState=42000 code=229"));
        for(String forbidden:List.of(secret,"password=","jdbc:","credential=","token="))assertFalse(text.contains(forbidden),forbidden);
        var nulled=String.join("\n",V14QaDiagnostics.lines("settings",new SQLException(secret,null,0)));
        assertTrue(nulled.contains("SQLState=UNKNOWN"));assertFalse(nulled.contains(secret));
    }
    @Test void wrapperAllowsOnlyStructuredDiagnosticsAndKeepsReadOnlyInspectSeparate() throws Exception {
        String script=java.nio.file.Files.readString(java.nio.file.Path.of("scripts/v14-sql-qa.ps1"));
        assertTrue(script.contains("'Credential','Inspect','Preflight','Run'"));
        assertTrue(script.contains("^DIAG cause=[0-7]"));assertTrue(script.contains("^DIAG sql=[0-7]"));
        assertFalse(script.contains("Write-Output $safeErrors"));assertFalse(script.contains("Write-Output $credential"));
    }
    @Test void emptyDatabaseInspectionUsesSelectOnlyAndDoesNotCreateHistory()throws Exception {
        var fixture=new JdbcInspection(false);var snapshot=V14ReadOnlyInspection.read(fixture.source);
        assertEquals(0,snapshot.objects());assertTrue(snapshot.tables().isEmpty());assertFalse(snapshot.historyExists());assertTrue(snapshot.history().isEmpty());
        assertTrue(snapshot.fixtureMarkers().values().stream().allMatch(v->v==0));fixture.assertReadOnly();
    }
    @Test void usedDatabaseInspectionReportsPartialHistoryAndExistingRowsWithoutFreshnessAssumption()throws Exception {
        var fixture=new JdbcInspection(true);var snapshot=V14ReadOnlyInspection.read(fixture.source);
        assertEquals(2,snapshot.objects());assertTrue(snapshot.historyExists());assertEquals(2,snapshot.tables().size());
        assertEquals(1,snapshot.history().size());assertEquals("1",snapshot.history().getFirst().version());
        assertEquals(189739357,snapshot.history().getFirst().checksum());assertTrue(snapshot.history().getFirst().success());
        assertEquals(1,snapshot.fixtureMarkers().get("users"));
        assertEquals(7,snapshot.tables().stream().filter(t->t.name().equals("users")).findFirst().orElseThrow().rows());
        fixture.assertReadOnly();
    }
    private static ResultSet rows(List<Object[]> values) throws SQLException {
        var r=mock(ResultSet.class);var index=new java.util.concurrent.atomic.AtomicInteger(-1);var wasNull=new java.util.concurrent.atomic.AtomicBoolean();
        when(r.next()).thenAnswer(call->index.incrementAndGet()<values.size());
        org.mockito.stubbing.Answer<Object> cell=call->{Object value=values.get(index.get())[call.<Integer>getArgument(0)-1];wasNull.set(value==null);
            return switch(call.getMethod().getName()){case "getString"->value==null?null:value.toString();case "getInt"->value==null?0:((Number)value).intValue();case "getLong"->value==null?0L:((Number)value).longValue();case "getBoolean"->value!=null&&(Boolean)value;default->value;};};
        when(r.getString(anyInt())).thenAnswer(cell);when(r.getInt(anyInt())).thenAnswer(cell);when(r.getLong(anyInt())).thenAnswer(cell);when(r.getBoolean(anyInt())).thenAnswer(cell);when(r.wasNull()).thenAnswer(call->wasNull.get());return r;
    }
    private static final class JdbcInspection {
        final DataSource source=mock(DataSource.class);final Connection connection=mock(Connection.class);final Statement statement=mock(Statement.class);
        final PreparedStatement prepared=mock(PreparedStatement.class);final List<String> queries=new ArrayList<>();
        JdbcInspection(boolean populated)throws SQLException {
            when(source.getConnection()).thenReturn(connection);when(connection.getCatalog()).thenReturn(V14QaTarget.DATABASE);when(connection.createStatement()).thenReturn(statement);
            when(statement.executeQuery(anyString())).thenAnswer(call->{String sql=call.getArgument(0);queries.add(sql);
                if(sql.startsWith("SELECT DB_NAME()"))return rows(Collections.singletonList(new Object[]{V14QaTarget.DATABASE,"QA-MACHINE","SQLEXPRESS",V14QaTarget.LOGIN,1433}));
                if(sql.startsWith("SELECT s.name,t.name"))return rows(populated?List.of(new Object[]{"dbo","flyway_schema_history"},new Object[]{"dbo","users"}):List.of());
                if(sql.startsWith("SELECT installed_rank"))return rows(Collections.singletonList(new Object[]{1,"1",189739357,true}));
                long count=sql.contains("FROM sys.objects")?(populated?2:0):sql.contains("FROM [dbo].[users]")?7:sql.contains("FROM [dbo].[flyway_schema_history]")?1:0;
                return rows(Collections.singletonList(new Object[]{count}));
            });
            when(connection.prepareStatement(anyString())).thenAnswer(call->{queries.add(call.getArgument(0));return prepared;});
            when(prepared.executeQuery()).thenAnswer(call->rows(Collections.singletonList(new Object[]{1L})));
        }
        void assertReadOnly()throws SQLException {
            assertTrue(queries.stream().allMatch(q->q.startsWith("SELECT ")));
            verify(statement,never()).execute(anyString());verify(statement,never()).executeUpdate(anyString());
            verify(prepared,never()).execute();verify(prepared,never()).executeUpdate();
            verify(connection,never()).commit();verify(connection,never()).rollback();verify(connection,never()).setCatalog(anyString());verify(connection,never()).setAutoCommit(anyBoolean());
        }
    }
}
