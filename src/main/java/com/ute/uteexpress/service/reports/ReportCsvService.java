package com.ute.uteexpress.service.reports;

import com.ute.uteexpress.dto.admin.ReportViews.*;
import com.ute.uteexpress.repository.AdminReportQueries;
import com.ute.uteexpress.service.AdminRuleException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true,timeout=30) @PreAuthorize("hasRole('ADMIN')")
public class ReportCsvService {
    public static final int MAX_ROWS=5000,MAX_BYTES=4*1024*1024;
    private final AdminReportingAccess access;private final ReportPolicy policy;private final AdminReportQueries queries;private final AdminReportService reports;
    public ReportCsvService(AdminReportingAccess access,ReportPolicy policy,AdminReportQueries queries,AdminReportService reports){this.access=access;this.policy=policy;this.queries=queries;this.reports=reports;}
    public Download export(Request request,Export kind){
        var actor=access.requireAdmin();Filter f=policy.filter(request);var output=new ByteArrayOutputStream();output.writeBytes(new byte[]{(byte)0xef,(byte)0xbb,(byte)0xbf});
        if(kind==Export.SUMMARY){
            var dashboard=reports.read(request,0,1);line(output,List.of("Nhóm","Chỉ số","Giá trị","Đơn vị / cơ sở"));
            for(var metric:dashboard.metrics())line(output,List.of("KPI",metric.label(),metric.value(),metric.unit()+"; "+metric.basis()));
            for(var group:dashboard.breakdowns())for(var bar:group.rows())line(output,List.of(group.title(),bar.label(),Long.toString(bar.value()),dashboard.basis()));
            for(var bar:dashboard.trend())line(output,List.of("Xu hướng "+f.trend(),bar.label(),Long.toString(bar.value()),dashboard.basis()));
        }else{
            if(queries.count(f,actor.getId())>MAX_ROWS)throw limit();line(output,queries.headers(f.dataset()));
            // Cursor stays inside this short transaction, never while a client slowly downloads the HTTP response.
            try(var rows=queries.stream(f,actor.getId(),MAX_ROWS+1)){var iterator=rows.iterator();int count=0;while(iterator.hasNext()){if(++count>MAX_ROWS)throw limit();line(output,AdminReportQueries.cells(iterator.next()));}}
        }
        access.requireAdmin();return new Download(output.toByteArray(),"uteexpress-"+f.dataset().name().toLowerCase(Locale.ROOT)+"-"+f.fromDate()+"-"+f.toDate()+"-"+(kind==Export.SUMMARY?"summary":"details")+".csv");
    }
    public static String cell(String input){
        String value=input==null?"":input;
        if(!value.isEmpty()){int first=value.codePointAt(0);if("=+-@".indexOf(first)>=0 || Character.isWhitespace(first) || Character.isSpaceChar(first) || Character.isISOControl(first) || Character.getType(first)==Character.FORMAT)value="'"+value;}
        return "\""+value.replace("\"","\"\"")+"\"";
    }
    private static void line(ByteArrayOutputStream output,List<String> cells){byte[] bytes=(String.join(",",cells.stream().map(ReportCsvService::cell).toList())+"\r\n").getBytes(StandardCharsets.UTF_8);if((long)output.size()+bytes.length>MAX_BYTES)throw limit();output.writeBytes(bytes);}
    private static AdminRuleException limit(){return new AdminRuleException("File xuất tối đa 5.000 dòng và 4 MiB. Hãy thu hẹp khoảng ngày hoặc chi nhánh.");}
}
