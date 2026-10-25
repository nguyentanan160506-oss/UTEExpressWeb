package com.ute.uteexpress.service.operations;

import com.ute.uteexpress.config.OperationsSettings;
import com.ute.uteexpress.dto.operations.OperationsDashboardViews.*;
import com.ute.uteexpress.dto.operations.OperationsLabels;
import com.ute.uteexpress.dto.operations.OperationsDashboardViews.Period;
import com.ute.uteexpress.dto.operations.OperationsDashboardViews.Queue;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.OperationsDashboardQueries;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true,timeout=15) @PreAuthorize("hasRole('MANAGER')")
public class OperationsDashboardReadService {
    private final OperationsAccessService access;private final OperationsDashboardQueries queries;private final Clock clock;private final OperationsSettings settings;
    public OperationsDashboardReadService(OperationsAccessService access,OperationsDashboardQueries queries,Clock clock,OperationsSettings settings){this.access=access;this.queries=queries;this.clock=clock;this.settings=settings;}
    public Range range(Period period,LocalDate from,LocalDate to){
        LocalDate today=LocalDate.ofInstant(clock.instant(),ZoneOffset.UTC);if(period==null)period=Period.LAST_7_DAYS;
        switch(period){case TODAY->{from=today;to=today;}case LAST_7_DAYS->{from=today.minusDays(6);to=today;}case LAST_30_DAYS->{from=today.minusDays(29);to=today;}case CUSTOM->{}}
        if(from==null || to==null || from.getYear()<1 || from.isAfter(to) || ChronoUnit.DAYS.between(from,to)>=93 || to.isAfter(today))throw new OperationsRuleException("Khoảng ngày UTC phải hợp lệ, tối đa 93 ngày và không vượt ngày hiện tại.");
        return new Range(from,to,from.atStartOfDay(),to.plusDays(1).atStartOfDay());
    }
    public Dashboard read(Long branchId,Period period,LocalDate from,LocalDate to,ShipmentStatus status,WorkGroup group,int page){
        if(page<0 || page>100000)throw new OperationsRuleException("Phân trang không hợp lệ.");Range range=range(period,from,to);var scope=access.scope(branchId);if(group==null)group=WorkGroup.CONFIRM;
        var counts=queries.groups("cohort",scope,range,status);long total=counts.values().stream().mapToLong(Long::longValue).sum();var attempts=queries.attempts(scope,range);
        var daily=queries.days(scope,range,status);var dayCounts=new LinkedHashMap<String,Long>();for(LocalDate d=range.fromDate();!d.isAfter(range.toDate());d=d.plusDays(1))dayCounts.put(d.toString(),daily.getOrDefault(d,0L));
        long delivered=queries.count("delivered",scope,range,null),returned=queries.count("returned",scope,range,null),originDelivered=queries.count("originDelivered",scope,range,null),requests=queries.count("returnRequests",scope,range,null);
        var now=LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);LocalDateTime cutoff=settings.shipmentWarningAge().isZero()?null:now.minus(settings.shipmentWarningAge());
        List<Queue> queues=Arrays.stream(WorkGroup.values()).map(g->new Queue(g,label(g),queries.workCount(scope,g,null),cutoff==null?null:queries.workCount(scope,g,cutoff))).toList();
        var durations=List.of(queries.duration("confirmationDuration","Tạo → xác nhận nguồn (giây)",scope,range),queries.duration("intakeDuration","Xác nhận → thực nhận nguồn (giây)",scope,range),
            queries.duration("transportDuration","Xuất chặng → kiểm nhận từng kiện/chặng (giây)",scope,range),queries.duration("destinationWait","Thực nhận đích → bắt đầu giao đầu tiên (giây)",scope,range),
            queries.duration("deliveryDuration","Bắt đầu → kết quả từng lần giao (giây)",scope,range),queries.duration("ticketDuration","Tạo ticket → giải quyết gần nhất (giây)",scope,range));
        var result=new Dashboard(scope.branch().getId(),scope.branch().getName(),range,status,total,queries.count("incoming",scope,range,null),queries.count("sourceIntakes",scope,range,null),queries.count("atDestination",scope,range,null),queries.count("outForDelivery",scope,range,null),queries.count("activeAssignments",scope,range,null),queries.count("held",scope,range,null),queries.count("unreceived",scope,range,null),
            delivered,attempts.failedShipments(),attempts.failed(),ratio(attempts.successful(),attempts.successful()+attempts.failed()),requests,returned,originDelivered,ratio(returned,returned+originDelivered),
            queries.count("awaitReceipt",scope,range,null),queries.count("discrepancies",scope,range,null),queries.count("ticketUnassigned",scope,range,null),queries.count("ticketMine",scope,range,null),queries.count("ticketWaiting",scope,range,null),queries.count("ticketWarning",scope,range,now),
            cutoff==null?null:settings.shipmentWarningAge().getSeconds(),bars(dayCounts),bars(counts),bars(new LinkedHashMap<>(Map.of("Lần giao thành công có bằng chứng",attempts.successful(),"Lần giao thất bại",attempts.failed()))),
            bars(queries.groups("reasons",scope,range,null)),bars(new LinkedHashMap<>(Map.of("Yêu cầu hoàn tại đích trong kỳ",requests,"Thực trả người gửi tại nguồn trong kỳ",returned))),bars(queries.groups("ticketStates",scope,range,null)),durations,queues,group,queries.work(scope,group,page),queries.trips(scope));
        access.scope(scope.branch().getId());return result;
    }
    public static BigDecimal ratio(long numerator,long denominator){return denominator==0?null:BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(denominator),2,RoundingMode.HALF_UP);}
    public static List<Bar> bars(Map<String,Long> values){long max=values.values().stream().mapToLong(Long::longValue).max().orElse(0);return values.entrySet().stream().map(e->new Bar(OperationsLabels.chartLabel(e.getKey()),e.getValue(),max==0?0:BigDecimal.valueOf(e.getValue()).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(max),0,RoundingMode.HALF_UP).intValue())).toList();}
    public static String label(WorkGroup group){return switch(group){case CONFIRM->"Chờ xác nhận nguồn";case PICKUP->"HOME_PICKUP chờ lấy hàng";case INTAKE->"Chờ thực nhận tại nguồn";case DISPATCH->"Đang giữ, chờ điều phối xuất";case DELIVERY->"Đích chờ phân công giao";case FAILED->"Giao thất bại chưa có quyết định";case RETURNS->"Hoàn hàng đang chờ xử lý";};}
}
