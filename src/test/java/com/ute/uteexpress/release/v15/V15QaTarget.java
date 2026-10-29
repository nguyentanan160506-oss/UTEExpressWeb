package com.ute.uteexpress.release.v15;

import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.AbstractDataSource;

/** Proposed target only. Never creates databases/logins or reads another helper's credentials. */
public final class V15QaTarget {
    private V15QaTarget() { }
    public static final String MACHINE="DESKTOP-31GJQ3T",DATABASE="UTEExpress_V15_QA_20261029",LOGIN="uteexpress_v15qa";
    static final List<String> PROTECTED=List.of("UTEExpressDB","UTEExpress_Test","UTEExpress_Demo","UTEExpress_V14_QA_20261029_7dc87af7");
    static final String URL="jdbc:sqlserver://127.0.0.1:1433;databaseName="+DATABASE+
        ";encrypt=true;trustServerCertificate=true;loginTimeout=10;socketTimeout=20000;applicationName=UTEExpress_V15_Isolated_QA";
    static final String SESSION_OPTIONS="SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON; SET ANSI_PADDING ON; SET ANSI_WARNINGS ON; SET CONCAT_NULL_YIELDS_NULL ON; SET ARITHABORT ON; SET NUMERIC_ROUNDABORT OFF;";
    record Settings(String mode,String user,String password) {
        @Override public String toString(){return "V15 QA settings [redacted]";}
    }
    record Identity(String catalog,String database,String machine,String instance,String login,int port,boolean metadata,
            boolean owner,boolean sysadmin,boolean controlServer,List<Integer> protectedAccess) { }
    static Settings settings(String mode,Map<String,String> env) {
        if(!Set.of("inspect","preflight","run").contains(mode))throw new IllegalArgumentException("Unsupported QA mode");
        if(!MACHINE.equals(env.get("COMPUTERNAME"))||!DATABASE.equals(env.get("UTE_V15_QA_READY_DATABASE")))throw new IllegalArgumentException("Explicit readiness for the exact machine/database is required");
        if("run".equals(mode)&&!DATABASE.equals(env.get("UTE_V15_QA_WRITE_DATABASE")))throw new IllegalArgumentException("Separate write approval is required");
        if(!LOGIN.equals(env.get("UTE_V15_QA_USERNAME"))||env.getOrDefault("UTE_V15_QA_PASSWORD","").isBlank())throw new IllegalArgumentException("Dedicated V15 credential required");
        return new Settings(mode,LOGIN,env.get("UTE_V15_QA_PASSWORD"));
    }
    static Identity identity(Connection c)throws SQLException {
        if(!"Microsoft SQL Server".equals(c.getMetaData().getDatabaseProductName()))throw new SQLException("SQL Server required","08004");
        String access=String.join(", ",PROTECTED.stream().map(n->"HAS_DBACCESS(N'"+n+"')").toList());
        try(var s=c.createStatement();var r=s.executeQuery("SELECT DB_NAME(), CONVERT(nvarchar(128),SERVERPROPERTY('MachineName')), CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName')), ORIGINAL_LOGIN(), CONVERT(int,CONNECTIONPROPERTY('local_tcp_port')), HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','VIEW DEFINITION'), IS_MEMBER('db_owner'), IS_SRVROLEMEMBER('sysadmin'), HAS_PERMS_BY_NAME(NULL,NULL,'CONTROL SERVER'), "+access)) {
            if(!r.next())throw new SQLException("Missing identity","08004");
            var protectedAccess=new ArrayList<Integer>();for(int i=10;i<10+PROTECTED.size();i++)protectedAccess.add((Integer)r.getObject(i));
            var result=new Identity(c.getCatalog(),r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getInt(5),
                Integer.valueOf(1).equals(r.getObject(6)),Integer.valueOf(1).equals(r.getObject(7)),!Integer.valueOf(0).equals(r.getObject(8)),!Integer.valueOf(0).equals(r.getObject(9)),Collections.unmodifiableList(protectedAccess));
            requireIdentity(result);return result;
        }
    }
    static void requireIdentity(Identity value)throws SQLException {
        if(!DATABASE.equals(value.catalog())||!DATABASE.equals(value.database())||!MACHINE.equalsIgnoreCase(value.machine())
                ||!"SQLEXPRESS".equalsIgnoreCase(value.instance())||!LOGIN.equalsIgnoreCase(value.login())||value.port()!=1433
                ||!value.metadata()||!value.owner()||value.sysadmin()||value.controlServer()||value.protectedAccess().size()!=4
                ||value.protectedAccess().stream().anyMatch(n->!Integer.valueOf(0).equals(n)))throw new SQLException("QA identity/permissions mismatch; no fallback","08004");
    }
    static final class GuardedSource extends AbstractDataSource {
        private final Settings settings;private boolean writes;
        GuardedSource(Settings settings){this.settings=settings;}
        void enableWrites(){if(!"run".equals(settings.mode()))throw new IllegalStateException("Read-only QA mode");writes=true;}
        @Override public Connection getConnection()throws SQLException {
            var c=DriverManager.getConnection(URL,settings.user(),settings.password());
            try {identity(c);if(writes)try(var s=c.createStatement()){s.execute(SESSION_OPTIONS);}return c;}
            catch(SQLException|RuntimeException error){c.close();throw error;}
        }
        @Override public Connection getConnection(String user,String password)throws SQLException{throw new SQLException("Credential override prohibited","08004");}
    }
    static void preflight(DataSource source)throws SQLException {
        try(var c=source.getConnection()) {
            requireFresh(V15Sql.scalar(c,"SELECT COUNT_BIG(*) FROM sys.objects WHERE is_ms_shipped=0"));
            try(var s=c.createStatement();var r=s.executeQuery("SELECT HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CREATE TABLE'),HAS_PERMS_BY_NAME('dbo','SCHEMA','ALTER')")) {
                if(!r.next()||r.getInt(1)!=1||r.getInt(2)!=1)throw new SQLException("QA DDL permission missing","08004");
            }
        }
    }
    static void requireFresh(long count){if(count!=0)throw new IllegalStateException("Database already used; no resume/reset/cleanup");}
}
