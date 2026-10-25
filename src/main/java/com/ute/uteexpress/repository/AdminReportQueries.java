package com.ute.uteexpress.repository;

import com.ute.uteexpress.dto.admin.ReportViews.*;
import com.ute.uteexpress.enums.*;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Fixed scalar/aggregate catalog shared by HTML and CSV. No caller-supplied SQL fragments. */
@Repository @Transactional(readOnly=true)
public class AdminReportQueries {
    public static final String GATE=" exists(select u.id from User u where u.id=:adminId and u.status=com.ute.uteexpress.enums.UserStatus.ACTIVE "
        +"and exists(select r.id from UserRole r where r.user.id=u.id and r.role.name='ROLE_ADMIN'))";
    public record Column(String label,String expression) { }
    public record Group(String label,String expression) { }
    public record Definition(String root,String predicate,String id,String time,String branch,String service,String status,
            List<String> statuses,String shipment,String basis,List<Column> columns,List<Group> groups) {
        public boolean snapshot(){return time==null;}
    }
    private final EntityManager em;
    public AdminReportQueries(EntityManager em){this.em=em;}
    private static Column c(String label,String expression){return new Column(label,expression);}
    private static Group g(String label,String expression){return new Group(label,expression);}
    private static <E extends Enum<E>> List<String> names(Class<E> type){return Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();}
    public static String expand(String hql){return hql.replace("SS.","com.ute.uteexpress.enums.ShipmentStatus.")
        .replace("TS.","com.ute.uteexpress.enums.TicketStatus.").replace("TR.","com.ute.uteexpress.enums.TripStatus.")
        .replace("DA.","com.ute.uteexpress.enums.DeliveryAttemptStatus.").replace("DR.","com.ute.uteexpress.enums.DeliveryRecoveryStatus.")
        .replace("RS.","com.ute.uteexpress.enums.ReturnStage.").replace("TD.","com.ute.uteexpress.enums.TripDiscrepancyStatus.");}
    // Exact proof definition used by Commit16, excluding only the per-branch/time suffix.
    public static String validProof(){return OperationalReportRules.VALID_PROOF;}
    public static Definition definition(Dataset dataset){
        return switch(dataset){
            case SHIPMENTS -> new Definition("Shipment s join s.originBranch b join s.service v","1=1","s.id","s.createdAt","b.id","v.id","s.status",names(ShipmentStatus.class),"s.id",
                "Cohort theo createdAt UTC; trạng thái hiện tại, không phải số lần chuyển trạng thái.",
                List.of(c("ID","s.id"),c("Mã vận đơn","s.trackingNumber"),c("Trạng thái","cast(s.status as String)"),c("Tạo lúc UTC","s.createdAt"),c("Chi nhánh nguồn","b.code"),c("Dịch vụ","v.code")),
                List.of(g("Trạng thái cohort","cast(s.status as String)"),g("Chi nhánh nguồn","b.code"),g("Loại dịch vụ","v.code")));
            case DELIVERIES -> new Definition("DeliveryAttempt a join a.shipment s join s.destinationBranch b",OperationsDashboardQueries.VALID_ATTEMPT,"a.id","a.attemptTime","b.id","s.service.id","a.status",names(DeliveryAttemptStatus.class),"s.id",
                "Lượt giao hợp lệ theo attemptTime UTC; SUCCESS cần proof khớp, FAILED cần failureCode. Chi nhánh đích.",
                List.of(c("ID lượt","a.id"),c("Mã vận đơn","s.trackingNumber"),c("Kết quả","cast(a.status as String)"),c("Thời điểm UTC","a.attemptTime"),c("Chi nhánh đích","b.code"),c("Mã lý do","cast(a.failureCode as String)")),
                List.of(g("Kết quả lượt giao","cast(a.status as String)"),g("Mã lý do thất bại","cast(a.failureCode as String)")));
            case DELIVERED -> new Definition("DeliveryProof p join p.shipment s join s.destinationBranch b",validProof(),"p.id","p.deliveredAt","b.id","s.service.id",null,List.of(),"s.id",
                "Vận đơn DELIVERED có proof hợp lệ theo deliveredAt UTC; không xuất tên/bằng chứng người nhận.",
                List.of(c("ID proof","p.id"),c("Mã vận đơn","s.trackingNumber"),c("Giao lúc UTC","p.deliveredAt"),c("Chi nhánh đích","b.code")),List.of(g("Chi nhánh đích","b.code")));
            case RETURN_REQUESTS,PENDING_RETURNS -> new Definition("ReturnRequest r join r.shipment s join s.destinationBranch b",
                dataset==Dataset.PENDING_RETURNS?"r.stage<>RS.COMPLETED":"1=1","r.id",dataset==Dataset.PENDING_RETURNS?null:"r.requestedAt","b.id","s.service.id","r.stage",names(ReturnStage.class),"s.id",
                dataset==Dataset.PENDING_RETURNS?"Snapshot chưa hoàn tất hiện tại, không cắt theo ngày tạo; chưa phải hàng đã trả.":"Yêu cầu hoàn theo requestedAt UTC, không phải bằng chứng trả hàng. Chi nhánh đích xử lý.",
                List.of(c("ID yêu cầu","r.id"),c("Mã vận đơn","s.trackingNumber"),c("Giai đoạn","cast(r.stage as String)"),c("Yêu cầu lúc UTC","r.requestedAt"),c("Chi nhánh đích","b.code")),List.of(g("Giai đoạn hoàn","cast(r.stage as String)")));
            case RETURN_STARTED -> new Definition("ShipmentEvent e join e.shipment s left join e.branch b",
                "e.toStatus=SS.RETURNING and e.fromStatus is not null and e.fromStatus<>SS.RETURNING","e.id","e.occurredAt","b.id","s.service.id",null,List.of(),"s.id",
                "Sự kiện bắt đầu RETURNING theo occurredAt UTC; không phải bằng chứng thực trả. Vận đơn duy nhất được đếm riêng với lượt sự kiện.",
                List.of(c("ID sự kiện","e.id"),c("Mã vận đơn","s.trackingNumber"),c("Từ trạng thái","cast(e.fromStatus as String)"),c("Khởi tạo UTC","e.occurredAt"),c("Chi nhánh sự kiện","b.code")),List.of(g("Chi nhánh khởi tạo","b.code")));
            case RETURNED -> new Definition("ReturnReceipt r join r.returnRequest q join q.shipment s join r.branch b",
                "s.status=SS.RETURNED and q.stage=RS.COMPLETED and b.id=s.originBranch.id","r.id","r.receivedAt","b.id","s.service.id",null,List.of(),"s.id",
                "Thực trả người gửi theo receivedAt UTC: receipt tại nguồn, ReturnRequest COMPLETED, Shipment RETURNED.",
                List.of(c("ID receipt","r.id"),c("Mã vận đơn","s.trackingNumber"),c("Thực trả UTC","r.receivedAt"),c("Chi nhánh nguồn","b.code")),List.of(g("Chi nhánh nguồn","b.code")));
            case TRIPS -> new Definition("TransportTrip t join t.origin b join t.destination dest",
                "1=1","t.id","t.createdAt","b.id",null,"t.status",names(TripStatus.class),null,
                "Cohort chuyến theo createdAt UTC; chi nhánh nguồn. DEPARTED không phải nhận hàng; CLOSED không tự bảo đảm nhận đủ.",
                List.of(c("ID chuyến","t.id"),c("Mã chuyến","t.code"),c("Trạng thái","cast(t.status as String)"),c("Tạo UTC","t.createdAt"),c("Nguồn","b.code"),c("Đích","dest.code"),c("Chặng","cast(t.leg as String)")),
                List.of(g("Trạng thái chuyến","cast(t.status as String)"),g("Chặng vận chuyển","cast(t.leg as String)"),g("Tuyến ID","cast(t.route.id as String)")));
            case TRIP_RECEIPTS -> new Definition("TripReceipt r join r.item i join i.trip t join i.shipment s join r.branch b",
                "b.id=t.destination.id and t.departedAt is not null and r.receivedAt>=t.departedAt","r.id","r.receivedAt","b.id","s.service.id",null,List.of(),"s.id",
                "Lượt thực nhận từng kiện/chặng theo receivedAt UTC; một shipment có thể có nhiều lượt. Chi nhánh nhận.",
                List.of(c("ID receipt","r.id"),c("Mã chuyến","t.code"),c("Mã vận đơn","s.trackingNumber"),c("Nhận UTC","r.receivedAt"),c("Chi nhánh nhận","b.code")),List.of(g("Chi nhánh nhận","b.code")));
            case DISCREPANCIES,OPEN_DISCREPANCIES -> new Definition("TripDiscrepancy d join d.trip t join t.destination b left join d.item item left join item.shipment parcel",
                dataset==Dataset.OPEN_DISCREPANCIES?"d.status=TD.OPEN":"1=1","d.id",dataset==Dataset.OPEN_DISCREPANCIES?null:"d.recordedAt","b.id","parcel.service.id","d.status",names(TripDiscrepancyStatus.class),null,
                dataset==Dataset.OPEN_DISCREPANCIES?"Snapshot sai lệch OPEN hiện tại, không cắt theo ngày ghi nhận. Chi nhánh đích.":"Sai lệch theo recordedAt UTC, trạng thái xử lý hiện tại; không xuất notes/resolution tự do.",
                List.of(c("ID sai lệch","d.id"),c("Mã chuyến","t.code"),c("Loại","cast(d.kind as String)"),c("Trạng thái","cast(d.status as String)"),c("Ghi nhận UTC","d.recordedAt"),c("Chi nhánh đích","b.code")),
                List.of(g("Loại sai lệch","cast(d.kind as String)"),g("Trạng thái xử lý","cast(d.status as String)")));
            case TICKETS,OPEN_TICKETS -> new Definition("SupportTicket t left join t.shipment s left join t.handlingBranch b",
                dataset==Dataset.OPEN_TICKETS?"t.status not in (TS.RESOLVED,TS.CLOSED)":"1=1","t.id",dataset==Dataset.OPEN_TICKETS?null:"t.createdAt","b.id","s.service.id","t.status",names(TicketStatus.class),"s.id",
                dataset==Dataset.OPEN_TICKETS?"Snapshot ticket đang mở hiện tại, gồm legacy chưa xác định branch; không cắt theo createdAt.":"Cohort ticket theo createdAt UTC; source không xác định của legacy được giữ nguyên.",
                List.of(c("ID ticket","t.id"),c("Mã ticket","t.ticketCode"),c("Trạng thái","cast(t.status as String)"),c("Loại vấn đề","cast(t.category as String)"),c("Ưu tiên","cast(t.priority as String)"),c("Nguồn","cast(t.source as String)"),c("Tạo UTC","t.createdAt"),c("Chi nhánh xử lý","b.code")),
                List.of(g("Trạng thái ticket","cast(t.status as String)"),g("Loại khiếu nại","cast(t.category as String)"),g("Ưu tiên","cast(t.priority as String)"),g("Nguồn tiếp nhận","cast(t.source as String)"),g("Chi nhánh chịu trách nhiệm","b.code")));
            case TICKET_EVENTS -> new Definition("SupportTicketEvent e join e.ticket t left join t.shipment s join e.branch b",
                "e.action in (com.ute.uteexpress.enums.TicketAction.RESOLVED,com.ute.uteexpress.enums.TicketAction.CLOSED,com.ute.uteexpress.enums.TicketAction.REOPENED,com.ute.uteexpress.enums.TicketAction.REOPEN_REQUESTED)",
                "e.id","e.occurredAt","b.id","s.service.id","e.action",List.of("RESOLVED","CLOSED","REOPENED","REOPEN_REQUESTED"),null,
                "Sự kiện ticket theo occurredAt UTC. REOPEN_REQUESTED của khách được tách khỏi REOPENED thực tế; không xuất message/audit detail.",
                List.of(c("ID sự kiện","e.id"),c("Mã ticket","t.ticketCode"),c("Hành động","cast(e.action as String)"),c("Thời điểm UTC","e.occurredAt"),c("Chi nhánh sự kiện","b.code")),List.of(g("Kết quả xử lý ticket","cast(e.action as String)")));
            case BRANCHES -> new Definition("Branch b","1=1","b.id",null,"b.id",null,null,List.of(),null,
                "Đơn nguồn/đích và lượt intake trong kỳ; custody/chờ dispatch/chờ giao/đang hoàn là snapshot hiện tại, không cộng thành tổng shipment duy nhất.",List.of(),List.of());
        };
    }
    public static String where(Definition d){
        String p=" where ("+d.predicate()+") and "+GATE+" and (:branch is null or "+d.branch()+"=:branch)";
        if(d.time()!=null)p+=" and "+d.time()+">=:from and "+d.time()+"<:to";
        if(d.service()!=null)p+=" and (:service is null or "+d.service()+"=:service)";
        else if(d.root().contains("TransportTrip ") || d.root().contains("TripDiscrepancy "))p+=" and (:service is null or exists(select item.id from TransportTripItem item where item.trip.id=t.id and item.shipment.service.id=:service))";
        if(d.status()!=null)p+=" and (:status is null or cast("+d.status()+" as String)=:status)";
        return expand(p);
    }
    public <T> TypedQuery<T> query(String hql,Class<T> type,Filter f,Long admin){
        hql=expand(hql);var q=em.createQuery(hql,type);
        for(var parameter:q.getParameters())switch(parameter.getName()){
            case "adminId"->q.setParameter("adminId",admin);case "branch"->q.setParameter("branch",f.branchId());case "service"->q.setParameter("service",f.serviceId());
            case "from"->q.setParameter("from",f.from());case "to"->q.setParameter("to",f.to());case "status"->q.setParameter("status",f.status());default->{}
        }
        q.setHint("jakarta.persistence.query.timeout",10000);return q;
    }
    public long count(Filter f,Long admin){var d=definition(f.dataset());return query("select count(*) from "+d.root()+where(d),Long.class,f,admin).getSingleResult();}
    public long distinctShipments(Filter f,Long admin){var d=definition(f.dataset());return d.shipment()==null?0:query("select count(distinct "+d.shipment()+") from "+d.root()+where(d),Long.class,f,admin).getSingleResult();}
    public List<Object[]> groups(Filter f,Long admin,Group group){var d=definition(f.dataset());return query("select "+group.expression()+",count(*) from "+d.root()+where(d)+(group.expression().equals("cast(a.failureCode as String)")?" and a.status=com.ute.uteexpress.enums.DeliveryAttemptStatus.FAILED":"")+" group by "+group.expression()+" order by count(*) desc,"+group.expression(),Object[].class,f,admin).setMaxResults(100).getResultList();}
    public List<Object[]> days(Filter f,Long admin){var d=definition(f.dataset());if(d.snapshot())return List.of();String date="cast("+d.time()+" as LocalDate)";return query("select "+date+",count(*) from "+d.root()+where(d)+" group by "+date+" order by "+date,Object[].class,f,admin).getResultList();}
    public List<String> headers(Dataset dataset){return dataset==Dataset.BRANCHES?List.of("ID","Mã chi nhánh","Tên","Đơn nguồn trong kỳ","Đơn đích trong kỳ","Lượt intake trong kỳ","Custody hiện tại","Chờ dispatch","Chờ giao","Đang hoàn"):
        definition(dataset).columns().stream().map(Column::label).toList();}
    public List<List<String>> rows(Filter f,Long admin,int offset,int limit){
        var d=definition(f.dataset());String select=f.dataset()==Dataset.BRANCHES?branchSelect():d.columns().stream().map(Column::expression).collect(Collectors.joining(","));
        return query("select "+select+" from "+d.root()+where(d)+" order by "+d.id()+" desc",Object[].class,f,admin).setFirstResult(offset).setMaxResults(limit).getResultList().stream().map(AdminReportQueries::cells).toList();
    }
    public java.util.stream.Stream<Object[]> stream(Filter f,Long admin,int limit){var d=definition(f.dataset());String select=f.dataset()==Dataset.BRANCHES?branchSelect():d.columns().stream().map(Column::expression).collect(Collectors.joining(","));
        return query("select "+select+" from "+d.root()+where(d)+" order by "+d.id()+" desc",Object[].class,f,admin).setMaxResults(limit).setHint("org.hibernate.fetchSize",250).getResultStream();}
    public static List<String> cells(Object[] row){return Arrays.stream(row).map(v->v==null?"Chưa xác định":v.toString()).toList();}
    private static String branchSelect(){
        String service=" and (:service is null or s.service.id=:service)",period=" and s.createdAt>=:from and s.createdAt<:to";
        String held=OperationsDashboardQueries.CUSTODY.replace(":branch","b.id");
        String dispatch=OperationsDashboardQueries.workPredicate(com.ute.uteexpress.dto.operations.OperationsDashboardViews.WorkGroup.DISPATCH).replace(":branch","b.id");
        String delivery=OperationsDashboardQueries.workPredicate(com.ute.uteexpress.dto.operations.OperationsDashboardViews.WorkGroup.DELIVERY).replace(":branch","b.id");
        return "b.id,b.code,b.name,(select count(s) from Shipment s where s.originBranch.id=b.id"+period+service+"),"
            +"(select count(s) from Shipment s where s.destinationBranch.id=b.id"+period+service+"),"
            +"(select count(i) from ShipmentIntake i where i.branch.id=b.id and i.receivedAt>=:from and i.receivedAt<:to and (:service is null or i.shipment.service.id=:service)),"
            +"(select count(s) from Shipment s where "+held+service+"),"
            +"(select count(s) from Shipment s where "+dispatch+service+"),"
            +"(select count(s) from Shipment s where "+delivery+service+"),"
            +"(select count(s) from Shipment s where s.currentBranch.id=b.id and s.status=SS.RETURNING"+service+")";
    }
    /** Compile all report shapes in offline SQLServerDialect tests as well as executing with JDBC fixtures. */
    public static List<String> catalog(){var list=new ArrayList<String>();for(var dataset:Dataset.values()){var d=definition(dataset);String base=" from "+d.root()+where(d);list.add(expand("select count(*)"+base));
        String select=dataset==Dataset.BRANCHES?branchSelect():d.columns().stream().map(Column::expression).collect(Collectors.joining(","));list.add(expand("select "+select+base+" order by "+d.id()+" desc"));}return list;}
}
