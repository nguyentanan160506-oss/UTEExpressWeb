package com.ute.uteexpress.repository;

import com.ute.uteexpress.dto.operations.OperationsDashboardViews.*;
import com.ute.uteexpress.dto.operations.OperationsDashboardViews.Range;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.operations.OperationsAccessService.Scope;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** All aggregation stays in the DB. Scalar/group projections never load Shipment/contact entities. */
@Repository @Transactional(readOnly=true)
public class OperationsDashboardQueries {
    private final EntityManager em;
    public OperationsDashboardQueries(EntityManager em){this.em=em;}
    // Applied to every query, including scalar counts and work lists; revocation cannot broaden scope.
    public static final String GATE=" exists (select m.id from ManagerBranch m where m.manager.id=:actor and m.branch.id=:branch "
        +"and m.branch.status=com.ute.uteexpress.enums.BranchStatus.ACTIVE and m.manager.status=com.ute.uteexpress.enums.UserStatus.ACTIVE "
        +"and exists (select r.id from UserRole r where r.user.id=m.manager.id and r.role.name='ROLE_MANAGER') "
        +"and exists (select e.id from Employee e where e.user.id=m.manager.id and e.status=com.ute.uteexpress.enums.EmployeeStatus.ACTIVE and e.branch.status=com.ute.uteexpress.enums.BranchStatus.ACTIVE))";
    public static final String CUSTODY=" s.currentBranch.id=:branch and s.status not in (SS.DELIVERED,SS.RETURNED,SS.CANCELLED) and ("
        +"exists (select i.id from ShipmentIntake i where i.shipment.id=s.id and i.branch.id=:branch) or "
        +"exists (select r.id from TripReceipt r where r.item.shipment.id=s.id and r.branch.id=:branch) or "
        +"exists (select r.id from DeliveryRecovery r where r.shipment.id=s.id and r.branch.id=:branch and r.status=DR.RECEIVED))";
    public static final String VALID_ATTEMPT=" a.assignment is not null and a.actor is not null and a.startedAt is not null and a.attemptTime>=a.startedAt "
        +"and a.assignment.shipment.id=a.shipment.id and a.assignment.assignmentType=com.ute.uteexpress.enums.AssignmentType.DELIVERY and a.employee.id=a.assignment.employee.id and ((a.status=DA.FAILED and a.failureCode is not null) or "
        +"(a.status=DA.SUCCESS and a.shipment.status=SS.DELIVERED and exists (select p.id from DeliveryProof p where p.attempt.id=a.id and p.shipment.id=a.shipment.id and p.assignment.id=a.assignment.id and p.employee.id=a.employee.id and p.actor.id=a.actor.id and p.deliveredAt>=a.attemptTime)))";
    // Catalog is public for offline Hibernate/H2 query tests, never accepts a client-supplied fragment.
    public static Map<String,String> catalog(){
        var q=new LinkedHashMap<String,String>();
        q.put("cohort","select cast(s.status as String),count(s) from Shipment s where s.originBranch.id=:branch and s.createdAt>=:from and s.createdAt<:to and (:status is null or s.status=:status) and "+GATE+" group by s.status");
        q.put("daily","select cast(s.createdAt as LocalDate),count(s) from Shipment s where s.originBranch.id=:branch and s.createdAt>=:from and s.createdAt<:to and (:status is null or s.status=:status) and "+GATE+" group by cast(s.createdAt as LocalDate) order by cast(s.createdAt as LocalDate)");
        q.put("incoming","select count(s) from Shipment s where s.destinationBranch.id=:branch and s.createdAt>=:from and s.createdAt<:to and "+GATE);
        q.put("sourceIntakes","select count(i) from ShipmentIntake i where i.branch.id=:branch and i.receivedAt>=:from and i.receivedAt<:to and "+GATE);
        q.put("atDestination","select count(s) from Shipment s where s.destinationBranch.id=:branch and s.status=SS.AT_DESTINATION_BRANCH and "+CUSTODY+" and "+GATE);
        q.put("outForDelivery","select count(s) from Shipment s where s.destinationBranch.id=:branch and s.status=SS.OUT_FOR_DELIVERY and exists(select d.id from DeliveryDispatch d where d.shipment.id=s.id and d.status=com.ute.uteexpress.enums.DeliveryDispatchStatus.STARTED and d.acceptedAt is not null and d.startedAt is not null) and "+GATE);
        q.put("activeAssignments","select count(a) from ShipmentAssignment a where a.status in (com.ute.uteexpress.enums.AssignmentStatus.ASSIGNED,com.ute.uteexpress.enums.AssignmentStatus.IN_PROGRESS) and ((a.assignmentType=com.ute.uteexpress.enums.AssignmentType.PICKUP and a.shipment.originBranch.id=:branch) or (a.assignmentType=com.ute.uteexpress.enums.AssignmentType.DELIVERY and a.shipment.destinationBranch.id=:branch)) and "+GATE);
        q.put("held","select count(s) from Shipment s where "+CUSTODY+" and "+GATE);
        q.put("unreceived","select count(distinct i.shipment.id) from TransportTripItem i where i.active=true and i.trip.origin.id=:branch and i.trip.departedAt is not null "
            +"and i.trip.status in (TR.DEPARTED,TR.RECONCILING) and not exists (select r.id from TripReceipt r where r.item.id=i.id) and "+GATE);
        q.put("awaitReceipt","select count(distinct i.shipment.id) from TransportTripItem i where i.active=true and i.trip.destination.id=:branch and i.trip.status=TR.RECONCILING "
            +"and not exists (select r.id from TripReceipt r where r.item.id=i.id) and "+GATE);
        String proof=" from DeliveryProof p where p.shipment.status=SS.DELIVERED and p.attempt.status=DA.SUCCESS and p.attempt.startedAt is not null "
            +"and p.attempt.shipment.id=p.shipment.id and p.assignment.id=p.attempt.assignment.id and p.assignment.assignmentType=com.ute.uteexpress.enums.AssignmentType.DELIVERY and p.employee.id=p.attempt.employee.id and p.actor.id=p.attempt.actor.id and p.deliveredAt>=p.attempt.attemptTime and p.deliveredAt>=:from and p.deliveredAt<:to and ";
        q.put("delivered","select count(distinct p.shipment.id)"+proof+"p.shipment.destinationBranch.id=:branch and "+GATE);
        q.put("originDelivered","select count(distinct p.shipment.id)"+proof+"p.shipment.originBranch.id=:branch and "+GATE);
        q.put("attempts","select cast(a.status as String),count(a),count(distinct a.shipment.id) from DeliveryAttempt a where a.shipment.destinationBranch.id=:branch "
            +"and a.attemptTime>=:from and a.attemptTime<:to and "+VALID_ATTEMPT+" and "+GATE+" group by a.status");
        q.put("reasons","select cast(a.failureCode as String),count(a) from DeliveryAttempt a where a.shipment.destinationBranch.id=:branch and a.status=DA.FAILED "
            +"and a.attemptTime>=:from and a.attemptTime<:to and "+VALID_ATTEMPT+" and "+GATE+" group by a.failureCode");
        q.put("returnRequests","select count(r) from ReturnRequest r where r.shipment.destinationBranch.id=:branch and r.requestedAt>=:from and r.requestedAt<:to and "+GATE);
        q.put("returned","select count(distinct r.returnRequest.shipment.id) from ReturnReceipt r where r.branch.id=:branch and r.returnRequest.shipment.originBranch.id=:branch "
            +"and r.returnRequest.shipment.status=SS.RETURNED and r.returnRequest.stage=RS.COMPLETED and r.receivedAt>=:from and r.receivedAt<:to and "+GATE);
        q.put("discrepancies","select count(d) from TripDiscrepancy d where d.trip.destination.id=:branch and d.status=TD.OPEN and "+GATE);
        q.put("ticketStates","select cast(t.status as String),count(t) from SupportTicket t where t.handlingBranch.id=:branch and t.createdAt>=:from and t.createdAt<:to and "+GATE+" group by t.status");
        q.put("ticketUnassigned","select count(t) from SupportTicket t where t.handlingBranch.id=:branch and t.status=TS.OPEN and t.assignedManager is null and "+GATE);
        q.put("ticketMine","select count(t) from SupportTicket t where t.handlingBranch.id=:branch and t.assignedManager.id=:actor and t.status not in (TS.RESOLVED,TS.CLOSED) and "+GATE);
        q.put("ticketWaiting","select count(t) from SupportTicket t where t.handlingBranch.id=:branch and t.status=TS.WAITING_CUSTOMER and "+GATE);
        q.put("ticketWarning","select count(t) from SupportTicket t where t.handlingBranch.id=:branch and t.warningDueAt<:now and t.status not in (TS.RESOLVED,TS.CLOSED) and "+GATE);
        q.put("confirmationDuration","select avg(timestampdiff(second,s.createdAt,e.occurredAt)),count(e) from ShipmentEvent e join e.shipment s where s.originBranch.id=:branch "
            +"and e.fromStatus=SS.CREATED and e.toStatus=SS.CONFIRMED and e.eventTitle='Trạng thái: Đã xác nhận' and e.occurredAt>=s.createdAt and e.occurredAt>=:from and e.occurredAt<:to and "+GATE);
        q.put("intakeDuration","select avg(timestampdiff(second,e.occurredAt,i.receivedAt)),count(i) from ShipmentIntake i, ShipmentEvent e where i.branch.id=:branch "
            +"and e.shipment.id=i.shipment.id and e.fromStatus=SS.CREATED and e.toStatus=SS.CONFIRMED and e.eventTitle='Trạng thái: Đã xác nhận' "
            +"and (select count(x.id) from ShipmentEvent x where x.shipment.id=i.shipment.id and x.fromStatus=SS.CREATED and x.toStatus=SS.CONFIRMED)=1 "
            +"and i.receivedAt>=e.occurredAt and i.receivedAt>=:from and i.receivedAt<:to and "+GATE);
        q.put("transportDuration","select avg(timestampdiff(second,r.item.trip.departedAt,r.receivedAt)),count(r) from TripReceipt r where r.branch.id=:branch "
            +"and r.item.trip.departedAt is not null and r.receivedAt>=r.item.trip.departedAt and r.receivedAt>=:from and r.receivedAt<:to and "+GATE);
        q.put("destinationWait","select avg(timestampdiff(second,a.occurredAt,d.occurredAt)),count(d) from ShipmentEvent a,ShipmentEvent d "
            +"where a.shipment.id=d.shipment.id and d.shipment.destinationBranch.id=:branch and a.toStatus=SS.AT_DESTINATION_BRANCH "
            +"and a.eventTitle in ('Đã kiểm nhận kiện tại điểm đến','Phân loại chờ giao tại cùng chi nhánh') "
            +"and (select count(x.id) from ShipmentEvent x where x.shipment.id=a.shipment.id and x.toStatus=SS.AT_DESTINATION_BRANCH "
            +"and x.eventTitle in ('Đã kiểm nhận kiện tại điểm đến','Phân loại chờ giao tại cùng chi nhánh'))=1 "
            +"and d.toStatus=SS.OUT_FOR_DELIVERY and d.eventTitle='Shipper bắt đầu lần giao' "
            +"and d.id=(select min(x.id) from ShipmentEvent x where x.shipment.id=d.shipment.id and x.toStatus=SS.OUT_FOR_DELIVERY and x.eventTitle='Shipper bắt đầu lần giao') "
            +"and d.occurredAt>=a.occurredAt and d.occurredAt>=:from and d.occurredAt<:to and "+GATE);
        q.put("deliveryDuration","select avg(timestampdiff(second,a.startedAt,a.attemptTime)),count(a) from DeliveryAttempt a where a.shipment.destinationBranch.id=:branch "
            +"and a.attemptTime>=:from and a.attemptTime<:to and "+VALID_ATTEMPT+" and "+GATE);
        q.put("ticketDuration","select avg(timestampdiff(second,t.createdAt,t.resolvedAt)),count(t) from SupportTicket t where t.handlingBranch.id=:branch "
            +"and t.resolvedAt>=t.createdAt and t.resolvedAt>=:from and t.resolvedAt<:to and t.status in (TS.RESOLVED,TS.CLOSED) and "+GATE);
        q.replaceAll((k,v)->expand(v));return Collections.unmodifiableMap(q);
    }
    private static String expand(String hql){return hql.replace("SS.","com.ute.uteexpress.enums.ShipmentStatus.").replace("TS.","com.ute.uteexpress.enums.TicketStatus.")
        .replace("TR.","com.ute.uteexpress.enums.TripStatus.").replace("DA.","com.ute.uteexpress.enums.DeliveryAttemptStatus.")
        .replace("DR.","com.ute.uteexpress.enums.DeliveryRecoveryStatus.").replace("RS.","com.ute.uteexpress.enums.ReturnStage.").replace("TD.","com.ute.uteexpress.enums.TripDiscrepancyStatus.");}
    public static String workPredicate(WorkGroup group){return expand(switch(group){
        case CONFIRM -> "s.originBranch.id=:branch and s.status=SS.CREATED";
        case PICKUP -> "s.originBranch.id=:branch and s.shipmentMethod=com.ute.uteexpress.enums.ShipmentMethod.HOME_PICKUP and s.status in (SS.CONFIRMED,SS.PICKUP_ASSIGNED)";
        case INTAKE -> "s.originBranch.id=:branch and ((s.shipmentMethod=com.ute.uteexpress.enums.ShipmentMethod.BRANCH_DROPOFF and s.status=SS.CONFIRMED) or "
            +"(s.shipmentMethod=com.ute.uteexpress.enums.ShipmentMethod.HOME_PICKUP and s.status=SS.PICKED_UP)) and not exists (select i.id from ShipmentIntake i where i.shipment.id=s.id)";
        case DISPATCH -> CUSTODY+" and s.status in (SS.AT_ORIGIN_BRANCH,SS.AT_ORIGIN_HUB,SS.AT_DESTINATION_HUB) and not exists(select i.id from TransportTripItem i where i.shipment.id=s.id and i.active=true)";
        case DELIVERY -> "s.destinationBranch.id=:branch and s.status=SS.AT_DESTINATION_BRANCH and "+CUSTODY
            +" and not exists(select a.id from ShipmentAssignment a where a.shipment.id=s.id and a.assignmentType=com.ute.uteexpress.enums.AssignmentType.DELIVERY and a.status in (com.ute.uteexpress.enums.AssignmentStatus.ASSIGNED,com.ute.uteexpress.enums.AssignmentStatus.IN_PROGRESS))";
        case FAILED -> "s.destinationBranch.id=:branch and s.status=SS.DELIVERY_FAILED and exists(select a.id from DeliveryAttempt a where a.shipment.id=s.id and a.status=DA.FAILED and a.attemptNumber=s.deliveryAttemptCount and a.startedAt is not null) "
            +"and not exists(select d.id from RedeliveryDecision d where d.shipment.id=s.id and d.afterAttemptNumber=s.deliveryAttemptCount) and not exists(select r.id from ReturnRequest r where r.shipment.id=s.id)";
        case RETURNS -> "s.status=SS.RETURNING and exists(select r.id from ReturnRequest r where r.shipment.id=s.id and r.stage<>RS.COMPLETED) "
            +"and (s.currentBranch.id=:branch or (s.destinationBranch.id=:branch and exists(select r.id from ReturnRequest r where r.shipment.id=s.id and r.stage=RS.REQUESTED)))";
    });}
    private <T> TypedQuery<T> query(String hql,Class<T> type,Scope scope,Range range,ShipmentStatus status,LocalDateTime now){
        var q=em.createQuery(hql,type);q.setParameter("actor",scope.actor().getId());q.setParameter("branch",scope.branch().getId());
        if(hql.contains(":from")){q.setParameter("from",range.from());q.setParameter("to",range.to());}
        if(hql.contains(":status"))q.setParameter("status",status);
        if(hql.contains(":now"))q.setParameter("now",now);
        q.setHint("jakarta.persistence.query.timeout",5000);return q;
    }
    public Map<String,Long> groups(String name,Scope s,Range r,ShipmentStatus status){var result=new LinkedHashMap<String,Long>();for(Object[] row:query(catalog().get(name),Object[].class,s,r,status,null).getResultList())result.put(String.valueOf(row[0]),((Number)row[1]).longValue());return result;}
    public Map<LocalDate,Long> days(Scope s,Range r,ShipmentStatus status){var result=new LinkedHashMap<LocalDate,Long>();for(Object[] row:query(catalog().get("daily"),Object[].class,s,r,status,null).getResultList())result.put((LocalDate)row[0],((Number)row[1]).longValue());return result;}
    public long count(String name,Scope s,Range r,LocalDateTime now){return query(catalog().get(name),Long.class,s,r,null,now).getSingleResult();}
    public AttemptResults attempts(Scope s,Range r){long success=0,failed=0,shipments=0;for(Object[] row:query(catalog().get("attempts"),Object[].class,s,r,null,null).getResultList()){if("SUCCESS".equals(row[0]))success=((Number)row[1]).longValue();else{failed=((Number)row[1]).longValue();shipments=((Number)row[2]).longValue();}}return new AttemptResults(success,failed,shipments);}
    public DurationMetric duration(String name,String label,Scope s,Range r){Object[] row=query(catalog().get(name),Object[].class,s,r,null,null).getSingleResult();return new DurationMetric(label,row[0]==null?null:((Number)row[0]).doubleValue(),((Number)row[1]).longValue());}
    public long workCount(Scope s,WorkGroup group,LocalDateTime cutoff){String hql="select count(s) from Shipment s where "+workPredicate(group)+" and "+GATE+(cutoff==null?"":" and s.createdAt<:cutoff");var q=query(hql,Long.class,s,null,null,null);if(cutoff!=null)q.setParameter("cutoff",cutoff);return q.getSingleResult();}
    public Page<Work> work(Scope s,WorkGroup group,int page){String hql="select s.id,s.trackingNumber,cast(s.status as String),s.createdAt from Shipment s where "+workPredicate(group)+" and "+GATE+" order by s.createdAt,s.id";
        var rows=query(hql,Object[].class,s,null,null,null).setFirstResult(page*20).setMaxResults(20).getResultList().stream().map(r->new Work((Long)r[0],(String)r[1],(String)r[2],(LocalDateTime)r[3],path(group,(Long)r[0]))).toList();return new PageImpl<>(rows,PageRequest.of(page,20),workCount(s,group,null));}
    private String path(WorkGroup group,Long id){return switch(group){case CONFIRM,PICKUP,INTAKE->"/manager/shipments/"+id;case DELIVERY,FAILED->"/manager/deliveries/"+id;case DISPATCH->"/manager/trips/shipments/"+id+"/history";case RETURNS->"/manager/deliveries";};}
    public static String tripWorkQuery(){return expand("select t.id,t.code,cast(t.status as String),t.createdAt from TransportTrip t where ((t.origin.id=:branch and t.status in (TR.DRAFT,TR.SEALED)) or (t.destination.id=:branch and (t.status=TR.RECONCILING or exists(select d.id from TripDiscrepancy d where d.trip.id=t.id and d.status=TD.OPEN)))) and "+GATE+" order by t.createdAt,t.id");}
    public List<Work> trips(Scope s){return query(tripWorkQuery(),Object[].class,s,null,null,null).setMaxResults(10).getResultList().stream().map(r->new Work((Long)r[0],(String)r[1],(String)r[2],(LocalDateTime)r[3],"/manager/trips/"+r[0])).toList();}
}
