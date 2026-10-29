package com.ute.uteexpress.release.v14;

import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.HexFormat;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.callback.*;

/** Does not inject a failure without a pinned binary AND positive SQL Server transaction proof. */
final class V14TransactionProbe implements Callback {
    static final String REVIEWED_FLYWAY="11.14.1";
    static final String REVIEWED_SHA256="45148edbcb55016418ff1ef7ecca3331b55f5216b3af4bd2db40063904db3715";
    static boolean reviewedBinary() {
        try {
            Path jar=Path.of(Flyway.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return jar.getFileName().toString().equals("flyway-core-"+REVIEWED_FLYWAY+".jar")
                &&HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))).equals(REVIEWED_SHA256);
        }catch(Exception unavailable){return false;}
    }
    record Stamp(Connection connection,int session,long transaction,int count,int state,boolean autoCommit) {
        boolean active(){return session>0&&transaction>0&&count>0&&state==1&&!autoCommit;}
        boolean same(Stamp other){return other!=null&&active()&&other.active()&&connection==other.connection
            &&session==other.session&&transaction==other.transaction&&count==other.count;}
    }
    private static Stamp stamp(Connection c)throws SQLException {
        try(var s=c.createStatement();var r=s.executeQuery("SELECT @@SPID, CURRENT_TRANSACTION_ID(), @@TRANCOUNT, XACT_STATE()")){
            if(!r.next())throw new SQLException("No transaction stamp");
            return new Stamp(c,r.getInt(1),r.getLong(2),r.getInt(3),r.getInt(4),c.getAutoCommit());
        }
    }
    static final class ExpectedRollback extends RuntimeException { ExpectedRollback(){super("V14_QA_EXPECTED_ROLLBACK");} }
    private final String version;
    private final boolean inject;
    private Stamp before;
    boolean sameTransaction;
    boolean fired;
    V14TransactionProbe(String version,boolean inject){this.version=version;this.inject=inject;}
    @Override public boolean supports(Event event,Context context){
        // Flyway 11.14.1 probes CREATE_SCHEMA and migrate/undo events with a null Context.
        // Only per-migration events with a known version can produce transaction proof.
        if((event!=Event.BEFORE_EACH_MIGRATE&&event!=Event.AFTER_EACH_MIGRATE)||context==null)return false;
        var migration=context.getMigrationInfo();
        return migration!=null&&migration.getVersion()!=null&&version.equals(migration.getVersion().getVersion());
    }
    @Override public boolean canHandleInTransaction(Event event,Context context){return true;}
    @Override public String getCallbackName(){return "v14-qa-transaction-proof";}
    @Override public void handle(Event event,Context context) {
        try {
            Stamp current=stamp(context.getConnection());
            if(event==Event.BEFORE_EACH_MIGRATE){before=current;return;}
            sameTransaction=current.same(before);
        }catch(SQLException unavailable){sameTransaction=false;return;}
        if(inject&&sameTransaction&&reviewedBinary()) {
            try{V14SqlServerQaTool.verifyV14(context.getConnection());}
            catch(SQLException invalid){throw new IllegalStateException("V14 metadata invalid before fault injection",invalid);}
            fired=true;throw new ExpectedRollback();
        }
    }
}
