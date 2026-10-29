package com.ute.uteexpress.release;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.internal.parser.ParsingContext;
import org.flywaydb.core.internal.resource.StringResource;
import org.flywaydb.database.sqlserver.SQLServerParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real SQL Server parser; constraint/upgrade semantics on isolated H2, NOT real SQL Server execution. */
class OriginIntakeMigrationTests {
    List<String> batches()throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V14__add_generated_origin_intake_receipts.sql"));
        assertFalse(sql.matches("(?is).*\\b(?:INSERT|UPDATE|DELETE|DROP|TRUNCATE|MERGE)\\b.*"));
        var parser=new SQLServerParser(Flyway.configure().placeholderReplacement(false),new ParsingContext());
        var result=new ArrayList<String>();
        try(var statements=parser.parse(new StringResource(sql))){while(statements.hasNext()){
            var statement=statements.next();assertTrue(statement.canExecuteInTransaction());result.add(statement.getSql());}}
        return result;
    }
    @Test void columnsAreInAnEarlierBatchThanCheckAndFilteredUniqueIndex()throws Exception {
        var batches=batches();assertEquals(2,batches.size());
        assertTrue(batches.getFirst().contains("ADD system_receipt_code VARCHAR(45) NULL"));
        assertTrue(batches.getFirst().contains("ADD request_version BIGINT NULL"));
        assertFalse(batches.getFirst().contains("CREATE UNIQUE INDEX"));
        assertTrue(batches.getLast().contains("WHERE system_receipt_code IS NOT NULL"));
    }
    @Test void upgradePreservesDuplicateLegacyReferencesAndRejectsDuplicateGeneratedCodes()throws Exception {
        String url="jdbc:h2:mem:receipt_upgrade_"+UUID.randomUUID().toString().replace("-","")+";MODE=MSSQLServer";
        try(var c=DriverManager.getConnection(url,"receipt_test",UUID.randomUUID().toString());var s=c.createStatement()) {
            s.execute("CREATE SCHEMA dbo");
            s.execute("CREATE TABLE dbo.shipment_intakes(id BIGINT PRIMARY KEY,shipment_id BIGINT UNIQUE NOT NULL,receipt_reference NVARCHAR(100) NOT NULL)");
            s.execute("INSERT INTO dbo.shipment_intakes VALUES(1,1,N'OLD-DUPLICATE'),(2,2,N'OLD-DUPLICATE')");
            for(String batch:batches()) {
                // H2 MSSQL mode requires explicit NULLS DISTINCT to model a filtered non-null unique index.
                String h2=batch.replace("WHERE system_receipt_code IS NOT NULL","").replace("CREATE UNIQUE INDEX","CREATE UNIQUE NULLS DISTINCT INDEX");
                for(String ddl:h2.split(";")) {
                    String executable=ddl.replaceAll("(?m)--[^\\r\\n]*","").strip();
                    if(!executable.isBlank()&&!executable.startsWith("SET "))s.execute(executable);
                }
            }
            try(var r=s.executeQuery("SELECT COUNT(*) FROM dbo.shipment_intakes WHERE receipt_reference='OLD-DUPLICATE' AND system_receipt_code IS NULL AND request_version IS NULL")){
                r.next();assertEquals(2,r.getInt(1));
            }
            String code="RCV-20261029-"+"A".repeat(32);
            try(var insert=c.prepareStatement("INSERT INTO dbo.shipment_intakes VALUES(?,?,?, ?,0)")) {
                insert.setLong(1,3);insert.setLong(2,3);insert.setString(3,code);insert.setString(4,code);insert.executeUpdate();
                insert.setLong(1,4);insert.setLong(2,4);assertEquals("23505",assertThrows(SQLException.class,insert::executeUpdate).getSQLState());
                insert.setString(4,"RCV-20261029-"+"B".repeat(32));assertEquals("23513",assertThrows(SQLException.class,insert::executeUpdate).getSQLState());
            }
            try(var r=s.executeQuery("SELECT COUNT(*) FROM dbo.shipment_intakes")){r.next();assertEquals(3,r.getInt(1));}
        }
    }
}
