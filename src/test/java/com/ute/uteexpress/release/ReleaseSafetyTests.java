package com.ute.uteexpress.release;

import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReleaseSafetyTests {
    Map<String,String> environment() {
        var env=new HashMap<String,String>();env.put("UTE_QA_SQLSERVER_INSTANCE","SQLEXPRESS");env.put("UTE_QA_SQLSERVER_DATABASE","UTEExpress_Test");env.put("UTE_QA_SQLSERVER_APPROVED_DATABASE","UTEExpress_Test");env.put("UTE_QA_SQLSERVER_URL","jdbc:sqlserver://127.0.0.1:1433;databaseName=UTEExpress_Test;encrypt=true");env.put("UTE_QA_SQLSERVER_USERNAME","uteexpress_qa");env.put("UTE_QA_SQLSERVER_PASSWORD",UUID.randomUUID().toString());return env;
    }
    @ParameterizedTest @ValueSource(strings={"UTE_QA_SQLSERVER_INSTANCE","UTE_QA_SQLSERVER_DATABASE","UTE_QA_SQLSERVER_APPROVED_DATABASE","UTE_QA_SQLSERVER_URL","UTE_QA_SQLSERVER_USERNAME","UTE_QA_SQLSERVER_PASSWORD"})
    void missingApprovalOrCredentialFailsBeforeAnyConnection(String key) {var env=environment();env.remove(key);assertThrows(IllegalArgumentException.class,()->SqlServerQaTool.settings("inspect",env));}
    @ParameterizedTest @ValueSource(strings={"sa","other_qa"})
    void anotherLoginCannotUseThisAuthorization(String login) {var env=environment();env.put("UTE_QA_SQLSERVER_USERNAME",login);assertThrows(IllegalArgumentException.class,()->SqlServerQaTool.settings("inspect",env));}
    @ParameterizedTest @ValueSource(strings={"master","model","msdb","tempdb","UTEExpressDB","db;DROP"})
    void systemExistingApplicationOrInvalidDatabaseIsRejected(String name) {var env=environment();env.put("UTE_QA_SQLSERVER_DATABASE",name);env.put("UTE_QA_SQLSERVER_APPROVED_DATABASE",name);assertThrows(IllegalArgumentException.class,()->SqlServerQaTool.settings("fresh",env));}
    @ParameterizedTest @ValueSource(strings={"clean","repair","upgrade",""})
    void unsupportedModeIsRejected(String mode) {assertThrows(IllegalArgumentException.class,()->SqlServerQaTool.settings(mode,environment()));}
    @Test void separateFreshWriteConsentAndEmptyDatabaseAreRequired() {var env=environment();assertThrows(IllegalArgumentException.class,()->SqlServerQaTool.settings("fresh",env));env.put("UTE_QA_SQLSERVER_ALLOW_MIGRATE","YES");assertEquals("fresh",SqlServerQaTool.settings("fresh",env).mode());assertThrows(IllegalStateException.class,()->SqlServerQaTool.requireEmpty(1));assertDoesNotThrow(()->SqlServerQaTool.requireEmpty(0));}
    @ParameterizedTest @ValueSource(strings={"v3","v4","v8","v9","v10","v11","v12","pending","smoke"})
    void pendingMigrationAlsoRequiresExplicitWriteConsent(String mode) {
        var env=environment();assertThrows(IllegalArgumentException.class,()->SqlServerQaTool.settings(mode,env));
        env.put("UTE_QA_SQLSERVER_ALLOW_MIGRATE","YES");assertEquals(mode,SqlServerQaTool.settings(mode,env).mode());
    }
    @Test void pendingStagesPreserveExactSuccessfulHistoryAndChecksums() {
        var v1=new SqlServerQaTool.HistoryRow("1",189739357,true);
        var v2=new SqlServerQaTool.HistoryRow("2",1191373203,true);
        var v3=new SqlServerQaTool.HistoryRow("3",-1720389847,true);
        var v4=new SqlServerQaTool.HistoryRow("4",-1466061241,true);
        assertDoesNotThrow(()->SqlServerQaTool.requirePendingHistory("v3",List.of(v1,v2)));
        assertDoesNotThrow(()->SqlServerQaTool.requirePendingHistory("v4",List.of(v1,v2,v3)));
        assertDoesNotThrow(()->SqlServerQaTool.requireHistoryThrough(4,List.of(v1,v2,v3,v4)));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("pending",List.of(v1,v2)));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("v3",List.of(v1,v2,v3)));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("v3",List.of(new SqlServerQaTool.HistoryRow("1",0,true),v2)));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("pending",List.of(v1,v2,new SqlServerQaTool.HistoryRow("3",-1720389847,false))));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("pending",List.of(v1,v2,new SqlServerQaTool.HistoryRow("3",-68259337,true))));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("pending",List.of(v1,v2,v3)));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("v4",List.of(v1,v2,v3,v4)));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("pending",List.of(v1,v2,v3,new SqlServerQaTool.HistoryRow("4",-2103371131,true))));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("pending",List.of(v1,v2,v3,new SqlServerQaTool.HistoryRow("4",-1466061241,false))));
    }
    @Test void nestedSqlFailureReportsCodesAndAllowlistedColumnWithoutRawDetails() {
        String secret=UUID.randomUUID().toString();
        var error=new IllegalStateException(secret,new SQLException("Invalid column name 'quote_id'. "+secret,"S0001",207));
        String diagnostic=SqlServerQaTool.sqlDiagnostics(error);
        assertTrue(diagnostic.contains("SQLState=S0001 errorCode=207 sourceColumn=quote_id"));
        assertFalse(diagnostic.contains(secret));assertTrue(diagnostic.contains("message=Invalid column name 'quote_id'."));
        var v8error=new IllegalStateException(secret,new SQLException("Invalid column name 'returned_at'. "+secret,"S0001",207));
        assertTrue(SqlServerQaTool.sqlDiagnostics(v8error).contains("sourceColumn=returned_at"));
        assertFalse(SqlServerQaTool.sqlDiagnostics(v8error).contains(secret));
        var next=new SQLException("Invalid column name 'request_fingerprint'. "+secret,"S0001",207);
        var first=new SQLException("Invalid column name 'request_token'. "+secret,"S0001",207);first.setNextException(next);
        String messages=SqlServerQaTool.sqlDiagnostics(first);
        assertTrue(messages.contains("message=Invalid column name 'request_token'."));assertTrue(messages.contains("message=Invalid column name 'request_fingerprint'."));assertFalse(messages.contains(secret));
        assertFalse(SqlServerQaTool.sqlDiagnostics(new SQLException(secret,"S0001",207)).contains(secret));
    }
    @Test void v8RequiresExactV1ToV7HistoryAndV9RequiresPatchedV8() {
        var rows=new ArrayList<SqlServerQaTool.HistoryRow>();
        int[] checksums={189739357,1191373203,-1720389847,-1466061241,1925825129,-1705174727,444247411};
        for(int i=0;i<checksums.length;i++)rows.add(new SqlServerQaTool.HistoryRow(Integer.toString(i+1),checksums[i],true));
        assertDoesNotThrow(()->SqlServerQaTool.requirePendingHistory("v8",rows));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("pending",rows));
        rows.add(new SqlServerQaTool.HistoryRow("8",-1022568645,true));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("pending",rows));
        rows.set(7,new SqlServerQaTool.HistoryRow("8",2036200466,false));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("pending",rows));
        rows.set(7,new SqlServerQaTool.HistoryRow("8",2036200466,true));
        assertDoesNotThrow(()->SqlServerQaTool.requirePendingHistory("v9",rows));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("v8",rows));
        rows.set(6,new SqlServerQaTool.HistoryRow("7",0,true));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("pending",rows));
    }
    @Test void v9RequiresExactV1ToV8HistoryAndV10RequiresPatchedV9() {
        var rows=new ArrayList<SqlServerQaTool.HistoryRow>();
        int[] checksums={189739357,1191373203,-1720389847,-1466061241,1925825129,-1705174727,444247411,2036200466};
        for(int i=0;i<checksums.length;i++)rows.add(new SqlServerQaTool.HistoryRow(Integer.toString(i+1),checksums[i],true));
        assertDoesNotThrow(()->SqlServerQaTool.requirePendingHistory("v9",rows));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("pending",rows));
        rows.add(new SqlServerQaTool.HistoryRow("9",1441625975,true));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("pending",rows));
        rows.set(8,new SqlServerQaTool.HistoryRow("9",61067824,false));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("pending",rows));
        rows.set(8,new SqlServerQaTool.HistoryRow("9",61067824,true));
        assertDoesNotThrow(()->SqlServerQaTool.requirePendingHistory("v10",rows));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("v9",rows));
        rows.set(6,new SqlServerQaTool.HistoryRow("7",0,true));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("pending",rows));
    }
    @Test void v10RequiresExactV1ToV9AndV11RequiresPatchedV10() {
        var rows=new ArrayList<SqlServerQaTool.HistoryRow>();
        int[] checksums={189739357,1191373203,-1720389847,-1466061241,1925825129,-1705174727,444247411,2036200466,61067824};
        for(int i=0;i<checksums.length;i++)rows.add(new SqlServerQaTool.HistoryRow(Integer.toString(i+1),checksums[i],true));
        assertDoesNotThrow(()->SqlServerQaTool.requirePendingHistory("v10",rows));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("pending",rows));
        rows.add(new SqlServerQaTool.HistoryRow("10",518542243,true));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("pending",rows));
        rows.set(9,new SqlServerQaTool.HistoryRow("10",236607534,false));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("pending",rows));
        rows.set(9,new SqlServerQaTool.HistoryRow("10",236607534,true));
        assertDoesNotThrow(()->SqlServerQaTool.requirePendingHistory("v11",rows));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("v10",rows));
        rows.add(new SqlServerQaTool.HistoryRow("11",275192352,true));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("pending",rows));
        rows.removeLast();rows.set(8,new SqlServerQaTool.HistoryRow("9",1441625975,true));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("pending",rows));
    }
    @Test void v11RequiresExactV1ToV10AndV12RequiresPatchedV11() {
        var rows=new ArrayList<SqlServerQaTool.HistoryRow>();
        int[] checksums={189739357,1191373203,-1720389847,-1466061241,1925825129,-1705174727,444247411,2036200466,61067824,236607534};
        for(int i=0;i<checksums.length;i++)rows.add(new SqlServerQaTool.HistoryRow(Integer.toString(i+1),checksums[i],true));
        assertDoesNotThrow(()->SqlServerQaTool.requirePendingHistory("v11",rows));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("pending",rows));
        rows.add(new SqlServerQaTool.HistoryRow("11",275192352,true));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("pending",rows));
        rows.set(10,new SqlServerQaTool.HistoryRow("11",2051557719,false));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("pending",rows));
        rows.set(10,new SqlServerQaTool.HistoryRow("11",2051557719,true));
        assertDoesNotThrow(()->SqlServerQaTool.requirePendingHistory("v12",rows));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("v11",rows));
        rows.add(new SqlServerQaTool.HistoryRow("12",-1357082083,true));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("pending",rows));
        rows.removeLast();rows.set(9,new SqlServerQaTool.HistoryRow("10",518542243,true));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("pending",rows));
    }
    @Test void v12RequiresExactV1ToV11AndPendingRequiresPatchedV12() {
        var rows=new ArrayList<SqlServerQaTool.HistoryRow>();
        int[] checksums={189739357,1191373203,-1720389847,-1466061241,1925825129,-1705174727,444247411,2036200466,61067824,236607534,2051557719};
        for(int i=0;i<checksums.length;i++)rows.add(new SqlServerQaTool.HistoryRow(Integer.toString(i+1),checksums[i],true));
        assertDoesNotThrow(()->SqlServerQaTool.requirePendingHistory("v12",rows));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("pending",rows));
        rows.add(new SqlServerQaTool.HistoryRow("12",-1357082083,true));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("pending",rows));
        rows.set(11,new SqlServerQaTool.HistoryRow("12",-1057022707,false));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("pending",rows));
        rows.set(11,new SqlServerQaTool.HistoryRow("12",-1057022707,true));
        assertDoesNotThrow(()->SqlServerQaTool.requirePendingHistory("pending",rows));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("v12",rows));
        rows.add(new SqlServerQaTool.HistoryRow("13",1007916434,true));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requirePendingHistory("pending",rows));
        assertDoesNotThrow(()->SqlServerQaTool.requireHistoryThrough(13,rows));
    }
    @Test void v12SeparatesAddedMessageColumnsFromCheckAndFilteredIndex() throws Exception {
        String sql;
        try(var input=getClass().getResourceAsStream("/db/migration/V12__add_customer_support_portal.sql")) {
            assertNotNull(input);sql=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
        assertEquals(1,sql.lines().filter(line->line.equals("GO")).count());
        assertEquals(-1057022707,org.flywaydb.core.internal.resolver.ChecksumCalculator.calculate(new org.flywaydb.core.internal.resource.StringResource(sql)));
        String original=sql.replaceAll("(?m)^GO\\r?\\n","");
        assertEquals(-1357082083,org.flywaydb.core.internal.resolver.ChecksumCalculator.calculate(new org.flywaydb.core.internal.resource.StringResource(original)),"Only one GO line may differ from released V12");
        var parser=new org.flywaydb.database.sqlserver.SQLServerParser(org.flywaydb.core.Flyway.configure().placeholderReplacement(false),new org.flywaydb.core.internal.parser.ParsingContext());
        var batches=new ArrayList<String>();
        try(var statements=parser.parse(new org.flywaydb.core.internal.resource.StringResource(sql))) {
            while(statements.hasNext()){var batch=statements.next();assertTrue(batch.canExecuteInTransaction());batches.add(batch.getSql());}
        }
        assertEquals(2,batches.size());
        assertTrue(batches.get(0).contains("SET QUOTED_IDENTIFIER ON"));assertTrue(batches.get(0).contains("CREATE TABLE dbo.support_ticket_routes"));assertTrue(batches.get(0).contains("ALTER TABLE dbo.support_ticket_messages ADD request_token"));assertFalse(batches.get(0).contains("CK_support_ticket_messages_request"));
        assertTrue(batches.get(1).contains("CK_support_ticket_messages_request"));assertTrue(batches.get(1).contains("UX_support_ticket_messages_request"));assertTrue(batches.get(1).contains("WHERE request_token IS NOT NULL"));assertTrue(batches.get(1).contains("CUSTOMER_MESSAGE"));
        try(var statements=parser.parse(new org.flywaydb.core.internal.resource.StringResource(original))){assertTrue(statements.hasNext());statements.next();assertFalse(statements.hasNext());}
    }
    @Test void v11AddedColumnsPrecedeAllChecksAndFilteredIndexesInRealFlywayBatches() throws Exception {
        String sql;
        try(var input=getClass().getResourceAsStream("/db/migration/V11__add_operations_tickets.sql")) {
            assertNotNull(input);sql=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
        assertEquals(2,sql.lines().filter(line->line.equals("GO")).count());
        assertEquals(2051557719,org.flywaydb.core.internal.resolver.ChecksumCalculator.calculate(new org.flywaydb.core.internal.resource.StringResource(sql)));
        String original=sql.replaceAll("(?m)^GO\\r?\\n","");
        assertEquals(275192352,org.flywaydb.core.internal.resolver.ChecksumCalculator.calculate(new org.flywaydb.core.internal.resource.StringResource(original)),"Only two GO boundaries may differ from released V11");
        var parser=new org.flywaydb.database.sqlserver.SQLServerParser(org.flywaydb.core.Flyway.configure().placeholderReplacement(false),new org.flywaydb.core.internal.parser.ParsingContext());
        var batches=new ArrayList<String>();
        try(var statements=parser.parse(new org.flywaydb.core.internal.resource.StringResource(sql))) {
            while(statements.hasNext()){var batch=statements.next();assertTrue(batch.canExecuteInTransaction());batches.add(batch.getSql());}
        }
        assertEquals(3,batches.size());
        assertTrue(batches.get(0).contains("DROP CONSTRAINT CK_support_tickets_status"));assertTrue(batches.get(0).contains("DF_support_tickets_version"));assertFalse(batches.get(0).contains("FK_support_tickets_branch"));assertFalse(batches.get(0).contains("CK_support_tickets_category"));
        assertTrue(batches.get(1).contains("CK_support_tickets_category"));assertTrue(batches.get(1).contains("UX_support_tickets_request"));assertTrue(batches.get(1).contains("CREATE TABLE dbo.support_ticket_events"));assertTrue(batches.get(1).contains("ALTER TABLE dbo.notifications ADD source_ticket_event_id"));assertFalse(batches.get(1).contains("FK_notifications_ticket_event"));
        assertTrue(batches.get(2).contains("FK_notifications_ticket_event"));assertTrue(batches.get(2).contains("CK_notifications_single_source"));assertTrue(batches.get(2).contains("UX_notifications_ticket_recipient"));assertTrue(batches.get(2).contains("CK_support_tickets_managed_state"));assertTrue(batches.get(2).contains("CK_notifications_ticket_context"));
        try(var statements=parser.parse(new org.flywaydb.core.internal.resource.StringResource(original))) {
            assertTrue(statements.hasNext());statements.next();assertFalse(statements.hasNext());
        }
    }
    @Test void v10NewColumnsPrecedeChecksAndFilteredIndexInRealFlywayBatches() throws Exception {
        String sql;
        try(var input=getClass().getResourceAsStream("/db/migration/V10__add_shipment_notifications.sql")) {
            assertNotNull(input);sql=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
        assertEquals(2,sql.lines().filter(line->line.equals("GO")).count());
        assertEquals(236607534,org.flywaydb.core.internal.resolver.ChecksumCalculator.calculate(new org.flywaydb.core.internal.resource.StringResource(sql)));
        String original=sql.replaceAll("(?m)^GO\\r?\\n","");
        assertEquals(518542243,org.flywaydb.core.internal.resolver.ChecksumCalculator.calculate(new org.flywaydb.core.internal.resource.StringResource(original)),"Only two GO lines may differ from released V10");
        var parser=new org.flywaydb.database.sqlserver.SQLServerParser(org.flywaydb.core.Flyway.configure().placeholderReplacement(false),new org.flywaydb.core.internal.parser.ParsingContext());
        var batches=new ArrayList<String>();
        try(var statements=parser.parse(new org.flywaydb.core.internal.resource.StringResource(sql))) {
            while(statements.hasNext()){var batch=statements.next();assertTrue(batch.canExecuteInTransaction());batches.add(batch.getSql());}
        }
        assertEquals(3,batches.size());
        assertTrue(batches.get(0).contains("notification_assignment_status VARCHAR(30) NULL"));assertFalse(batches.get(0).contains("CK_shipment_events_notification_assignment_status"));
        assertTrue(batches.get(1).contains("CK_shipment_events_notification_assignment_status"));assertTrue(batches.get(1).contains("IX_shipment_events_notification_work"));assertTrue(batches.get(1).contains("ALTER TABLE dbo.notifications ADD source_event_id"));assertTrue(batches.get(1).contains("DF_notifications_delivery_attempts"));assertFalse(batches.get(1).contains("FK_notifications_source_event"));
        assertTrue(batches.get(2).contains("FK_notifications_source_event"));assertTrue(batches.get(2).contains("CK_notifications_audience"));assertTrue(batches.get(2).contains("UX_notifications_source_recipient_audience"));assertTrue(batches.get(2).contains("WHERE source_event_id IS NOT NULL"));
        try(var statements=parser.parse(new org.flywaydb.core.internal.resource.StringResource(original))) {
            assertTrue(statements.hasNext());statements.next();assertFalse(statements.hasNext());
        }
    }
    @Test void v9ThreeAddedColumnGroupsPrecedeTheirConstraintAndIndexBatches() throws Exception {
        String sql;
        try(var input=getClass().getResourceAsStream("/db/migration/V9__add_service_catalog_and_pricing_management.sql")) {
            assertNotNull(input);sql=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
        assertEquals(3,sql.lines().filter(line->line.equals("GO")).count());
        assertEquals(61067824,org.flywaydb.core.internal.resolver.ChecksumCalculator.calculate(new org.flywaydb.core.internal.resource.StringResource(sql)));
        String original=sql.replaceAll("(?m)^GO\\r?\\n","");
        assertEquals(1441625975,org.flywaydb.core.internal.resolver.ChecksumCalculator.calculate(new org.flywaydb.core.internal.resource.StringResource(original)),"Only three GO lines may differ from released V9");
        var parser=new org.flywaydb.database.sqlserver.SQLServerParser(org.flywaydb.core.Flyway.configure().placeholderReplacement(false),new org.flywaydb.core.internal.parser.ParsingContext());
        var batches=new ArrayList<String>();
        try(var statements=parser.parse(new org.flywaydb.core.internal.resource.StringResource(sql))) {
            while(statements.hasNext()){var batch=statements.next();assertTrue(batch.canExecuteInTransaction());batches.add(batch.getSql());}
        }
        assertEquals(4,batches.size());
        assertTrue(batches.get(0).contains("ADD max_height_cm"));assertFalse(batches.get(0).contains("CK_services_limits"));
        assertTrue(batches.get(1).contains("CK_services_limits"));assertTrue(batches.get(1).contains("ALTER TABLE dbo.shipping_rate_rules ADD retired_from"));assertFalse(batches.get(1).contains("FK_rate_rules_pricing_version"));
        assertTrue(batches.get(2).contains("CK_rate_rules_retirement"));assertTrue(batches.get(2).contains("ALTER TABLE dbo.pickup_fee_rules ADD retired_from"));assertFalse(batches.get(2).contains("FK_pickup_rules_pricing_version"));
        assertTrue(batches.get(3).contains("CK_pickup_rules_retirement"));assertTrue(batches.get(3).contains("UX_pickup_legacy_start"));assertTrue(batches.get(3).contains("CK_audit_logs_resource"));
        try(var statements=parser.parse(new org.flywaydb.core.internal.resource.StringResource(original))) {
            assertTrue(statements.hasNext());statements.next();assertFalse(statements.hasNext());
        }
    }
    @Test void v8ThreeNewColumnGroupsPrecedeTheirConsumersInSeparateFlywayBatches() throws Exception {
        String sql;
        try(var input=getClass().getResourceAsStream("/db/migration/V8__add_last_mile_delivery_and_returns.sql")) {
            assertNotNull(input);sql=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
        assertEquals(3,sql.lines().filter(line->line.equals("GO")).count());
        assertEquals(2036200466,org.flywaydb.core.internal.resolver.ChecksumCalculator.calculate(new org.flywaydb.core.internal.resource.StringResource(sql)));
        String original=sql.replaceAll("(?m)^GO\\r?\\n","");
        assertEquals(-1022568645,org.flywaydb.core.internal.resolver.ChecksumCalculator.calculate(new org.flywaydb.core.internal.resource.StringResource(original)),"Only three GO lines may differ from released V8");
        var parser=new org.flywaydb.database.sqlserver.SQLServerParser(org.flywaydb.core.Flyway.configure().placeholderReplacement(false),new org.flywaydb.core.internal.parser.ParsingContext());
        var batches=new ArrayList<String>();
        try(var statements=parser.parse(new org.flywaydb.core.internal.resource.StringResource(sql))) {
            while(statements.hasNext()){var batch=statements.next();assertTrue(batch.canExecuteInTransaction());batches.add(batch.getSql());}
        }
        assertEquals(4,batches.size());
        assertTrue(batches.get(0).contains("ADD returned_at"));assertFalse(batches.get(0).contains("CK_shipments_delivered_time"));
        assertTrue(batches.get(1).contains("CK_shipments_returned_time"));assertTrue(batches.get(1).contains("ADD started_at"));assertFalse(batches.get(1).contains("FK_delivery_attempts_assignment"));
        assertTrue(batches.get(2).contains("CK_delivery_attempts_context"));assertTrue(batches.get(2).contains("ADD return_request_id"));assertFalse(batches.get(2).contains("FK_trip_items_return_request"));
        assertTrue(batches.get(3).contains("FK_trip_items_return_request"));assertTrue(batches.get(3).contains("IX_trip_items_return_request"));
        try(var statements=parser.parse(new org.flywaydb.core.internal.resource.StringResource(original))) {
            assertTrue(statements.hasNext());statements.next();assertFalse(statements.hasNext());
        }
    }
    @Test void v3SeparatesNewColumnGroupsFromTheirConsumersUsingRealFlywayParser() throws Exception {
        String sql;
        try(var input=getClass().getResourceAsStream("/db/migration/V3__extend_shipper_identity_and_shipment_lifecycle.sql")) {
            assertNotNull(input);sql=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
        assertEquals(3,sql.lines().filter(line->line.equals("GO")).count());
        assertEquals(-1720389847,org.flywaydb.core.internal.resolver.ChecksumCalculator.calculate(new org.flywaydb.core.internal.resource.StringResource(sql)));
        String original=sql.replaceAll("(?m)^GO\\r?\\n","");
        assertEquals(-68259337,org.flywaydb.core.internal.resolver.ChecksumCalculator.calculate(new org.flywaydb.core.internal.resource.StringResource(original)),"Only three GO lines may differ from the released V3");
        var parser=new org.flywaydb.database.sqlserver.SQLServerParser(org.flywaydb.core.Flyway.configure().placeholderReplacement(false),new org.flywaydb.core.internal.parser.ParsingContext());
        var batches=new ArrayList<String>();
        try(var statements=parser.parse(new org.flywaydb.core.internal.resource.StringResource(sql))) {
            while(statements.hasNext()) {var statement=statements.next();assertTrue(statement.canExecuteInTransaction());batches.add(statement.getSql());}
        }
        assertEquals(4,batches.size());
        assertTrue(batches.get(0).contains("ALTER TABLE dbo.employees ADD user_id"));assertFalse(batches.get(0).contains("FK_employees_user"));
        assertTrue(batches.get(1).contains("UX_employees_user_nonnull"));assertTrue(batches.get(1).contains("ALTER TABLE dbo.branches ADD parent_hub_id"));assertFalse(batches.get(1).contains("FK_branches_parent_hub"));
        assertTrue(batches.get(2).contains("FK_branches_parent_hub"));assertTrue(batches.get(2).contains("ALTER TABLE dbo.shipments ADD version"));assertFalse(batches.get(2).contains("FK_shipments_origin_hub"));
        assertTrue(batches.get(3).contains("FK_shipments_origin_hub"));assertTrue(batches.get(3).contains("CK_shipments_version"));
        try(var statements=parser.parse(new org.flywaydb.core.internal.resource.StringResource(original))) {
            assertTrue(statements.hasNext());statements.next();assertFalse(statements.hasNext(),"Original V3 is one batch; semicolons do not create the required boundary");
        }
    }
    @Test void approvalMustMatchAndSettingsNeverPrintCredentials() {var env=environment();var config=SqlServerQaTool.settings("inspect",env);assertFalse(config.toString().contains(env.get("UTE_QA_SQLSERVER_PASSWORD")));assertFalse(config.toString().contains(env.get("UTE_QA_SQLSERVER_URL")));env.put("UTE_QA_SQLSERVER_APPROVED_DATABASE","OTHER");assertThrows(IllegalArgumentException.class,()->SqlServerQaTool.settings("inspect",env));}
    @Test void v4NewShipmentColumnsAreAddedBeforeConstraintAndFilteredIndexBatch() throws Exception {
        String sql;
        try(var input=getClass().getResourceAsStream("/db/migration/V4__add_pickup_pricing_and_shipment_quotes.sql")) {
            assertNotNull(input);sql=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
        assertEquals(1,sql.lines().filter(line->line.equals("GO")).count());
        assertEquals(-1466061241,org.flywaydb.core.internal.resolver.ChecksumCalculator.calculate(new org.flywaydb.core.internal.resource.StringResource(sql)));
        String original=sql.replaceAll("(?m)^GO\\r?\\n","");
        assertEquals(-2103371131,org.flywaydb.core.internal.resolver.ChecksumCalculator.calculate(new org.flywaydb.core.internal.resource.StringResource(original)),"Only the GO boundary may change released V4 SQL");
        var parser=new org.flywaydb.database.sqlserver.SQLServerParser(org.flywaydb.core.Flyway.configure().placeholderReplacement(false),new org.flywaydb.core.internal.parser.ParsingContext());
        var batches=new ArrayList<String>();
        try(var statements=parser.parse(new org.flywaydb.core.internal.resource.StringResource(sql))) {
            while(statements.hasNext()){var batch=statements.next();assertTrue(batch.canExecuteInTransaction());batches.add(batch.getSql());}
        }
        assertEquals(2,batches.size());
        assertTrue(batches.get(0).contains("ALTER TABLE dbo.shipments ADD pickup_fee"));
        assertTrue(batches.get(0).contains("ALTER TABLE dbo.shipments ADD quote_id"));
        assertFalse(batches.get(0).contains("FK_shipments_quote"));assertFalse(batches.get(0).contains("UX_shipments_quote_nonnull"));
        assertTrue(batches.get(1).contains("FK_shipments_quote"));assertTrue(batches.get(1).contains("UX_shipments_quote_nonnull"));
        assertTrue(batches.get(1).contains("CK_shipments_pickup_fee"));assertTrue(batches.get(1).contains("CK_shipments_quote_snapshot"));
        try(var statements=parser.parse(new org.flywaydb.core.internal.resource.StringResource(original))) {
            assertTrue(statements.hasNext());statements.next();assertFalse(statements.hasNext());
        }
    }
    @ParameterizedTest @ValueSource(strings={";password=synthetic",";USER=synthetic",";accessToken=synthetic","; userName =synthetic",";trustStorePassword=synthetic"})
    void inlineCredentialsAreRejected(String suffix) {var env=environment();env.compute("UTE_QA_SQLSERVER_URL",(k,v)->v+suffix);assertThrows(IllegalArgumentException.class,()->SqlServerQaTool.settings("inspect",env));}
    @ParameterizedTest @ValueSource(strings={
        "jdbc:sqlserver://127.0.0.1:1433;databaseName=UTEExpressDB",
        "jdbc:sqlserver://127.0.0.1:1433;encrypt=true",
        "jdbc:sqlserver://127.0.0.1:1433;databaseName=UTEExpress_Test;database=UTEExpressDB",
        "jdbc:sqlserver://remote-host:1433;databaseName=UTEExpress_Test",
        "jdbc:sqlserver://127.0.0.1:1433;databaseName=UTEExpress_Test;failoverPartner=remote-host"})
    void credentialCannotBeSentToAnotherOrImplicitDatabase(String url) {var env=environment();env.put("UTE_QA_SQLSERVER_URL",url);assertThrows(IllegalArgumentException.class,()->SqlServerQaTool.settings("inspect",env));}
    @Test void actualDatabaseMustMatchApprovalBeforeAnyWrite() throws Exception {
        var c=mock(Connection.class);var metadata=mock(DatabaseMetaData.class);when(c.getMetaData()).thenReturn(metadata);when(metadata.getDatabaseProductName()).thenReturn("Microsoft SQL Server");when(c.getCatalog()).thenReturn("UTEExpressDB");assertThrows(IllegalStateException.class,()->SqlServerQaTool.checkTarget(c,"UTEExpress_Test"));verify(c,never()).createStatement();
        when(c.getCatalog()).thenReturn("UTEExpress_Test");var statement=mock(Statement.class);var row=mock(ResultSet.class);when(c.createStatement()).thenReturn(statement);when(statement.executeQuery("SELECT DB_NAME()")).thenReturn(row);when(row.next()).thenReturn(true);when(row.getString(1)).thenReturn("UTEExpress_Test");assertDoesNotThrow(()->SqlServerQaTool.checkTarget(c,"UTEExpress_Test"));
        when(row.getString(1)).thenReturn("UTEExpressDB");assertThrows(IllegalStateException.class,()->SqlServerQaTool.checkTarget(c,"UTEExpress_Test"));
    }
    @Test void configuredPortCannotSubstituteForActualInstanceIdentity() {
        assertDoesNotThrow(()->SqlServerQaTool.requireIdentity("SQLEXPRESS","QA-HOST","SQLEXPRESS","QA-HOST"));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requireIdentity("SQLEXPRESS","QA-HOST","MSSQLSERVER","QA-HOST"));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requireIdentity("SQLEXPRESS","QA-HOST","SQLEXPRESS","OTHER-HOST"));
    }
    @Test void restrictedMetadataOrExistingObjectsCannotBeMistakenForEmptyDatabase() {
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requireCompleteMetadata(false));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requireFreshObjects(1,true,true));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requireFreshObjects(0,false,true));
        assertThrows(IllegalStateException.class,()->SqlServerQaTool.requireFreshObjects(0,true,false));
        assertDoesNotThrow(()->SqlServerQaTool.requireFreshObjects(0,true,true));
    }
    @Test void releaseProfileHasNoDatabaseDefaultsAndKeepsSecurityAndSchemaChecks() throws Exception {
        var properties=new Properties();try(var input=getClass().getResourceAsStream("/application-release.properties")){assertNotNull(input);properties.load(input);}
        assertEquals("${DB_URL}",properties.getProperty("spring.datasource.url"));assertEquals("${DB_USERNAME}",properties.getProperty("spring.datasource.username"));assertEquals("${DB_PASSWORD}",properties.getProperty("spring.datasource.password"));
        assertEquals("validate",properties.getProperty("spring.jpa.hibernate.ddl-auto"));assertEquals("true",properties.getProperty("spring.flyway.enabled"));assertEquals("true",properties.getProperty("spring.flyway.clean-disabled"));assertEquals("false",properties.getProperty("spring.jpa.show-sql"));assertEquals("false",properties.getProperty("spring.h2.console.enabled"));assertEquals("never",properties.getProperty("server.error.include-stacktrace"));assertEquals("${SESSION_COOKIE_SECURE:true}",properties.getProperty("server.servlet.session.cookie.secure"));assertNull(properties.getProperty("spring.profiles.active"));
    }
}
