package com.ute.uteexpress.dto.admin;

import java.time.*;
import java.util.*;

public final class ReportViews {
    private ReportViews() { }
    public enum Period { TODAY, LAST_7_DAYS, LAST_30_DAYS, THIS_MONTH, CUSTOM }
    public enum Trend { DAY, WEEK, MONTH }
    public enum Dataset {
        SHIPMENTS("Vận đơn tạo trong kỳ"), DELIVERIES("Các lần giao trong kỳ"), DELIVERED("Vận đơn giao có proof"),
        RETURN_REQUESTS("Yêu cầu hoàn trong kỳ"), RETURN_STARTED("Khởi tạo quy trình hoàn trong kỳ"), RETURNED("Thực trả người gửi"), PENDING_RETURNS("Hoàn hàng chưa hoàn tất hiện tại"),
        TRIPS("Chuyến tạo trong kỳ"), TRIP_RECEIPTS("Lượt kiểm nhận kiện/chặng"),
        DISCREPANCIES("Sai lệch ghi nhận trong kỳ"), OPEN_DISCREPANCIES("Sai lệch chưa xử lý hiện tại"),
        TICKETS("Ticket tạo trong kỳ"), TICKET_EVENTS("Kết quả xử lý ticket trong kỳ"), OPEN_TICKETS("Ticket đang mở hiện tại"),
        BRANCHES("Hoạt động theo chi nhánh");
        private final String label;
        Dataset(String label){this.label=label;}
        public String getLabel(){return label;}
    }
    public enum Export { SUMMARY, DETAILS }
    public record Request(Dataset dataset,Period period,LocalDate from,LocalDate to,Long branchId,Long serviceId,String status,Trend trend) { }
    public record Filter(Dataset dataset,LocalDate fromDate,LocalDate toDate,LocalDateTime from,LocalDateTime to,
            Long branchId,Long serviceId,String status,Trend trend) { }
    public record Option(Long id,String code,String name) { }
    public record Metric(String label,String value,String unit,String basis) { }
    public record Bar(String label,long value,int percent) { }
    public record Breakdown(String title,List<Bar> rows) { }
    public record Table(List<String> headers,List<List<String>> rows,int page,int size,long total) {
        public boolean hasPrevious(){return page>0;}
        public boolean hasNext(){return (long)(page+1)*size<total;}
    }
    public record Dashboard(Filter filter,String basis,LocalDateTime generatedAt,List<Metric> metrics,
            List<Breakdown> breakdowns,List<Bar> trend,Table details,List<String> statuses) { }
    public record Download(byte[] bytes,String filename) { }
}
