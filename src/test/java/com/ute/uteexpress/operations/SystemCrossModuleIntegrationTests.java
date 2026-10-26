package com.ute.uteexpress.operations;

import com.ute.uteexpress.dto.admin.*;
import com.ute.uteexpress.dto.delivery.DeliveryCommands.*;
import com.ute.uteexpress.dto.operations.*;
import com.ute.uteexpress.dto.shipment.*;
import com.ute.uteexpress.dto.transport.TripCommands.Create;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.notifications.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.server.ResponseStatusException;
import static com.ute.uteexpress.operations.OperationsJdbcFixture.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(SystemSecurityIntegrationConfig.class)
class SystemCrossModuleIntegrationTests extends SystemSecuritySupport {
    @Autowired NotificationWorker shipmentWorker;
    @Autowired TicketNotificationWorker ticketWorker;
    @Test void fourSourceAuditKeepsCollidingIdsSeparateAndReportsNeverExposeSupportBodies() {
        Long id=intake(ShipmentMethod.HOME_PICKUP),ticket=customerTicket(id);operator(f.destination);
        tickets.note(ticket,new TicketCommands.Message(ticketVersion(ticket),"INTERNAL-INVESTIGATION-21"));tickets.reply(ticket,new TicketCommands.Message(ticketVersion(ticket),"Public answer"));
        forward(id);Long task=running(id);lastMile.complete(success(id,task));as(f.admin,"ROLE_ADMIN");adminUsers.lock(f.other.getId());
        var first=audit.read(auditRequest(),0,7);assertEquals(first.rows(),audit.read(auditRequest(),0,7).rows());
        var all=new ArrayList<AuditViews.Row>();for(int page=0;page<30;page++){var batch=audit.read(auditRequest(),page,7);all.addAll(batch.rows());if(!batch.hasNext())break;}
        assertEquals(count(AuditLog.class)+count(ShipmentEvent.class)+count(TransportTripEvent.class)+count(SupportTicketEvent.class),all.size());
        assertEquals(all.size(),all.stream().map(r->r.source()+":"+r.id()).distinct().count());assertEquals(Set.of("ADMIN","SHIPMENT","TRANSPORT","TICKET"),all.stream().map(AuditViews.Row::source).collect(java.util.stream.Collectors.toSet()));
        assertEquals(4,all.stream().filter(r->r.id().equals(firstId(AuditLog.class))).map(AuditViews.Row::source).distinct().count());
        assertTrue(all.stream().anyMatch(r->r.source().equals("SHIPMENT") && r.action().equals("STATUS_DELIVERED") && r.actorId().equals(f.shipper.getId())));
        assertFalse(all.toString().contains("INTERNAL-INVESTIGATION-21"));
        var stats=emf.unwrap(org.hibernate.engine.spi.SessionFactoryImplementor.class).getStatistics();stats.clear();
        assertEquals(1,report(ReportViews.Dataset.SHIPMENTS).details().total());var trips=report(ReportViews.Dataset.TRIPS);assertEquals("1",metric(trips,"Vận đơn duy nhất qua các chặng"));assertEquals("3",metric(trips,"Lượt vận chuyển kiện/chặng"));
        var file=csv.export(reportRequest(ReportViews.Dataset.TICKETS),ReportViews.Export.DETAILS);assertFalse(new String(file.bytes(),java.nio.charset.StandardCharsets.UTF_8).contains("INTERNAL-INVESTIGATION-21"));
        audit.read(auditRequest(),0,20);assertEquals(0,stats.getEntityInsertCount());assertEquals(0,stats.getEntityUpdateCount());assertEquals(0,stats.getEntityDeleteCount());assertEquals(0,notices.count());
        shipmentWorker.poll();ticketWorker.poll();as(f.owner,"ROLE_USER");assertTrue(inbox.count()>0);assertFalse(customer.portalDetail(ticket,0).toString().contains("INTERNAL-INVESTIGATION-21"));
    }
    Long firstId(Class<?> type) {return db(()->em.createQuery("select min(e.id) from "+type.getSimpleName()+" e",Long.class).getSingleResult());}
    @Test void failedDeliveryReverseReceiptAndFinalSenderHandbackHaveExactAuditActorAndReportMeaning() {
        Long id=atDestination(),task=running(id);fail(id,task);operator(f.destination);lastMile.initiateReturn(new ReturnStart(id,shipmentVersion(id),"Return after failed delivery"));recover(id,task);reverse(id);
        assertEquals(0,report(ReportViews.Dataset.RETURNED).details().total());operator(f.source);lastMile.returnToSender(senderReceipt(id));
        as(f.admin,"ROLE_ADMIN");var filtered=audit.read(new AuditViews.Request(ReportViews.Period.TODAY,null,null,AuditViews.Source.SHIPMENT,f.manager.getId(),"STATUS_RETURNED","SHIPMENT",id.toString(),f.source.getId()),0,20);
        assertEquals(1,filtered.total());assertEquals(f.manager.getId(),filtered.rows().getFirst().actorId());assertEquals(1,report(ReportViews.Dataset.RETURNED).details().total());assertEquals(0,report(ReportViews.Dataset.DELIVERED).details().total());
        assertEquals("1",metric(report(ReportViews.Dataset.TRIPS),"Vận đơn duy nhất qua các chặng"));assertEquals(6,count(TripReceipt.class));
    }
    @Test void crossUserShipmentTicketAndNoticeOwnershipCannotBeGainedFromTrackingOrNotificationId() {
        Long id=created(ShipmentMethod.BRANCH_DROPOFF),ticket=customerTicket(id);operator(f.destination);tickets.reply(ticket,new TicketCommands.Message(ticketVersion(ticket),"Public reply"));tickets.note(ticket,new TicketCommands.Message(ticketVersion(ticket),"INTERNAL-PRIVATE-21"));ticketWorker.poll();shipmentWorker.poll();
        as(f.owner,"ROLE_USER");String code=userShipments.detail(id,0).trackingNumber();Long notice=inbox.list(0,50,false).items().getFirst().id();
        as(f.other,"ROLE_USER");assertEquals(404,assertThrows(ResponseStatusException.class,()->userShipments.detail(id,0)).getStatusCode().value());assertTrue(userShipments.list(code,null,org.springframework.data.domain.PageRequest.of(0,20)).isEmpty());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->customer.portalDetail(ticket,0)).getStatusCode().value());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->tickets.customerMessage(ticket,new CustomerTicketCommands.Message(ticketVersion(ticket),"Forged reply",token()))).getStatusCode().value());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->tickets.requestCustomerReopen(ticket,new CustomerTicketCommands.Message(ticketVersion(ticket),"Forged reopen",token()))).getStatusCode().value());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->inbox.target(notice)).getStatusCode().value());assertEquals(404,assertThrows(ResponseStatusException.class,()->inbox.read(notice)).getStatusCode().value());assertEquals(0,inbox.count());
        assertFalse(trackingService.lookup(code).orElseThrow().toString().contains("INTERNAL-PRIVATE-21"));assertEquals(1,ticketRows.count());assertEquals(2,messageRows.count());
    }
    @Test void multiBranchManagementAndRevocationUseDatabaseMembershipNotClientBranchSelection() {
        Long id=created(ShipmentMethod.BRANCH_DROPOFF);db(()->{f.membership(f.manager,f.hub);return null;});operator(f.source);assertEquals(2,managerRead.branches().size());
        as(f.outsider,"ROLE_MANAGER");long before=count(ShipmentEvent.class);assertThrows(AccessDeniedException.class,()->lifecycle.transition(new ShipmentTransitionCommand(id,shipmentVersion(id),ShipmentStatus.CONFIRMED)));assertEquals(before,count(ShipmentEvent.class));assertEquals(0,notices.count());
        db(()->{em.createQuery("delete from ManagerBranch m where m.manager.id=:id and m.branch.id=:branch").setParameter("id",f.manager.getId()).setParameter("branch",f.source.getId()).executeUpdate();return null;});operator(f.source);
        assertThrows(AccessDeniedException.class,()->lifecycle.transition(new ShipmentTransitionCommand(id,shipmentVersion(id),ShipmentStatus.CONFIRMED)));assertEquals(1,managerRead.branches().size());assertCustody(id,null,ShipmentStatus.CREATED);
    }
}
