package com.ute.uteexpress.demo;

import com.ute.uteexpress.UteexpressApplication;
import com.ute.uteexpress.operations.DemoMailConfiguration;
import java.sql.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;

/** Operator-only classpath launcher. It and the fake SMTP never ship inside the production JAR. */
public final class SqlDemoApplication {
    private SqlDemoApplication() { }
    public static void main(String[] args) {
        try { execute(args); }
        catch(Exception error) {
            // Never print provider messages, URLs, stack traces, bind values or credential objects.
            SQLException sql=null;
            for(Throwable cause=error;cause!=null;cause=cause.getCause())if(cause instanceof SQLException s){sql=s;break;}
            System.err.println("SQL DEMO STOPPED: "+error.getClass().getSimpleName()
                +(sql==null?"":" SQLState="+sql.getSQLState()+" code="+sql.getErrorCode()));
            System.exit(2);
        }
    }
    private static void execute(String[] args)throws Exception {
        if(args.length<1||args.length>2)throw new IllegalArgumentException("Mode and optional port only");
        String mode=args[0];var env=System.getenv();SqlDemoTarget.requireApproval(mode,env);
        int port=args.length==2?Integer.parseInt(args[1]):8081;
        if(port<1024||port>65535)throw new IllegalArgumentException("Invalid port");
        DataSource source=SqlDemoTarget.source(env);
        long objects;
        try(var c=source.getConnection();var s=c.createStatement()) {
            try(var r=s.executeQuery("SELECT COUNT(*) FROM sys.objects WHERE is_ms_shipped=0 AND type IN ('U','V','P','PC','FN','IF','TF','FS','FT','SO','SN')")){r.next();objects=r.getLong(1);}
            System.out.println("PASS identity: local SQLEXPRESS / UTEExpress_Demo / uteexpress_demo; user objects="+objects);
            if("migrate".equals(mode)) {
                // No resuming an unknown/partially initialized schema without explicit investigation.
                if(objects!=0)throw new IllegalStateException("Fresh demo migration requires an empty database; inspect and stop");
                try(var r=s.executeQuery("SELECT HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CREATE TABLE'), HAS_PERMS_BY_NAME('dbo','SCHEMA','ALTER')")){
                    if(!r.next()||r.getInt(1)!=1||r.getInt(2)!=1)throw new IllegalStateException("Migration permissions missing");
                }
            }
            if(objects>0) {
                try(var r=s.executeQuery("SELECT version, checksum, success FROM dbo.flyway_schema_history ORDER BY installed_rank")) {
                    while(r.next())System.out.println("History V"+r.getString(1)+" checksum="+r.getInt(2)+" success="+r.getBoolean(3));
                }
            }
        }
        if("inspect".equals(mode))return;
        var flyway=Flyway.configure().dataSource(source).locations("classpath:db/migration").defaultSchema("dbo")
            .target("13").cleanDisabled(true).baselineOnMigrate(false).outOfOrder(false).validateOnMigrate(true).load();
        if("migrate".equals(mode)){flyway.migrate();flyway.validate();System.out.println("PASS migration V1-V13");return;}
        flyway.validate();
        var applied=flyway.info().applied();
        if(applied.length!=13||java.util.Arrays.stream(applied).anyMatch(m->m.getState()!=org.flywaydb.core.api.MigrationState.SUCCESS)
                ||!"13".equals(flyway.info().current().getVersion().getVersion()))
            throw new IllegalStateException("Complete V1-V13 history required; never auto-migrate at startup");
        System.setProperty("spring.devtools.restart.enabled","false");
        var app=new SpringApplicationBuilder(UteexpressApplication.class,Configuration.class)
            .web("run".equals(mode)?WebApplicationType.SERVLET:WebApplicationType.NONE).run(
                "--spring.config.location=classpath:/demo-sql.properties","--spring.profiles.active=demo-sql",
                "--server.address=127.0.0.1","--server.port="+port,"--spring.flyway.enabled=false",
                "--spring.jpa.hibernate.ddl-auto=validate","--uteexpress.demo.test-mail.enabled=true",
                "--uteexpress.demo.enabled=true","--uteexpress.notifications.worker.enabled="+"run".equals(mode));
        if("seed".equals(mode)) {
            try{boolean created=app.getBean(SqlDemoSeed.class).seed(env.get("UTE_SQL_DEMO_SEED_PASSWORD"));
                System.out.println(created?"PASS demo reference data created atomically":"PASS seed no-op; existing data and password hashes preserved");}
            finally{app.close();}
        }else System.out.println("READY http://127.0.0.1:"+port+" — SQL Server persistent demo; no automatic seed or migrations");
    }
    @TestConfiguration(proxyBeanMethods=false)
    @Profile("demo-sql")
    @Import(DemoMailConfiguration.class)
    public static class Configuration {
        @Bean DataSource dataSource(){return SqlDemoTarget.source(System.getenv());}
        @Bean SqlDemoSeed sqlDemoSeed(){return new SqlDemoSeed();}
    }
}
