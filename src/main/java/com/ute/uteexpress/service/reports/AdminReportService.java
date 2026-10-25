package com.ute.uteexpress.service.reports;

import com.ute.uteexpress.dto.admin.ReportViews.*;
import com.ute.uteexpress.dto.operations.OperationsLabels;
import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.service.operations.OperationsDashboardReadService;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true,timeout=30) @PreAuthorize("hasRole('ADMIN')")
public class AdminReportService {
    private final AdminReportingAccess access;private final ReportPolicy policy;private final AdminReportQueries queries;private final Clock clock;
    public AdminReportService(AdminReportingAccess access,ReportPolicy policy,AdminReportQueries queries,Clock clock){this.access=access;this.policy=policy;this.queries=queries;this.clock=clock;}
    public Dashboard read(Request request,int page,int size){
        var actor=access.requireAdmin();ReportPolicy.page(page,size);Filter f=policy.filter(request);
        long total=queries.count(f,actor.getId());var d=AdminReportQueries.definition(f.dataset());
        var breakdowns=d.groups().stream().map(group->{
            var rows=new ArrayList<>(queries.groups(f,actor.getId(),group));
            long shown=rows.stream().mapToLong(row->((Number)row[1]).longValue()).sum();
            if(rows.size()==100 && shown<total)rows.add(new Object[]{"Nhóm khác",total-shown});
            return new Breakdown(group.label(),bars(rows));
        }).toList();
        var metrics=new ArrayList<Metric>();metrics.add(new Metric("Tổng "+f.dataset().getLabel().toLowerCase(Locale.ROOT),Long.toString(total),unit(f.dataset()),d.basis()));
        if(d.shipment()!=null)metrics.add(metric("Vận đơn duy nhất trong tập đang lọc",queries.distinctShipments(f,actor.getId()),"vận đơn",d.basis()));
        if(f.dataset()==Dataset.DELIVERIES){
            Filter all=new Filter(f.dataset(),f.fromDate(),f.toDate(),f.from(),f.to(),f.branchId(),f.serviceId(),null,f.trend());
            var def=AdminReportQueries.definition(Dataset.DELIVERIES);
            var result=queries.query("select cast(a.status as String),count(a),count(distinct s.id) from "+def.root()+AdminReportQueries.where(def)+" group by a.status",Object[].class,all,actor.getId()).getResultList();
            long success=0,failed=0,failedShipments=0;for(Object[] r:result){if("SUCCESS".equals(r[0]))success=((Number)r[1]).longValue();else{failed=((Number)r[1]).longValue();failedShipments=((Number)r[2]).longValue();}}
            metrics.add(metric("Lần giao SUCCESS hợp lệ",success,"lượt","attemptTime trong kỳ; không lọc kết quả"));metrics.add(metric("Lần giao FAILED hợp lệ",failed,"lượt","attemptTime trong kỳ; không lọc kết quả"));metrics.add(metric("Vận đơn có FAILED",failedShipments,"vận đơn","COUNT DISTINCT shipment, không phải số lượt"));
            var ratio=OperationsDashboardReadService.ratio(success,success+failed);metrics.add(new Metric("Tỷ lệ giao thành công theo lần",ratio==null?"N/A":ratio.toPlainString(),"%","SUCCESS / (SUCCESS + FAILED) hợp lệ; không lọc kết quả"));
        }
        if(f.dataset()==Dataset.TRIPS){
            String base=" from "+d.root()+AdminReportQueries.where(d);
            metrics.add(metric("Chuyến có sai lệch",queries.query("select count(*)"+base+" and exists(select x.id from TripDiscrepancy x where x.trip.id=t.id)",Long.class,f,actor.getId()).getSingleResult(),"chuyến","Cohort chuyến; gồm sai lệch đã và chưa xử lý"));
            metrics.add(metric("Chuyến CLOSED đã kiểm nhận đủ manifest",queries.query("select count(*)"+base+" and t.status=TR.CLOSED and not exists(select d.id from TripDiscrepancy d where d.trip.id=t.id and d.status=TD.OPEN) and t.expectedCount>0 and t.expectedCount=(select count(i) from TransportTripItem i where i.trip.id=t.id) "
                +"and not exists(select i.id from TransportTripItem i where i.trip.id=t.id and not exists(select r.id from TripReceipt r where r.item.id=i.id and r.branch.id=t.destination.id and t.departedAt is not null and r.receivedAt>=t.departedAt))",Long.class,f,actor.getId()).getSingleResult(),"chuyến","CLOSED + mọi kiện có receipt hợp lệ + không sai lệch OPEN; không suy từ status đơn thuần"));
            metrics.add(metric("Lượt vận chuyển kiện/chặng",queries.query("select count(i) from TransportTripItem i where (:service is null or i.shipment.service.id=:service) and exists(select t.id"+base+" and t.id=i.trip.id)",Long.class,f,actor.getId()).getSingleResult(),"lượt","Các manifest của cohort chuyến, chỉ kiện khớp dịch vụ nếu có lọc"));
            metrics.add(metric("Vận đơn duy nhất qua các chặng",queries.query("select count(distinct i.shipment.id) from TransportTripItem i where (:service is null or i.shipment.service.id=:service) and exists(select t.id"+base+" and t.id=i.trip.id)",Long.class,f,actor.getId()).getSingleResult(),"vận đơn","Chỉ kiện khớp dịch vụ nếu có lọc; không cộng lượt thành số vận đơn"));
        }
        if(f.dataset()==Dataset.TICKET_EVENTS){
            String predicate=" from SupportTicket t left join t.shipment s left join t.handlingBranch b where t.status in (TS.RESOLVED,TS.CLOSED) and t.resolvedAt>=t.createdAt and t.resolvedAt>=:from and t.resolvedAt<:to and (:branch is null or b.id=:branch) and (:service is null or s.service.id=:service) and "+AdminReportQueries.GATE;
            Object[] duration=queries.query("select avg(timestampdiff(second,t.createdAt,t.resolvedAt)),count(t)"+predicate,Object[].class,f,actor.getId()).getSingleResult();
            metrics.add(new Metric("Tạo → giải quyết gần nhất",duration[0]==null?"N/A":String.format(Locale.ROOT,"%.2f",((Number)duration[0]).doubleValue()),"giây","Root hiện còn RESOLVED/CLOSED; resolvedAt trong kỳ; "+duration[1]+" mẫu; cùng định nghĩa Commit16"));
        }
        Table details=new Table(queries.headers(f.dataset()),queries.rows(f,actor.getId(),page*size,size),page,size,total);
        var trend=trend(f,queries.days(f,actor.getId()));access.requireAdmin();
        return new Dashboard(f,d.basis(),LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC),List.copyOf(metrics),breakdowns,trend,details,d.statuses());
    }
    public List<Option> branches(int page){access.requireAdmin();ReportPolicy.page(page,50);return queriesOptions("Branch",page);}
    public List<Option> services(int page){access.requireAdmin();ReportPolicy.page(page,50);return queriesOptions("ShippingService",page);}
    private List<Option> queriesOptions(String type,int page){
        var f=new Filter(Dataset.SHIPMENTS,LocalDate.now(clock),LocalDate.now(clock),null,null,null,null,null,Trend.DAY);
        return queries.query("select b.id,b.code,b.name from "+type+" b where "+AdminReportQueries.GATE+" order by b.id",Object[].class,f,access.requireAdmin().getId()).setFirstResult(page*50).setMaxResults(50).getResultList().stream().map(r->new Option((Long)r[0],(String)r[1],(String)r[2])).toList();
    }
    private Metric metric(String label,long value,String unit,String basis){return new Metric(label,Long.toString(value),unit,basis);}
    private static String unit(Dataset d){return switch(d){case SHIPMENTS,DELIVERED,RETURNED->"vận đơn";case DELIVERIES->"lượt giao";case TRIPS->"chuyến";case TRIP_RECEIPTS->"lượt nhận";case RETURN_REQUESTS,PENDING_RETURNS->"yêu cầu hoàn";case RETURN_STARTED->"sự kiện khởi tạo hoàn";case TICKETS,OPEN_TICKETS->"ticket";case TICKET_EVENTS->"sự kiện ticket";case DISCREPANCIES,OPEN_DISCREPANCIES->"sai lệch";case BRANCHES->"chi nhánh";};}
    private static List<Bar> bars(List<Object[]> rows){long max=rows.stream().mapToLong(r->((Number)r[1]).longValue()).max().orElse(0);return rows.stream().map(r->{long value=((Number)r[1]).longValue();return new Bar(r[0]==null?"Chưa xác định (legacy)":new com.ute.uteexpress.dto.admin.ReportLabels().text(r[0]),value,max==0?0:(int)Math.round(value*100.0/max));}).toList();}
    private static List<Bar> trend(Filter f,List<Object[]> days){
        if(AdminReportQueries.definition(f.dataset()).snapshot())return List.of();Map<LocalDate,Long> daily=new HashMap<>();for(var row:days)daily.put((LocalDate)row[0],((Number)row[1]).longValue());
        Map<LocalDate,Long> buckets=new TreeMap<>();for(LocalDate day=f.fromDate();!day.isAfter(f.toDate());day=day.plusDays(1)){LocalDate key=switch(f.trend()){case DAY->day;case WEEK->day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));case MONTH->day.withDayOfMonth(1);};buckets.merge(key,daily.getOrDefault(day,0L),Long::sum);}
        return bars(buckets.entrySet().stream().map(e->new Object[]{e.getKey(),e.getValue()}).toList());
    }
}
