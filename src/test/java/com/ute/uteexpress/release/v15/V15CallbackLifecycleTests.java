package com.ute.uteexpress.release.v15;

import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.callback.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Actual Flyway lifecycle over H2 only; deliberately cannot claim SQL Server transaction proof. */
class V15CallbackLifecycleTests {
    @TempDir Path directory;
    @Test void realFlywayProbesNullContextsAndCannotInjectFaultWithoutSqlServerTransactionProof()throws Exception {
        Files.writeString(directory.resolve("V15__lifecycle.sql"),"CREATE TABLE callback_probe(id INT PRIMARY KEY);");
        var source=new DriverManagerDataSource("jdbc:h2:mem:v15_callback_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","v15_test",UUID.randomUUID().toString());
        var probe=new V15TransactionProbe("15",true);var nulls=new AtomicInteger();var callbacks=new AtomicInteger();
        Callback observer=new Callback() {
            @Override public boolean supports(Event event,Context c){if(c==null)nulls.incrementAndGet();return probe.supports(event,c);}
            @Override public boolean canHandleInTransaction(Event event,Context c){return true;}
            @Override public String getCallbackName(){return "v15-offline-lifecycle";}
            @Override public void handle(Event event,Context c){callbacks.incrementAndGet();probe.handle(event,c);}
        };
        var f=Flyway.configure().dataSource(source).locations("filesystem:"+directory.toAbsolutePath().toString().replace('\\','/')).cleanDisabled(true).callbacks(observer).load();
        assertEquals(1,f.migrate().migrationsExecuted);assertTrue(nulls.get()>0);assertEquals(2,callbacks.get());assertFalse(probe.fired);assertFalse(probe.sameTransaction);assertDoesNotThrow(f::validate);
    }
}
