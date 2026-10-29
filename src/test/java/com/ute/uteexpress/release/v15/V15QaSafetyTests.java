package com.ute.uteexpress.release.v15;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Offline guard tests. DriverManager is mocked: no credential import or network connections. */
class V15QaSafetyTests {
    static Map<String,String> env() {
        var e=new HashMap<String,String>();e.put("COMPUTERNAME",V15QaTarget.MACHINE);e.put("UTE_V15_QA_READY_DATABASE",V15QaTarget.DATABASE);
        e.put("UTE_V15_QA_WRITE_DATABASE",V15QaTarget.DATABASE);e.put("UTE_V15_QA_USERNAME",V15QaTarget.LOGIN);e.put("UTE_V15_QA_PASSWORD",UUID.randomUUID().toString());return e;
    }
    static V15QaTarget.Identity identity(String catalog,String db,String machine,String instance,String login,int port,List<Integer> access) {
        return new V15QaTarget.Identity(catalog,db,machine,instance,login,port,true,true,false,false,access);
    }
    static V15QaTarget.Identity good(){return identity(V15QaTarget.DATABASE,V15QaTarget.DATABASE,V15QaTarget.MACHINE,"SQLEXPRESS",V15QaTarget.LOGIN,1433,List.of(0,0,0,0));}
    @Test void protectedTargetsAreRejectedBySettingsAndByIndependentServerIdentity() {
        for(String database:V15QaTarget.PROTECTED) {
            var e=env();e.put("UTE_V15_QA_READY_DATABASE",database);assertThrows(IllegalArgumentException.class,()->V15QaTarget.settings("run",e));
            e.put("UTE_V15_QA_READY_DATABASE",V15QaTarget.DATABASE);e.put("UTE_V15_QA_WRITE_DATABASE",database);assertThrows(IllegalArgumentException.class,()->V15QaTarget.settings("run",e));
            assertThrows(SQLException.class,()->V15QaTarget.requireIdentity(identity(database,V15QaTarget.DATABASE,V15QaTarget.MACHINE,"SQLEXPRESS",V15QaTarget.LOGIN,1433,List.of(0,0,0,0))));
            assertThrows(SQLException.class,()->V15QaTarget.requireIdentity(identity(V15QaTarget.DATABASE,database,V15QaTarget.MACHINE,"SQLEXPRESS",V15QaTarget.LOGIN,1433,List.of(0,0,0,0))));
        }
        assertDoesNotThrow(()->V15QaTarget.requireIdentity(good()));
    }
    @Test void wrongMachineInstanceLoginPortOrProtectedAccessIncludingNullIsRejected() {
        var variants=List.of(identity(V15QaTarget.DATABASE,V15QaTarget.DATABASE,"OTHER","SQLEXPRESS",V15QaTarget.LOGIN,1433,List.of(0,0,0,0)),
            identity(V15QaTarget.DATABASE,V15QaTarget.DATABASE,V15QaTarget.MACHINE,"OTHER",V15QaTarget.LOGIN,1433,List.of(0,0,0,0)),
            identity(V15QaTarget.DATABASE,V15QaTarget.DATABASE,V15QaTarget.MACHINE,"SQLEXPRESS","sa",1433,List.of(0,0,0,0)),
            identity(V15QaTarget.DATABASE,V15QaTarget.DATABASE,V15QaTarget.MACHINE,"SQLEXPRESS",V15QaTarget.LOGIN,1434,List.of(0,0,0,0)),
            identity(V15QaTarget.DATABASE,V15QaTarget.DATABASE,V15QaTarget.MACHINE,"SQLEXPRESS",V15QaTarget.LOGIN,1433,List.of(0,0,0,1)),
            identity(V15QaTarget.DATABASE,V15QaTarget.DATABASE,V15QaTarget.MACHINE,"SQLEXPRESS",V15QaTarget.LOGIN,1433,Arrays.asList(0,0,0,null)));
        for(var v:variants)assertThrows(SQLException.class,()->V15QaTarget.requireIdentity(v));
        for(var v:List.of(new V15QaTarget.Identity(good().catalog(),good().database(),good().machine(),good().instance(),good().login(),1433,false,true,false,false,List.of(0,0,0,0)),
            new V15QaTarget.Identity(good().catalog(),good().database(),good().machine(),good().instance(),good().login(),1433,true,false,false,false,List.of(0,0,0,0)),
            new V15QaTarget.Identity(good().catalog(),good().database(),good().machine(),good().instance(),good().login(),1433,true,true,true,false,List.of(0,0,0,0)),
            new V15QaTarget.Identity(good().catalog(),good().database(),good().machine(),good().instance(),good().login(),1433,true,true,false,true,List.of(0,0,0,0))))assertThrows(SQLException.class,()->V15QaTarget.requireIdentity(v));
    }
    @Test void readinessDedicatedCredentialAndSeparateWriteApprovalAreMandatoryWithoutUrlOverride() {
        for(String name:List.of("COMPUTERNAME","UTE_V15_QA_READY_DATABASE","UTE_V15_QA_WRITE_DATABASE","UTE_V15_QA_USERNAME","UTE_V15_QA_PASSWORD")) {
            var e=env();e.remove(name);assertThrows(IllegalArgumentException.class,()->V15QaTarget.settings("run",e));
        }
        var oldCredential=env();oldCredential.put("UTE_V15_QA_USERNAME","uteexpress_v14qa_7dc87af7");assertThrows(IllegalArgumentException.class,()->V15QaTarget.settings("inspect",oldCredential));
        var e=env();e.put("UTE_V15_QA_JDBC_URL","jdbc:sqlserver://unsafe");assertFalse(V15QaTarget.URL.contains("unsafe"));
        assertFalse(V15QaTarget.settings("run",e).toString().contains(e.get("UTE_V15_QA_PASSWORD")));
        assertThrows(IllegalArgumentException.class,()->V15QaTarget.settings("repair",env()));
    }
    @Test void readOnlyModesCannotEnableWriteOptionsOrOverrideCredentialsAndFreshnessRejectsUsedDatabase() {
        for(String mode:List.of("inspect","preflight")) {
            var source=new V15QaTarget.GuardedSource(V15QaTarget.settings(mode,env()));
            assertThrows(IllegalStateException.class,source::enableWrites);assertThrows(SQLException.class,()->source.getConnection("sa","unused"));
        }
        assertDoesNotThrow(()->V15QaTarget.requireFresh(0));for(long count:List.of(1L,42L))assertThrows(IllegalStateException.class,()->V15QaTarget.requireFresh(count));
    }
    @Test void everyReturnedConnectionIsIdentityCheckedAndRejectedConnectionClosesBeforeSet()throws Exception {
        var c=mock(Connection.class);var metadata=mock(DatabaseMetaData.class);when(c.getMetaData()).thenReturn(metadata);when(metadata.getDatabaseProductName()).thenReturn("Microsoft SQL Server");
        when(c.getCatalog()).thenReturn("UTEExpress_Demo");var statement=mock(Statement.class);when(c.createStatement()).thenReturn(statement);
        var result=mock(ResultSet.class);when(statement.executeQuery(anyString())).thenReturn(result);when(result.next()).thenReturn(true);
        when(result.getString(1)).thenReturn("UTEExpress_Demo");when(result.getString(2)).thenReturn(V15QaTarget.MACHINE);when(result.getString(3)).thenReturn("SQLEXPRESS");when(result.getString(4)).thenReturn(V15QaTarget.LOGIN);when(result.getInt(5)).thenReturn(1433);
        var settings=V15QaTarget.settings("run",env());var source=new V15QaTarget.GuardedSource(settings);source.enableWrites();
        try(var driver=mockStatic(DriverManager.class)) {
            driver.when(()->DriverManager.getConnection(V15QaTarget.URL,settings.user(),settings.password())).thenReturn(c);
            assertThrows(SQLException.class,source::getConnection);driver.verify(()->DriverManager.getConnection(V15QaTarget.URL,settings.user(),settings.password()));
        }
        verify(c).close();verify(statement,never()).execute(anyString());verify(c,never()).setCatalog(anyString());
    }
    @Test void sourceChecksumParserAndTransactionFlagsMatchTheReviewedUnmodifiedMigration()throws Exception {
        assertDoesNotThrow(V15SourceReview::requireReviewed);
        assertEquals("faa2cf1cb51b3407b0fe642640b5479e99ee3709e1e8b2b301b84695e1696367",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(V15SourceReview.source().getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }
    @Test void wrapperKeepsV15CredentialsAndClasspathSeparateAndRequiresApprovalBeforeImport()throws Exception {
        String script=Files.readString(Path.of("scripts/v15-sql-qa.ps1"));
        assertTrue(script.contains("'Credential','Inspect','Preflight','Run'"));assertTrue(script.contains("uteexpress-v15-qa.credential.xml"));assertTrue(script.contains("target/v15-qa-classpath.txt"));
        assertTrue(script.indexOf("if ($ReadyDatabase -cne $approvedDatabase)")<script.indexOf("$credential=Import-Clixml -LiteralPath $credentialPath"));
        for(String forbidden:List.of("uteexpress-v14-qa.credential.xml","-Mode Run -AllowWrite","Write-Output $credential","Write-Output $safeErrors"))assertFalse(script.contains(forbidden));
        assertTrue(script.contains("^DIAG cause=[0-7]"));assertTrue(script.contains("$start.CreateNoWindow=$true"));
    }
    @Test void diagnosticOutputContainsCodesAndFramesButNoProviderMessageOrSecret() {
        String secret=UUID.randomUUID().toString();var sql=new SQLException("jdbc:sqlserver://host;password="+secret,"S0001",207);
        sql.setNextException(new SQLException("token="+secret,"42000",229));
        var exception=new IllegalStateException("credential="+secret,sql);
        var output=String.join("\n",V15QaDiagnostics.lines("v15-migrate",exception));
        assertTrue(output.contains("SQLState=S0001 code=207"));assertTrue(output.contains("SQLState=42000 code=229"));
        for(String forbidden:List.of(secret,"password=","token=","credential=","jdbc:"))assertFalse(output.contains(forbidden));
    }
}
