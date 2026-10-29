package com.ute.uteexpress.release.v15;

import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.callback.Callback;
import static com.ute.uteexpress.release.v15.V15Sql.*;

/** Opt-in CLI in test sources only; no Boot, HTTP, worker, reset, repair or database creation. */
public final class V15SqlServerQaTool {
    private V15SqlServerQaTool() { }
    private static String stage="settings";
    public static void main(String[] args) {
        try {
            if(args.length!=1)throw new IllegalArgumentException("Exactly one mode is required");
            execute(V15QaTarget.settings(args[0],System.getenv()));
        }catch(Exception error){V15QaDiagnostics.lines(stage,error).forEach(System.err::println);System.exit(2);}
    }
    static Flyway flyway(DataSource source,String target,Callback... callbacks) {
        return Flyway.configure().dataSource(source).locations("classpath:db/migration").defaultSchema("dbo")
            .target(target).cleanDisabled(true).baselineOnMigrate(false).outOfOrder(false).validateOnMigrate(true)
            .validateMigrationNaming(true).skipDefaultCallbacks(true).callbacks(callbacks).load();
    }
    private static void execute(V15QaTarget.Settings settings)throws Exception {
        var source=new V15QaTarget.GuardedSource(settings);
        if(settings.mode().equals("inspect")) {
            stage="inspect";V15ReadOnlyInspection.inspect(source).forEach(System.out::println);return;
        }
        if(settings.mode().equals("run")){stage="source-review";V15SourceReview.requireReviewed();}
        stage="preflight";V15QaTarget.preflight(source);pass("identity-port-login-protected-access-permissions-empty");
        if(settings.mode().equals("preflight"))return;
        source.enableWrites();
        stage="baseline-configure";var observe=new V15TransactionProbe("14",false);
        stage="baseline-migrate";flyway(source,"14",observe).migrate();
        stage="baseline-validate";history(source,14);
        Map<String,String> indexes;
        try(var c=source.getConnection()){V15SchemaChecks.baseline(c);indexes=V15SchemaChecks.pickupSignature(c);}
        pass("unmodified-flyway-v1-v14-history-checksums-v14-metadata");
        stage="legacy-fixtures";var rows=V15LegacyFixtures.create(source);var snapshot=V15LegacyFixtures.snapshot(source,rows);
        pass("synthetic-legacy-fixtures-committed-and-fingerprinted");
        boolean rollback=false;
        stage="migration-rollback-proof";
        if(observe.sameTransaction&&V15TransactionProbe.reviewedBinary()) {
            var fault=new V15TransactionProbe("15",true);
            try {flyway(source,"15",fault).migrate();require(!fault.fired,"Fault failed to stop migration");}
            catch(Exception failure) {
                if(!fault.fired||!isExpectedRollback(failure))throw failure;
                System.out.println("EXPECTED_FAIL v15-afterEachMigrate-same-transaction");
                history(source,14);snapshot.verify(source);
                try(var c=source.getConnection()) {
                    V15SchemaChecks.baseline(c);
                    require(V15SchemaChecks.pickupSignature(c).equals(indexes),"Rollback changed index definitions");
                    require(scalar(c,"SELECT COUNT_BIG(*) FROM dbo.flyway_schema_history WHERE version='15'")==0,"V15 history survived rollback");
                }
                rollback=true;pass("v15-ddl-history-index-legacy-transaction-rollback");
            }
            if(!fault.fired)System.out.println("SKIP migration-rollback-no-positive-v15-transaction-proof");
        }else System.out.println("SKIP migration-rollback-no-positive-baseline-transaction-or-reviewed-binary");
        stage="v15-migrate";flyway(source,"15").migrate();
        stage="v15-history-schema-legacy";history(source,15);snapshot.verify(source);
        try(var c=source.getConnection()) {
            V15SchemaChecks.upgraded(c);V15SchemaChecks.legacyNulls(c,rows);
            require(V15SchemaChecks.pickupSignature(c).equals(indexes),"Upgrade changed existing index definitions");
            require(scalar(c,"SELECT COUNT_BIG(*) FROM dbo.flyway_schema_history WHERE version='15' AND checksum=? AND success=1",V15SourceReview.CHECKSUM)==1,"Applied V15 checksum mismatch");
        }
        pass("v15-original-migration-columns-fks-indexes-checks-and-legacy-preserved");
        stage="constraints";V15ConstraintChecks.run(source,rows);snapshot.verify(source);
        stage="hibernate-business";V15BusinessChecks.run(source);
        stage="final-validate";history(source,15);snapshot.verify(source);
        try(var c=source.getConnection()){V15SchemaChecks.upgraded(c);V15SchemaChecks.legacyNulls(c,rows);}
        System.out.println("RESULT PASS required-gates migration-rollback="+(rollback?"PASS":"SKIP")+" database-retained-no-cleanup");
    }
    static boolean isExpectedRollback(Throwable error) {
        var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        for(var current=error;current!=null&&seen.add(current);current=current.getCause())if(current instanceof V15TransactionProbe.ExpectedRollback)return true;
        return false;
    }
    static void history(DataSource source,int target)throws SQLException {
        var f=flyway(source,Integer.toString(target));f.validate();var applied=f.info().applied();
        require(applied.length==target,"Unexpected history count");
        for(int i=0;i<target;i++) {
            var m=applied[i];require(m.getVersion()!=null&&Integer.toString(i+1).equals(m.getVersion().getVersion())&&m.getState()==MigrationState.SUCCESS,"Unexpected history state");
            require(m.getChecksum()!=null,"Missing applied checksum");
            pass("history-v"+(i+1)+"-success-checksum="+m.getChecksum());
        }
    }
    private static void pass(String name){System.out.println("PASS "+name);}
}
