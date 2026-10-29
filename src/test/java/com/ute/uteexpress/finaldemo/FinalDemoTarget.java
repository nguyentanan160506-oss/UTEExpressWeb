package com.ute.uteexpress.finaldemo;

import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.AbstractDataSource;

/** Each connection is verified before being exposed to Hibernate, Flyway or application code. */
public final class FinalDemoTarget {
    private FinalDemoTarget() { }
    public static final String DATABASE="UTEExpress_FinalDemo", LOGIN="uteexpress_finaldemo";
    static final String URL="jdbc:sqlserver://127.0.0.1:1433;databaseName=UTEExpress_FinalDemo;encrypt=true;trustServerCertificate=true;loginTimeout=10;socketTimeout=30000;applicationName=UTEExpress_FinalDemo";
    static void requireApproval(String mode,Map<String,String> env) {
        if(!Set.of("inspect","migrate","seed","validate","run").contains(mode))throw new IllegalArgumentException("Unsupported mode");
        if(!"inspect".equals(mode)&&!DATABASE.equals(env.get("UTE_FINAL_DEMO_APPROVED")))throw new IllegalArgumentException("Explicit target approval required");
        if(!LOGIN.equals(env.get("UTE_FINAL_DEMO_USERNAME"))||env.getOrDefault("UTE_FINAL_DEMO_PASSWORD","").isBlank())throw new IllegalArgumentException("Dedicated credential required");
    }
    static void requireIdentity(String product,String catalog,String db,String machine,String instance,String login,int port,boolean owner,boolean isolated)throws SQLException {
        if(!"Microsoft SQL Server".equals(product)||!DATABASE.equals(catalog)||!DATABASE.equals(db)
                ||!"DESKTOP-31GJQ3T".equalsIgnoreCase(machine)||!"SQLEXPRESS".equalsIgnoreCase(instance)
                ||!LOGIN.equalsIgnoreCase(login)||port!=1433||!owner||!isolated)throw new SQLException("Final Demo identity/permission mismatch","08004");
    }
    public static void verify(Connection c)throws SQLException {
        try(var s=c.createStatement();var r=s.executeQuery("SELECT DB_NAME(),CONVERT(nvarchar(128),SERVERPROPERTY('MachineName')),CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName')),ORIGINAL_LOGIN(),CONVERT(int,CONNECTIONPROPERTY('local_tcp_port')),IS_MEMBER('db_owner'),IS_SRVROLEMEMBER('sysadmin'),IS_SRVROLEMEMBER('dbcreator'),(SELECT COUNT(*) FROM sys.databases WHERE database_id>4 AND name<>N'UTEExpress_FinalDemo' AND HAS_DBACCESS(name)=1)")) {
            if(!r.next())throw new SQLException("Missing identity","08004");
            requireIdentity(c.getMetaData().getDatabaseProductName(),c.getCatalog(),r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getInt(5),r.getInt(6)==1,r.getInt(7)==0&&r.getInt(8)==0&&r.getInt(9)==0);
        }
    }
    public static DataSource source(Map<String,String> env) {
        final String user=env.get("UTE_FINAL_DEMO_USERNAME"),password=env.get("UTE_FINAL_DEMO_PASSWORD");
        return new AbstractDataSource() {
            @Override public Connection getConnection()throws SQLException {
                var c=DriverManager.getConnection(URL,user,password);
                try{verify(c);return c;}catch(SQLException|RuntimeException e){c.close();throw e;}
            }
            @Override public Connection getConnection(String u,String p)throws SQLException {throw new SQLException("Overrides forbidden","08004");}
        };
    }
}
