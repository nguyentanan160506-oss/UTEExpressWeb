package com.ute.uteexpress.release.v15;

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

/** Offline JDBC mocks: validates helper control flow, never SQL Server correctness. */
class V15HelperRegressionTests {
    @Test void callbackRejectsNullContextsAndIrrelevantEventsBeforeDereferencingMetadata() {
        var probe=new V15TransactionProbe("15",true);
        for(var event:Event.values())assertFalse(assertDoesNotThrow(()->probe.supports(event,null)));
        var context=mock(Context.class);assertFalse(probe.supports(Event.BEFORE_EACH_MIGRATE,context));
        var info=mock(MigrationInfo.class);when(context.getMigrationInfo()).thenReturn(info);assertFalse(probe.supports(Event.AFTER_EACH_MIGRATE,context));
        when(info.getVersion()).thenReturn(MigrationVersion.fromVersion("14"));assertFalse(probe.supports(Event.BEFORE_EACH_MIGRATE,context));
        when(info.getVersion()).thenReturn(MigrationVersion.fromVersion("15"));assertTrue(probe.supports(Event.BEFORE_EACH_MIGRATE,context));assertTrue(probe.supports(Event.AFTER_EACH_MIGRATE,context));
        assertFalse(probe.supports(Event.CREATE_SCHEMA,context));verify(context,never()).getConnection();
    }
    @Test void faultInjectionRequiresSameConnectionSessionActiveTransactionAndNoAutocommit() {
        var c=mock(Connection.class);var other=mock(Connection.class);
        var proof=new V15TransactionProbe.Stamp(c,60,123,1,1,false);
        assertTrue(proof.same(new V15TransactionProbe.Stamp(c,60,123,1,1,false)));
        for(var v:List.of(new V15TransactionProbe.Stamp(other,60,123,1,1,false),new V15TransactionProbe.Stamp(c,61,123,1,1,false),
            new V15TransactionProbe.Stamp(c,60,124,1,1,false),new V15TransactionProbe.Stamp(c,60,123,0,0,false),
            new V15TransactionProbe.Stamp(c,60,123,1,-1,false),new V15TransactionProbe.Stamp(c,60,123,1,1,true)))assertFalse(proof.same(v));
        assertFalse(proof.same(null));
    }
    @Test void legacyInsertsBindExactlyAllPlaceholdersCommitOnceAndRollBackOnUnexpectedFailure()throws Exception {
        var source=mock(DataSource.class);var c=mock(Connection.class);when(source.getConnection()).thenReturn(c);var sqls=new ArrayList<String>();
        when(c.prepareStatement(anyString())).thenAnswer(call->{
            String sql=call.getArgument(0);sqls.add(sql);var p=mock(PreparedStatement.class);var bindings=new HashMap<Integer,Object>();
            doAnswer(set->{bindings.put(set.getArgument(0),set.getArgument(1));return null;}).when(p).setObject(anyInt(),any());
            when(p.executeQuery()).thenAnswer(execute->{assertEquals(sql.chars().filter(ch->ch=='?').count(),bindings.size(),"Fixture placeholder count mismatch");
                if(sql.contains("dbo.users"))assertTrue(Objects.toString(bindings.get(3),"").startsWith("$2"),"Synthetic actor needs BCrypt hash");
                var r=mock(ResultSet.class);when(r.next()).thenReturn(true,false);when(r.getLong(1)).thenReturn((long)sqls.size());return r;});return p;
        });
        var fixture=V15LegacyFixtures.create(source);assertTrue(fixture.actor()>0&&fixture.payment()>0);
        assertEquals(11,sqls.size());assertTrue(sqls.stream().allMatch(sql->sql.startsWith("INSERT INTO dbo.")&&sql.contains("OUTPUT INSERTED.id")));
        assertTrue(sqls.stream().noneMatch(sql->sql.contains("user_roles")||sql.contains("rate_scope")));
        verify(c).setAutoCommit(false);verify(c).commit();verify(c,never()).rollback();
        var failing=mock(DataSource.class);var failed=mock(Connection.class);when(failing.getConnection()).thenReturn(failed);
        when(failed.prepareStatement(anyString())).thenThrow(new SQLException("synthetic failure","42000",229));
        assertThrows(SQLException.class,()->V15LegacyFixtures.create(failing));verify(failed).rollback();verify(failed,never()).commit();
    }
    @Test void constraintHelperRollsBackExpectedErrorsButDoesNotHideUnexpectedSqlErrors()throws Exception {
        var source=mock(DataSource.class);var c=mock(Connection.class);when(source.getConnection()).thenReturn(c);
        V15ConstraintChecks.reject(source,Set.of(547),"QA_CHECK",connection->{throw new SQLException("QA_CHECK","23000",547);});verify(c).rollback();
        var unexpected=assertThrows(SQLException.class,()->V15ConstraintChecks.reject(source,Set.of(547),"QA_CHECK",connection->{throw new SQLException("connection error","08001",0);}));assertEquals("08001",unexpected.getSQLState());
        assertThrows(IllegalStateException.class,()->V15ConstraintChecks.reject(source,Set.of(547),"QA_CHECK",connection->{}));
        verify(c,times(3)).rollback();verify(c,never()).commit();
    }
    @Test void snapshotSqlRejectsUnexpectedTablesAndFieldsBeforePreparingAnySql()throws Exception {
        var c=mock(Connection.class);
        assertThrows(IllegalStateException.class,()->V15ConstraintChecks.change(c,"users",1,Map.of("rate_scope","CENTRAL_SOUTH")));
        assertThrows(IllegalStateException.class,()->V15ConstraintChecks.change(c,"shipments",1,Map.of("total_fee",0)));
        assertThrows(SQLException.class,()->V15Sql.identifier("dbo.x; DELETE"));verify(c,never()).prepareStatement(anyString());
    }
    @Test void inspectUsedDatabaseUsesOnlySelectAndReportsHistoryWithoutRequiringEmptySchema()throws Exception {
        var source=mock(DataSource.class);var c=mock(Connection.class);when(source.getConnection()).thenReturn(c);var meta=mock(DatabaseMetaData.class);when(c.getMetaData()).thenReturn(meta);when(meta.getDatabaseProductName()).thenReturn("Microsoft SQL Server");
        when(c.getCatalog()).thenReturn(V15QaTarget.DATABASE);var statement=mock(Statement.class);when(c.createStatement()).thenReturn(statement);var queries=new ArrayList<String>();
        when(statement.executeQuery(anyString())).thenAnswer(call->{String sql=call.getArgument(0);queries.add(sql);
            if(sql.startsWith("SELECT DB_NAME()"))return rows(new Object[]{V15QaTarget.DATABASE,V15QaTarget.MACHINE,"SQLEXPRESS",V15QaTarget.LOGIN,1433,1,1,0,0,0,0,0,0});
            if(sql.startsWith("SELECT s.name"))return rows(new Object[]{"dbo","flyway_schema_history"},new Object[]{"dbo","users"});
            if(sql.startsWith("SELECT installed_rank"))return rows(new Object[]{1,"1",189739357,true});
            throw new AssertionError("Unexpected statement");
        });
        when(c.prepareStatement(anyString())).thenAnswer(call->{String sql=call.getArgument(0);queries.add(sql);var p=mock(PreparedStatement.class);when(p.executeQuery()).thenAnswer(query->rows(new Object[]{sql.contains("sys.objects")?42L:1L}));return p;});
        var output=String.join("\n",V15ReadOnlyInspection.inspect(source));assertTrue(output.contains("version=1 checksum=189739357 success=true"));assertTrue(output.contains("RESULT INSPECT"));assertTrue(queries.stream().allMatch(q->q.startsWith("SELECT ")));
        verify(c,never()).setAutoCommit(anyBoolean());verify(c,never()).commit();verify(c,never()).rollback();verify(statement,never()).execute(anyString());
    }
    static ResultSet rows(Object[]... values)throws SQLException {
        var result=mock(ResultSet.class);var index=new java.util.concurrent.atomic.AtomicInteger(-1);var wasNull=new java.util.concurrent.atomic.AtomicBoolean();when(result.next()).thenAnswer(call->index.incrementAndGet()<values.length);
        org.mockito.stubbing.Answer<Object> cell=call->{var value=values[index.get()][call.<Integer>getArgument(0)-1];wasNull.set(value==null);return switch(call.getMethod().getName()){case "getString"->value==null?null:value.toString();case "getInt"->value==null?0:((Number)value).intValue();case "getLong"->value==null?0L:((Number)value).longValue();case "getBoolean"->value!=null&&(Boolean)value;default->value;};};
        when(result.getString(anyInt())).thenAnswer(cell);when(result.getInt(anyInt())).thenAnswer(cell);when(result.getLong(anyInt())).thenAnswer(cell);when(result.getObject(anyInt())).thenAnswer(cell);when(result.getBoolean(anyInt())).thenAnswer(cell);when(result.wasNull()).thenAnswer(call->wasNull.get());return result;
    }
}
