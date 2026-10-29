package com.ute.uteexpress.release.v14;

import java.sql.*;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.flywaydb.core.api.*;
import org.flywaydb.core.api.callback.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Offline safeguards and JDBC mocks ONLY. Never imports DPAPI, opens a socket or invokes the QA CLI. */
class V14QaSafetyTests {
    @Test void credentialReplacementKeepsOldEncryptedFileUntilAtomicReplacementAndNeverConnects() throws Exception {
        String script=java.nio.file.Files.readString(java.nio.file.Path.of("scripts/v14-sql-qa.ps1"));
        String mode=script.substring(script.indexOf("if ($Mode -eq 'Credential')"),script.indexOf("# Check readiness"));
        assertTrue(mode.contains("-NoClobber"));assertTrue(mode.contains("[IO.File]::Replace($candidate,$credentialPath,$backup)"));
        assertTrue(mode.contains("Set-Acl -LiteralPath $candidate"));assertTrue(mode.contains("$verified=Import-Clixml -LiteralPath $candidate"));
        assertFalse(mode.contains("Remove-Item -LiteralPath $credentialPath"));assertFalse(mode.contains(".getConnection("));
    }
    @Test void v14ValidationOverrideExcludesOnlyTheTwoAdditiveV15Embeddings() throws Exception {
        try(var in=getClass().getResourceAsStream("/orm-v14-qa.xml")) {
            assertNotNull(in);String xml=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(2,xml.split("<transient name=\"priceSnapshot\"/>").length-1);
            assertFalse(xml.contains("metadata-complete=\"true\""));
        }
    }
    Map<String,String> env(){var env=new HashMap<String,String>();env.put("UTE_V14_QA_READY_DATABASE",V14QaTarget.DATABASE);
        env.put("UTE_V14_QA_WRITE_DATABASE",V14QaTarget.DATABASE);env.put("UTE_V14_QA_USERNAME",V14QaTarget.LOGIN);
        env.put("UTE_V14_QA_PASSWORD",UUID.randomUUID().toString());env.put("COMPUTERNAME","LAPTOP");return env;}
    @ParameterizedTest @ValueSource(strings={"clean","repair","reset","drop","seed",""})
    void noUnapprovedModes(String mode){assertThrows(IllegalArgumentException.class,()->V14QaTarget.settings(mode,env()));}
    @ParameterizedTest @ValueSource(strings={"UTE_V14_QA_READY_DATABASE","UTE_V14_QA_USERNAME","UTE_V14_QA_PASSWORD","COMPUTERNAME"})
    void missingEnvironmentStopsBeforeConnection(String field){var env=env();env.remove(field);assertThrows(IllegalArgumentException.class,()->V14QaTarget.settings("preflight",env));}
    @ParameterizedTest @ValueSource(strings={"UTEExpressDB","UTEExpress_Test","UTEExpress_Demo","other"})
    void wrongReadinessDatabaseIsRejected(String name){var env=env();env.put("UTE_V14_QA_READY_DATABASE",name);assertThrows(IllegalArgumentException.class,()->V14QaTarget.settings("run",env));}
    @ParameterizedTest @ValueSource(strings={"sa","uteexpress_qa","uteexpress_demo"})
    void protectedLoginsCannotBeReused(String login){var env=env();env.put("UTE_V14_QA_USERNAME",login);assertThrows(IllegalArgumentException.class,()->V14QaTarget.settings("preflight",env));}
    @Test void readinessIsNotWriteApprovalAndSettingsNeverRenderCredential()throws Exception {
        var env=env();env.remove("UTE_V14_QA_WRITE_DATABASE");
        var settings=V14QaTarget.settings("preflight",env);
        assertThrows(IllegalArgumentException.class,()->V14QaTarget.settings("run",env));
        assertFalse(settings.toString().contains(env.get("UTE_V14_QA_PASSWORD")));
        var ds=new V14QaTarget.GuardedSource(settings);assertThrows(IllegalStateException.class,ds::enableWrites);
        assertThrows(SQLException.class,()->ds.getConnection("sa","unused"));
        assertTrue(V14QaTarget.URL.startsWith("jdbc:sqlserver://127.0.0.1:1433;databaseName="+V14QaTarget.DATABASE+";"));
    }
    @Test void everyIdentityDimensionMustMatch()throws Exception {
        assertDoesNotThrow(()->V14QaTarget.requireIdentity(V14QaTarget.DATABASE,V14QaTarget.DATABASE,"SQLEXPRESS","LAPTOP","LAPTOP",V14QaTarget.LOGIN,1433,true,true,false,false));
        for(int invalid=0;invalid<10;invalid++){
            final int n=invalid;
            assertThrows(SQLException.class,()->V14QaTarget.requireIdentity(n==0?"UTEExpress_Demo":V14QaTarget.DATABASE,
                n==1?"UTEExpress_Test":V14QaTarget.DATABASE,n==2?"OTHER":"SQLEXPRESS",n==3?"REMOTE":"LAPTOP","LAPTOP",
                n==4?"sa":V14QaTarget.LOGIN,n==5?1434:1433,n!=6,n!=7,n==8,n==9));
        }
    }
    @Test void preflightIssuesSelectOnlyAndRejectsNonEmptyDatabase()throws Exception {
        DataSource ds=mock(DataSource.class);Connection c=mock(Connection.class);Statement s=mock(Statement.class);
        ResultSet objects=mock(ResultSet.class),permissions=mock(ResultSet.class);
        when(ds.getConnection()).thenReturn(c);when(c.createStatement()).thenReturn(s);
        when(s.executeQuery(anyString())).thenReturn(objects,permissions);when(objects.next()).thenReturn(true);when(objects.getLong(1)).thenReturn(0L);
        when(permissions.next()).thenReturn(true);when(permissions.getInt(anyInt())).thenReturn(1);
        V14QaTarget.preflight(ds);verify(s,never()).execute(anyString());verify(s,never()).executeUpdate(anyString());verify(c,never()).commit();
        assertThrows(IllegalStateException.class,()->V14QaTarget.requireFresh(1));
    }
    @Test void reviewedFlywayBinaryMatchesActualLocalDependency(){assertTrue(V14TransactionProbe.reviewedBinary(),"Dependency changed: review callback transaction source before enabling fault injection");}
    Context context(String version,Connection c){var context=mock(Context.class);var migration=mock(MigrationInfo.class);
        when(context.getConnection()).thenReturn(c);when(context.getMigrationInfo()).thenReturn(migration);
        when(migration.getVersion()).thenReturn(MigrationVersion.fromVersion(version));return context;}
    Connection stamps(long... ids)throws Exception {
        var c=mock(Connection.class);var s=mock(Statement.class);when(c.createStatement()).thenReturn(s);when(c.getAutoCommit()).thenReturn(false);
        var index=new AtomicInteger();when(s.executeQuery("SELECT @@SPID, CURRENT_TRANSACTION_ID(), @@TRANCOUNT, XACT_STATE()")).thenAnswer(call->{
            var r=mock(ResultSet.class);when(r.next()).thenReturn(true);when(r.getInt(1)).thenReturn(99);when(r.getLong(2)).thenReturn(ids[Math.min(index.getAndIncrement(),ids.length-1)]);
            when(r.getInt(3)).thenReturn(1);when(r.getInt(4)).thenReturn(1);return r;});return c;
    }
    @Test void observesNonThrowingV13ProbeInSameTransaction()throws Exception {
        var c=stamps(10,10);var context=context("13",c);var probe=new V14TransactionProbe("13",false);
        assertTrue(probe.supports(Event.BEFORE_EACH_MIGRATE,context));assertFalse(probe.supports(Event.AFTER_MIGRATE,context));
        probe.handle(Event.BEFORE_EACH_MIGRATE,context);probe.handle(Event.AFTER_EACH_MIGRATE,context);
        assertTrue(probe.sameTransaction);assertFalse(probe.fired);
    }
    @Test void neverInjectsOnDifferentTransactionOrAutocommit()throws Exception {
        var c=stamps(10,11);var context=context("14",c);var probe=new V14TransactionProbe("14",true);
        probe.handle(Event.BEFORE_EACH_MIGRATE,context);assertDoesNotThrow(()->probe.handle(Event.AFTER_EACH_MIGRATE,context));
        assertFalse(probe.fired);assertFalse(probe.sameTransaction);
        c=stamps(12,12);when(c.getAutoCommit()).thenReturn(true);var auto=context("14",c);var autoProbe=new V14TransactionProbe("14",true);
        autoProbe.handle(Event.BEFORE_EACH_MIGRATE,auto);autoProbe.handle(Event.AFTER_EACH_MIGRATE,auto);assertFalse(autoProbe.fired);
    }
    @Test void neverInjectsIfTransactionStampUnavailable()throws Exception {
        var c=stamps(1);when(c.createStatement()).thenThrow(new SQLException("Unavailable","42000",229));
        var probe=new V14TransactionProbe("14",true);var context=context("14",c);
        probe.handle(Event.BEFORE_EACH_MIGRATE,context);assertDoesNotThrow(()->probe.handle(Event.AFTER_EACH_MIGRATE,context));assertFalse(probe.fired);
    }
    @Test void injectsOnlyAfterPositiveSameTransactionAndMetadataProof()throws Exception {
        var c=stamps(17,17);var context=context("14",c);var probe=new V14TransactionProbe("14",true);
        try(var metadata=mockStatic(V14SqlServerQaTool.class)) {
            probe.handle(Event.BEFORE_EACH_MIGRATE,context);
            assertThrows(V14TransactionProbe.ExpectedRollback.class,()->probe.handle(Event.AFTER_EACH_MIGRATE,context));
            metadata.verify(()->V14SqlServerQaTool.verifyV14(c));assertTrue(probe.fired);
        }
    }
    @Test void fixtureStatementsHaveCorrectBindingsAndNoUsableWebAccount()throws Exception {
        DataSource ds=mock(DataSource.class);Connection c=mock(Connection.class);when(ds.getConnection()).thenReturn(c);
        var keys=new AtomicInteger();var queries=new ArrayList<String>();
        when(c.prepareStatement(anyString())).thenAnswer(call->{
            String sql=call.getArgument(0);queries.add(sql);int parameters=(int)sql.chars().filter(ch->ch=='?').count();
            var bound=new HashSet<Integer>();var p=mock(PreparedStatement.class);
            doAnswer(set->{int i=set.getArgument(0);assertTrue(i>0&&i<=parameters);bound.add(i);return null;}).when(p).setObject(anyInt(),any());
            when(p.executeQuery()).thenAnswer(execute->{assertEquals(parameters,bound.size(),"Every placeholder must be bound");
                var r=mock(ResultSet.class);when(r.next()).thenReturn(true);when(r.getLong(1)).thenReturn((long)keys.incrementAndGet());return r;});return p;
        });
        var rows=V14QaFixtures.create(ds);assertEquals(4,rows.shipments().size());assertEquals(10,queries.size());
        assertTrue(queries.getFirst().contains("'LOCKED'"));assertTrue(queries.stream().noneMatch(sql->sql.contains("user_roles")));
        verify(c).commit();verify(c,never()).rollback();
    }
}
