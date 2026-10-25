package com.ute.uteexpress.operations;

import com.ute.uteexpress.dto.admin.ReportViews.*;
import com.ute.uteexpress.dto.admin.ReportViews.Period;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.AdminRuleException;
import com.ute.uteexpress.service.reports.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import static com.ute.uteexpress.operations.OperationsJdbcFixture.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(AdminReportingJdbcConfig.class)
class AdminCsvTests extends OperationsJdbcSupport {
    @Autowired ReportCsvService csv;@Autowired AdminReportService reports;
    @BeforeEach void admin(){as(f.admin,"ROLE_ADMIN");}
    Request request(Dataset d){return new Request(d,Period.TODAY,null,null,null,null,null,Trend.DAY);}
    @ParameterizedTest @ValueSource(strings={"=1+1","+SUM(A1:A2)","-1+2","@SUM(A1)"," =1","\t=1","\r=1","\n=1","\u0000=1","\u00a0=1","\u200b=1","\ufeff=1"})
    void formulaAndLeadingWhitespaceControlsAreNeutralizedServerSide(String value){assertEquals("\"'"+value+"\"",ReportCsvService.cell(value));}
    @Test void quotesCommasAndLineBreaksAreRfc4180Escaped(){assertEquals("\"Việt, \"\"Nam\"\"\r\nDòng 2\"",ReportCsvService.cell("Việt, \"Nam\"\r\nDòng 2"));assertEquals("\"\"",ReportCsvService.cell(null));}
    @Test void utf8BomAndNoDataHeaderAreValid(){var file=csv.export(request(Dataset.SHIPMENTS),Export.DETAILS);assertEquals(0xef,file.bytes()[0]&255);assertEquals(0xbb,file.bytes()[1]&255);assertEquals(0xbf,file.bytes()[2]&255);String text=new String(file.bytes(),StandardCharsets.UTF_8);assertTrue(text.contains("Mã vận đơn"));assertTrue(text.endsWith("\r\n"));assertEquals(1,parse(file.bytes()).size());assertTrue(file.filename().matches("uteexpress-shipments-[0-9-]+-details\\.csv"));}
    @Test void detailExportExactlyMatchesHtmlTableProjectionAndFilters(){db(()->{f.shipment(f.source,f.destination,ShipmentStatus.CREATED,NOW);f.shipment(f.foreign,f.destination,ShipmentStatus.CREATED,NOW);return null;});var request=new Request(Dataset.SHIPMENTS,Period.TODAY,null,null,f.source.getId(),f.catalog.getId(),"CREATED",Trend.DAY);var view=reports.read(request,0,50);var rows=parse(csv.export(request,Export.DETAILS).bytes());assertEquals(view.details().headers(),rows.getFirst());assertEquals(view.details().rows(),rows.subList(1,rows.size()));}
    @Test void summaryExportReusesIdenticalKpisBreakdownsAndTrends(){db(()->f.shipment(f.source,f.destination,ShipmentStatus.CREATED,NOW));var request=request(Dataset.SHIPMENTS);var view=reports.read(request,0,20);var rows=parse(csv.export(request,Export.SUMMARY).bytes());for(var metric:view.metrics())assertTrue(rows.stream().anyMatch(r->r.get(1).equals(metric.label())&&r.get(2).equals(metric.value())));for(var group:view.breakdowns())for(var bar:group.rows())assertTrue(rows.stream().anyMatch(r->r.get(0).equals(group.title())&&r.get(1).equals(bar.label())&&r.get(2).equals(Long.toString(bar.value()))));}
    @Test void storedBranchNamesAreEscapedAndDoNotBecomeSpreadsheetFormulas(){db(()->{em.find(com.ute.uteexpress.entity.Branch.class,f.source.getId()).setName("\t=HYPERLINK(\"x\",\"y\")\nViệt Nam");return null;});var rows=parse(csv.export(request(Dataset.BRANCHES),Export.DETAILS).bytes());var row=rows.stream().filter(r->r.size()>2&&r.get(1).equals("SOURCE")).findFirst().orElseThrow();assertTrue(row.get(2).startsWith("'\t="));assertTrue(row.get(2).contains("\nViệt Nam"));}
    @Test void moreThanFiveThousandRowsFailsBeforeReturningPartialDownload(){
        // H2-only bulk fixture, not a SQL Server benchmark or production seed.
        db(()->{var sample=f.shipment(f.source,f.destination,ShipmentStatus.CREATED,NOW);@SuppressWarnings("unchecked") var columns=(List<String>)em.createNativeQuery("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='DBO' AND TABLE_NAME='SHIPMENTS' ORDER BY ORDINAL_POSITION").getResultList();var cols=columns.stream().filter(c->!c.equalsIgnoreCase("id")).toList();String names=String.join(",",cols);String select=String.join(",",cols.stream().map(c->c.equalsIgnoreCase("tracking_number")?"CONCAT('EXPORT',r.X)":"s."+c).toList());em.createNativeQuery("INSERT INTO dbo.shipments ("+names+") SELECT "+select+" FROM dbo.shipments s CROSS JOIN SYSTEM_RANGE(1,5000) r WHERE s.id="+sample.getId()).executeUpdate();return null;});
        assertThrows(AdminRuleException.class,()->csv.export(request(Dataset.SHIPMENTS),Export.DETAILS));assertNotNull(csv.export(request(Dataset.SHIPMENTS),Export.SUMMARY));
    }
    static List<List<String>> parse(byte[] bytes){String text=new String(bytes,StandardCharsets.UTF_8);if(text.startsWith("\ufeff"))text=text.substring(1);var rows=new ArrayList<List<String>>();var row=new ArrayList<String>();var cell=new StringBuilder();boolean quoted=false;for(int i=0;i<text.length();i++){char ch=text.charAt(i);if(ch=='"'){if(quoted&&i+1<text.length()&&text.charAt(i+1)=='"'){cell.append('"');i++;}else quoted=!quoted;}else if(ch==','&&!quoted){row.add(cell.toString());cell.setLength(0);}else if(ch=='\r'&&!quoted){assertEquals('\n',text.charAt(++i));row.add(cell.toString());rows.add(List.copyOf(row));row.clear();cell.setLength(0);}else cell.append(ch);}assertFalse(quoted);assertTrue(row.isEmpty());return rows;}
}
