package com.ute.uteexpress.operations;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.dto.operations.OperationsDashboardViews.*;
import com.ute.uteexpress.dto.operations.OperationsDashboardViews.Period;
import com.ute.uteexpress.dto.operations.TicketCommands.*;
import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.service.operations.*;
import com.ute.uteexpress.service.notifications.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.ute.uteexpress.operations.OperationsJdbcFixture.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(OperationsJdbcConfig.class)
class OperationsJdbcTests extends OperationsJdbcSupport {
    @Test void allDashboardQueriesExecuteAgainstJdbcOnEmptyOperationalData(){var d=dashboard.read(f.source.getId(),Period.TODAY,null,null,null,WorkGroup.CONFIRM,0);assertEquals(0,d.originTotal());assertNull(d.deliverySuccessRate());assertNull(d.returnOutcomeRate());assertTrue(d.durations().stream().allMatch(x->x.averageSeconds()==null && x.samples()==0));assertTrue(d.work().isEmpty());}
    @Test void actualTicketWritesPersistAuditAndSeparateInternalPublicMessages(){
        var s=db(()->f.shipment(f.source,f.destination,ShipmentStatus.CREATED,NOW.minusHours(1)));Long id=tickets.create(command(s));var t=ticketRows.findById(id).orElseThrow();assertEquals(f.owner.getId(),t.getUser().getId());assertEquals(f.manager.getId(),t.getCreatedBy().getId());assertEquals(TicketStatus.OPEN,t.getStatus());
        tickets.assign(id,new Assign(t.getVersion(),f.secondEmployee.getId(),null));t=ticketRows.findById(id).orElseThrow();assertEquals(TicketStatus.ASSIGNED,t.getStatus());tickets.changeStatus(id,new ChangeStatus(t.getVersion(),TicketStatus.PROCESSING,null));
        t=ticketRows.findById(id).orElseThrow();tickets.note(id,new Message(t.getVersion(),"INTERNAL PRIVATE NOTE"));t=ticketRows.findById(id).orElseThrow();tickets.reply(id,new Message(t.getVersion(),"PUBLIC RESPONSE"));
        var detail=read.detail(id,0,0,0);assertEquals(2,detail.messages().getTotalElements());assertEquals(5,detail.events().getTotalElements());
        as(f.owner,"ROLE_USER");var view=customer.detail(id,0);assertEquals(1,view.messages().getTotalElements());assertEquals("PUBLIC RESPONSE",view.messages().getContent().getFirst().body());assertFalse(view.toString().contains("INTERNAL PRIVATE NOTE"));
        db(()->{assertEquals(ShipmentStatus.CREATED,em.find(Shipment.class,s.getId()).getStatus());return null;});
    }
    Long version(Long id){return ticketRows.findById(id).orElseThrow().getVersion();}
    Long create(){return tickets.create(command(db(()->f.shipment(f.source,f.destination,ShipmentStatus.CREATED,NOW.minusHours(2)))));}
    void assigned(Long id){tickets.assign(id,new Assign(version(id),f.secondEmployee.getId(),null));}
    void processing(Long id){assigned(id);tickets.changeStatus(id,new ChangeStatus(version(id),TicketStatus.PROCESSING,null));}
    @Test void persistedDateBoundaryAndSelectedBranchDoNotDoubleCount(){
        db(()->{f.membership(f.manager,f.destination);f.shipment(f.source,f.source,ShipmentStatus.CREATED,NOW.toLocalDate().atStartOfDay());f.shipment(f.source,f.destination,ShipmentStatus.CONFIRMED,NOW);f.shipment(f.source,f.destination,ShipmentStatus.CREATED,NOW.toLocalDate().minusDays(1).atTime(23,59,59));f.shipment(f.source,f.destination,ShipmentStatus.CREATED,NOW.toLocalDate().plusDays(1).atStartOfDay());f.shipment(f.foreign,f.foreign,ShipmentStatus.CREATED,NOW);return null;});
        var d=dashboard.read(f.source.getId(),Period.TODAY,null,null,null,WorkGroup.CONFIRM,0);assertEquals(2,d.originTotal());assertEquals(1,d.destinationCohort());assertEquals(2,d.statuses().stream().mapToLong(Bar::value).sum());assertEquals(2,d.daily().stream().mapToLong(Bar::value).sum());
        assertEquals(1,dashboard.read(f.source.getId(),Period.TODAY,null,null,ShipmentStatus.CONFIRMED,WorkGroup.CONFIRM,0).originTotal());
    }
    @Test void failedAttemptsAndFailedShipmentsHaveDifferentDenominatorsAndProofIsMandatory(){
        db(()->{var s=f.shipment(f.source,f.destination,ShipmentStatus.DELIVERED,NOW.minusDays(4));var a=f.assignment(s,f.shipper);for(int n=1;n<=3;n++)f.attempt(s,a,n,DeliveryAttemptStatus.FAILED,NOW.minusHours(4-n));var success=f.attempt(s,a,4,DeliveryAttemptStatus.SUCCESS,NOW);f.proof(s,a,success,NOW);a.setStatus(AssignmentStatus.COMPLETED);a.setCompletedAt(NOW);f.shipment(f.source,f.destination,ShipmentStatus.DELIVERED,NOW);return null;});
        as(f.destinationManager,"ROLE_MANAGER");var d=dashboard.read(f.destination.getId(),Period.TODAY,null,null,null,WorkGroup.FAILED,0);assertEquals(1,d.deliverySuccess());assertEquals(1,d.failedShipments());assertEquals(3,d.failedAttempts());assertEquals(new java.math.BigDecimal("25.00"),d.deliverySuccessRate());assertEquals(3,d.failureReasons().getFirst().value());
    }
    @Test void returnRequestIsNotACompletedReturnAndReturnedIsNotDelivered(){
        db(()->{var s=f.shipment(f.source,f.destination,ShipmentStatus.RETURNING,NOW.minusDays(2));var r=new ReturnRequest();r.setShipment(s);r.setRequestedBy(f.destinationManager);r.setReason("Synthetic return request");r.setStage(ReturnStage.REQUESTED);r.setRequestedAt(NOW);em.persist(r);
            var done=f.shipment(f.source,f.destination,ShipmentStatus.RETURNED,NOW.minusDays(2));done.setReturnedAt(NOW);var rr=new ReturnRequest();rr.setShipment(done);rr.setRequestedBy(f.destinationManager);rr.setReason("Synthetic completed return");rr.setStage(ReturnStage.COMPLETED);rr.setRequestedAt(NOW.minusHours(1));rr.setCompletedAt(NOW);em.persist(rr);var receipt=new ReturnReceipt();receipt.setReturnRequest(rr);receipt.setActor(f.manager);receipt.setEmployee(f.managerEmployee);receipt.setBranch(f.source);receipt.setRecipientType(ReturnRecipientType.SENDER);receipt.setRecipientName("Synthetic sender");receipt.setReference("TEST-RETURN");receipt.setReceivedAt(NOW);em.persist(receipt);return null;});
        var source=dashboard.read(f.source.getId(),Period.TODAY,null,null,null,WorkGroup.RETURNS,0);assertEquals(1,source.returned());assertEquals(0,source.deliverySuccess());assertEquals(new java.math.BigDecimal("100.00"),source.returnOutcomeRate());
        as(f.destinationManager,"ROLE_MANAGER");var dest=dashboard.read(f.destination.getId(),Period.TODAY,null,null,null,WorkGroup.RETURNS,0);assertEquals(2,dest.returnRequests());assertEquals(0,dest.returned());assertNull(dest.returnOutcomeRate());
    }
    @Test void custodyRequiresReceiptAndBacklogUsesExplicitAgeNotFabricatedSla(){
        db(()->{var received=f.shipment(f.source,f.destination,ShipmentStatus.AT_ORIGIN_BRANCH,NOW.minusDays(2));f.intake(received,f.source,NOW.minusDays(1));var invalid=f.shipment(f.source,f.destination,ShipmentStatus.AT_ORIGIN_BRANCH,NOW.minusDays(2));invalid.setCurrentBranch(f.source);f.shipment(f.source,f.destination,ShipmentStatus.CREATED,NOW.minusDays(2));return null;});
        var d=dashboard.read(f.source.getId(),Period.TODAY,null,null,null,WorkGroup.DISPATCH,0);assertEquals(0,d.originTotal());assertEquals(1,d.held());assertEquals(1,d.work().getTotalElements());assertEquals(1,d.queues().stream().filter(q->q.group()==WorkGroup.CONFIRM).findFirst().orElseThrow().overThreshold());assertEquals(86400,d.warningAgeSeconds());
    }
    @Test void durationsUseActualEndpointsAndMissingRecordsRemainNotAvailable(){
        db(()->{var s=f.shipment(f.source,f.destination,ShipmentStatus.AT_ORIGIN_BRANCH,NOW.minusHours(3));f.event(s,"Trạng thái: Đã xác nhận",ShipmentStatus.CREATED,ShipmentStatus.CONFIRMED,NOW.minusHours(2));f.intake(s,f.source,NOW.minusHours(1));return null;});
        var d=dashboard.read(f.source.getId(),Period.TODAY,null,null,null,WorkGroup.DISPATCH,0);assertEquals(3600.0,d.durations().get(0).averageSeconds());assertEquals(3600.0,d.durations().get(1).averageSeconds());assertEquals(1,d.durations().get(1).samples());assertNull(d.durations().get(2).averageSeconds());
    }
    @Test void dashboardDoesNotWriteOrLoadShipmentEntitiesForAggregation(){
        db(()->{for(int n=0;n<32;n++)f.shipment(f.source,f.destination,ShipmentStatus.CREATED,NOW);return null;});
        var statistics=emf.unwrap(org.hibernate.SessionFactory.class).getStatistics();statistics.clear();dashboard.read(f.source.getId(),Period.TODAY,null,null,null,WorkGroup.CONFIRM,0);
        assertEquals(0,statistics.getEntityInsertCount());assertEquals(0,statistics.getEntityUpdateCount());assertEquals(0,statistics.getEntityDeleteCount());assertEquals(0,statistics.getEntityStatistics(Shipment.class.getName()).getLoadCount());assertEquals(0,notices.count());assertEquals(0,eventRows.count());
    }
    @Test void manualCreationIsIdempotentAndDoesNotPretendCustomerSubmitted(){
        var s=db(()->f.shipment(f.source,f.destination,ShipmentStatus.DELIVERED,NOW));var c=command(s);Long id=tickets.create(c);assertEquals(id,tickets.create(c));assertEquals(1,ticketRows.count());var detail=read.detail(id,0,0,0);assertEquals(TicketSource.MANAGER_PHONE,detail.source());assertEquals(1,detail.events().getTotalElements());assertEquals(f.manager.getUsername(),detail.events().getContent().getFirst().actor());
        var e=eventRows.findAll().getFirst();notifications.process(e.getId());assertEquals(0,notices.count());
    }
    @Test void manualSourceAndShipmentScopeCannotBeForged(){
        var s=db(()->f.shipment(f.foreign,f.foreign,ShipmentStatus.CREATED,NOW));assertThrows(OperationsRuleException.class,()->tickets.create(command(s)));assertEquals(0,ticketRows.count());
        var owned=db(()->f.shipment(f.source,f.destination,ShipmentStatus.CREATED,NOW));var c=command(owned);assertThrows(OperationsRuleException.class,()->tickets.create(new Create(c.branchId(),c.trackingNumber(),c.subject(),c.content(),c.category(),c.priority(),TicketSource.CUSTOMER,c.token())));assertEquals(0,ticketRows.count());
    }
    @Test void fullStateMachineRetainsMessagesAndHistoryWithoutTouchingShipment(){
        Long id=create();processing(id);tickets.changeStatus(id,new ChangeStatus(version(id),TicketStatus.WAITING_CUSTOMER,null));tickets.changeStatus(id,new ChangeStatus(version(id),TicketStatus.PROCESSING,null));
        tickets.resolve(id,new Decision(version(id),"PUBLIC RESOLUTION"));assertNotNull(ticketRows.findById(id).orElseThrow().getResolvedAt());tickets.close(id,new Decision(version(id),"Đã xác nhận hoàn tất xử lý"));assertEquals(TicketStatus.CLOSED,ticketRows.findById(id).orElseThrow().getStatus());
        long count=eventRows.count(),messages=messageRows.count();assertThrows(OperationsRuleException.class,()->tickets.note(id,new Message(version(id),"No closed notes")));assertEquals(count,eventRows.count());assertEquals(messages,messageRows.count());
        tickets.reopen(id,new Decision(version(id),"Có thông tin mới cần kiểm tra"));var t=ticketRows.findById(id).orElseThrow();assertEquals(TicketStatus.REOPENED,t.getStatus());assertNull(t.getResolvedAt());assertNull(t.getClosedAt());assertEquals(count+1,eventRows.count());assertEquals(messages,messageRows.count());
    }
    @Test void reassignmentClosesHistoryAndConflictingVersionsDoNotOverwrite(){
        Long id=create();assigned(id);Long old=version(id);tickets.assign(id,new Assign(old,f.managerEmployee.getId(),"Chuyển người phụ trách"));
        assertThrows(org.springframework.orm.ObjectOptimisticLockingFailureException.class,()->tickets.note(id,new Message(old,"Stale note")));assertEquals(0,messageRows.count());
        db(()->{var all=em.createQuery("select a from SupportTicketAssignment a order by a.id",SupportTicketAssignment.class).getResultList();assertEquals(2,all.size());assertNotNull(all.getFirst().getEndedAt());assertEquals(f.manager.getId(),all.getFirst().getEndedBy().getId());assertNull(all.getLast().getEndedAt());return null;});
    }
    @Test void metadataEditsAreVersionedAuditedAndDoNotMoveDeadline(){
        Long id=create();var due=ticketRows.findById(id).orElseThrow().getWarningDueAt();Long v=version(id);tickets.classify(id,new Classify(v,TicketCategory.SHIPPING_FEE,TicketPriority.URGENT,"Phân loại đúng nội dung tiếp nhận"));assertEquals(3,eventRows.count());assertEquals(due,ticketRows.findById(id).orElseThrow().getWarningDueAt());assertThrows(org.springframework.orm.ObjectOptimisticLockingFailureException.class,()->tickets.classify(id,new Classify(v,TicketCategory.OTHER,TicketPriority.LOW,"Stale")));
    }
    @Test void exactDeadlineBoundaryAndClosedTicketsAreNotFalseOverdue(){
        Long id=create();var due=ticketRows.findById(id).orElseThrow().getWarningDueAt();clock.set(due);assertFalse(read.detail(id,0,0,0).ticket().overdue());clock.set(due.plusNanos(100));assertTrue(read.detail(id,0,0,0).ticket().overdue());processing(id);tickets.resolve(id,new Decision(version(id),"Đã xử lý"));assertFalse(read.detail(id,0,0,0).ticket().overdue());
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"employee","user","role","branch","membership"})
    void invalidAssigneesAreRejectedAtomically(String mode){
        Long id=create();db(()->{switch(mode){case "employee"->em.find(Employee.class,f.secondEmployee.getId()).setStatus(EmployeeStatus.INACTIVE);case "user"->em.find(User.class,f.secondManager.getId()).setStatus(UserStatus.LOCKED);case "role"->em.createQuery("delete from UserRole r where r.user.id=:id").setParameter("id",f.secondManager.getId()).executeUpdate();case "branch"->em.find(Employee.class,f.secondEmployee.getId()).setBranch(f.foreign);case "membership"->em.createQuery("delete from ManagerBranch m where m.manager.id=:id").setParameter("id",f.secondManager.getId()).executeUpdate();}return null;});
        assertThrows(OperationsRuleException.class,()->tickets.assign(id,new Assign(version(id),f.secondEmployee.getId(),null)));assertEquals(TicketStatus.OPEN,ticketRows.findById(id).orElseThrow().getStatus());assertEquals(1,eventRows.count());
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"notes","reply","assign","status","resolve","close","reopen","classify"})
    void managerCannotMutateOrReadOtherBranchTicketsById(String operation){
        Long id=create();Long v=version(id);as(f.outsider,"ROLE_MANAGER");assertThrows(org.springframework.web.server.ResponseStatusException.class,()->{switch(operation){case "notes"->tickets.note(id,new Message(v,"Private"));case "reply"->tickets.reply(id,new Message(v,"Private"));case "assign"->tickets.assign(id,new Assign(v,f.outsiderEmployee.getId(),null));case "status"->tickets.changeStatus(id,new ChangeStatus(v,TicketStatus.PROCESSING,null));case "resolve"->tickets.resolve(id,new Decision(v,"Private"));case "close"->tickets.close(id,new Decision(v,"Private"));case "reopen"->tickets.reopen(id,new Decision(v,"Private"));case "classify"->tickets.classify(id,new Classify(v,TicketCategory.OTHER,TicketPriority.LOW,"Private"));}});assertThrows(org.springframework.web.server.ResponseStatusException.class,()->read.detail(id,0,0,0));assertEquals(1,eventRows.count());
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"employee","user","role","membership"})
    void currentManagerRightsAreCheckedAgainstDatabase(String mode){
        Long id=create();db(()->{switch(mode){case "employee"->em.find(Employee.class,f.managerEmployee.getId()).setStatus(EmployeeStatus.INACTIVE);case "user"->em.find(User.class,f.manager.getId()).setStatus(UserStatus.LOCKED);case "role"->em.createQuery("delete from UserRole r where r.user.id=:id").setParameter("id",f.manager.getId()).executeUpdate();case "membership"->em.createQuery("delete from ManagerBranch m where m.manager.id=:id").setParameter("id",f.manager.getId()).executeUpdate();}return null;});assertThrows(org.springframework.security.access.AccessDeniedException.class,()->dashboard.read(f.source.getId(),Period.TODAY,null,null,null,WorkGroup.CONFIRM,0));assertThrows(RuntimeException.class,()->tickets.note(id,new Message(version(id),"No permission")));assertEquals(1,eventRows.count());
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"ROLE_USER","ROLE_ADMIN","ROLE_SHIPPER"})
    void otherRolesDoNotImplicitlyHaveManagerAuthority(String role){var s=db(()->f.shipment(f.source,f.destination,ShipmentStatus.CREATED,NOW));as(f.admin,role);assertThrows(org.springframework.security.access.AccessDeniedException.class,()->tickets.create(command(s)));assertThrows(org.springframework.security.access.AccessDeniedException.class,()->dashboard.read(f.source.getId(),Period.TODAY,null,null,null,WorkGroup.CONFIRM,0));}
    @Test void customerProjectionCannotEnumerateAnotherOwnerOrLegacyUnscopedTicket(){Long id=create();as(f.other,"ROLE_USER");assertThrows(org.springframework.web.server.ResponseStatusException.class,()->customer.detail(id,0));}
    @Test void noBareResolvedOrCloseStatusCanBypassRequiredDecisionText(){Long id=create();assertThrows(OperationsRuleException.class,()->tickets.changeStatus(id,new ChangeStatus(version(id),TicketStatus.RESOLVED,null)));assigned(id);assertThrows(jakarta.validation.ConstraintViolationException.class,()->tickets.resolve(id,new Decision(version(id)," ")));assertThrows(OperationsRuleException.class,()->tickets.close(id,new Decision(version(id),"No result")));assertEquals(2,eventRows.count());}

    @Test void departedTripDoesNotMeanDestinationReceiptAndDiscrepancyIsSeparate(){
        Long[] ids=db(()->{f.membership(f.manager,f.hub);var hubManager=f.user("hub-manager","ROLE_MANAGER");var staff=f.employee(hubManager,f.hub,EmployeePosition.DISPATCHER);f.membership(hubManager,f.hub);em.find(Branch.class,f.source.getId()).setParentHub(f.hub);
            var s=f.shipment(f.source,f.destination,ShipmentStatus.IN_TRANSIT,NOW.minusDays(1));var route=new TransportRoute();route.setOrigin(f.source);route.setDestination(f.hub);route.setLeg(TransportLeg.COLLECTION);em.persist(route);
            var t=new TransportTrip();t.setRoute(route);t.setOrigin(f.source);t.setDestination(f.hub);t.setLeg(TransportLeg.COLLECTION);t.setCode("TESTTRIP");t.setRequestToken(UUID.randomUUID().toString().replace("-",""));t.setCreatedBy(f.manager);t.setCreatedAt(NOW.minusHours(4));t.setSealedBy(f.manager);t.setSealedAt(NOW.minusHours(3));t.setExpectedCount(1);t.setDepartedBy(f.manager);t.setDepartedEmployee(f.managerEmployee);t.setDepartedAt(NOW.minusHours(2));t.setStatus(TripStatus.DEPARTED);em.persist(t);
            var item=new TransportTripItem();item.setTrip(t);item.setShipment(s);item.setAddedAt(NOW.minusHours(4));em.persist(item);em.flush();return new Long[]{s.getId(),t.getId(),item.getId(),staff.getId(),hubManager.getId()};});
        assertEquals(1,dashboard.read(f.source.getId(),Period.TODAY,null,null,null,WorkGroup.DISPATCH,0).unreceivedDeparted());var arriving=dashboard.read(f.hub.getId(),Period.TODAY,null,null,null,WorkGroup.DISPATCH,0);assertEquals(0,arriving.held());assertEquals(0,arriving.awaitingReceipt());
        db(()->{var t=em.find(TransportTrip.class,ids[1]);t.setStatus(TripStatus.RECONCILING);t.setArrivedBy(em.find(User.class,ids[4]));t.setArrivedAt(NOW.minusHours(1));var d=new TripDiscrepancy();d.setTrip(t);d.setKind(TripDiscrepancyKind.UNEXPECTED);d.setActor(em.find(User.class,ids[4]));d.setEmployee(em.find(Employee.class,ids[3]));d.setReference("TEST-EXTRA");d.setNotes("Synthetic discrepancy");d.setRecordedAt(NOW);em.persist(d);return null;});
        var pending=dashboard.read(f.hub.getId(),Period.TODAY,null,null,null,WorkGroup.DISPATCH,0);assertEquals(1,pending.awaitingReceipt());assertEquals(0,pending.held());assertEquals(1,pending.openDiscrepancies());
        db(()->{var item=em.find(TransportTripItem.class,ids[2]);var receipt=new TripReceipt();receipt.setItem(item);receipt.setBranch(f.hub);receipt.setActor(em.find(User.class,ids[4]));receipt.setEmployee(em.find(Employee.class,ids[3]));receipt.setReference("ACTUAL-RECEIPT");receipt.setReceivedAt(NOW);em.persist(receipt);item.setActive(false);item.setStatus(TripItemStatus.RECEIVED);var s=em.find(Shipment.class,ids[0]);s.setStatus(ShipmentStatus.AT_ORIGIN_HUB);s.setCurrentBranch(f.hub);return null;});
        var received=dashboard.read(f.hub.getId(),Period.TODAY,null,null,null,WorkGroup.DISPATCH,0);assertEquals(1,received.held());assertEquals(0,received.awaitingReceipt());assertEquals(7200.0,received.durations().get(2).averageSeconds());assertEquals(1,received.openDiscrepancies());
    }
    @Test void spoofedSessionManagerRoleIsNotEnoughWithoutDatabaseRole(){as(f.admin,"ROLE_MANAGER");assertThrows(org.springframework.security.access.AccessDeniedException.class,()->access.scope(f.source.getId()));}
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({"-1,20,createdAt","0,0,createdAt","0,51,createdAt","0,20,passwordHash","0,20,createdAt;DROP"})
    void unsafeOrUnboundedTicketFiltersAreRejected(int page,int size,String sort){assertThrows(OperationsRuleException.class,()->read.list(f.source.getId(),null,null,null,null,false,false,page,size,sort));}

    @Test void creationRetryUsesOriginalFingerprintEvenAfterPriorityChanged(){var s=db(()->f.shipment(f.source,f.destination,ShipmentStatus.CREATED,NOW));var c=command(s);Long id=tickets.create(c);tickets.classify(id,new Classify(version(id),TicketCategory.OTHER,TicketPriority.URGENT,"Updated classification"));assertEquals(id,tickets.create(c));assertEquals(1,ticketRows.count());assertThrows(OperationsRuleException.class,()->tickets.create(new Create(c.branchId(),c.trackingNumber(),"Different payload",c.content(),c.category(),c.priority(),c.source(),c.token())));}
    @Test void newTicketCodesAreUniqueAndLegacyRowsAreRetainedWithoutInventedScope(){
        var s=db(()->f.shipment(f.source,f.destination,ShipmentStatus.CREATED,NOW));var codes=new HashSet<String>();for(int n=0;n<8;n++){Long id=tickets.create(command(s));assertTrue(codes.add(ticketRows.findById(id).orElseThrow().getTicketCode()));}
        Long legacy=db(()->{SupportTicket t=new SupportTicket();t.setUser(f.owner);t.setTicketCode("LEGACY-TICKET");t.setSubject("Legacy");t.setContent("Existing historical record");t.setStatus(TicketStatus.PROCESSING);em.persist(t);em.flush();return t.getId();});assertEquals(9,ticketRows.count());assertThrows(org.springframework.web.server.ResponseStatusException.class,()->read.detail(legacy,0,0,0));assertEquals(8,read.list(f.source.getId(),null,null,null,null,false,false,0,20,"createdAt").getTotalElements());
    }

    @Test void messageTimelineAndTicketFiltersAreActuallyPagedAndStablyOrdered(){
        Long id=create();assigned(id);tickets.classify(id,new Classify(version(id),TicketCategory.SHIPPING_FEE,TicketPriority.HIGH,"Correct category"));for(int n=0;n<25;n++)tickets.note(id,new Message(version(id),"Note "+n));
        var first=read.detail(id,0,0,0);var second=read.detail(id,1,1,0);assertEquals(25,first.messages().getTotalElements());assertEquals(20,first.messages().getNumberOfElements());assertEquals(5,second.messages().getNumberOfElements());assertEquals("Note 24",first.messages().getContent().getFirst().body());assertEquals("Note 4",second.messages().getContent().getFirst().body());
        assertEquals(1,read.list(f.source.getId(),TicketStatus.ASSIGNED,TicketPriority.HIGH,TicketCategory.SHIPPING_FEE,f.secondManager.getId(),false,false,0,20,"createdAt").getTotalElements());assertEquals(0,read.list(f.source.getId(),TicketStatus.OPEN,null,null,null,false,false,0,20,"createdAt").getTotalElements());
    }

    @Test void mismatchedProofAssignmentOrActorIsNotAConfirmedDeliveryOutcome(){
        db(()->{var s=f.shipment(f.source,f.destination,ShipmentStatus.DELIVERED,NOW);var first=f.assignment(s,f.shipper);first.setStatus(AssignmentStatus.COMPLETED);first.setCompletedAt(NOW);var attempt=f.attempt(s,first,1,DeliveryAttemptStatus.SUCCESS,NOW);
            var user=f.user("another-courier","ROLE_SHIPPER");f.employee(user,f.destination,EmployeePosition.COURIER_DRIVER);em.flush();var second=f.assignment(s,user);second.setStatus(AssignmentStatus.COMPLETED);second.setCompletedAt(NOW);f.proof(s,second,attempt,NOW);return null;});
        as(f.destinationManager,"ROLE_MANAGER");var d=dashboard.read(f.destination.getId(),Period.TODAY,null,null,null,WorkGroup.DELIVERY,0);assertEquals(0,d.deliverySuccess());assertNull(d.deliverySuccessRate());
    }

}
