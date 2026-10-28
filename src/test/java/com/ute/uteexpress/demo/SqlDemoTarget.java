package com.ute.uteexpress.demo;

import java.sql.*;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.AbstractDataSource;

/** Fixed destination, checked on EVERY connection before handing it to Flyway, Hibernate or business code. */
public final class SqlDemoTarget {
    private SqlDemoTarget() { }
    public static final String DATABASE="UTEExpress_Demo";
    public static final String LOGIN="uteexpress_demo";
    static final String URL="jdbc:sqlserver://127.0.0.1:1433;databaseName=UTEExpress_Demo;encrypt=true;trustServerCertificate=true;loginTimeout=10;socketTimeout=20000;applicationName=UTEExpress_Commit23_Demo";
    static void requireApproval(String mode,Map<String,String> env) {
        if(!java.util.Set.of("inspect","migrate","seed","run").contains(mode))throw new IllegalArgumentException("Unsupported demo mode");
        if(!"inspect".equals(mode)&&!DATABASE.equals(env.get("UTE_SQL_DEMO_APPROVED")))
            throw new IllegalArgumentException("Explicit approval for UTEExpress_Demo is required for migration, seed or application writes");
        if(!LOGIN.equals(env.get("UTE_SQL_DEMO_USERNAME"))||env.getOrDefault("UTE_SQL_DEMO_PASSWORD","").isBlank())
            throw new IllegalArgumentException("Dedicated demo credential required; QA and sa credentials are not accepted");
    }
    public static void verify(Connection connection)throws SQLException {
        if(!"Microsoft SQL Server".equals(connection.getMetaData().getDatabaseProductName())
                ||!DATABASE.equals(connection.getCatalog()))throw new SQLException("Demo database identity mismatch","08004");
        try(var s=connection.createStatement();var r=s.executeQuery("SELECT DB_NAME(), CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName')), CONVERT(nvarchar(128),SERVERPROPERTY('MachineName')), ORIGINAL_LOGIN(), HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','VIEW DEFINITION')")) {
            if(!r.next())throw new SQLException("Missing SQL identity","08004");
            requireIdentity(connection.getCatalog(),r.getString(1),r.getString(2),r.getString(3),
                System.getenv("COMPUTERNAME"),r.getString(4),r.getInt(5)==1);
        }
    }
    static void requireIdentity(String catalog,String name,String instance,String machine,String local,String login,boolean metadata)throws SQLException {
        if(!DATABASE.equals(catalog)||!DATABASE.equals(name)||!"SQLEXPRESS".equalsIgnoreCase(instance)
                ||local==null||!local.equalsIgnoreCase(machine)||!LOGIN.equalsIgnoreCase(login)||!metadata)
            throw new SQLException("Demo identity or metadata permission mismatch; no fallback","08004");
    }
    public static DataSource source(Map<String,String> env) {
        final String user=env.get("UTE_SQL_DEMO_USERNAME"),password=env.get("UTE_SQL_DEMO_PASSWORD");
        return new AbstractDataSource() {
            @Override public Connection getConnection()throws SQLException {
                Connection c=DriverManager.getConnection(URL,user,password);
                try{verify(c);return c;}catch(SQLException|RuntimeException error){c.close();throw error;}
            }
            @Override public Connection getConnection(String user,String password)throws SQLException {
                throw new SQLException("Credential overrides forbidden","08004");
            }
        };
    }
}
