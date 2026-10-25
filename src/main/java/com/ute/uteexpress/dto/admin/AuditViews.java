package com.ute.uteexpress.dto.admin;

import java.time.*;
import java.util.List;

public final class AuditViews {
    private AuditViews() { }
    public enum Source { ALL, ADMIN, SHIPMENT, TRANSPORT, TICKET }
    public record Request(ReportViews.Period period,LocalDate from,LocalDate to,Source source,Long actorId,String action,String entityType,String entityId,Long branchId) { }
    public record Filter(LocalDateTime from,LocalDateTime to,Source source,Long actorId,String action,String entityType,String entityId,Long branchId) { }
    public record Row(String source,Long id,LocalDateTime at,Long actorId,String action,String entityType,String entityId,Long branchId) {
        public String actorLabel(){return actorId==null?"Không xác định (legacy)":"User #"+actorId;}
        public String outcome(){return "ADMIN".equals(source)?"Bản ghi quản trị đã lưu":"Lịch sử nghiệp vụ đã ghi nhận";}
    }
    public record Page(Filter filter,List<Row> rows,int page,int size,long total) {
        public boolean hasPrevious(){return page>0;}
        public boolean hasNext(){return (long)(page+1)*size<total;}
    }
}
