package com.ute.uteexpress.repository;

import com.ute.uteexpress.dto.admin.AuditViews.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** UNION ALL read model; never copies business histories into AuditLog. Projects no free-text payloads. */
@Repository @Transactional(readOnly=true)
public class AdminAuditQueries {
    private final EntityManager em;
    public AdminAuditQueries(EntityManager em){this.em=em;}
    public static String union(){
        String admin="select 'ADMIN' as source,a.id as eventId,a.createdAt as at,a.actor.id as actorId,a.action as action,cast(a.resourceType as String) as entityType,a.resourceId as entityId,cast(null as Long) as branchId from AuditLog a where ";
        String shipment="select 'SHIPMENT' as source,e.id as eventId,e.occurredAt as at,e.actor.id as actorId,concat('STATUS_',cast(e.toStatus as String)) as action,'SHIPMENT' as entityType,cast(e.shipment.id as String) as entityId,e.branch.id as branchId from ShipmentEvent e where ";
        String trip="select 'TRANSPORT' as source,e.id as eventId,e.occurredAt as at,e.actor.id as actorId,cast(e.type as String) as action,'TRANSPORT_TRIP' as entityType,cast(e.trip.id as String) as entityId,e.branch.id as branchId from TransportTripEvent e where ";
        String ticket="select 'TICKET' as source,e.id as eventId,e.occurredAt as at,e.actor.id as actorId,cast(e.action as String) as action,'SUPPORT_TICKET' as entityType,cast(e.ticket.id as String) as entityId,e.branch.id as branchId from SupportTicketEvent e where ";
        return admin+gate("ADMIN","a.createdAt","a.actor.id","a.action","cast(a.resourceType as String)","a.resourceId",null)
            +" union all "+shipment+gate("SHIPMENT","e.occurredAt","e.actor.id","concat('STATUS_',cast(e.toStatus as String))","'SHIPMENT'","cast(e.shipment.id as String)","e.branch.id")
            +" union all "+trip+gate("TRANSPORT","e.occurredAt","e.actor.id","cast(e.type as String)","'TRANSPORT_TRIP'","cast(e.trip.id as String)","e.branch.id")
            +" union all "+ticket+gate("TICKET","e.occurredAt","e.actor.id","cast(e.action as String)","'SUPPORT_TICKET'","cast(e.ticket.id as String)","e.branch.id");
    }
    private static String gate(String source,String time,String actor,String action,String type,String id,String branch){return
        "(:source='ALL' or :source='"+source+"') and "+time+">=:from and "+time+"<:to and (:filterActor is null or "+actor+"=:filterActor) "
        +"and (:action is null or "+action+"=:action) and (:entityType is null or "+type+"=:entityType) and (:entityId is null or "+id+"=:entityId) and "
        +(branch==null?":branch is null":"(:branch is null or "+branch+"=:branch)")+" and "+AdminReportQueries.GATE;}
    public static String rowsHql(){return "select x.source,x.eventId,x.at,x.actorId,x.action,x.entityType,x.entityId,x.branchId from ("+union()+") x order by x.at desc,x.source,x.eventId desc";}
    public static String countHql(){return "select count(*) from ("+union()+") x";}
    private <T> TypedQuery<T> query(String hql,Class<T> type,Filter f,Long admin){var q=em.createQuery(hql,type);q.setParameter("source",f.source().name());q.setParameter("from",f.from());q.setParameter("to",f.to());q.setParameter("filterActor",f.actorId());q.setParameter("action",f.action());q.setParameter("entityType",f.entityType());q.setParameter("entityId",f.entityId());q.setParameter("branch",f.branchId());q.setParameter("adminId",admin);q.setHint("jakarta.persistence.query.timeout",10000);return q;}
    public long count(Filter f,Long admin){return query(countHql(),Long.class,f,admin).getSingleResult();}
    public List<Row> rows(Filter f,Long admin,int page,int size){return query(rowsHql(),Object[].class,f,admin).setFirstResult(page*size).setMaxResults(size).getResultList().stream().map(r->new Row((String)r[0],(Long)r[1],(LocalDateTime)r[2],(Long)r[3],(String)r[4],(String)r[5],(String)r[6],(Long)r[7])).toList();}
}
