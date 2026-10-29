package com.ute.uteexpress.release.v15;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import static org.junit.jupiter.api.Assertions.*;

/** Prepares an isolated H2 schema only to rehearse the helper's service journey. Not SQL migration QA. */
public class V15BusinessHarnessTests {
    public static LocalDateTime utcNow(){return LocalDateTime.now(ZoneOffset.UTC);}
    @Test void actualServicesMethodSecurityTransactionsAndTwoThreadJourneyWorkWithValidateOnlyContext()throws Exception {
        try(var source=new com.zaxxer.hikari.HikariDataSource()) {
        source.setJdbcUrl("jdbc:h2:mem:v15_business_"+UUID.randomUUID().toString().replace("-","")+";MODE=MSSQLServer;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        source.setUsername("v15_test");source.setPassword(UUID.randomUUID().toString());source.setMaximumPoolSize(8);source.setMinimumIdle(1);source.setMaxLifetime(0);source.setIdleTimeout(0);
        try(var c=source.getConnection();var s=c.createStatement()) {
            assertEquals("H2",c.getMetaData().getDatabaseProductName());s.execute("CREATE SCHEMA dbo");
            s.execute("CREATE ALIAS SYSUTCDATETIME FOR 'com.ute.uteexpress.release.v15.V15BusinessHarnessTests.utcNow'");
        }
        var schema=new LocalContainerEntityManagerFactoryBean();schema.setDataSource(source);schema.setPackagesToScan("com.ute.uteexpress.entity");schema.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        schema.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create","hibernate.hbm2ddl.halt_on_error",true,"hibernate.show_sql",false));
        try{schema.afterPropertiesSet();assertNotNull(schema.getObject());}finally{schema.destroy();}
        try(var c=source.getConnection();var s=c.createStatement()){for(String role:List.of("ROLE_USER","ROLE_MANAGER","ROLE_ADMIN","ROLE_SHIPPER"))s.executeUpdate("INSERT INTO dbo.roles(name) VALUES('"+role+"')");}
        assertDoesNotThrow(()->V15BusinessChecks.run(source));
        try(var c=source.getConnection()){assertEquals(2,V15Sql.scalar(c,"SELECT COUNT(*) FROM dbo.pricing_versions"));assertEquals(5,V15Sql.scalar(c,"SELECT COUNT(*) FROM dbo.shipments"));}
        }
    }
    @Test void legacyFingerprintIgnoresNewColumnsButDetectsEveryOldRowChangeIncludingSecondRate()throws Exception {
        var source=new DriverManagerDataSource("jdbc:h2:mem:v15_legacy_"+UUID.randomUUID()+";MODE=MSSQLServer;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","v15_test",UUID.randomUUID().toString());
        var tables=List.of("branches","shipping_services","pricing_versions","shipping_rate_rules","pickup_fee_rules","shipments","shipment_quotes","shipment_payments");
        try(var c=source.getConnection();var s=c.createStatement()) {
            s.execute("CREATE SCHEMA dbo");
            for(String name:tables){s.execute("CREATE TABLE dbo."+V15Sql.identifier(name)+"(id BIGINT PRIMARY KEY, marker VARCHAR(40))");s.executeUpdate("INSERT INTO dbo."+V15Sql.identifier(name)+" VALUES(1,'synthetic-legacy')");}
            s.executeUpdate("INSERT INTO dbo.branches VALUES(2,'synthetic-hub')");s.executeUpdate("INSERT INTO dbo.shipping_rate_rules VALUES(2,'synthetic-second-rate')");
        }
        var rows=new V15LegacyFixtures.Rows(1,2,1,1,1,1,2,1,1,1,1);var snapshot=V15LegacyFixtures.snapshot(source,rows);
        try(var c=source.getConnection();var s=c.createStatement()){s.execute("ALTER TABLE dbo.shipments ADD new_snapshot_column DECIMAL(18,2) NULL");}
        assertDoesNotThrow(()->snapshot.verify(source));assertFalse(snapshot.toString().contains("synthetic-legacy"));
        try(var c=source.getConnection();var s=c.createStatement()){s.executeUpdate("UPDATE dbo.shipping_rate_rules SET marker='changed' WHERE id=2");}
        assertThrows(IllegalStateException.class,()->snapshot.verify(source));
    }
}
