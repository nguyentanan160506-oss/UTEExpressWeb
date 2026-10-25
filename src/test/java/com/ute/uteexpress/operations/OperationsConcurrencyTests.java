package com.ute.uteexpress.operations;

import com.ute.uteexpress.dto.operations.TicketCommands.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.notifications.NotificationDelivery;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import static com.ute.uteexpress.operations.OperationsJdbcFixture.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real Hibernate/JPA transactions and row locks on H2; this does not establish SQL Server isolation behavior. */
@SpringJUnitConfig(OperationsJdbcConfig.class)
class OperationsConcurrencyTests extends OperationsJdbcSupport {
    @Autowired NotificationDelivery delivery;
    Long version(Long id){return ticketRows.findById(id).orElseThrow().getVersion();}
    Long create(){return tickets.create(command(db(()->f.shipment(f.source,f.destination,ShipmentStatus.CREATED,NOW.minusHours(1)))));}
    void process(Long id){tickets.assign(id,new Assign(version(id),f.secondEmployee.getId(),null));tickets.changeStatus(id,new ChangeStatus(version(id),TicketStatus.PROCESSING,null));}
    void race(Runnable a,Runnable b)throws Exception{
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{var fa=pool.submit(()->runAs(f.manager,a,start));var fb=pool.submit(()->runAs(f.secondManager,b,start));start.countDown();var result=List.of(fa.get(8,TimeUnit.SECONDS),fb.get(8,TimeUnit.SECONDS));assertEquals(1,result.stream().filter(Boolean::booleanValue).count());}
        finally{pool.shutdownNow();}
    }
    boolean runAs(User actor,Runnable work,CountDownLatch start)throws Exception{as(actor,"ROLE_MANAGER");try{assertTrue(start.await(3,TimeUnit.SECONDS));work.run();return true;}catch(ObjectOptimisticLockingFailureException expected){return false;}finally{SecurityContextHolder.clearContext();}}
    @Test void twoManagersAssigningSameVersionHaveOneWinnerAndOneHistory()throws Exception{Long id=create(),v=version(id);race(()->tickets.assign(id,new Assign(v,f.managerEmployee.getId(),null)),()->tickets.assign(id,new Assign(v,f.secondEmployee.getId(),null)));assertEquals(2,eventRows.count());db(()->{assertEquals(1L,em.createQuery("select count(a) from SupportTicketAssignment a where a.endedAt is null",Long.class).getSingleResult());return null;});}
    @Test void concurrentPriorityEditsAreNotLastWriteWins()throws Exception{Long id=create(),v=version(id);race(()->tickets.classify(id,new Classify(v,TicketCategory.DELAY,TicketPriority.HIGH,"Cần ưu tiên")),()->tickets.classify(id,new Classify(v,TicketCategory.DELAY,TicketPriority.URGENT,"Cần ưu tiên")));assertEquals(2,eventRows.count());}
    @Test void duplicateResolveCreatesOneResolutionMessageAndEvent()throws Exception{Long id=create();process(id);Long v=version(id);race(()->tickets.resolve(id,new Decision(v,"Resolution A")),()->tickets.resolve(id,new Decision(v,"Resolution B")));assertEquals(1,messageRows.count());assertEquals(4,eventRows.count());assertEquals(TicketStatus.RESOLVED,ticketRows.findById(id).orElseThrow().getStatus());}
    @Test void closeRacingReplyDetectsConflictRatherThanAppendingToClosedTicket()throws Exception{Long id=create();process(id);tickets.resolve(id,new Decision(version(id),"Resolved"));Long v=version(id);long before=eventRows.count();race(()->tickets.close(id,new Decision(v,"Verified")),()->tickets.reply(id,new Message(v,"Additional reply")));assertEquals(before+1,eventRows.count());var status=ticketRows.findById(id).orElseThrow().getStatus();assertEquals(status==TicketStatus.CLOSED?1:2,messageRows.count());}
    @Test void closeRacingReopenHasOneConsistentStateAndAudit()throws Exception{Long id=create();process(id);tickets.resolve(id,new Decision(version(id),"Resolved"));Long v=version(id);long before=eventRows.count();race(()->tickets.close(id,new Decision(v,"Verified")),()->tickets.reopen(id,new Decision(v,"New facts")));assertEquals(before+1,eventRows.count());var t=ticketRows.findById(id).orElseThrow();if(t.getStatus()==TicketStatus.REOPENED){assertNull(t.getClosedAt());assertNull(t.getResolvedAt());}else{assertEquals(TicketStatus.CLOSED,t.getStatus());assertNotNull(t.getClosedAt());}}
    @Test void outerBusinessRollbackLeavesNoTicketEventMessageOrFakeNotification(){
        var s=db(()->f.shipment(f.source,f.destination,ShipmentStatus.CREATED,NOW));var c=command(s);
        assertThrows(IllegalStateException.class,()->db(()->{tickets.create(c);assertEquals(0,notices.count());throw new IllegalStateException("Synthetic rollback");}));assertEquals(0,ticketRows.count());assertEquals(0,eventRows.count());assertEquals(0,messageRows.count());assertEquals(0,notices.count());
    }
    @Test void jdbcAuditFailureRollsBackMessageAndRootVersionAtomically(){
        Long id=create(),v=version(id);db(()->{em.createNativeQuery("ALTER TABLE dbo.support_ticket_events ADD CONSTRAINT test_reject_note CHECK (action <> 'INTERNAL_NOTE')").executeUpdate();return null;});
        try{assertThrows(RuntimeException.class,()->tickets.note(id,new Message(v,"MUST ROLLBACK")));assertEquals(0,messageRows.count());assertEquals(1,eventRows.count());assertEquals(v,version(id));assertEquals(0,notices.count());}
        finally{db(()->{em.createNativeQuery("ALTER TABLE dbo.support_ticket_events DROP CONSTRAINT test_reject_note").executeUpdate();return null;});}
    }
    Long event(TicketAction action){return db(()->em.createQuery("select e.id from SupportTicketEvent e where e.action=:action",Long.class).setParameter("action",action).getSingleResult());}
    @Test void ticketNotificationIsDurablePrivateAndIdempotentWithoutSendingOnCreation(){
        Long id=create();tickets.assign(id,new Assign(version(id),f.secondEmployee.getId(),null));assertEquals(0,notices.count());Long event=event(TicketAction.ASSIGNED);notifications.process(event);notifications.process(event);assertEquals(1,notices.count());
        as(f.secondManager,"ROLE_MANAGER");assertEquals(1,notices.unreadCount(f.secondManager.getId()));var notice=notices.findVisibleById(notices.findAll().getFirst().getId(),f.secondManager.getId()).orElseThrow();assertEquals(NotificationType.SUPPORT,notice.getNotificationType());assertFalse(notice.getMessage().contains("Nội dung tiếp nhận"));
        delivery.deliver(notice.getId());assertNotNull(notices.findById(notice.getId()).orElseThrow().getDeliveredAt());
    }
    @Test void reassignmentNotifiesSnapshotOldAndNewManagersRatherThanOnlyCurrentAssignee(){Long id=create();tickets.assign(id,new Assign(version(id),f.secondEmployee.getId(),null));tickets.assign(id,new Assign(version(id),f.managerEmployee.getId(),"Transferred"));notifications.process(event(TicketAction.REASSIGNED));assertEquals(2,notices.count());assertEquals(1,notices.unreadCount(f.manager.getId()));assertEquals(1,notices.unreadCount(f.secondManager.getId()));}
    @Test void internalNotesNeverBecomeCustomerNotificationsAndPublicRepliesAreOwnerBound(){
        Long id=create();tickets.note(id,new Message(version(id),"INTERNAL SECRET TEST TEXT"));notifications.process(event(TicketAction.INTERNAL_NOTE));assertEquals(0,notices.count());tickets.reply(id,new Message(version(id),"PUBLIC BODY TEST"));notifications.process(event(TicketAction.PUBLIC_REPLY));assertEquals(1,notices.count());var n=notices.findAll().getFirst();assertEquals(f.owner.getId(),n.getUser().getId());assertEquals(0,notices.unreadCount(f.other.getId()));assertFalse(n.getMessage().contains("INTERNAL SECRET TEST TEXT"));assertFalse(n.getMessage().contains("PUBLIC BODY TEST"));
    }
    @Test void notificationVisibilityRechecksEmployeeAndMembershipAfterCreation(){Long id=create();tickets.assign(id,new Assign(version(id),f.secondEmployee.getId(),null));notifications.process(event(TicketAction.ASSIGNED));Long notice=notices.findAll().getFirst().getId();db(()->{em.find(Employee.class,f.secondEmployee.getId()).setStatus(EmployeeStatus.INACTIVE);return null;});assertEquals(0,notices.unreadCount(f.secondManager.getId()));assertTrue(notices.findVisibleById(notice,f.secondManager.getId()).isEmpty());}
    @Test void revokedAssigneeReceivesNothingWhenEventIsReconciled(){Long id=create();tickets.assign(id,new Assign(version(id),f.secondEmployee.getId(),null));db(()->{em.createQuery("delete from ManagerBranch m where m.manager.id=:id").setParameter("id",f.secondManager.getId()).executeUpdate();return null;});notifications.process(event(TicketAction.ASSIGNED));assertEquals(0,notices.count());}
    @Test void sourceWorkerFailureRollsBackPartialNoticesAndAckThenRetryRecovers(){
        Long id=create();tickets.assign(id,new Assign(version(id),f.secondEmployee.getId(),null));tickets.assign(id,new Assign(version(id),f.managerEmployee.getId(),"Transferred"));Long event=event(TicketAction.REASSIGNED);
        db(()->{em.createNativeQuery("ALTER TABLE dbo.notifications ADD CONSTRAINT test_reject_recipient CHECK (user_id <> "+f.secondManager.getId()+")").executeUpdate();return null;});
        try{assertThrows(RuntimeException.class,()->notifications.process(event));assertEquals(0,notices.count());assertNull(eventRows.findById(event).orElseThrow().getProcessedAt());notifications.retry(event);assertNotNull(eventRows.findById(event).orElseThrow().getRetryAt());}
        finally{db(()->{em.createNativeQuery("ALTER TABLE dbo.notifications DROP CONSTRAINT test_reject_recipient").executeUpdate();return null;});}
        notifications.process(event);assertEquals(0,notices.count());clock.set(NOW.plusSeconds(3));notifications.process(event);notifications.process(event);assertEquals(2,notices.count());assertNotNull(eventRows.findById(event).orElseThrow().getProcessedAt());
    }
    @Test void concurrentNotificationWorkersSerializeOnPersistedTicketEvent()throws Exception{
        Long id=create();tickets.assign(id,new Assign(version(id),f.secondEmployee.getId(),null));Long event=event(TicketAction.ASSIGNED);var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{var a=pool.submit(()->{start.await();notifications.process(event);return true;});var b=pool.submit(()->{start.await();notifications.process(event);return true;});start.countDown();assertTrue(a.get(8,TimeUnit.SECONDS));assertTrue(b.get(8,TimeUnit.SECONDS));assertEquals(1,notices.count());}finally{pool.shutdownNow();}
    }
    @Test void shipmentNotificationsStillVisibleAlongsideNewTicketNotifications(){
        Long id=create();tickets.reply(id,new Message(version(id),"Public response"));notifications.process(event(TicketAction.PUBLIC_REPLY));db(()->{var t=em.find(SupportTicket.class,id);var e=f.event(t.getShipment(),"Đã tạo vận đơn",null,ShipmentStatus.CREATED,NOW);em.flush();var n=new Notification();n.setUser(f.owner);n.setSourceEvent(e);n.setRelatedShipment(t.getShipment());n.setAudience("USER");n.setNotificationType(NotificationType.SHIPMENT_STATUS);n.setTitle("Shipment progress");n.setMessage("Existing shipment notice");em.persist(n);return null;});assertEquals(2,notices.unreadCount(f.owner.getId()));
    }
    @Test void membershipRevokedWhileWriterWaitsForParentLockPreventsMutation()throws Exception{
        Long id=create(),v=version(id);var locked=new CountDownLatch(1);var revoke=new CountDownLatch(1);var started=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var holder=pool.submit(()->db(()->{em.find(SupportTicket.class,id,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);locked.countDown();try{assertTrue(revoke.await(4,TimeUnit.SECONDS));}catch(InterruptedException e){throw new IllegalStateException(e);}em.createQuery("delete from ManagerBranch m where m.manager.id=:actor").setParameter("actor",f.secondManager.getId()).executeUpdate();return true;}));
            assertTrue(locked.await(3,TimeUnit.SECONDS));var writer=pool.submit(()->{as(f.secondManager,"ROLE_MANAGER");try{started.countDown();tickets.note(id,new Message(v,"Must not commit after revocation"));return true;}finally{SecurityContextHolder.clearContext();}});
            assertTrue(started.await(3,TimeUnit.SECONDS));Thread.sleep(100);assertFalse(writer.isDone());revoke.countDown();assertTrue(holder.get(5,TimeUnit.SECONDS));var failure=assertThrows(ExecutionException.class,()->writer.get(5,TimeUnit.SECONDS));assertTrue(failure.getCause() instanceof org.springframework.security.access.AccessDeniedException || failure.getCause() instanceof org.springframework.web.server.ResponseStatusException);assertEquals(0,messageRows.count());assertEquals(1,eventRows.count());
        }finally{revoke.countDown();pool.shutdownNow();}
    }

}
