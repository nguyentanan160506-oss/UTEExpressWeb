package com.ute.uteexpress.finaldemo;

import com.ute.uteexpress.UteexpressApplication;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;

/** Operator tool on test classpath, excluded from the production JAR. No automatic migration/seed on Run. */
public final class FinalDemoApplication {
    private FinalDemoApplication() { }
    public static void main(String[] args) {
        String stage=args.length==0?"arguments":args[0];
        try{execute(args);}catch(Exception error){
            SQLException sql=null;for(Throwable t=error;t!=null;t=t.getCause())if(t instanceof SQLException s){sql=s;break;}
            System.err.println("FINAL DEMO STOPPED stage="+stage+" type="+error.getClass().getSimpleName()+(sql==null?"":" SQLState="+sql.getSQLState()+" code="+sql.getErrorCode()));System.exit(2);
        }
    }
    private static void execute(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Mode and port required");
        String mode=args[0];var env=System.getenv();FinalDemoTarget.requireApproval(mode,env);
        int port=Integer.parseInt(args[1]);if(port<1024||port>65535||port==8083||port==18084)throw new IllegalArgumentException("Dedicated free port required");
        var source=FinalDemoTarget.source(env);long objects;
        try(var c=source.getConnection();var s=c.createStatement()){
            try(var r=s.executeQuery("SELECT COUNT(*) FROM sys.objects WHERE is_ms_shipped=0 AND type IN ('U','V','P','PC','FN','IF','TF','FS','FT','SO','SN')")){r.next();objects=r.getLong(1);}
            System.out.println("PASS identity FinalDemo SQLEXPRESS tcp1433 dedicated-login isolated objects="+objects);
            boolean history;try(var r=s.executeQuery("SELECT CASE WHEN OBJECT_ID(N'dbo.flyway_schema_history',N'U') IS NULL THEN 0 ELSE 1 END")){r.next();history=r.getInt(1)==1;}
            if(history)try(var r=s.executeQuery("SELECT version,checksum,success FROM dbo.flyway_schema_history ORDER BY installed_rank")){while(r.next())System.out.println("History V"+r.getString(1)+" checksum="+r.getInt(2)+" success="+r.getBoolean(3));}
            if("inspect".equals(mode))return;
            if("migrate".equals(mode)&&objects!=0)throw new IllegalStateException("Fresh empty database required; stop for inspection, no resume/reset");
        }
        var flyway=Flyway.configure().dataSource(source).locations("classpath:db/migration").defaultSchema("dbo").target("15")
            .cleanDisabled(true).baselineOnMigrate(false).outOfOrder(false).validateOnMigrate(true).load();
        if("migrate".equals(mode)){flyway.migrate();flyway.validate();System.out.println("PASS migration V1-V15");return;}
        flyway.validate();var applied=flyway.info().applied();
        if(applied.length!=15||Arrays.stream(applied).anyMatch(m->m.getState()!=MigrationState.SUCCESS)
                ||!"15".equals(flyway.info().current().getVersion().getVersion()))throw new IllegalStateException("Complete V1-V15 required");
        System.out.println("PASS Flyway validate V1-V15");
        System.setProperty("spring.devtools.restart.enabled","false");
        var app=new SpringApplicationBuilder(UteexpressApplication.class,Configuration.class)
            .web("run".equals(mode)?WebApplicationType.SERVLET:WebApplicationType.NONE).run(
                "--spring.config.location=classpath:/final-demo.properties","--spring.profiles.active=final-demo",
                "--server.address=127.0.0.1","--server.port="+port,"--spring.flyway.enabled=false",
                "--spring.jpa.hibernate.ddl-auto=validate","--uteexpress.notifications.worker.enabled="+("run".equals(mode)&&!"YES".equals(env.get("UTE_FINAL_DEMO_WORKER_DISABLED"))));
        boolean running=false;
        try{
            System.out.println("PASS Hibernate validate");
            if("seed".equals(mode)){
                // A dedicated session lock serializes seed operators; business operations remain one Spring transaction.
                try(var c=source.getConnection();var s=c.createStatement();var r=s.executeQuery("DECLARE @r int; EXEC @r=sys.sp_getapplock @Resource=N'UTEExpress_FinalDemo_seed',@LockMode='Exclusive',@LockOwner='Session',@LockTimeout=0; SELECT @r")){
                    if(!r.next()||r.getInt(1)<0)throw new IllegalStateException("Another seed operator active");
                    boolean created=app.getBean(FinalDemoSeed.class).seed(env.get("UTE_FINAL_DEMO_WEB_PASSWORD"));
                    System.out.println(created?"PASS seed atomic 35-branches 7-hubs 112-routes 90-users 20-shipments":"PASS seed no-op existing passwords data and workflows preserved");
                }
            }
            if("validate".equals(mode)||"seed".equals(mode)){
                app.getBean(FinalDemoValidation.class).validate();
                try(var c=source.getConnection()){
                    var checked=FinalDemoConstraintCheck.validate(c);
                    System.out.println("PASS SQL DBCC resultSets="+checked.resultSets()+" updateCounts="+checked.updateCounts()+" violationRows="+checked.violationRows());
                }
                System.out.println("PASS SQL constraints and technical network validation");
            }
            if("run".equals(mode)){running=true;System.out.println("READY http://127.0.0.1:"+port+" FinalDemo persistent no automatic migration or seed");}
        }finally{if(!running)app.close();}
    }
    @TestConfiguration(proxyBeanMethods=false) @Profile("final-demo")
    public static class Configuration {
        @Bean DataSource dataSource(){return FinalDemoTarget.source(System.getenv());}
        @Bean FinalDemoSeed finalDemoSeed(){return new FinalDemoSeed();}
        @Bean FinalDemoValidation finalDemoValidation(){return new FinalDemoValidation();}
    }
}
