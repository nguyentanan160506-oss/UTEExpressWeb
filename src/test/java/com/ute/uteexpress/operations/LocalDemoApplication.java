package com.ute.uteexpress.operations;

import com.ute.uteexpress.UteexpressApplication;
import com.zaxxer.hikari.HikariDataSource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;

/** Explicit, loopback-only, disposable H2 demo. Neither this entry point nor H2 ships in the release JAR. */
public final class LocalDemoApplication {
    private LocalDemoApplication() { }
    public static void main(String[] args) {
        if (args.length > 1 || (args.length == 1 && !args[0].matches("--port=[0-9]{1,5}"))) {
            throw new IllegalArgumentException("Only --port=<local port> is supported; database overrides are forbidden.");
        }
        int port = args.length == 0 ? 8081 : Integer.parseInt(args[0].substring(7));
        // Disable before Spring's first startup event; a property in demo-local is too late for CLI restart.
        System.setProperty("spring.devtools.restart.enabled", "false");
        start(System.getenv("UTE_DEMO_PASSWORD"), port, "YES".equals(System.getenv("UTE_DEMO_TEST_MAIL")));
    }
    public static ConfigurableApplicationContext start(String password, int port) {
        return start(password,port,false);
    }
    public static ConfigurableApplicationContext start(String password, int port, boolean testMail) {
        LocalDemoSeed.requirePassword(password);
        if (port < 0 || port > 65535) { throw new IllegalArgumentException("Invalid local port"); }
        var context = new SpringApplicationBuilder(UteexpressApplication.class, DemoConfiguration.class).run(
            "--spring.config.location=classpath:/demo-local.properties",
            "--spring.profiles.active=demo-local", "--uteexpress.demo.enabled=true", "--server.port=" + port,
            "--uteexpress.demo.test-mail.enabled="+testMail,
            "--server.address=127.0.0.1", "--debug=false", "--spring.flyway.enabled=false",
            "--spring.jpa.hibernate.ddl-auto=create-drop", "--spring.h2.console.enabled=false",
            "--spring.jpa.show-sql=false", "--logging.level.org.hibernate.SQL=OFF",
            "--logging.level.org.hibernate.orm.jdbc.bind=OFF");
        try {
            var manifest = context.getBean(LocalDemoSeed.class).seed(password);
            System.out.println("LOCAL H2 DEMO seeded " + manifest.size() + " scenarios; no SQL Server/Flyway validation implied.");
            manifest.forEach((scenario,id) -> System.out.println("DEMO " + scenario + " shipment=" + id));
            return context;
        } catch (RuntimeException error) { context.close(); throw error; }
    }
    public static LocalDateTime utcNow() { return LocalDateTime.now(ZoneOffset.UTC); }

    @TestConfiguration(proxyBeanMethods=false)
    @Profile("demo-local")
    @ConditionalOnProperty(name="uteexpress.demo.enabled",havingValue="true")
    @Import(DemoMailConfiguration.class)
    public static class DemoConfiguration {
        @Bean(destroyMethod="close") DataSource dataSource() throws Exception {
            var ds = new HikariDataSource();
            ds.setJdbcUrl("jdbc:h2:mem:uteexpress_demo_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MSSQLServer;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
            ds.setUsername("demo_local"); ds.setPassword(UUID.randomUUID().toString()); ds.setMaximumPoolSize(8);
            try (var c=ds.getConnection();var s=c.createStatement()) {
                s.execute("CREATE SCHEMA dbo");
                s.execute("CREATE ALIAS SYSUTCDATETIME FOR 'com.ute.uteexpress.operations.LocalDemoApplication.utcNow'");
            }
            return ds;
        }
        @Bean LocalDemoSeed localDemoSeed() { return new LocalDemoSeed(); }
    }
}
