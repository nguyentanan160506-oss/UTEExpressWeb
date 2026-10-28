package com.ute.uteexpress.release;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

/** Explicit test-only operator tool: never invoked by application startup or the default Maven test lifecycle. */
public final class SqlServerQaTool {
    private SqlServerQaTool() { }
    record Settings(String mode,String url,String user,String password,String database,String instance) {
        // Deliberately omit record's generated toString, which would expose credentials.
        @Override public String toString() {return "SqlServer QA settings [redacted]";}
    }
    record HistoryRow(String version,int checksum,boolean success) { }
    static Settings settings(String mode,Map<String,String> env) {
        if(!Set.of("inspect","fresh","v3","v4","v8","v9","v10","v11","v12","pending","validate","smoke").contains(mode))throw new IllegalArgumentException("Unsupported mode; upgrade/repair/clean are not supported.");
        String database=required(env,"UTE_QA_SQLSERVER_DATABASE");
        if(!"UTEExpress_Test".equals(database))throw new IllegalArgumentException("This QA authorization is restricted to UTEExpress_Test; no other database or fallback is permitted.");
        if(!database.equals(required(env,"UTE_QA_SQLSERVER_APPROVED_DATABASE")))throw new IllegalArgumentException("Operator approval must identify the exact database after inspecting it.");
        String url=required(env,"UTE_QA_SQLSERVER_URL");
        if(!url.startsWith("jdbc:sqlserver://") || url.matches("(?is).*;\\s*[^;=]*(?:user(?:name)?|password|token|secret)\\s*=.*"))throw new IllegalArgumentException("Use a SQL Server JDBC URL without embedded credentials.");
        requireApprovedUrl(url);
        if(Set.of("fresh","v3","v4","v8","v9","v10","v11","v12","pending","smoke").contains(mode) && !"YES".equals(env.get("UTE_QA_SQLSERVER_ALLOW_MIGRATE")))throw new IllegalArgumentException("Migration or rollback-only SQL smoke requires separate explicit write authorization.");
        String instance=required(env,"UTE_QA_SQLSERVER_INSTANCE");
        if(!"SQLEXPRESS".equalsIgnoreCase(instance))throw new IllegalArgumentException("Only the approved local SQLEXPRESS instance may use this credential.");
        String user=required(env,"UTE_QA_SQLSERVER_USERNAME");
        if(!"uteexpress_qa".equalsIgnoreCase(user))throw new IllegalArgumentException("Only the explicitly approved SQL login uteexpress_qa may run this QA workflow; no sa or fallback login.");
        return new Settings(mode,url,user,required(env,"UTE_QA_SQLSERVER_PASSWORD"),database,instance);
    }
    private static String required(Map<String,String> env,String key) {String value=env.get(key);if(value==null||value.isBlank())throw new IllegalArgumentException("Missing "+key+"; do not put secret values in command arguments or Git.");return value;}
    static void requireApprovedUrl(String url) {
        String authority=url.substring("jdbc:sqlserver://".length()).split(";",2)[0].toLowerCase(Locale.ROOT).replace("\\sqlexpress","");
        if(!authority.matches("(?:127\\.0\\.0\\.1|localhost)(?::[0-9]{1,5})?") || url.matches("(?is).*;\\s*failoverPartner\\s*=.*"))
            throw new IllegalArgumentException("Only a local SQL endpoint without database failover may receive the test credential.");
        int databases=0;
        for(String part:url.split(";")) {
            String[] option=part.trim().split("=",2);
            if(option.length==2 && (option[0].trim().equalsIgnoreCase("databaseName") || option[0].trim().equalsIgnoreCase("database"))) {
                databases++;
                if(!option[0].trim().equalsIgnoreCase("databaseName") || !option[1].trim().equals("UTEExpress_Test"))
                    throw new IllegalArgumentException("JDBC databaseName must explicitly be UTEExpress_Test before any connection.");
            }
        }
        if(databases!=1)throw new IllegalArgumentException("Exactly one explicit databaseName=UTEExpress_Test is required; default/duplicate database routing is forbidden.");
    }
    static void checkTarget(Connection c,String expected) throws SQLException {
        if(!"Microsoft SQL Server".equals(c.getMetaData().getDatabaseProductName()) || !expected.equals(c.getCatalog()))throw new IllegalStateException("Connected database/product differs from the explicitly approved target; stopped before writes.");
        try(var statement=c.createStatement();var row=statement.executeQuery("SELECT DB_NAME()")) {
            if(!row.next() || !expected.equals(row.getString(1)))throw new IllegalStateException("Server-side DB_NAME differs from UTEExpress_Test; stopped before writes.");
        }
    }
    static void requireEmpty(long tables) {if(tables!=0)throw new IllegalStateException("Fresh mode requires zero existing user tables; no baseline, cleanup or reset will be attempted.");}
    static void requireIdentity(String expectedInstance,String localMachine,String instance,String machine) {
        if(localMachine==null || !expectedInstance.equalsIgnoreCase(instance) || !localMachine.equalsIgnoreCase(machine))
            throw new IllegalStateException("SQL Server is not the approved local named instance; stopped before writes.");
    }
    static void requireCompleteMetadata(boolean canView) {if(!canView)throw new IllegalStateException("VIEW DEFINITION is required to prove the dedicated database is empty; restricted catalog visibility is insufficient.");}
    static void requireFreshObjects(long objects,boolean canCreate,boolean canAlter) {
        requireEmpty(objects);
        if(!canCreate||!canAlter)throw new IllegalStateException("Fresh migration requires CREATE TABLE and ALTER on schema dbo in the dedicated test database.");
    }
    static void requirePendingHistory(String mode,List<HistoryRow> rows) {
        int current=switch(mode){case "v3"->2;case "v4"->3;case "v8"->7;case "v9"->8;case "v10"->9;case "v11"->10;case "v12"->11;case "pending"->12;default->throw new IllegalArgumentException("Pending history guard requires a migration stage");};
        requireHistoryThrough(current,rows);
    }
    static void requireHistoryThrough(int version,List<HistoryRow> rows) {
        var approved=List.of(new HistoryRow("1",189739357,true),new HistoryRow("2",1191373203,true),
            new HistoryRow("3",-1720389847,true),new HistoryRow("4",-1466061241,true),new HistoryRow("5",1925825129,true),
            new HistoryRow("6",-1705174727,true),new HistoryRow("7",444247411,true),new HistoryRow("8",2036200466,true),new HistoryRow("9",61067824,true),new HistoryRow("10",236607534,true),new HistoryRow("11",2051557719,true),new HistoryRow("12",-1057022707,true),new HistoryRow("13",1007916434,true));
        if(version<1||version>approved.size()||!approved.subList(0,version).equals(rows))throw new IllegalStateException("History/checksums differ from the approved migration stage; stopped before writes, no repair attempted.");
    }
    static String sqlDiagnostics(Throwable error) {
        var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        Set<String> diagnostics=new LinkedHashSet<>();
        for(Throwable cause=error;cause!=null&&seen.size()<32&&seen.add(cause);cause=cause.getCause()) {
            if(cause instanceof SQLException sql) {
                for(SQLException next=sql;next!=null;next=next.getNextException()) {
                    String message=Objects.toString(next.getMessage(),"");
                    String column=List.of("user_id","parent_hub_id","shipment_method","version","quote_id","pickup_fee","delivered_at","returned_at","assignment_id","actor_id","failure_code","started_at","return_request_id","pricing_version_id","retired_from","max_weight_kg","max_length_cm","max_width_cm","max_height_cm","notification_assignment_status","notification_enabled","notification_processed_at","notification_retry_at","source_event_id","audience","scope_branch_id","read_at","delivery_retry_at","delivery_attempts","category","source","request_token","request_fingerprint","handling_branch_id","created_by","resolved_at","closed_at","warning_due_at","source_ticket_event_id","related_ticket_id").stream()
                        .filter(name->message.contains("'"+name+"'")).findFirst().orElse("unclassified");
                    String safeMessage="";
                    if(next.getErrorCode()==207&&!column.equals("unclassified")) {
                        var invalidColumn=java.util.regex.Pattern.compile("(?i)Invalid column name '"+java.util.regex.Pattern.quote(column)+"'\\.?").matcher(message);
                        if(invalidColumn.find())safeMessage=" message="+invalidColumn.group();
                    }
                    diagnostics.add("SQLState="+next.getSQLState()+" errorCode="+next.getErrorCode()+" sourceColumn="+column+safeMessage);
                    if(next.getNextException()==null||!seen.add(next.getNextException()))break;
                }
            }
        }
        return String.join("; ",diagnostics);
    }
    public static void main(String[] args) {
        // SQL Server permission was not granted merely by finding a running Windows service.
        try {run(settings(args.length==1?args[0]:"",System.getenv()));}
        catch(Exception error) {
            // JDBC/Flyway exceptions can embed connection details or values; never print their messages/stack traces.
            String diagnostic=" "+sqlDiagnostics(error);
            System.out.println("SQL QA FAILED/BLOCKED ("+error.getClass().getSimpleName()+")"+diagnostic+"; no clean/reset/repair attempted. Verify dedicated target, credentials, permissions and migration history locally.");
            {
                for(Throwable cause=error;cause!=null;cause=cause.getCause()) {
                    System.out.println("Failure type="+cause.getClass().getName());
                    Arrays.stream(cause.getStackTrace()).limit(5).forEach(frame->System.out.println("Failure frame="+frame));
                }
            }
            System.exit(1);
        }
    }
    static void run(Settings config) throws Exception {
        var ds=new DriverManagerDataSource(config.url(),config.user(),config.password());ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        long tables;
        try(var c=ds.getConnection()) {
            checkTarget(c,config.database());
            try(var s=c.createStatement();var r=s.executeQuery("SELECT CONVERT(nvarchar(128),SERVERPROPERTY('ServerName')), CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName')), CONVERT(nvarchar(128),SERVERPROPERTY('MachineName')), CONVERT(int,CONNECTIONPROPERTY('local_tcp_port')), HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','VIEW DEFINITION'), HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CREATE TABLE'), HAS_PERMS_BY_NAME('dbo','SCHEMA','ALTER'), ORIGINAL_LOGIN(), IS_MEMBER('db_owner')")) {
                r.next();requireIdentity(config.instance(),System.getenv("COMPUTERNAME"),r.getString(2),r.getString(3));
                if(!config.user().equalsIgnoreCase(r.getString(8)))throw new IllegalStateException("Actual SQL login differs from the approved QA login.");
                requireCompleteMetadata(r.getInt(5)==1);
                System.out.println("JDBC identity verified: server="+r.getString(1)+" instance="+r.getString(2)+" database="+c.getCatalog()+" TCP port="+r.getInt(4));
                System.out.println("Approved SQL login verified; test database db_owner="+(r.getInt(9)==1));
                boolean create=r.getInt(6)==1,alter=r.getInt(7)==1;
                if(Set.of("v3","v4","v8","v9","v10","v11","v12","pending").contains(config.mode())&&(!create||!alter))throw new IllegalStateException("Pending migration requires CREATE TABLE and ALTER dbo");
                try(var objects=c.createStatement();var rows=objects.executeQuery("SELECT COUNT(*) FROM sys.objects WHERE is_ms_shipped=0 AND type IN ('U','V','P','PC','FN','IF','TF','FS','FT','SO','SN')")) {
                    rows.next();long count=rows.getLong(1);
                    System.out.println("Read-only preflight: user objects="+count+" VIEW_DEFINITION=true CREATE_TABLE="+create+" ALTER_DBO="+alter);
                    if(config.mode().equals("fresh"))requireFreshObjects(count,create,alter);
                }
            }
            try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0")){r.next();tables=r.getLong(1);}
            System.out.println("SQL Server target verified against operator approval; user tables="+tables);
            if(Set.of("smoke","validate").contains(config.mode()))requireHistoryThrough(13,readHistory(c));
            try(var s=c.createStatement();var r=s.executeQuery("SELECT CAST(N'Đà Nẵng – Hồ Chí Minh' AS NVARCHAR(MAX)), CAST(SYSUTCDATETIME() AS DATETIME2(7))")) {
                r.next();if(!"Đà Nẵng – Hồ Chí Minh".equals(r.getString(1))||r.getTimestamp(2)==null)throw new IllegalStateException("Unicode/UTC SQL expression smoke failed");
            }
            if(Set.of("v3","v4","v8","v9","v10","v11","v12","pending").contains(config.mode())) {
                requirePendingHistory(config.mode(),readHistory(c));
                if(config.mode().equals("pending")){verifyRemainingTarget(c,12);requireNoV13Artifacts(c);}
                else if(config.mode().equals("v3"))requireNoV3Artifacts(c,tables);
                else {
                    verifyV3(c);
                    if(config.mode().equals("v4"))requireNoV4Artifacts(c);
                    else {
                        verifyV4(c);
                        if(config.mode().equals("v8"))requireNoV8Artifacts(c);
                        else {verifyV8(c);if(config.mode().equals("v9"))requireNoV9Artifacts(c);else {verifyV9(c);if(config.mode().equals("v10"))requireNoV10Artifacts(c);else {verifyV10(c,config.mode().equals("pending")?42:config.mode().equals("v12")?41:38);if(config.mode().equals("v11"))requireNoV11Artifacts(c);else {verifyV11(c,config.mode().equals("pending")?42:41);if(config.mode().equals("v12"))requireNoV12Artifacts(c);else {verifyRemainingTarget(c,12);requireNoV13Artifacts(c);}}}}}
                    }
                }
            }
        }
        if(config.mode().equals("fresh"))requireEmpty(tables);
        var builder=Flyway.configure().dataSource(ds).locations("classpath:db/migration").defaultSchema("dbo")
            .cleanDisabled(true).baselineOnMigrate(false).outOfOrder(false).validateOnMigrate(true).validateMigrationNaming(true);
        if(config.mode().equals("v3"))builder.target("3");
        if(config.mode().equals("v4"))builder.target("4");
        if(config.mode().equals("v8"))builder.target("8");
        if(config.mode().equals("v9"))builder.target("9");
        if(config.mode().equals("v10"))builder.target("10");
        if(config.mode().equals("v11"))builder.target("11");
        if(config.mode().equals("v12"))builder.target("12");
        if(config.mode().equals("pending"))builder.target("13");
        var flyway=builder.load();
        // Inspect never migrates/repairs/cleans or calls Hibernate schema tooling.
        for(var migration:flyway.info().all())System.out.println("Flyway version="+migration.getVersion()+" state="+migration.getState());
        if(config.mode().equals("inspect")) {
            try(var c=ds.getConnection()) {
                checkTarget(c,config.database());
                inspectMigrationState(c);
            }
            return;
        }
        if(config.mode().equals("fresh")){var result=flyway.migrate();System.out.println("Fresh migrations executed="+result.migrationsExecuted);}
        if(Set.of("v3","v4","v8","v9","v10","v11","v12","pending").contains(config.mode())) {
            if(config.mode().equals("pending")) {
                // Advance one target at a time; an exception immediately stops the loop and all later writes.
                for(int target=13;target<=13;target++) {
                    var stage=builder.target(Integer.toString(target)).load();
                    System.out.println("Starting approved pending target V"+target);
                    var result=stage.migrate();
                    System.out.println("Target V"+target+" migrations executed="+result.migrationsExecuted);
                    if(result.migrationsExecuted!=1||!stage.validateWithResult().validationSuccessful)throw new IllegalStateException("Unexpected pending target result");
                    try(var c=ds.getConnection()) {
                        checkTarget(c,config.database());
                        var history=readHistory(c);requireHistoryThrough(target,history);
                        if(history.size()!=target||history.stream().anyMatch(row->!row.success()))throw new IllegalStateException("Unexpected pending history result");
                        if(!history.get(target-1).equals(new HistoryRow(Integer.toString(target),target==12?-1057022707:1007916434,true)))throw new IllegalStateException("Pending source/history checksum differs");
                        verifyRemainingTarget(c,target);
                    }
                }
                flyway=builder.target("13").load();
            } else {
                var result=flyway.migrate();System.out.println("Pending migrations executed="+result.migrationsExecuted);
            }
            try(var c=ds.getConnection()) {
                checkTarget(c,config.database());
                var history=readHistory(c);
                if(config.mode().equals("v3")) {requirePendingHistory("v4",history);verifyV3(c);}
                else if(config.mode().equals("v4")) {
                    requireHistoryThrough(4,history);verifyV4(c);
                    expectCount(c,"SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0",22);
                    expectCount(c,"SELECT COUNT_BIG(*) FROM dbo.pickup_fee_rules",0);
                    expectCount(c,"SELECT COUNT_BIG(*) FROM dbo.shipment_quotes",0);
                    expectCount(c,"SELECT COUNT_BIG(*) FROM dbo.shipments WHERE quote_id IS NOT NULL OR pickup_fee IS NOT NULL",0);
                    expectCount(c,"SELECT COUNT_BIG(*) FROM dbo.branch_service_areas WHERE supports_delivery<>0",0);
                    System.out.println("V4 initial data verified: no price/quote seed, legacy snapshots remain NULL, coverage default is false");
                }
                else if(config.mode().equals("v8")) {
                    requirePendingHistory("v9",history);verifyV8(c);
                    expectCount(c,"SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0",37);
                }
                else if(config.mode().equals("v9")) {requirePendingHistory("v10",history);verifyV9(c);expectCount(c,"SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0",38);}
                else if(config.mode().equals("v10")) {requirePendingHistory("v11",history);verifyV10(c);}
                else if(config.mode().equals("v11")) {requirePendingHistory("v12",history);verifyV11(c);}
                else if(config.mode().equals("v12")) {requirePendingHistory("pending",history);verifyRemainingTarget(c,12);}
                else if(history.size()!=13||history.stream().anyMatch(row->!row.success()))throw new IllegalStateException("Expected thirteen successful migration history rows");
            }
        }
        if(!flyway.validateWithResult().validationSuccessful)throw new IllegalStateException("Flyway validation failed; do not repair automatically");
        if(Set.of("v3","v4","v8","v9","v10","v11","v12").contains(config.mode())){System.out.println("Target "+config.mode()+" migration/history/schema/checksum PASS; Hibernate waits until V13");return;}
        var factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(ds);factory.setPackagesToScan("com.ute.uteexpress.entity");factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.jdbc.time_zone","UTC","hibernate.show_sql",false));
        try{factory.afterPropertiesSet();System.out.println("Hibernate schema validate PASS");}finally{factory.destroy();}
        try(var c=ds.getConnection();var s=c.createStatement()) {
            checkTarget(c,config.database());
            expectCount(c,"SELECT COUNT(*) FROM sys.foreign_keys WHERE schema_id=SCHEMA_ID('dbo') AND (is_disabled=1 OR is_not_trusted=1)",0);
            expectCount(c,"SELECT COUNT(*) FROM sys.check_constraints WHERE schema_id=SCHEMA_ID('dbo') AND (is_disabled=1 OR is_not_trusted=1)",0);
            expectCount(c,"SELECT COUNT(*) FROM sys.indexes WHERE is_disabled=1 AND object_id IN(SELECT object_id FROM sys.tables WHERE schema_id=SCHEMA_ID('dbo') AND is_ms_shipped=0)",0);
            System.out.println("All dbo FKs/CHECKs enabled and trusted; no disabled user-table indexes");
            for(String object:List.of("foreign_keys","check_constraints","key_constraints","indexes"))try(var r=s.executeQuery("SELECT COUNT(*) FROM sys."+object)){r.next();System.out.println("sys."+object+" count="+r.getLong(1));}
        }
        System.out.println("Flyway/checksum and Hibernate validation PASS; business smoke/concurrency, upgrade and browser QA still require separate evidence.");
        if(config.mode().equals("smoke"))SqlServerBusinessSmoke.run(config);
    }
    private static List<HistoryRow> readHistory(Connection c) throws SQLException {
        var history=new ArrayList<HistoryRow>();
        try(var s=c.createStatement();var r=s.executeQuery("SELECT version,checksum,success FROM dbo.flyway_schema_history ORDER BY installed_rank")) {
            while(r.next()) {
                var row=new HistoryRow(r.getString(1),r.getInt(2),r.getBoolean(3));history.add(row);
                System.out.println("History version="+row.version()+" checksum="+row.checksum()+" success="+row.success());
            }
        }
        return history;
    }
    private static void expectCount(Connection c,String sql,long expected) throws SQLException {
        try(var s=c.createStatement();var r=s.executeQuery(sql)) {
            if(!r.next()||r.getLong(1)!=expected)throw new IllegalStateException("Schema artifact count differs from approved migration state");
        }
    }
    private static final String V3_COLUMNS="SELECT COUNT(*) FROM sys.columns WHERE (object_id=OBJECT_ID('dbo.employees') AND name='user_id') OR(object_id=OBJECT_ID('dbo.branches') AND name='parent_hub_id') OR(object_id=OBJECT_ID('dbo.shipments') AND name IN('shipment_method','origin_hub_id','destination_hub_id','version'))";
    private static final String V3_INDEXES="SELECT COUNT(*) FROM sys.indexes WHERE name IN('UX_employees_user_nonnull','IX_branches_parent_hub','IX_branch_service_areas_lookup')";
    private static void requireNoV8Artifacts(Connection c) throws SQLException {
        expectCount(c,"SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0",31);
        expectCount(c,"SELECT COUNT(*) FROM sys.columns WHERE (object_id=OBJECT_ID('dbo.shipments') AND name IN('delivered_at','returned_at')) OR(object_id=OBJECT_ID('dbo.delivery_attempts') AND name IN('assignment_id','actor_id','failure_code','started_at')) OR(object_id=OBJECT_ID('dbo.transport_trip_items') AND name='return_request_id')",0);
        expectCount(c,"SELECT COUNT(*) FROM sys.objects WHERE schema_id=SCHEMA_ID('dbo') AND name IN('delivery_dispatches','delivery_proofs','delivery_recoveries','redelivery_decisions','return_requests','return_receipts','CK_shipments_delivered_time','CK_shipments_returned_time','FK_delivery_attempts_assignment','FK_delivery_attempts_actor','CK_delivery_attempts_code','CK_delivery_attempts_context','FK_trip_items_return_request')",0);
        expectCount(c,"SELECT COUNT(*) FROM sys.indexes WHERE name IN('UX_assignments_active_delivery','UX_delivery_attempts_success','IX_delivery_attempts_assignment','IX_trip_items_return_request')",0);
        System.out.println("V1-V7-only schema verified: thirty-one tables and no inspected partial V8 artifacts");
    }
    private static Set<String> sourceNames(String sql,String regex) {
        var names=new LinkedHashSet<String>();var matcher=java.util.regex.Pattern.compile(regex).matcher(sql);
        while(matcher.find())names.add(matcher.group(1));return names;
    }
    private static String quotedNames(Set<String> names) {
        if(names.isEmpty()||names.stream().anyMatch(name->!name.matches("\\w+")))throw new IllegalStateException("Invalid schema name list");
        return "'"+String.join("','",names)+"'";
    }
    private static void verifyRemainingTarget(Connection c,int target) throws Exception {
        String resource=target==12?"V12__add_customer_support_portal.sql":"V13__add_admin_reports_audit.sql",sql;
        try(var input=SqlServerQaTool.class.getResourceAsStream("/db/migration/"+resource)){if(input==null)throw new IllegalStateException("Missing pending schema contract");sql=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        var indexes=sourceNames(sql,"CREATE (?:UNIQUE )?INDEX (\\w+) ON");
        expectCount(c,"SELECT COUNT(*) FROM sys.indexes WHERE name IN("+quotedNames(indexes)+") AND is_disabled=0",target==12?3:10);
        expectCount(c,"SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0",42);
        var definitions=java.util.regex.Pattern.compile("CREATE (UNIQUE )?INDEX (\\w+) ON dbo\\.(\\w+) \\(([^)]+)\\)(?: WHERE ([^;]+))?;").matcher(sql);int indexCount=0;
        while(definitions.find()) {
            var actual=new ArrayList<String>();
            try(var s=c.prepareStatement("SELECT i.is_unique,i.filter_definition,k.key_ordinal,c.name,k.is_descending_key FROM sys.indexes i JOIN sys.index_columns k ON k.object_id=i.object_id AND k.index_id=i.index_id JOIN sys.columns c ON c.object_id=k.object_id AND c.column_id=k.column_id WHERE i.object_id=OBJECT_ID(?) AND i.name=? AND k.key_ordinal>0 ORDER BY k.key_ordinal")) {
                s.setString(1,"dbo."+definitions.group(3));s.setString(2,definitions.group(2));
                try(var r=s.executeQuery()){while(r.next()){if(r.getBoolean(1)!=(definitions.group(1)!=null)||!normalizedPredicate(r.getString(2)).equals(normalizedPredicate(definitions.group(5))))throw new IllegalStateException("Pending index uniqueness/filter differs");actual.add(r.getString(4)+(r.getBoolean(5)?" DESC":""));}}
            }
            if(!actual.equals(Arrays.stream(definitions.group(4).split(",")).map(String::trim).toList()))throw new IllegalStateException("Pending index key order differs");indexCount++;
        }
        if(indexCount!=indexes.size())throw new IllegalStateException("Unexpected pending index contract");
        if(target==12) {
            expectCount(c,"SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('dbo.support_ticket_routes')",4);
            verifyColumn(c,"support_ticket_routes","id","BIGINT",false,true);
            for(String name:List.of("origin_branch_id","handling_branch_id"))verifyColumn(c,"support_ticket_routes",name,"BIGINT",false,false);
            verifyColumn(c,"support_ticket_routes","is_active","BIT",false,false);
            verifyColumn(c,"support_ticket_messages","request_token","VARCHAR(32)",true,false);verifyColumn(c,"support_ticket_messages","request_fingerprint","VARCHAR(64)",true,false);
            expectCount(c,"SELECT COUNT(*) FROM sys.foreign_keys WHERE name IN('FK_support_ticket_routes_origin','FK_support_ticket_routes_handling') AND is_disabled=0 AND is_not_trusted=0 AND delete_referential_action=0",2);
            for(String name:List.of("origin_branch_id","handling_branch_id"))expectCount(c,"SELECT COUNT(*) FROM sys.foreign_key_columns k JOIN sys.foreign_keys f ON f.object_id=k.constraint_object_id WHERE f.parent_object_id=OBJECT_ID('dbo.support_ticket_routes') AND COL_NAME(k.parent_object_id,k.parent_column_id)='"+name+"' AND f.referenced_object_id=OBJECT_ID('dbo.branches') AND COL_NAME(k.referenced_object_id,k.referenced_column_id)='id'",1);
            expectCount(c,"SELECT COUNT(*) FROM sys.check_constraints WHERE name IN('CK_support_ticket_messages_request','CK_support_ticket_events_action') AND is_disabled=0 AND is_not_trusted=0",2);
            expectCount(c,"SELECT COUNT(*) FROM sys.check_constraints WHERE name='CK_support_ticket_events_action' AND definition LIKE '%CUSTOMER_MESSAGE%' AND definition LIKE '%REOPEN_REQUESTED%'",1);
            expectCount(c,"SELECT COUNT(*) FROM sys.key_constraints WHERE name IN('PK_support_ticket_routes','UQ_support_ticket_routes_origin')",2);
            expectCount(c,"SELECT COUNT(*) FROM sys.default_constraints WHERE name='DF_support_ticket_routes_active' AND REPLACE(REPLACE(definition,'(',''),')','')='1'",1);
            expectCount(c,"SELECT COUNT(*) FROM sys.indexes WHERE name='UX_support_ticket_messages_request' AND is_unique=1 AND has_filter=1 AND filter_definition LIKE '%request_token%IS NOT NULL%'",1);
            expectCount(c,"SELECT COUNT_BIG(*) FROM dbo.support_ticket_routes",0);
            expectCount(c,"SELECT COUNT_BIG(*) FROM dbo.support_ticket_messages WHERE request_token IS NOT NULL OR request_fingerprint IS NOT NULL",0);
        }
        System.out.println("Pending target V"+target+" schema metadata verified");
    }
    private static void requireNoV13Artifacts(Connection c) throws Exception {
        String sql;
        try(var input=SqlServerQaTool.class.getResourceAsStream("/db/migration/V13__add_admin_reports_audit.sql")){if(input==null)throw new IllegalStateException("Missing V13 schema contract");sql=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        expectCount(c,"SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0",42);
        expectCount(c,"SELECT COUNT(*) FROM sys.indexes WHERE name IN("+quotedNames(sourceNames(sql,"CREATE INDEX (\\w+) ON"))+")",0);
        System.out.println("V13 preflight: forty-two tables; all ten new report/audit indexes absent");
    }
    private static void requireNoV12Artifacts(Connection c) throws Exception {
        expectCount(c,"SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0",41);
        expectCount(c,"SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('dbo.support_ticket_messages') AND name IN('request_token','request_fingerprint')",0);
        String sql;
        try(var input=SqlServerQaTool.class.getResourceAsStream("/db/migration/V12__add_customer_support_portal.sql")){if(input==null)throw new IllegalStateException("Missing V12 schema contract");sql=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        var objects=sourceNames(sql,"CONSTRAINT ((?:FK|CK|PK|DF|UQ)_\\w+) ");objects.remove("CK_support_ticket_events_action");objects.add("support_ticket_routes");
        expectCount(c,"SELECT COUNT(*) FROM sys.objects WHERE schema_id=SCHEMA_ID('dbo') AND name IN("+quotedNames(objects)+")",0);
        expectCount(c,"SELECT COUNT(*) FROM sys.indexes WHERE name IN("+quotedNames(sourceNames(sql,"CREATE (?:UNIQUE )?INDEX (\\w+) ON"))+")",0);
        expectCount(c,"SELECT COUNT(*) FROM sys.check_constraints WHERE name='CK_support_ticket_events_action' AND parent_object_id=OBJECT_ID('dbo.support_ticket_events') AND is_disabled=0 AND is_not_trusted=0 AND definition NOT LIKE '%CUSTOMER_MESSAGE%' AND definition NOT LIKE '%REOPEN_REQUESTED%'",1);
        System.out.println("V1-V11-only schema verified: forty-one tables; V12 route table, two message columns, named constraints/defaults/keys/indexes absent; previous event action CHECK retained");
    }
    private static void requireNoV11Artifacts(Connection c) throws Exception {
        expectCount(c,"SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0",38);
                expectCount(c,"SELECT COUNT(*) FROM sys.columns WHERE (object_id=OBJECT_ID('dbo.support_tickets') AND name IN('handling_branch_id','created_by','source','category','request_token','request_fingerprint','resolved_at','closed_at','warning_due_at','version')) OR(object_id=OBJECT_ID('dbo.notifications') AND name IN('source_ticket_event_id','related_ticket_id'))",0);
                String sql;
                try(var input=SqlServerQaTool.class.getResourceAsStream("/db/migration/V11__add_operations_tickets.sql")){if(input==null)throw new IllegalStateException("Missing V11 schema contract");sql=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
                var objects=sourceNames(sql,"CONSTRAINT ((?:FK|CK|PK|DF)_\\w+) ");objects.remove("CK_support_tickets_status");
                expectCount(c,"SELECT COUNT(*) FROM sys.objects WHERE schema_id=SCHEMA_ID('dbo') AND name IN("+quotedNames(objects)+")",0);
                expectCount(c,"SELECT COUNT(*) FROM sys.tables WHERE name IN('support_ticket_assignments','support_ticket_messages','support_ticket_events') AND schema_id=SCHEMA_ID('dbo')",0);
                expectCount(c,"SELECT COUNT(*) FROM sys.indexes WHERE name IN("+quotedNames(sourceNames(sql,"CREATE (?:UNIQUE )?INDEX (\\w+) ON"))+")",0);
                expectCount(c,"SELECT COUNT(*) FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID('dbo.support_tickets') AND name='CK_support_tickets_status' AND is_disabled=0 AND is_not_trusted=0 AND definition NOT LIKE '%REOPENED%' AND definition NOT LIKE '%WAITING_CUSTOMER%'",1);
    }
    private static void verifyColumn(Connection c,String table,String name,String declaration,boolean nullable,boolean identity) throws SQLException {
        try(var s=c.prepareStatement("SELECT TYPE_NAME(user_type_id),max_length,scale,is_nullable,is_identity FROM sys.columns WHERE object_id=OBJECT_ID(?) AND name=?")) {
            s.setString(1,"dbo."+table);s.setString(2,name);
            try(var r=s.executeQuery()) {
                String type=declaration.replaceAll("\\(.*","").toLowerCase(Locale.ROOT);
                if(!r.next()||!type.equals(r.getString(1))||nullable!=r.getBoolean(4)||identity!=r.getBoolean(5))throw new IllegalStateException("V11 column type/nullability/identity differs from source");
                if(type.equals("datetime2")&&r.getInt(3)!=7)throw new IllegalStateException("V11 UTC timestamp precision differs");
                if(type.equals("varchar")||type.equals("nvarchar")) {
                    int length=Integer.parseInt(declaration.replaceAll("\\D",""))*(type.equals("nvarchar")?2:1);
                    if(length!=r.getInt(2))throw new IllegalStateException("V11 character length differs");
                }
            }
        }
    }
    private static String parentTable(String sql,int position) {
        var tables=java.util.regex.Pattern.compile("(?:ALTER|CREATE) TABLE dbo\\.(\\w+)").matcher(sql.substring(0,position));String table=null;
        while(tables.find())table=tables.group(1);
        if(table==null)throw new IllegalStateException("Missing schema object parent");return table;
    }
    private static String normalizedPredicate(String value) {return value==null?"":value.replaceAll("[\\[\\]()\\s]","").toUpperCase(Locale.ROOT);}
    private static void verifyV11(Connection c) throws Exception {verifyV11(c,41);}
    private static void verifyV11(Connection c,int expectedTables) throws Exception {
        String sql;
        try(var input=SqlServerQaTool.class.getResourceAsStream("/db/migration/V11__add_operations_tickets.sql")) {
            if(input==null)throw new IllegalStateException("Missing V11 schema contract");sql=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
        var fks=sourceNames(sql,"CONSTRAINT (FK_\\w+) FOREIGN KEY");var checks=sourceNames(sql,"CONSTRAINT (CK_\\w+) CHECK");var defaults=sourceNames(sql,"CONSTRAINT (DF_\\w+) DEFAULT");var indexes=sourceNames(sql,"CREATE (?:UNIQUE )?INDEX (\\w+) ON");
        if(fks.size()!=17||checks.size()!=19||defaults.size()!=5||indexes.size()!=21)throw new IllegalStateException("Unexpected V11 schema contract");
        expectCount(c,"SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0",expectedTables);
        var columns=java.util.regex.Pattern.compile("(?m)^\\s*(\\w+) (BIGINT|INT|NVARCHAR\\(\\d+\\)|VARCHAR\\(\\d+\\)|DATETIME2\\(7\\))( IDENTITY\\(1,1\\))? (NOT NULL|NULL)");
        var tables=java.util.regex.Pattern.compile("CREATE TABLE dbo\\.(\\w+) \\((.*?)\\r?\\n\\);",java.util.regex.Pattern.DOTALL).matcher(sql);int total=0,tableCount=0;
        while(tables.find()) {
            String table=tables.group(1);var declarations=columns.matcher(tables.group(2));int count=0;
            while(declarations.find()){verifyColumn(c,table,declarations.group(1),declarations.group(2),declarations.group(4).equals("NULL"),declarations.group(3)!=null);count++;}
            expectCount(c,"SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('dbo."+table+"')",count);
            expectCount(c,"SELECT COUNT_BIG(*) FROM dbo."+table,0);total+=count;tableCount++;
        }
        String added=sql.substring(sql.indexOf("ALTER TABLE dbo.support_tickets ADD handling_branch_id")+"ALTER TABLE dbo.support_tickets ADD ".length());added=added.substring(0,added.indexOf(';'));
        var declarations=columns.matcher(added);int addedCount=0;
        while(declarations.find()){verifyColumn(c,"support_tickets",declarations.group(1),declarations.group(2),declarations.group(4).equals("NULL"),false);addedCount++;}
        verifyColumn(c,"notifications","source_ticket_event_id","BIGINT",true,false);verifyColumn(c,"notifications","related_ticket_id","BIGINT",true,false);
        if(tableCount!=3||total!=30||addedCount!=10)throw new IllegalStateException("Unexpected V11 column count");
        expectCount(c,"SELECT COUNT(*) FROM sys.foreign_keys WHERE name IN("+quotedNames(fks)+") AND is_disabled=0 AND is_not_trusted=0 AND delete_referential_action=0 AND update_referential_action=0",17);
        var references=java.util.regex.Pattern.compile("CONSTRAINT (FK_\\w+) FOREIGN KEY \\((\\w+)\\) REFERENCES dbo\\.(\\w+) \\((\\w+)\\)").matcher(sql);int fkCount=0;
        while(references.find()) {
            String parent=parentTable(sql,references.start());
            expectCount(c,"SELECT COUNT(*) FROM sys.foreign_keys f JOIN sys.foreign_key_columns k ON k.constraint_object_id=f.object_id WHERE f.name='"+references.group(1)+"' AND f.parent_object_id=OBJECT_ID('dbo."+parent+"') AND COL_NAME(k.parent_object_id,k.parent_column_id)='"+references.group(2)+"' AND f.referenced_object_id=OBJECT_ID('dbo."+references.group(3)+"') AND COL_NAME(k.referenced_object_id,k.referenced_column_id)='"+references.group(4)+"'",1);fkCount++;
        }
        if(fkCount!=17)throw new IllegalStateException("Unexpected V11 FK column contract");
        expectCount(c,"SELECT COUNT(*) FROM sys.check_constraints WHERE name IN("+quotedNames(checks)+") AND is_disabled=0 AND is_not_trusted=0",19);
        expectCount(c,"SELECT COUNT(*) FROM sys.check_constraints WHERE name='CK_support_tickets_status' AND definition LIKE '%REOPENED%' AND definition LIKE '%WAITING_CUSTOMER%'",1);
        expectCount(c,"SELECT COUNT(*) FROM sys.key_constraints WHERE name IN('PK_support_ticket_assignments','PK_support_ticket_messages','PK_support_ticket_events') AND type='PK'",3);
        expectCount(c,"SELECT COUNT(*) FROM sys.key_constraints WHERE name IN('PK_support_tickets','UQ_support_tickets_code') AND parent_object_id=OBJECT_ID('dbo.support_tickets')",2);
        expectCount(c,"SELECT COUNT(*) FROM sys.default_constraints WHERE name IN("+quotedNames(defaults)+")",5);
        expectCount(c,"SELECT COUNT(*) FROM sys.default_constraints WHERE name IN('DF_support_tickets_version','DF_support_ticket_events_attempts') AND REPLACE(REPLACE(definition,'(',''),')','')='0'",2);
        expectCount(c,"SELECT COUNT(*) FROM sys.default_constraints WHERE name IN('DF_support_ticket_assignments_assigned_at','DF_support_ticket_messages_created_at','DF_support_ticket_events_occurred_at') AND LOWER(REPLACE(REPLACE(definition,'(',''),')',''))='sysutcdatetime'",3);
        var indexDefinitions=java.util.regex.Pattern.compile("CREATE (UNIQUE )?INDEX (\\w+) ON dbo\\.(\\w+) \\(([^)]+)\\)(?: WHERE ([^;]+))?;").matcher(sql);int indexCount=0;
        while(indexDefinitions.find()) {
            String name=indexDefinitions.group(2),table=indexDefinitions.group(3);boolean unique=indexDefinitions.group(1)!=null;
            try(var s=c.prepareStatement("SELECT is_unique,filter_definition,is_disabled FROM sys.indexes WHERE object_id=OBJECT_ID(?) AND name=?")) {
                s.setString(1,"dbo."+table);s.setString(2,name);
                try(var r=s.executeQuery()){if(!r.next()||r.getBoolean(1)!=unique||r.getBoolean(3)||!normalizedPredicate(indexDefinitions.group(5)).equals(normalizedPredicate(r.getString(2))))throw new IllegalStateException("V11 index uniqueness/filter differs from source");}
            }
            var actual=new ArrayList<String>();
            try(var s=c.prepareStatement("SELECT c.name,k.is_descending_key FROM sys.indexes i JOIN sys.index_columns k ON k.object_id=i.object_id AND k.index_id=i.index_id JOIN sys.columns c ON c.object_id=k.object_id AND c.column_id=k.column_id WHERE i.object_id=OBJECT_ID(?) AND i.name=? AND k.key_ordinal>0 ORDER BY k.key_ordinal")) {
                s.setString(1,"dbo."+table);s.setString(2,name);try(var r=s.executeQuery()){while(r.next())actual.add(r.getString(1)+(r.getBoolean(2)?" DESC":""));}
            }
            if(!actual.equals(Arrays.stream(indexDefinitions.group(4).split(",")).map(String::trim).toList()))throw new IllegalStateException("V11 index keys/order differ from source");indexCount++;
        }
        if(indexCount!=21)throw new IllegalStateException("Unexpected V11 index count");
        expectCount(c,"SELECT COUNT_BIG(*) FROM dbo.support_tickets WHERE handling_branch_id IS NOT NULL OR created_by IS NOT NULL OR source IS NOT NULL OR category IS NOT NULL OR request_token IS NOT NULL OR request_fingerprint IS NOT NULL OR resolved_at IS NOT NULL OR closed_at IS NOT NULL OR warning_due_at IS NOT NULL OR version<>0",0);
        expectCount(c,"SELECT COUNT_BIG(*) FROM dbo.notifications WHERE source_ticket_event_id IS NOT NULL OR related_ticket_id IS NOT NULL",0);
        try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT_BIG(*) FROM dbo.support_tickets")){r.next();System.out.println("V11 baseline support_tickets rows="+r.getLong(1));}
        System.out.println("V11 verified: three tables/thirty columns plus twelve added columns/types/nullability/identity; seventeen trusted NO ACTION FKs/targets, nineteen CHECKs, five UTC/zero defaults, three PKs/prior ticket unique key, twenty-one indexes/keys/order/three filtered unique; no seed/backfill");
    }
    private static void requireNoV10Artifacts(Connection c) throws SQLException {
                expectCount(c,"SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0",38);
                expectCount(c,"SELECT COUNT(*) FROM sys.columns WHERE (object_id=OBJECT_ID('dbo.shipment_events') AND name IN('notification_enabled','notification_processed_at','notification_retry_at','notification_attempts','notification_assignment_id','notification_previous_assignment_id','notification_assignee_user_id','notification_previous_user_id','notification_assignment_status')) OR(object_id=OBJECT_ID('dbo.notifications') AND name IN('source_event_id','audience','scope_branch_id','assignment_id','read_at','delivered_at','delivery_retry_at','delivery_attempts'))",0);
                expectCount(c,"SELECT COUNT(*) FROM sys.objects WHERE schema_id=SCHEMA_ID('dbo') AND name IN('CK_shipment_events_notification_assignment_status','FK_shipment_events_notification_assignment','FK_shipment_events_notification_previous_assignment','FK_shipment_events_notification_assignee_user','FK_shipment_events_notification_previous_user','FK_notifications_source_event','FK_notifications_scope_branch','FK_notifications_assignment','CK_notifications_audience','DF_shipment_events_notification_enabled','DF_shipment_events_notification_attempts','DF_notifications_delivery_attempts')",0);
                expectCount(c,"SELECT COUNT(*) FROM sys.indexes WHERE name IN('IX_shipment_events_notification_work','UX_notifications_source_recipient_audience','IX_notifications_inbox','IX_notifications_delivery')",0);
    }
    private static void verifyV10(Connection c) throws Exception {
        verifyV10(c,38);
    }
    private static void verifyV10(Connection c,int expectedTables) throws Exception {
        String sql;
        try(var input=SqlServerQaTool.class.getResourceAsStream("/db/migration/V10__add_shipment_notifications.sql")) {
            if(input==null)throw new IllegalStateException("Missing V10 schema contract");sql=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
        var fks=sourceNames(sql,"CONSTRAINT (FK_\\w+) FOREIGN KEY");var checks=sourceNames(sql,"CONSTRAINT (CK_\\w+) CHECK");var indexes=sourceNames(sql,"CREATE (?:UNIQUE )?INDEX (\\w+) ON");
        if(fks.size()!=7||checks.size()!=2||indexes.size()!=4)throw new IllegalStateException("Unexpected V10 schema contract");
        expectCount(c,"SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0",expectedTables);
        expectCount(c,"SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('dbo.shipment_events') AND ((name='notification_enabled' AND TYPE_NAME(user_type_id)='bit' AND is_nullable=0) OR(name='notification_attempts' AND TYPE_NAME(user_type_id)='int' AND is_nullable=0) OR(name IN('notification_processed_at','notification_retry_at') AND TYPE_NAME(user_type_id)='datetime2' AND scale=7 AND is_nullable=1) OR(name IN('notification_assignment_id','notification_previous_assignment_id','notification_assignee_user_id','notification_previous_user_id') AND TYPE_NAME(user_type_id)='bigint' AND is_nullable=1) OR(name='notification_assignment_status' AND TYPE_NAME(user_type_id)='varchar' AND max_length=30 AND is_nullable=1))",9);
        expectCount(c,"SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('dbo.notifications') AND ((name IN('source_event_id','scope_branch_id','assignment_id') AND TYPE_NAME(user_type_id)='bigint' AND is_nullable=1) OR(name='audience' AND TYPE_NAME(user_type_id)='varchar' AND max_length=20 AND is_nullable=1) OR(name IN('read_at','delivered_at','delivery_retry_at') AND TYPE_NAME(user_type_id)='datetime2' AND scale=7 AND is_nullable=1) OR(name='delivery_attempts' AND TYPE_NAME(user_type_id)='int' AND is_nullable=0))",8);
        expectCount(c,"SELECT COUNT(*) FROM sys.default_constraints WHERE name IN('DF_shipment_events_notification_enabled','DF_shipment_events_notification_attempts','DF_notifications_delivery_attempts') AND REPLACE(REPLACE(definition,'(',''),')','')='0'",3);
        expectCount(c,"SELECT COUNT(*) FROM sys.foreign_keys WHERE name IN("+quotedNames(fks)+") AND is_disabled=0 AND is_not_trusted=0 AND delete_referential_action=0 AND update_referential_action=0",7);
        expectCount(c,"SELECT COUNT(*) FROM sys.check_constraints WHERE name IN("+quotedNames(checks)+") AND is_disabled=0 AND is_not_trusted=0",2);
        expectCount(c,"SELECT COUNT(*) FROM sys.indexes WHERE name IN("+quotedNames(indexes)+") AND is_disabled=0",4);
        expectCount(c,"SELECT COUNT(*) FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.notifications') AND name='UX_notifications_source_recipient_audience' AND is_unique=1 AND has_filter=1 AND REPLACE(REPLACE(filter_definition,'(',''),')','')='[source_event_id] IS NOT NULL'",1);
        // Match each FK's parent column and target, not merely its count/name.
        var matcher=java.util.regex.Pattern.compile("CONSTRAINT (FK_\\w+) FOREIGN KEY \\((\\w+)\\) REFERENCES dbo\\.(\\w+) \\((\\w+)\\)").matcher(sql);
        int fkCount=0;
        while(matcher.find()) {
            String parent=matcher.group(1).startsWith("FK_shipment_events_")?"shipment_events":"notifications";
            expectCount(c,"SELECT COUNT(*) FROM sys.foreign_keys f JOIN sys.foreign_key_columns k ON k.constraint_object_id=f.object_id WHERE f.name='"+matcher.group(1)+"' AND f.parent_object_id=OBJECT_ID('dbo."+parent+"') AND COL_NAME(k.parent_object_id,k.parent_column_id)='"+matcher.group(2)+"' AND f.referenced_object_id=OBJECT_ID('dbo."+matcher.group(3)+"') AND COL_NAME(k.referenced_object_id,k.referenced_column_id)='"+matcher.group(4)+"'",1);fkCount++;
        }
        if(fkCount!=7)throw new IllegalStateException("Unexpected V10 FK column contract");
        try(var s=c.createStatement();var r=s.executeQuery("SELECT i.name,c.name,k.key_ordinal,k.is_descending_key FROM sys.indexes i JOIN sys.index_columns k ON k.object_id=i.object_id AND k.index_id=i.index_id JOIN sys.columns c ON c.object_id=k.object_id AND c.column_id=k.column_id WHERE i.name IN("+quotedNames(indexes)+") AND k.key_ordinal>0 ORDER BY i.name,k.key_ordinal")) {
            var actual=new LinkedHashMap<String,List<String>>();while(r.next())actual.computeIfAbsent(r.getString(1),key->new ArrayList<>()).add(r.getString(2)+(r.getBoolean(4)?" DESC":""));
            var expected=Map.of("IX_shipment_events_notification_work",List.of("notification_enabled","notification_processed_at","notification_retry_at","id"),"UX_notifications_source_recipient_audience",List.of("source_event_id","user_id","audience"),"IX_notifications_inbox",List.of("user_id","created_at DESC","id DESC"),"IX_notifications_delivery",List.of("delivered_at","delivery_retry_at","id"));
            if(!actual.equals(expected))throw new IllegalStateException("V10 index keys/order differ from source");
        }
        expectCount(c,"SELECT COUNT_BIG(*) FROM dbo.shipment_events WHERE notification_enabled<>0 OR notification_attempts<>0 OR notification_processed_at IS NOT NULL OR notification_retry_at IS NOT NULL OR notification_assignment_id IS NOT NULL OR notification_previous_assignment_id IS NOT NULL OR notification_assignee_user_id IS NOT NULL OR notification_previous_user_id IS NOT NULL OR notification_assignment_status IS NOT NULL",0);
        expectCount(c,"SELECT COUNT_BIG(*) FROM dbo.notifications WHERE source_event_id IS NOT NULL OR audience IS NOT NULL OR scope_branch_id IS NOT NULL OR assignment_id IS NOT NULL OR read_at IS NOT NULL OR delivered_at IS NOT NULL OR delivery_retry_at IS NOT NULL OR delivery_attempts<>0",0);
        for(String table:List.of("shipment_events","notifications"))try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT_BIG(*) FROM dbo."+table)){r.next();System.out.println("V10 baseline rows dbo."+table+"="+r.getLong(1));}
        System.out.println("V10 verified: seventeen columns/types/nullability, three zero defaults, seven trusted NO ACTION FKs with target columns, two trusted CHECKs, four indexes with key order/filtered unique; no backfill/seed");
    }
    private static void requireNoV9Artifacts(Connection c) throws SQLException {
        expectCount(c,"SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0",37);
        expectCount(c,"SELECT COUNT(*) FROM sys.columns WHERE (object_id=OBJECT_ID('dbo.shipping_services') AND name IN('version','created_by','updated_by','max_weight_kg','max_length_cm','max_width_cm','max_height_cm')) OR(object_id IN(OBJECT_ID('dbo.shipping_rate_rules'),OBJECT_ID('dbo.pickup_fee_rules')) AND name IN('pricing_version_id','retired_from'))",0);
        expectCount(c,"SELECT COUNT(*) FROM sys.objects WHERE schema_id=SCHEMA_ID('dbo') AND name IN('pricing_versions','FK_services_created_by','FK_services_updated_by','CK_services_limits','FK_rate_rules_pricing_version','CK_rate_rules_retirement','FK_pickup_rules_pricing_version','CK_pickup_rules_retirement')",0);
        expectCount(c,"SELECT COUNT(*) FROM sys.indexes WHERE name IN('IX_pricing_versions_lookup','IX_rate_rules_version','IX_pickup_rules_version','UX_pickup_legacy_start','UX_rate_rules_version_scope','UX_pickup_rules_version_branch')",0);
        expectCount(c,"SELECT COUNT(*) FROM sys.key_constraints WHERE parent_object_id=OBJECT_ID('dbo.pickup_fee_rules') AND name='UQ_pickup_fee_rules_start'",1);
        expectCount(c,"SELECT COUNT(*) FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID('dbo.audit_logs') AND name='CK_audit_logs_resource' AND definition NOT LIKE '%SHIPPING_SERVICE%' AND is_disabled=0 AND is_not_trusted=0",1);
        System.out.println("V1-V8-only schema verified: thirty-seven tables; no partial V9 columns/table/FKs/CHECKs/indexes; prior uniqueness and audit CHECK retained");
    }
    private static void verifyV9(Connection c) throws Exception {
        String sql;
        try(var input=SqlServerQaTool.class.getResourceAsStream("/db/migration/V9__add_service_catalog_and_pricing_management.sql")) {
            if(input==null)throw new IllegalStateException("Missing V9 schema contract");sql=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
        var fks=sourceNames(sql,"CONSTRAINT (FK_\\w+) FOREIGN KEY");var checks=sourceNames(sql,"CONSTRAINT (CK_\\w+) CHECK");var indexes=sourceNames(sql,"CREATE (?:UNIQUE )?INDEX (\\w+) ON");
        if(fks.size()!=8||checks.size()!=10||indexes.size()!=6)throw new IllegalStateException("Unexpected V9 schema contract");
        expectCount(c,"SELECT COUNT(*) FROM sys.tables WHERE object_id=OBJECT_ID('dbo.pricing_versions')",1);
        Set<String> expectedColumns=sourceNames(sql.substring(sql.indexOf("CREATE TABLE dbo.pricing_versions"),sql.indexOf("CREATE INDEX IX_pricing_versions_lookup")),"(?m)^\\s*(\\w+) (?:BIGINT|VARCHAR|DATETIME2)\\b");
        var actualColumns=new LinkedHashSet<String>();
        try(var s=c.createStatement();var r=s.executeQuery("SELECT name FROM sys.columns WHERE object_id=OBJECT_ID('dbo.pricing_versions')")){while(r.next())actualColumns.add(r.getString(1));}
        if(expectedColumns.size()!=15||!expectedColumns.equals(actualColumns))throw new IllegalStateException("Pricing version columns differ from migration");
        expectCount(c,"SELECT COUNT(*) FROM sys.columns WHERE (object_id=OBJECT_ID('dbo.shipping_services') AND ((name='version' AND TYPE_NAME(user_type_id)='bigint' AND is_nullable=0) OR(name IN('created_by','updated_by') AND TYPE_NAME(user_type_id)='bigint' AND is_nullable=1) OR(name IN('max_weight_kg','max_length_cm','max_width_cm','max_height_cm') AND TYPE_NAME(user_type_id)='decimal' AND precision=10 AND scale=2 AND is_nullable=1))) OR(object_id IN(OBJECT_ID('dbo.shipping_rate_rules'),OBJECT_ID('dbo.pickup_fee_rules')) AND ((name='pricing_version_id' AND TYPE_NAME(user_type_id)='bigint') OR(name='retired_from' AND TYPE_NAME(user_type_id)='datetime2' AND scale=7)) AND is_nullable=1)",11);
        expectCount(c,"SELECT COUNT(*) FROM sys.default_constraints WHERE name IN('DF_services_version','DF_pricing_versions_version') AND REPLACE(REPLACE(definition,'(',''),')','')='0'",2);
        expectCount(c,"SELECT COUNT(*) FROM sys.foreign_keys WHERE name IN("+quotedNames(fks)+") AND is_disabled=0 AND is_not_trusted=0 AND delete_referential_action=0",8);
        expectCount(c,"SELECT COUNT(*) FROM sys.check_constraints WHERE name IN("+quotedNames(checks)+") AND is_disabled=0 AND is_not_trusted=0",10);
        expectCount(c,"SELECT COUNT(*) FROM sys.indexes WHERE name IN("+quotedNames(indexes)+") AND is_disabled=0",6);
        expectCount(c,"SELECT COUNT(*) FROM sys.key_constraints WHERE parent_object_id=OBJECT_ID('dbo.pricing_versions') AND name IN('PK_pricing_versions','UQ_pricing_versions_code')",2);
        expectCount(c,"SELECT COUNT(*) FROM sys.key_constraints WHERE parent_object_id=OBJECT_ID('dbo.pickup_fee_rules') AND name='UQ_pickup_fee_rules_start'",0);
        expectCount(c,"SELECT COUNT(*) FROM sys.indexes WHERE name IN('UX_pickup_legacy_start','UX_rate_rules_version_scope','UX_pickup_rules_version_branch') AND is_unique=1 AND has_filter=1 AND is_disabled=0",3);
        expectCount(c,"SELECT COUNT(*) FROM sys.check_constraints WHERE name='CK_audit_logs_resource' AND definition LIKE '%SHIPPING_SERVICE%' AND definition LIKE '%PRICE_VERSION%' AND definition LIKE '%PICKUP_RULE%'",1);
        try(var s=c.createStatement();var r=s.executeQuery("SELECT name,filter_definition FROM sys.indexes WHERE name IN('UX_pickup_legacy_start','UX_rate_rules_version_scope','UX_pickup_rules_version_branch') ORDER BY name")){while(r.next())System.out.println("V9 filtered index="+r.getString(1)+" predicate="+r.getString(2));}
        expectCount(c,"SELECT COUNT_BIG(*) FROM dbo.pricing_versions",0);
        expectCount(c,"SELECT COUNT_BIG(*) FROM dbo.shipping_services WHERE version<>0 OR created_by IS NOT NULL OR updated_by IS NOT NULL OR max_weight_kg IS NOT NULL OR max_length_cm IS NOT NULL OR max_width_cm IS NOT NULL OR max_height_cm IS NOT NULL",0);
        for(String table:List.of("shipping_rate_rules","pickup_fee_rules"))expectCount(c,"SELECT COUNT_BIG(*) FROM dbo."+table+" WHERE pricing_version_id IS NOT NULL OR retired_from IS NOT NULL",0);
        System.out.println("V9 verified: pricing_versions/15 columns, eleven added columns/types/nullability/defaults, eight trusted NO ACTION FKs, ten CHECKs, two keys, six indexes/three filtered unique; prior uniqueness replaced and audit CHECK extended; no pricing seed");
    }
    private static void verifyV8(Connection c) throws Exception {
        String sql;
        try(var input=SqlServerQaTool.class.getResourceAsStream("/db/migration/V8__add_last_mile_delivery_and_returns.sql")) {
            if(input==null)throw new IllegalStateException("Missing V8 schema contract");sql=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
        var tables=sourceNames(sql,"CREATE TABLE dbo\\.(\\w+) ");
        var fks=sourceNames(sql,"CONSTRAINT (FK_\\w+) FOREIGN KEY");
        var checks=sourceNames(sql,"CONSTRAINT (CK_\\w+) CHECK");
        var keys=sourceNames(sql,"CONSTRAINT ((?:PK|UQ)_\\w+) (?:PRIMARY KEY|UNIQUE)");
        var indexes=sourceNames(sql,"CREATE (?:UNIQUE )?INDEX (\\w+) ON");
        if(tables.size()!=6||fks.size()!=31||checks.size()!=23||keys.size()!=12||indexes.size()!=13)throw new IllegalStateException("Unexpected V8 schema contract");
        expectCount(c,"SELECT COUNT(*) FROM sys.tables WHERE schema_id=SCHEMA_ID('dbo') AND name IN("+quotedNames(tables)+")",6);
        expectCount(c,"SELECT COUNT(*) FROM sys.foreign_keys WHERE schema_id=SCHEMA_ID('dbo') AND name IN("+quotedNames(fks)+") AND is_disabled=0 AND is_not_trusted=0 AND delete_referential_action=0",31);
        expectCount(c,"SELECT COUNT(*) FROM sys.check_constraints WHERE schema_id=SCHEMA_ID('dbo') AND name IN("+quotedNames(checks)+") AND is_disabled=0 AND is_not_trusted=0",23);
        expectCount(c,"SELECT COUNT(*) FROM sys.key_constraints WHERE schema_id=SCHEMA_ID('dbo') AND name IN("+quotedNames(keys)+")",12);
        expectCount(c,"SELECT COUNT(*) FROM sys.indexes WHERE object_id IN(SELECT object_id FROM sys.tables WHERE schema_id=SCHEMA_ID('dbo')) AND name IN("+quotedNames(indexes)+") AND is_disabled=0",13);
        int columns=0;
        var blocks=java.util.regex.Pattern.compile("CREATE TABLE dbo\\.(\\w+) \\(([\\s\\S]*?)\\r?\\n\\);").matcher(sql);
        while(blocks.find()) {
            Set<String> expected=sourceNames(blocks.group(2),"(?m)^\\s*(\\w+) (?:BIGINT|INT|BIT|VARCHAR|NVARCHAR|DATETIME2)\\b");
            Set<String> actual=new LinkedHashSet<>();
            try(var s=c.prepareStatement("SELECT name FROM sys.columns WHERE object_id=OBJECT_ID(?)")) {
                s.setString(1,"dbo."+blocks.group(1));try(var r=s.executeQuery()){while(r.next())actual.add(r.getString(1));}
            }
            if(!expected.equals(actual))throw new IllegalStateException("V8 table columns differ from migration");columns+=expected.size();
        }
        expectCount(c,"SELECT COUNT(*) FROM sys.columns WHERE (object_id=OBJECT_ID('dbo.shipments') AND name IN('delivered_at','returned_at') AND TYPE_NAME(user_type_id)='datetime2' AND scale=7 AND is_nullable=1) OR(object_id=OBJECT_ID('dbo.delivery_attempts') AND ((name IN('assignment_id','actor_id') AND TYPE_NAME(user_type_id)='bigint') OR(name='failure_code' AND TYPE_NAME(user_type_id)='varchar' AND max_length=30) OR(name='started_at' AND TYPE_NAME(user_type_id)='datetime2' AND scale=7)) AND is_nullable=1) OR(object_id=OBJECT_ID('dbo.transport_trip_items') AND name='return_request_id' AND TYPE_NAME(user_type_id)='bigint' AND is_nullable=1)",7);
        expectCount(c,"SELECT COUNT(*) FROM sys.indexes WHERE name IN('UX_assignments_active_delivery','UX_delivery_attempts_success','UX_delivery_recoveries_pending','UX_delivery_recoveries_received') AND is_unique=1 AND has_filter=1 AND is_disabled=0",4);
        try(var s=c.createStatement();var r=s.executeQuery("SELECT name,filter_definition FROM sys.indexes WHERE name IN('UX_assignments_active_delivery','UX_delivery_attempts_success','UX_delivery_recoveries_pending','UX_delivery_recoveries_received') ORDER BY name")) {
            while(r.next())System.out.println("V8 filtered index="+r.getString(1)+" predicate="+r.getString(2));
        }
        for(String table:tables)expectCount(c,"SELECT COUNT_BIG(*) FROM dbo."+table,0);
        expectCount(c,"SELECT COUNT_BIG(*) FROM dbo.shipments WHERE delivered_at IS NOT NULL OR returned_at IS NOT NULL",0);
        expectCount(c,"SELECT COUNT_BIG(*) FROM dbo.delivery_attempts WHERE assignment_id IS NOT NULL OR actor_id IS NOT NULL OR failure_code IS NOT NULL OR started_at IS NOT NULL",0);
        expectCount(c,"SELECT COUNT_BIG(*) FROM dbo.transport_trip_items WHERE return_request_id IS NOT NULL",0);
        System.out.println("V8 verified: six tables/"+columns+" table columns, seven nullable added columns, 31 trusted NO ACTION FKs, 23 CHECKs, 12 keys, 13 indexes/four filtered unique; no seeded proof/return data");
    }
    private static void requireNoV4Artifacts(Connection c) throws SQLException {
        expectCount(c,"SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0",20);
        expectCount(c,"SELECT COUNT(*) FROM sys.columns WHERE (object_id=OBJECT_ID('dbo.branch_service_areas') AND name='supports_delivery') OR(object_id=OBJECT_ID('dbo.shipments') AND name IN('pickup_fee','quote_id'))",0);
        expectCount(c,"SELECT COUNT(*) FROM sys.objects WHERE schema_id=SCHEMA_ID('dbo') AND name IN('pickup_fee_rules','shipment_quotes','FK_shipments_quote','CK_shipments_pickup_fee','CK_shipments_quote_snapshot')",0);
        expectCount(c,"SELECT COUNT(*) FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.shipments') AND name='UX_shipments_quote_nonnull'",0);
        System.out.println("Twenty V1-V3 tables retained; no inspected partial V4 columns/tables/constraints/index present");
    }
    private static void verifyV4(Connection c) throws SQLException {
        expectCount(c,"SELECT COUNT(*) FROM sys.tables WHERE schema_id=SCHEMA_ID('dbo') AND name IN('pickup_fee_rules','shipment_quotes')",2);
        expectCount(c,"SELECT COUNT(*) FROM sys.columns c WHERE (object_id=OBJECT_ID('dbo.branch_service_areas') AND name='supports_delivery' AND TYPE_NAME(user_type_id)='bit' AND is_nullable=0) OR(object_id=OBJECT_ID('dbo.shipments') AND name='pickup_fee' AND TYPE_NAME(user_type_id)='decimal' AND precision=18 AND scale=2 AND is_nullable=1) OR(object_id=OBJECT_ID('dbo.shipments') AND name='quote_id' AND TYPE_NAME(user_type_id)='bigint' AND is_nullable=1)",3);
        expectCount(c,"SELECT COUNT(*) FROM sys.default_constraints WHERE parent_object_id=OBJECT_ID('dbo.branch_service_areas') AND name='DF_service_areas_delivery' AND REPLACE(REPLACE(definition,'(',''),')','')='0'",1);
        expectCount(c,"SELECT COUNT(*) FROM sys.foreign_keys WHERE name IN('FK_pickup_fee_rules_branch','FK_pickup_fee_rules_service','FK_quotes_requester','FK_quotes_origin_branch','FK_quotes_destination_branch','FK_quotes_origin_hub','FK_quotes_destination_hub','FK_quotes_service','FK_quotes_shipping_rate','FK_quotes_pickup_rule','FK_shipments_quote') AND is_disabled=0 AND is_not_trusted=0 AND delete_referential_action=0",11);
        expectCount(c,"SELECT COUNT(*) FROM sys.check_constraints WHERE name IN('CK_pickup_fee_rules_amount','CK_pickup_fee_rules_period','CK_quotes_method','CK_quotes_payer','CK_quotes_weight','CK_quotes_length','CK_quotes_width','CK_quotes_height','CK_quotes_amounts','CK_quotes_pickup','CK_quotes_expiry','CK_quotes_consumed','CK_shipments_pickup_fee','CK_shipments_quote_snapshot') AND is_disabled=0 AND is_not_trusted=0",14);
        expectCount(c,"SELECT COUNT(*) FROM sys.indexes WHERE name IN('IX_pickup_fee_rules_lookup','IX_shipment_quotes_owner','IX_shipment_quotes_expiry','UX_shipments_quote_nonnull') AND is_disabled=0",4);
        expectCount(c,"SELECT COUNT(*) FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.shipments') AND name='UX_shipments_quote_nonnull' AND is_unique=1 AND has_filter=1 AND is_disabled=0 AND REPLACE(REPLACE(REPLACE(filter_definition,'[',''),']',''),' ','') LIKE '%quote_idISNOTNULL%'",1);
        expectCount(c,"SELECT COUNT(*) FROM sys.index_columns ic JOIN sys.indexes i ON i.object_id=ic.object_id AND i.index_id=ic.index_id JOIN sys.columns col ON col.object_id=ic.object_id AND col.column_id=ic.column_id WHERE i.object_id=OBJECT_ID('dbo.shipments') AND i.name='UX_shipments_quote_nonnull' AND ic.key_ordinal=1 AND col.name='quote_id'",1);
        System.out.println("V4 two tables, three added columns/types/nullability/default, eleven trusted NO ACTION FKs, fourteen CHECKs, four indexes and quote_id filtered unique key verified");
    }
    private static void requireNoV3Artifacts(Connection c,long tables) throws SQLException {
        if(tables!=19)throw new IllegalStateException("Expected eighteen V1 tables plus history before V3");
        expectCount(c,V3_COLUMNS,0);expectCount(c,V3_INDEXES,0);
        expectCount(c,"SELECT COUNT(*) FROM sys.objects WHERE schema_id=SCHEMA_ID('dbo') AND name IN('branch_service_areas','FK_employees_user','FK_branches_parent_hub','CK_branches_parent_hub','FK_shipments_origin_hub','FK_shipments_destination_hub','CK_shipments_method','CK_shipments_version','CK_shipment_events_from_status','CK_shipment_events_to_status')",0);
        expectCount(c,"SELECT COUNT(*) FROM dbo.roles WHERE name='ROLE_SHIPPER'",0);
        expectCount(c,"SELECT COUNT(*) FROM sys.check_constraints WHERE name='CK_shipments_status' AND definition NOT LIKE '%AT_ORIGIN_BRANCH%' AND is_disabled=0 AND is_not_trusted=0",1);
        System.out.println("V1/V2-only schema verified; no partial V3 artifacts; existing database preserved");
    }
    private static void verifyV3(Connection c) throws SQLException {
        expectCount(c,V3_COLUMNS,6);expectCount(c,V3_INDEXES,3);
        expectCount(c,"SELECT COUNT(*) FROM sys.tables WHERE object_id=OBJECT_ID('dbo.branch_service_areas')",1);
        expectCount(c,"SELECT COUNT(*) FROM sys.foreign_keys WHERE name IN('FK_employees_user','FK_branches_parent_hub','FK_shipments_origin_hub','FK_shipments_destination_hub','FK_branch_service_areas_branch') AND is_disabled=0 AND is_not_trusted=0 AND delete_referential_action=0",5);
        expectCount(c,"SELECT COUNT(*) FROM sys.check_constraints WHERE name IN('CK_branches_parent_hub','CK_branch_service_areas_method','CK_shipments_method','CK_shipments_version','CK_shipment_events_from_status','CK_shipment_events_to_status') AND is_disabled=0 AND is_not_trusted=0",6);
        expectCount(c,"SELECT COUNT(*) FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.employees') AND name='UX_employees_user_nonnull' AND is_unique=1 AND has_filter=1 AND is_disabled=0 AND REPLACE(REPLACE(REPLACE(filter_definition,'[',''),']',''),' ','') LIKE '%user_idISNOTNULL%'",1);
        expectCount(c,"SELECT COUNT(*) FROM dbo.roles WHERE name='ROLE_SHIPPER'",1);
        expectCount(c,"SELECT COUNT(*) FROM sys.check_constraints WHERE name='CK_shipments_status' AND definition LIKE '%AT_ORIGIN_BRANCH%' AND definition LIKE '%AT_DESTINATION_BRANCH%' AND is_disabled=0 AND is_not_trusted=0",1);
        System.out.println("V3 six columns, service-area table, five trusted NO ACTION FKs, CHECKs, three indexes and unique user_id IS NOT NULL filter verified");
    }
    private static void inspectMigrationState(Connection c) throws Exception {
        // SELECT only: never retry a failed migration, execute its DDL, repair history or reset the database.
        try(var s=c.createStatement();var r=s.executeQuery("SELECT OBJECT_ID('dbo.flyway_schema_history','U')")) {
            r.next();if(r.getObject(1)==null)return;
        }
        String latest=null;
        try(var s=c.createStatement();var r=s.executeQuery("SELECT installed_rank,version,success FROM dbo.flyway_schema_history ORDER BY installed_rank")) {
            while(r.next()) {
                System.out.println("History rank="+r.getInt(1)+" version="+r.getString(2)+" success="+r.getBoolean(3));
                if(r.getBoolean(3))latest=r.getString(2);
            }
        }
        if("3".equals(latest)) {
            requirePendingHistory("v4",readHistory(c));verifyV3(c);requireNoV4Artifacts(c);
            try(var s=c.createStatement();var r=s.executeQuery("SELECT OBJECT_NAME(c.object_id),c.name,TYPE_NAME(c.user_type_id),c.max_length,c.is_nullable FROM sys.columns c WHERE (object_id=OBJECT_ID('dbo.employees') AND c.name='user_id') OR(object_id=OBJECT_ID('dbo.branches') AND c.name='parent_hub_id') OR(object_id=OBJECT_ID('dbo.shipments') AND c.name IN('shipment_method','origin_hub_id','destination_hub_id','version')) ORDER BY c.object_id,c.column_id")) {
                while(r.next())System.out.println("V3 column="+r.getString(1)+"."+r.getString(2)+" type="+r.getString(3)+" maxBytes="+r.getInt(4)+" nullable="+r.getBoolean(5));
            }
            return;
        }
        if(latest!=null&&Integer.parseInt(latest)>=4) {
            readHistory(c);verifyV4(c);
            if("7".equals(latest)) {
                requirePendingHistory("v8",readHistory(c));requireNoV8Artifacts(c);
                System.out.println("Post-V8 rollback verified: thirty-one tables; no inspected V8 columns, tables, constraints or old-table indexes present; V4 retained");
            }
            if("8".equals(latest)) {
                requirePendingHistory("v9",readHistory(c));verifyV8(c);requireNoV9Artifacts(c);
                System.out.println("Post-V9 rollback verified: thirty-seven tables; eleven added V9 columns, pricing table, named FKs/CHECKs/indexes absent; prior pickup uniqueness and audit CHECK retained");
            }
            if("9".equals(latest)) {
                requirePendingHistory("v10",readHistory(c));verifyV9(c);
                requireNoV10Artifacts(c);
                System.out.println("Post-V10 rollback verified: thirty-eight tables, no inspected seventeen V10 columns or named FKs/CHECKs/defaults/four indexes; V9 retained");
            }
            if("10".equals(latest)) {
                requirePendingHistory("v11",readHistory(c));verifyV10(c);
                requireNoV11Artifacts(c);
                System.out.println("V1-V10-only schema verified: thirty-eight tables; twelve V11 columns, three tables, named constraints/defaults/indexes absent; original ticket status CHECK retained");
            }
            if("11".equals(latest)) {
                requirePendingHistory("v12",readHistory(c));verifyV11(c);requireNoV12Artifacts(c);
            }
            if("12".equals(latest)) {requirePendingHistory("pending",readHistory(c));verifyRemainingTarget(c,12);requireNoV13Artifacts(c);}
            if("13".equals(latest)) {
                requireHistoryThrough(13,readHistory(c));verifyRemainingTarget(c,12);verifyRemainingTarget(c,13);
                var names=new ArrayList<String>();long businessRows=0;
                try(var s=c.createStatement();var r=s.executeQuery("SELECT name FROM sys.tables WHERE schema_id=SCHEMA_ID('dbo') AND is_ms_shipped=0 AND name NOT IN('roles','flyway_schema_history')")){while(r.next())names.add(r.getString(1));}
                for(String name:names){if(!name.matches("\\w+"))throw new IllegalStateException("Unexpected catalog identifier");try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT_BIG(*) FROM dbo.["+name+"]")){r.next();businessRows+=r.getLong(1);}}
                System.out.println("Read-only total business rows excluding roles/history="+businessRows);
                try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT_BIG(*) FROM dbo.roles")){r.next();System.out.println("Read-only seeded roles="+r.getLong(1));}
            }
            for(String table:List.of("pickup_fee_rules","shipment_quotes","shipments")) {
                try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT_BIG(*) FROM dbo."+table)) {r.next();System.out.println("Read-only row count dbo."+table+"="+r.getLong(1));}
            }
            return;
        }
        if(!"2".equals(latest))return;
        for(String table:List.of("roles","users","employees","branches","shipments","shipment_events")) {
            try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT_BIG(*) FROM dbo."+table)) {
                r.next();System.out.println("Post-failure row count dbo."+table+"="+r.getLong(1));
            }
        }
        try(var s=c.createStatement();var r=s.executeQuery("SELECT COL_LENGTH('dbo.employees','user_id'),COL_LENGTH('dbo.branches','parent_hub_id'),COL_LENGTH('dbo.shipments','shipment_method'),OBJECT_ID('dbo.branch_service_areas','U'),(SELECT COUNT(*) FROM dbo.roles WHERE name='ROLE_SHIPPER')")) {
            r.next();System.out.println("V3 artifacts present: employee user_id="+(r.getObject(1)!=null)+" parent_hub_id="+(r.getObject(2)!=null)+" shipment_method="+(r.getObject(3)!=null)+" branch_service_areas="+(r.getObject(4)!=null)+" ROLE_SHIPPER rows="+r.getInt(5));
        }
        // Patched V3 contains GO delimiters understood by Flyway, not by the SQL Server metadata DMV.
        // The original static-analysis evidence remains in the historical sanitized QA log.
    }
}
