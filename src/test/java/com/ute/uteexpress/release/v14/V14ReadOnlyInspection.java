package com.ute.uteexpress.release.v14;

import java.sql.*;
import java.util.*;
import javax.sql.DataSource;

/** SELECT-only inspection of the fixed QA target. Does not instantiate Flyway or enable write options. */
final class V14ReadOnlyInspection {
    private V14ReadOnlyInspection() { }
    record Table(String schema,String name,long rows) { }
    record Migration(int rank,String version,Integer checksum,boolean success) { }
    record Snapshot(String catalog,String database,String machine,String instance,String login,int port,
            long objects,List<Table> tables,boolean historyExists,List<Migration> history,
            Map<String,Long> fixtureMarkers,long v14Columns,long v14Index,long v14Check,long v15Columns) { }
    static Snapshot read(DataSource source)throws SQLException {
        try(var c=source.getConnection()) {
            String catalog=c.getCatalog(),database,machine,instance,login;int port;
            try(var s=c.createStatement();var r=s.executeQuery("SELECT DB_NAME(), CONVERT(nvarchar(128),SERVERPROPERTY('MachineName')), CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName')), ORIGINAL_LOGIN(), CONVERT(int,CONNECTIONPROPERTY('local_tcp_port'))")) {
                if(!r.next())throw new SQLException("Missing inspection identity","08004");
                database=r.getString(1);machine=r.getString(2);instance=r.getString(3);login=r.getString(4);port=r.getInt(5);
                // GuardedSource already checked permissions and the current machine before handing out this connection.
                if(!V14QaTarget.DATABASE.equals(catalog)||!V14QaTarget.DATABASE.equals(database)||!"SQLEXPRESS".equalsIgnoreCase(instance)
                        ||!V14QaTarget.LOGIN.equalsIgnoreCase(login)||port!=1433)throw new SQLException("Inspection identity changed","08004");
            }
            long objects=count(c,"SELECT COUNT_BIG(*) FROM sys.objects WHERE is_ms_shipped=0 AND type IN ('U','V','P','PC','FN','IF','TF','FS','FT','SO','SN')");
            var names=new ArrayList<String[]>();
            try(var s=c.createStatement();var r=s.executeQuery("SELECT s.name,t.name FROM sys.tables t JOIN sys.schemas s ON s.schema_id=t.schema_id WHERE t.is_ms_shipped=0 ORDER BY s.name,t.name")) {
                while(r.next())names.add(new String[]{identifier(r.getString(1)),identifier(r.getString(2))});
            }
            var tables=new ArrayList<Table>();
            for(var name:names)tables.add(new Table(name[0],name[1],count(c,"SELECT COUNT_BIG(*) FROM ["+name[0]+"].["+name[1]+"]")));
            boolean historyExists=exists(tables,"flyway_schema_history");var history=new ArrayList<Migration>();
            if(historyExists) {
                try(var s=c.createStatement();var r=s.executeQuery("SELECT installed_rank,version,checksum,success FROM dbo.flyway_schema_history ORDER BY installed_rank")) {
                    while(r.next()) {
                        String version=r.getString(2);
                        if(version!=null&&!version.matches("[0-9]+(?:\\.[0-9]+)*"))throw new SQLException("Unexpected history version","08004");
                        int checksum=r.getInt(3);Integer value=r.wasNull()?null:checksum;
                        history.add(new Migration(r.getInt(1),version,value,r.getBoolean(4)));
                    }
                }
            }
            var markers=new LinkedHashMap<String,Long>();
            marker(c,tables,markers,"users","username", "v14qa_actor");
            marker(c,tables,markers,"branches","code", "V14QA-BRANCH");
            marker(c,tables,markers,"employees","employee_code", "V14QA-EMP");
            marker(c,tables,markers,"shipping_services","code", "V14QA-SERVICE");
            marker(c,tables,markers,"shipment_intakes","receipt_reference", "V14QA-LEGACY-DUPLICATE");
            long columns=count(c,"SELECT COUNT_BIG(*) FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.shipment_intakes') AND name IN ('system_receipt_code','request_version')");
            long index=count(c,"SELECT COUNT_BIG(*) FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.shipment_intakes') AND name='UX_intakes_system_receipt_code'");
            long check=count(c,"SELECT COUNT_BIG(*) FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.shipment_intakes') AND name='CK_intakes_system_receipt'");
            long v15=count(c,"SELECT COUNT_BIG(*) FROM sys.columns WHERE object_id IN (OBJECT_ID(N'dbo.shipments'),OBJECT_ID(N'dbo.shipment_quotes')) AND name='rate_scope'");
            return new Snapshot(catalog,database,machine,instance,login,port,objects,List.copyOf(tables),historyExists,List.copyOf(history),Collections.unmodifiableMap(markers),columns,index,check,v15);
        }
    }
    static void print(Snapshot snapshot) {
        System.out.println("PASS inspect-identity catalog="+snapshot.catalog()+" database="+snapshot.database()+" machine="+snapshot.machine()+" instance="+snapshot.instance()+" port="+snapshot.port()+" login="+snapshot.login());
        System.out.println("PASS inspect-objects count="+snapshot.objects()+" tables="+snapshot.tables().size());
        for(var table:snapshot.tables())System.out.println("PASS inspect-table name="+table.schema()+"."+table.name()+" rows="+table.rows());
        System.out.println("PASS inspect-history exists="+snapshot.historyExists()+" rows="+snapshot.history().size());
        for(var row:snapshot.history())System.out.println("PASS inspect-history-row rank="+row.rank()+" version="+(row.version()==null?"none":row.version())+" checksum="+(row.checksum()==null?"none":row.checksum())+" success="+row.success());
        snapshot.fixtureMarkers().forEach((table,rows)->System.out.println("PASS inspect-fixture-marker table="+table+" rows="+rows));
        System.out.println("PASS inspect-v14 columns="+snapshot.v14Columns()+" index="+snapshot.v14Index()+" check="+snapshot.v14Check()+" v15-columns="+snapshot.v15Columns());
        System.out.println("RESULT INSPECT SELECT-only-no-migration-no-fixtures-no-write-approval");
    }
    private static void marker(Connection c,List<Table> tables,Map<String,Long> result,String table,String field,String value)throws SQLException {
        if(!exists(tables,table)){result.put(table,0L);return;}
        try(var s=c.prepareStatement("SELECT COUNT_BIG(*) FROM dbo.["+identifier(table)+"] WHERE ["+identifier(field)+"]=?")) {
            s.setString(1,value);try(var r=s.executeQuery()){if(!r.next())throw new SQLException("Missing fixture count");result.put(table,r.getLong(1));}
        }
    }
    private static boolean exists(List<Table> tables,String name){return tables.stream().anyMatch(t->t.schema().equals("dbo")&&t.name().equals(name));}
    private static long count(Connection c,String sql)throws SQLException {
        try(var s=c.createStatement();var r=s.executeQuery(sql)){if(!r.next())throw new SQLException("Missing read-only count");return r.getLong(1);}
    }
    private static String identifier(String name)throws SQLException {
        if(name==null||!name.matches("[A-Za-z_][A-Za-z0-9_]{0,127}"))throw new SQLException("Unexpected metadata identifier","08004");return name;
    }
}
