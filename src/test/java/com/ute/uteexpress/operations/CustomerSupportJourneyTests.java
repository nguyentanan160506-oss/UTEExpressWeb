package com.ute.uteexpress.operations;

import com.ute.uteexpress.dto.operations.CustomerTicketCommands;
import com.ute.uteexpress.dto.operations.TicketCommands;
import com.ute.uteexpress.dto.operations.OperationsDashboardViews.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.notifications.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.server.ResponseStatusException;
import static com.ute.uteexpress.operations.OperationsJdbcFixture.*;
import static org.junit.jupiter.api.Assertions.*;

/** Full service/JDBC journeys with real outbox polling; broker transport is an in-process channel. */
@SpringJUnitConfig(CustomerJourneyConfig.class)
class CustomerSupportJourneyTests extends CustomerJourneySupport {
    @Autowired TicketNotificationWorker ticketWorker;
    @Autowired NotificationWorker shipmentWorker;
    @Autowired SimpMessagingTemplate broker;
    @Autowired List<Message<?>> socketSignals;

    Long ticket() {
        Long shipment = create();
        db(() -> { var route = new SupportTicketRoute(); route.setOriginBranch(f.source); route.setHandlingBranch(f.destination); em.persist(route); return null; });
        return tickets.createCustomer(new CustomerTicketCommands.Create(shipment,TicketCategory.DELAY,"Customer issue","Customer initial description",token()));
    }
    String token() { return UUID.randomUUID().toString().replace("-", ""); }
    Long version(Long id) { return ticketRows.findById(id).orElseThrow().getVersion(); }
    Long event(Long id, TicketAction action) { return db(() -> em.createQuery("select e.id from SupportTicketEvent e where e.ticket.id=:id and e.action=:action order by e.id desc",Long.class)
        .setParameter("id",id).setParameter("action",action).setMaxResults(1).getSingleResult()); }
    void process() { ticketWorker.poll(); }

    @Test void customerShipmentTicketDatabaseRoutingManagerConversationResolutionAndReopenJourney() {
        Long id=ticket(); Long shipment=ticketRows.findById(id).orElseThrow().getShipment().getId();
        assertEquals(f.destination.getId(),ticketRows.findById(id).orElseThrow().getHandlingBranch().getId());
        process(); assertEquals(1,notices.count()); assertEquals(f.destinationManager.getId(),notices.findAll().getFirst().getUser().getId());
        as(f.manager,"ROLE_MANAGER"); assertEquals(404,assertThrows(ResponseStatusException.class,()->read.detail(id,0,0,0)).getStatusCode().value());
        as(f.destinationManager,"ROLE_MANAGER");
        assertEquals(1,dashboard.read(f.destination.getId(),Period.TODAY,null,null,null,WorkGroup.CONFIRM,0).openUnassignedTickets());
        tickets.assign(id,new TicketCommands.Assign(version(id),f.destinationEmployee.getId(),null));
        tickets.changeStatus(id,new TicketCommands.ChangeStatus(version(id),TicketStatus.PROCESSING,null));
        tickets.note(id,new TicketCommands.Message(version(id),"PRIVATE-INVESTIGATION"));
        tickets.reply(id,new TicketCommands.Message(version(id),"Public answer"));
        tickets.resolve(id,new TicketCommands.Decision(version(id),"Public resolution"));
        tickets.close(id,new TicketCommands.Decision(version(id),"PRIVATE-CLOSING-REASON")); process();
        as(f.owner,"ROLE_USER"); var detail=customer.portalDetail(id,0);
        assertEquals(TicketStatus.CLOSED,detail.ticket().status()); assertEquals("Public resolution",detail.resolution().body());
        assertFalse(detail.toString().contains("PRIVATE-")); assertFalse(inbox.list(0,50,false).toString().contains("PRIVATE-"));
        var request=new CustomerTicketCommands.Message(version(id),"Please review the resolution",token());
        tickets.requestCustomerReopen(id,request); tickets.requestCustomerReopen(id,request); process();
        assertEquals(TicketStatus.CLOSED,ticketRows.findById(id).orElseThrow().getStatus());
        assertTrue(customer.portalDetail(id,0).reopenPending());
        Long requestEvent=event(id,TicketAction.REOPEN_REQUESTED);
        assertEquals(1,notices.findAll().stream().filter(n->n.getSourceTicketEvent()!=null && n.getSourceTicketEvent().getId().equals(requestEvent)).count());
        as(f.destinationManager,"ROLE_MANAGER"); tickets.reopen(id,new TicketCommands.Decision(version(id),"Review accepted")); process();
        as(f.owner,"ROLE_USER"); assertEquals(TicketStatus.REOPENED,customer.portalDetail(id,0).ticket().status());
        assertFalse(customer.portalDetail(id,0).reopenPending()); assertEquals(ShipmentStatus.CREATED,userShipments.detail(shipment,0).status());
        db(()->{var e=em.find(SupportTicketEvent.class,requestEvent);assertEquals(f.owner.getId(),e.getActor().getId());return null;});
        for(var item:inbox.list(0,50,false).items()) assertEquals("/account/support/"+id,inbox.target(item.id()));
        as(f.other,"ROLE_USER"); assertEquals(0,inbox.count()); assertEquals(404,assertThrows(ResponseStatusException.class,()->customer.portalDetail(id,0)).getStatusCode().value());
    }
    @Test void ticketOutboxDatabaseFailureRetriesAtControlledClockWithoutDuplicatesOrLostEvents() {
        Long id=ticket(); Long event=event(id,TicketAction.CREATED);
        constraint("ALTER TABLE dbo.notifications ADD CONSTRAINT TEST_NOTICE_FAILURE CHECK (1=0)");
        try { process(); assertEquals(0,notices.count()); var e=eventRows.findById(event).orElseThrow();assertNull(e.getProcessedAt());assertEquals(1,e.getAttempts());
            process();assertEquals(1,eventRows.findById(event).orElseThrow().getAttempts());
        } finally {constraint("ALTER TABLE dbo.notifications DROP CONSTRAINT TEST_NOTICE_FAILURE");}
        clock.set(NOW.plusSeconds(2));process();assertEquals(1,notices.count());assertNotNull(eventRows.findById(event).orElseThrow().getProcessedAt());
        process();assertEquals(1,notices.count());assertEquals(1,eventRows.count());
    }
    @Test void disconnectedBrokerDoesNotUndoCommittedTicketReplyAndPersistentNoticeCanBeReconciled() {
        Long id=ticket();process();
        as(f.destinationManager,"ROLE_MANAGER");tickets.reply(id,new TicketCommands.Message(version(id),"Public reply"));process();
        Long notice=notices.findAll().stream().filter(n->"USER".equals(n.getAudience())).findFirst().orElseThrow().getId();
        var channel=(SubscribableChannel)broker.getMessageChannel(); MessageHandler unavailable=message->{throw new MessageDeliveryException("Synthetic channel unavailable");};
        socketSignals.clear();channel.subscribe(unavailable);
        try {assertThrows(MessageDeliveryException.class,()->delivery.deliver(notice));}
        finally {channel.unsubscribe(unavailable);}
        assertNull(notices.findById(notice).orElseThrow().getDeliveredAt());
        assertEquals(1,messageRows.count());as(f.owner,"ROLE_USER");assertEquals(1,inbox.count());
        assertEquals("Public reply",customer.portalDetail(id,0).messages().getContent().getFirst().body());
        socketSignals.clear();delivery.deliver(notice);delivery.deliver(notice);
        assertEquals(1,socketSignals.size());assertEquals(Map.of("type","REFRESH"),socketSignals.getFirst().getPayload());
        assertNotNull(notices.findById(notice).orElseThrow().getDeliveredAt());
    }
    @Test void shipmentWorkerDatabaseRetryPersistsOfflineNoticeAndDoesNotCreateRecursiveEvents() {
        Long id=create();long eventCount=count(ShipmentEvent.class);
        constraint("ALTER TABLE dbo.notifications ADD CONSTRAINT TEST_SHIPMENT_NOTICE_FAILURE CHECK (1=0)");
        try {shipmentWorker.poll();assertEquals(0,notices.count());}
        finally {constraint("ALTER TABLE dbo.notifications DROP CONSTRAINT TEST_SHIPMENT_NOTICE_FAILURE");}
        clock.set(NOW.plusSeconds(2));shipmentWorker.poll();long n=notices.count();assertTrue(n>0);
        shipmentWorker.poll();assertEquals(n,notices.count());assertEquals(eventCount,count(ShipmentEvent.class));
        as(f.owner,"ROLE_USER");var own=inbox.list(0,20,false).items().getFirst();assertEquals("/user/shipments/"+id,inbox.target(own.id()));
        as(f.other,"ROLE_USER");assertEquals(0,inbox.count());assertEquals(404,assertThrows(ResponseStatusException.class,()->inbox.read(own.id())).getStatusCode().value());
    }
}
