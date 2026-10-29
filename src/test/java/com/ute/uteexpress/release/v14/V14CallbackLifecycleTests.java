package com.ute.uteexpress.release.v14;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.callback.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Flyway's actual lifecycle over an isolated H2 database, not SQL Server integration. */
class V14CallbackLifecycleTests {
    @TempDir Path migrations;
    @Test void realFlywayCanProbeCallbackWithNullContextThenExecuteItsTargetMigration() throws Exception {
        Files.writeString(migrations.resolve("V13__callback_lifecycle_probe.sql"),"CREATE TABLE callback_probe(id INT PRIMARY KEY);");
        var source=new DriverManagerDataSource("jdbc:h2:mem:v14_callbacks_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","callback_test",UUID.randomUUID().toString());
        var probe=new V14TransactionProbe("13",false);
        var nullContexts=new AtomicInteger();var before=new AtomicInteger();var after=new AtomicInteger();
        Callback observed=new Callback() {
            @Override public boolean supports(Event event,Context context) {
                if(context==null)nullContexts.incrementAndGet();
                return probe.supports(event,context);
            }
            @Override public boolean canHandleInTransaction(Event event,Context context){return probe.canHandleInTransaction(event,context);}
            @Override public String getCallbackName(){return "observed-v14-qa-proof";}
            @Override public void handle(Event event,Context context) {
                if(event==Event.BEFORE_EACH_MIGRATE)before.incrementAndGet();
                if(event==Event.AFTER_EACH_MIGRATE)after.incrementAndGet();
                probe.handle(event,context);
            }
        };
        var flyway=Flyway.configure().dataSource(source).locations("filesystem:"+migrations.toAbsolutePath().toString().replace('\\','/'))
            .target("13").cleanDisabled(true).callbacks(observed).load();
        assertEquals(1,assertDoesNotThrow(flyway::migrate).migrationsExecuted);
        assertTrue(nullContexts.get()>0,"Exercise Flyway's real supports(event, null) call");
        assertEquals(1,before.get());assertEquals(1,after.get());
        assertFalse(probe.sameTransaction,"H2 cannot prove SQL Server transaction stamps");assertFalse(probe.fired);
        assertDoesNotThrow(flyway::validate);assertEquals(0,flyway.migrate().migrationsExecuted);
        try(var c=source.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM callback_probe")) {assertTrue(r.next());assertEquals(0,r.getInt(1));}
    }
}
