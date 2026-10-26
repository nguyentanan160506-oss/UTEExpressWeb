package com.ute.uteexpress.operations;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import static com.ute.uteexpress.operations.OperationsJdbcFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(SystemSecurityWebSupport.Config.class)
@TestPropertySource(properties={SystemSecurityWebSupport.EXCLUDE,"spring.flyway.enabled=false","spring.sql.init.mode=never","debug=false"})
class SystemReportSecurityTests extends SystemSecurityWebSupport {
    static Stream<Arguments> invalidFilters() {return Stream.of(
        Arguments.of("/admin/reports","dataset","UNKNOWN"),Arguments.of("/admin/reports/export","kind","UNKNOWN"),
        Arguments.of("/admin/reports","period","CUSTOM"),Arguments.of("/admin/reports","from","bad-date"),
        Arguments.of("/admin/reports","branchId",Long.toString(Long.MAX_VALUE)),Arguments.of("/admin/reports","serviceId",Long.toString(Long.MAX_VALUE)),
        Arguments.of("/admin/reports","size","51"),Arguments.of("/admin/reports","page","-1"),
        Arguments.of("/admin/audit","source","UNKNOWN"),Arguments.of("/admin/audit","actorId","-1"),
        Arguments.of("/admin/audit","action","' OR 1=1 --"),Arguments.of("/admin/audit","entityType","UNKNOWN"),Arguments.of("/admin/audit","entityId","<script>"));}
    @ParameterizedTest @MethodSource("invalidFilters")
    void invalidFiltersReturnSafeErrorsWithoutBusinessWrites(String path,String field,String value) throws Exception {
        var session=login(f.admin);mvc.perform(get(path).session(session).param(field,value)).andExpect(status().isBadRequest()).andExpect(result->{String html=result.getResponse().getContentAsString();safe(html);assertFalse(html.contains("SQL statement"));assertFalse(html.contains("<script>"));});assertEquals(0,auditRows.count());assertEquals(0,notices.count());
    }
    @ParameterizedTest @ValueSource(strings={"=1+1","+1","-1","@SUM(A1)","\t=1","\u00a0=1","\u200b=1","\ufeff=1"})
    void databaseBackedCsvNeutralizesFormulaPrefixesAndPreservesVietnamese(String prefix) throws Exception {
        String value=prefix+",\"Việt Nam\"\r\nDòng 2";db(()->{em.find(Branch.class,f.source.getId()).setName(value);return null;});var session=login(f.admin);
        var response=mvc.perform(get("/admin/reports/export").session(session).param("dataset","BRANCHES").param("filename","../../untrusted.csv"))
            .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/csv")).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse();
        byte[] data=response.getContentAsByteArray();assertArrayEquals(new byte[]{(byte)0xef,(byte)0xbb,(byte)0xbf},Arrays.copyOf(data,3));
        var rows=AdminCsvTests.parse(data);var row=rows.stream().filter(r->r.size()>2 && r.get(1).equals("SOURCE")).findFirst().orElseThrow();assertEquals("'"+value,row.get(2));
        String disposition=response.getHeader("Content-Disposition");assertTrue(disposition.contains("uteexpress-branches-"));assertFalse(disposition.contains("untrusted"));assertFalse(disposition.contains("\r"));assertFalse(disposition.contains("\n"));safe(new String(data,StandardCharsets.UTF_8));assertEquals(0,auditRows.count());
    }
    void shipmentRows(int total) {
        // Read-model load fixture only, inside random H2; not a simulated successful logistics workflow.
        db(()->{var s=f.shipment(f.source,f.destination,ShipmentStatus.CREATED,NOW);
            @SuppressWarnings("unchecked") var columns=(List<String>)em.createNativeQuery("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='DBO' AND TABLE_NAME='SHIPMENTS' ORDER BY ORDINAL_POSITION").getResultList();
            var cols=columns.stream().filter(c->!c.equalsIgnoreCase("id")).toList();String selected=String.join(",",cols.stream().map(c->c.equalsIgnoreCase("tracking_number")?"CONCAT('UTE',LPAD(CAST(r.X AS VARCHAR),32,'0'))":"s."+c).toList());
            em.createNativeQuery("INSERT INTO dbo.shipments ("+String.join(",",cols)+") SELECT "+selected+" FROM dbo.shipments s CROSS JOIN SYSTEM_RANGE(1,"+(total-1)+") r WHERE s.id="+s.getId()).executeUpdate();return null;});
    }
    @ParameterizedTest @ValueSource(ints={5000,5001})
    void csvRowLimitIsEnforcedAtHttpBoundaryWithoutSuccessfulPartialFiles(int size) throws Exception {
        shipmentRows(size);var session=login(f.admin);
        var response=mvc.perform(get("/admin/reports/export").session(session).param("dataset","SHIPMENTS")).andReturn().getResponse();
        if(size==5000){assertEquals(200,response.getStatus());assertEquals(5001,AdminCsvTests.parse(response.getContentAsByteArray()).size());assertTrue(response.getContentAsByteArray().length<=4*1024*1024);}
        else {assertEquals(400,response.getStatus());assertNull(response.getHeader("Content-Disposition"));assertFalse(response.getContentType().startsWith("text/csv"));safe(response.getContentAsString());}
        assertEquals(size,count(Shipment.class));assertEquals(0,count(ShipmentEvent.class));assertEquals(0,auditRows.count());assertEquals(0,notices.count());
    }
    @Test void auditHtmlAndReadonlyExportsNeverLoadOrRenderLegacyFreeTextPayloads() throws Exception {
        db(()->{var row=new AuditLog();row.setActor(secondAdmin);row.setAction("USER_LOCKED");row.setResourceType(AuditResourceType.USER);row.setResourceId(f.other.getId().toString());row.setOldValue("LEGACY-INTERNAL-PAYLOAD-21");row.setNewValue("PRIVATE-DETAIL-21");row.setCreatedAt(NOW);em.persist(row);return null;});
        var session=login(f.admin);var stats=emf.unwrap(org.hibernate.engine.spi.SessionFactoryImplementor.class).getStatistics();stats.clear();
        for(String path:List.of("/admin/reports","/admin/audit","/admin/reports/export"))mvc.perform(get(path).session(session)).andExpect(status().isOk()).andExpect(result->{var body=result.getResponse().getContentAsString();safe(body);assertFalse(body.contains("LEGACY-INTERNAL-PAYLOAD-21"));assertFalse(body.contains("PRIVATE-DETAIL-21"));assertFalse(body.contains(secondAdmin.getEmail()));});
        assertEquals(0,stats.getEntityInsertCount());assertEquals(0,stats.getEntityUpdateCount());assertEquals(0,stats.getEntityDeleteCount());assertEquals(1,auditRows.count());assertEquals(0,notices.count());
    }
    @Test void reversedDatesDoNotReturnExportOrChangeData() throws Exception {
        var session=login(f.admin);mvc.perform(get("/admin/reports/export").session(session).param("period","CUSTOM").param("from",NOW.toLocalDate().toString()).param("to",NOW.minusDays(1).toLocalDate().toString()))
            .andExpect(status().isBadRequest()).andExpect(header().doesNotExist("Content-Disposition"));assertEquals(0,auditRows.count());
    }
}
