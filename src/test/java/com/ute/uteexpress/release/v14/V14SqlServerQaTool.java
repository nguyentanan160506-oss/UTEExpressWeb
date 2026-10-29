package com.ute.uteexpress.release.v14;

import com.ute.uteexpress.service.OriginReceiptCodes;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.callback.Callback;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

/** Explicit QA CLI only. No database/login creation, cleanup, seed, Spring Boot or web server. */
public final class V14SqlServerQaTool {
    private V14SqlServerQaTool() { }
    private static String stage="settings";
    public static void main(String[] args) {
        try {
            if(args.length!=1)throw new IllegalArgumentException("Exactly one QA mode is required");
            execute(V14QaTarget.settings(args[0],System.getenv()));
        }catch(Exception error){
            V14QaDiagnostics.lines(stage,error).forEach(System.err::println);
            System.exit(2);
        }
    }
    static Flyway flyway(DataSource ds,String version,Callback... callbacks) {
        return Flyway.configure().dataSource(ds).locations("classpath:db/migration").defaultSchema("dbo")
            .target(version).cleanDisabled(true).baselineOnMigrate(false).outOfOrder(false).validateOnMigrate(true)
            .validateMigrationNaming(true).skipDefaultCallbacks(true).callbacks(callbacks).load();
    }
    private static void execute(V14QaTarget.Settings settings)throws Exception {
        var ds=new V14QaTarget.GuardedSource(settings);
        if(settings.mode().equals("inspect")) {
            stage="inspect";V14ReadOnlyInspection.print(V14ReadOnlyInspection.read(ds));return;
        }
        stage="preflight";V14QaTarget.preflight(ds);pass("identity-port-login-permissions-empty");
        if(settings.mode().equals("preflight"))return;
        ds.enableWrites();
        stage="v13-configure";var probe13=new V14TransactionProbe("13",false);var baseline=flyway(ds,"13",probe13);
        stage="v13-migrate";baseline.migrate();
        stage="v13-history";history(ds,13);
        stage="v13-absence-v14";requireNoV14(ds);pass("flyway-v1-v13");
        boolean reviewed=V14TransactionProbe.reviewedBinary();
        System.out.println((reviewed?"PASS":"SKIP")+" reviewed-flyway-binary-"+V14TransactionProbe.REVIEWED_FLYWAY);
        System.out.println((probe13.sameTransaction?"PASS":"SKIP")+" callback-v13-same-session-transaction");
        stage="fixtures";var rows=V14QaFixtures.create(ds);List<List<String>> legacy;
        try(var c=ds.getConnection()){legacy=V14QaFixtures.legacy(c);}
        require(legacy.size()==2,"Expected two duplicate legacy fixtures");pass("legacy-fixtures-committed");
        stage="fault-probe";
        boolean migrationRollback=false;
        if(reviewed&&probe13.sameTransaction) {
            var fault=new V14TransactionProbe("14",true);
            try {flyway(ds,"14",fault).migrate();require(!fault.fired,"Injected failure did not abort migration");}
            catch(Exception error){
                if(!fault.fired||!hasExpectedRollback(error))throw error;
                System.out.println("EXPECTED_FAIL afterEachMigrate-v14-rollback-probe");
                requireNoV14(ds);history(ds,13);checkLegacy(ds,legacy,false);migrationRollback=true;pass("flyway-v14-transaction-rollback");
            }
            if(!fault.fired)System.out.println("SKIP flyway-v14-transaction-rollback-proof-unavailable");
        }else System.out.println("SKIP flyway-v14-transaction-rollback-proof-unavailable");
        stage="v14";flyway(ds,"14").migrate();history(ds,14);flyway(ds,"14").validate();
        try(var c=ds.getConnection()){verifyV14(c);}
        checkLegacy(ds,legacy,true);pass("flyway-v14-checksum-index-check-legacy");
        stage="constraints";constraints(ds,rows);
        stage="hibernate-validate";hibernate(ds);pass("hibernate-schema-validate");
        history(ds,14);flyway(ds,"14").validate();checkLegacy(ds,legacy,true);
        System.out.println("RESULT PASS required-checks migration-rollback="+(migrationRollback?"PASS":"SKIP")+" database-retained-no-cleanup");
    }
    private static boolean hasExpectedRollback(Throwable error){for(Throwable t=error;t!=null;t=t.getCause())if(t instanceof V14TransactionProbe.ExpectedRollback)return true;return false;}
    static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    static void pass(String name){System.out.println("PASS "+name);}
    static void history(DataSource source,int target)throws SQLException {
        var flyway=flyway(source,Integer.toString(target));flyway.validate();
        var applied=flyway.info().applied();require(applied.length==target,"Unexpected history count");
        for(int i=0;i<target;i++)require(applied[i].getVersion()!=null&&Integer.toString(i+1).equals(applied[i].getVersion().getVersion())
            &&applied[i].getState()==org.flywaydb.core.api.MigrationState.SUCCESS,"Unexpected history state");
    }
    static long scalar(Connection c,String sql)throws SQLException {
        try(var s=c.createStatement();var r=s.executeQuery(sql)){if(!r.next())throw new SQLException("Missing scalar");return r.getLong(1);}
    }
    private static void requireNoV14(DataSource source)throws SQLException {
        try(var c=source.getConnection()) {
            require(scalar(c,"SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.shipment_intakes') AND name IN ('system_receipt_code','request_version')")==0,"V14 columns remain");
            require(scalar(c,"SELECT COUNT(*) FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.shipment_intakes') AND name='UX_intakes_system_receipt_code'")==0,"V14 index remains");
            require(scalar(c,"SELECT COUNT(*) FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.shipment_intakes') AND name='CK_intakes_system_receipt'")==0,"V14 CHECK remains");
            require(scalar(c,"SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE version='14'")==0,"V14 history remains");
        }
    }
    static void verifyV14(Connection c)throws SQLException {
        require(scalar(c,"SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.shipment_intakes') AND name='system_receipt_code' AND TYPE_NAME(user_type_id)='varchar' AND max_length=45 AND is_nullable=1")==1,"Code column mismatch");
        require(scalar(c,"SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.shipment_intakes') AND name='request_version' AND TYPE_NAME(user_type_id)='bigint' AND is_nullable=1")==1,"Version column mismatch");
        try(var s=c.createStatement();var r=s.executeQuery("SELECT is_unique,has_filter,is_disabled,filter_definition,ignore_dup_key FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.shipment_intakes') AND name='UX_intakes_system_receipt_code'")) {
            require(r.next()&&r.getBoolean(1)&&r.getBoolean(2)&&!r.getBoolean(3)&&!r.getBoolean(5),"Unique filtered index mismatch");
            String definition=r.getString(4);require(definition!=null,"Filter definition unavailable");
            String filter=definition.replaceAll("[\\[\\]()]","").replaceAll("\\s+"," ").strip();
            require(filter.equalsIgnoreCase("system_receipt_code IS NOT NULL"),"Filter predicate mismatch");
        }
        require(scalar(c,"SELECT COUNT(*) FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.shipment_intakes') AND name='CK_intakes_system_receipt' AND is_disabled=0 AND is_not_trusted=0")==1,"CHECK disabled or untrusted");
        require(scalar(c,"SELECT COUNT(*) FROM sys.index_columns ic JOIN sys.indexes i ON i.object_id=ic.object_id AND i.index_id=ic.index_id JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE i.object_id=OBJECT_ID(N'dbo.shipment_intakes') AND i.name='UX_intakes_system_receipt_code' AND ic.key_ordinal>0 AND c.name='system_receipt_code'")==1,"Index key mismatch");
    }
    private static void checkLegacy(DataSource source,List<List<String>> before,boolean v14)throws SQLException {
        try(var c=source.getConnection()) {
            require(before.equals(V14QaFixtures.legacy(c)),"Legacy rows changed");
            if(v14)require(scalar(c,"SELECT COUNT(*) FROM dbo.shipment_intakes WHERE receipt_reference=N'V14QA-LEGACY-DUPLICATE' AND system_receipt_code IS NULL AND request_version IS NULL")==2,"Legacy nullable metadata changed");
        }
    }
    private static void constraints(DataSource source,V14QaFixtures.Rows rows)throws SQLException {
        String code=OriginReceiptCodes.create(LocalDateTime.now(ZoneOffset.UTC));
        try(var c=source.getConnection()) {
            c.setAutoCommit(false);
            try{V14QaFixtures.intake(c,rows,rows.shipments().get(2),code,code,0L);c.commit();}
            catch(SQLException|RuntimeException error){c.rollback();throw error;}
        }
        try(var c=source.getConnection()){require(scalar(c,"SELECT COUNT(*) FROM dbo.shipment_intakes WHERE system_receipt_code IS NOT NULL")==1,"Positive receipt not persisted");}
        pass("valid-generated-receipt-commit-reconnect");
        expectFailure(source,rows,code,code,0L,Set.of(2601,2627),"UX_intakes_system_receipt_code",true);pass("unique-and-business-transaction-rollback");
        String unused="RCV-20261029-"+"B".repeat(32); // Separate CHECK tests from the already-used UNIQUE key.
        expectFailure(source,rows,"RCV-20261029-"+"C".repeat(32),unused,0L,Set.of(547),"CK_intakes_system_receipt",false);pass("check-reference-mismatch");
        expectFailure(source,rows,unused,unused,null,Set.of(547),"CK_intakes_system_receipt",false);pass("check-missing-version");
        expectFailure(source,rows,unused,null,0L,Set.of(547),"CK_intakes_system_receipt",false);pass("check-missing-code");
        expectFailure(source,rows,unused,unused,-1L,Set.of(547),"CK_intakes_system_receipt",false);pass("check-negative-version");
        expectFailure(source,rows,"SHORT","SHORT",0L,Set.of(547),"CK_intakes_system_receipt",false);pass("check-code-length");
    }
    private static void expectFailure(DataSource source,V14QaFixtures.Rows rows,String reference,String code,Long version,
            Set<Integer> expected,String constraint,boolean updateShipment)throws SQLException {
        boolean rejected=false;
        try(var c=source.getConnection()) {
            c.setAutoCommit(false);
            try {
                if(updateShipment)try(var p=c.prepareStatement("UPDATE dbo.shipments SET status='AT_ORIGIN_BRANCH',current_branch_id=? WHERE id=?")){
                    p.setLong(1,rows.branch());p.setLong(2,rows.shipments().get(3));require(p.executeUpdate()==1,"Fixture shipment missing");
                }
                V14QaFixtures.intake(c,rows,rows.shipments().get(3),reference,code,version);
            }catch(SQLException error){
                if(!expected.contains(error.getErrorCode())||!Objects.toString(error.getMessage(),"").contains(constraint))throw error;
                rejected=true;
            }finally{c.rollback();}
        }
        require(rejected,"Database accepted invalid receipt");
        try(var c=source.getConnection();var p=c.prepareStatement("SELECT status,current_branch_id FROM dbo.shipments WHERE id=?")){
            p.setLong(1,rows.shipments().get(3));try(var r=p.executeQuery()){require(r.next()&&"CONFIRMED".equals(r.getString(1))&&r.getObject(2)==null,"Fixture shipment rollback mismatch");}
            require(scalar(c,"SELECT COUNT(*) FROM dbo.shipment_intakes")==3,"Receipt rollback count mismatch");
        }
    }
    private static void hibernate(DataSource source) {
        var factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(source);factory.setPackagesToScan("com.ute.uteexpress.entity");
        factory.setMappingResources("orm-v14-qa.xml"); // Never migrate V15 in the V14-only approved database.
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.jdbc.time_zone","UTC","hibernate.show_sql",false));
        try{factory.afterPropertiesSet();require(factory.getObject()!=null,"No EntityManagerFactory");}finally{factory.destroy();}
    }
}
