package com.ute.uteexpress.release.v14;

import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.AbstractDataSource;

/** Operator-only target. No Spring configuration, application startup or default JUnit discovery. */
public final class V14QaTarget {
    private V14QaTarget() { }
    public static final String DATABASE="UTEExpress_V14_QA_20261029_7dc87af7";
    public static final String LOGIN="uteexpress_v14qa_7dc87af7";
    static final String URL="jdbc:sqlserver://127.0.0.1:1433;databaseName="+DATABASE+
        ";encrypt=true;trustServerCertificate=true;loginTimeout=10;socketTimeout=20000;applicationName=UTEExpress_V14_Isolated_QA";
    static final String SESSION_OPTIONS="SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON; SET ANSI_PADDING ON; "
        +"SET ANSI_WARNINGS ON; SET CONCAT_NULL_YIELDS_NULL ON; SET ARITHABORT ON; SET NUMERIC_ROUNDABORT OFF;";

    record Settings(String mode,String user,String password,String machine) {
        @Override public String toString(){return "V14 QA settings [redacted]";}
    }
    static Settings settings(String mode,Map<String,String> env) {
        if(!Set.of("inspect","preflight","run").contains(mode))throw new IllegalArgumentException("Unsupported V14 QA mode");
        if(!DATABASE.equals(env.get("UTE_V14_QA_READY_DATABASE")))
            throw new IllegalArgumentException("Environment readiness must be confirmed before any connection");
        if(mode.equals("run")&&!DATABASE.equals(env.get("UTE_V14_QA_WRITE_DATABASE")))
            throw new IllegalArgumentException("Separate write approval for the exact QA database is required");
        if(!LOGIN.equals(env.get("UTE_V14_QA_USERNAME"))||env.getOrDefault("UTE_V14_QA_PASSWORD","").isBlank()
                ||env.getOrDefault("COMPUTERNAME","").isBlank())
            throw new IllegalArgumentException("Dedicated credential and local machine identity required");
        return new Settings(mode,LOGIN,env.get("UTE_V14_QA_PASSWORD"),env.get("COMPUTERNAME"));
    }
    static void requireIdentity(String catalog,String name,String instance,String machine,String expectedMachine,
            String login,int port,boolean metadata,boolean owner,boolean sysadmin,boolean protectedAccess)throws SQLException {
        if(!DATABASE.equals(catalog)||!DATABASE.equals(name)||!"SQLEXPRESS".equalsIgnoreCase(instance)
                ||expectedMachine==null||!expectedMachine.equalsIgnoreCase(machine)||!LOGIN.equalsIgnoreCase(login)
                ||port!=1433||!metadata||!owner||sysadmin||protectedAccess)
            throw new SQLException("QA identity, endpoint or permission mismatch; no fallback","08004");
    }
    static void verify(Connection c,String machine)throws SQLException {
        if(!"Microsoft SQL Server".equals(c.getMetaData().getDatabaseProductName()))
            throw new SQLException("SQL Server required","08004");
        try(var s=c.createStatement();var r=s.executeQuery("SELECT DB_NAME(), CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName')), "
            +"CONVERT(nvarchar(128),SERVERPROPERTY('MachineName')), ORIGINAL_LOGIN(), CONVERT(int,CONNECTIONPROPERTY('local_tcp_port')), "
            +"HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','VIEW DEFINITION'), IS_MEMBER('db_owner'), IS_SRVROLEMEMBER('sysadmin'), "
            +"HAS_DBACCESS(N'UTEExpressDB'), HAS_DBACCESS(N'UTEExpress_Test'), HAS_DBACCESS(N'UTEExpress_Demo')")) {
            if(!r.next())throw new SQLException("Missing identity result","08004");
            requireIdentity(c.getCatalog(),r.getString(1),r.getString(2),r.getString(3),machine,r.getString(4),
                r.getInt(5),r.getInt(6)==1,r.getInt(7)==1,!Integer.valueOf(0).equals(r.getObject(8)),
                !Integer.valueOf(0).equals(r.getObject(9))||!Integer.valueOf(0).equals(r.getObject(10))||!Integer.valueOf(0).equals(r.getObject(11)));
        }
    }
    static final class GuardedSource extends AbstractDataSource {
        private final Settings settings;
        private boolean writesEnabled;
        GuardedSource(Settings settings){this.settings=settings;}
        void enableWrites(){if(!settings.mode().equals("run"))throw new IllegalStateException("Read-only mode");writesEnabled=true;}
        @Override public Connection getConnection()throws SQLException {
            Connection c=DriverManager.getConnection(URL,settings.user(),settings.password());
            try {
                verify(c,settings.machine());
                if(writesEnabled)try(var s=c.createStatement()){s.execute(SESSION_OPTIONS);}
                return c;
            }catch(SQLException|RuntimeException error){c.close();throw error;}
        }
        @Override public Connection getConnection(String user,String password)throws SQLException {
            throw new SQLException("Credential override forbidden","08004");
        }
    }
    static void requireFresh(long objects){if(objects!=0)throw new IllegalStateException("QA database is not empty; no reuse or resume");}
    static void preflight(DataSource ds)throws SQLException {
        try(var c=ds.getConnection();var s=c.createStatement()) {
            try(var r=s.executeQuery("SELECT COUNT(*) FROM sys.objects WHERE is_ms_shipped=0 AND type IN ('U','V','P','PC','FN','IF','TF','FS','FT','SO','SN')")){
                r.next();requireFresh(r.getLong(1));
            }
            try(var r=s.executeQuery("SELECT HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CREATE TABLE'), HAS_PERMS_BY_NAME('dbo','SCHEMA','ALTER')")){
                if(!r.next()||r.getInt(1)!=1||r.getInt(2)!=1)throw new SQLException("Migration permissions missing","08004");
            }
        }
    }
}
