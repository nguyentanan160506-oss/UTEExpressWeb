package com.ute.uteexpress.release;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.internal.parser.ParsingContext;
import org.flywaydb.core.internal.resource.StringResource;
import org.flywaydb.database.sqlserver.SQLServerParser;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** SQL Server parser + explicitly adapted H2 upgrade. Not SQL Server runtime proof. */
class NationwidePricingMigrationTests {
    static String source()throws Exception {
        try(var in=NationwidePricingMigrationTests.class.getResourceAsStream("/db/migration/V15__add_nationwide_shipping_pricing.sql")) {
            assertNotNull(in);return new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    @Test void parserSeparatesNewColumnsFromCheckAndEveryBatchIsTransactional()throws Exception {
        var parser=new SQLServerParser(Flyway.configure().placeholderReplacement(false),new ParsingContext());var batches=new ArrayList<String>();
        try(var iterator=parser.parse(new StringResource(source()))) {
            while(iterator.hasNext()){var batch=iterator.next();assertTrue(batch.canExecuteInTransaction());batches.add(batch.getSql());}
        }
        assertEquals(2,batches.size());assertTrue(batches.getFirst().contains("ADD rate_scope VARCHAR(30) NULL"));
        assertFalse(batches.getFirst().contains("CK_quotes_nationwide_price"));assertTrue(batches.getLast().contains("CK_quotes_nationwide_price"));
        String sql=source().replaceAll("(?m)--[^\\r\\n]*","").toUpperCase(Locale.ROOT);
        for(var forbidden:List.of("INSERT INTO","UPDATE DBO.","DELETE FROM","TRUNCATE","DROP TABLE","CREATE TABLE","CREATE DATABASE"))assertFalse(sql.contains(forbidden),forbidden);
        assertTrue(sql.contains("'INTER_PROVINCE', 'INTER_REGION'"));assertEquals(16,sql.split(" ADD \\w+ (?:VARCHAR|DECIMAL)").length-1);
    }
    @Test void populatedUpgradePreservesLegacyMoneyAddressesAndBranchTariffsAndChecksNewSnapshot()throws Exception {
        try(var c=DriverManager.getConnection("jdbc:h2:mem:nationwide_upgrade_"+UUID.randomUUID()+";MODE=MSSQLServer","migration_test",UUID.randomUUID().toString());var s=c.createStatement()) {
            s.execute("CREATE SCHEMA dbo");
            s.execute("CREATE TABLE dbo.shipping_rate_rules(id BIGINT PRIMARY KEY,scope_type VARCHAR(30) NOT NULL,CONSTRAINT CK_rate_rules_scope CHECK(scope_type IN ('INTRA_PROVINCE','INTER_PROVINCE','INTER_REGION')))");
            s.execute("INSERT INTO dbo.shipping_rate_rules VALUES(1,'INTER_PROVINCE'),(2,'INTER_REGION')");
            s.execute("CREATE TABLE dbo.shipment_quotes(id BIGINT PRIMARY KEY,shipping_fee DECIMAL(18,2) NOT NULL)");
            s.execute("CREATE TABLE dbo.shipments(id BIGINT PRIMARY KEY,shipping_fee DECIMAL(18,2) NOT NULL,sender_province NVARCHAR(100),receiver_province NVARCHAR(100))");
            s.execute("INSERT INTO dbo.shipments VALUES(1,12345.67,N'Quảng Nam',N'Bình Dương')");
            s.execute("INSERT INTO dbo.shipment_quotes VALUES(1,12345.67)");
            s.execute("CREATE TABLE dbo.branches(id BIGINT PRIMARY KEY)");s.execute("INSERT INTO dbo.branches VALUES(1),(2)");
            s.execute("CREATE TABLE dbo.pickup_fee_rules(id BIGINT PRIMARY KEY,branch_id BIGINT NOT NULL,service_id BIGINT NOT NULL,is_active BIT NOT NULL,effective_from TIMESTAMP NOT NULL,effective_to TIMESTAMP,pricing_version_id BIGINT,CONSTRAINT FK_pickup_fee_rules_branch FOREIGN KEY(branch_id) REFERENCES dbo.branches(id))");
            s.execute("INSERT INTO dbo.pickup_fee_rules VALUES(1,1,1,1,'2026-01-01',NULL,NULL),(2,2,1,1,'2026-01-01',NULL,NULL)");
            s.execute("CREATE INDEX IX_pickup_fee_rules_lookup ON dbo.pickup_fee_rules(branch_id,service_id,is_active,effective_from,effective_to)");
            s.execute("CREATE INDEX IX_pickup_rules_version ON dbo.pickup_fee_rules(pricing_version_id,branch_id)");
            // Pre-existing rows have distinct branch IDs; nullable uniqueness emulation is tested below.
            s.execute("CREATE INDEX UX_pickup_legacy_start ON dbo.pickup_fee_rules(branch_id,service_id,effective_from)");
            s.execute("CREATE INDEX UX_pickup_rules_version_branch ON dbo.pickup_fee_rules(pricing_version_id,branch_id)");
            String sql=source().replaceAll("(?m)--[^\\r\\n]*","").replaceAll("(?m)^GO\\s*$","")
                .replaceAll("DROP INDEX (\\w+) ON dbo\\.pickup_fee_rules", "DROP INDEX dbo.$1")
                // H2 has no filtered indexes: exclude the SQL Server filter, use ordinary indexes here.
                // UNIQUE semantics for the new nullable default are asserted separately below.
                .replace("CREATE UNIQUE INDEX UX_pickup_", "CREATE INDEX UX_pickup_")
                .replace(" WHERE pricing_version_id IS NULL", "").replace(" WHERE pricing_version_id IS NOT NULL", "");
            for(String statement:sql.split(";")){String q=statement.strip();if(!q.isEmpty()&&!q.startsWith("SET "))s.execute(q);}
            try(var r=s.executeQuery("SELECT shipping_fee,sender_province,receiver_province,rate_scope,base_shipping_fee FROM dbo.shipments WHERE id=1")){
                assertTrue(r.next());assertEquals("12345.67",r.getBigDecimal(1).toPlainString());assertEquals("Quảng Nam",r.getString(2));assertEquals("Bình Dương",r.getString(3));assertNull(r.getString(4));assertNull(r.getBigDecimal(5));
            }
            assertEquals(2,scalar(s,"SELECT COUNT(*) FROM dbo.shipping_rate_rules WHERE scope_type IN('INTER_PROVINCE','INTER_REGION')"));
            assertEquals(2,scalar(s,"SELECT COUNT(*) FROM dbo.pickup_fee_rules WHERE branch_id IS NOT NULL"));
            s.execute("INSERT INTO dbo.pickup_fee_rules VALUES(3,NULL,1,1,'2026-02-01',NULL,10)");
            assertThrows(SQLException.class,()->s.execute("INSERT INTO dbo.pickup_fee_rules VALUES(4,999,1,1,'2026-02-01',NULL,10)"));
            s.execute("UPDATE dbo.shipments SET rate_scope='CENTRAL_SOUTH',origin_province_code='48',destination_province_code='79',base_shipping_fee=12345.67,extra_weight_fee=0,priced_base_weight_kg=1,priced_step_weight_kg=1,priced_extra_price=5000 WHERE id=1");
            assertThrows(SQLException.class,()->s.execute("UPDATE dbo.shipments SET extra_weight_fee=1 WHERE id=1"));
            assertThrows(SQLException.class,()->s.execute("UPDATE dbo.shipment_quotes SET rate_scope='NORTH_SOUTH' WHERE id=1"));
            s.execute("CREATE UNIQUE NULLS NOT DISTINCT INDEX QA_default_unique ON dbo.pickup_fee_rules(pricing_version_id,branch_id)");
            assertThrows(SQLException.class,()->s.execute("INSERT INTO dbo.pickup_fee_rules VALUES(4,NULL,1,1,'2026-03-01',NULL,10)"));
        }
    }
    static int scalar(Statement s,String sql)throws SQLException{try(var r=s.executeQuery(sql)){r.next();return r.getInt(1);}}
}
