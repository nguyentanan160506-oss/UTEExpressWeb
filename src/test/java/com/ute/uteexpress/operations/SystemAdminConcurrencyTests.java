package com.ute.uteexpress.operations;

import com.ute.uteexpress.dto.admin.ReportViews;
import com.ute.uteexpress.dto.operations.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.AdminRuleException;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import static com.ute.uteexpress.operations.OperationsJdbcFixture.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(SystemSecurityIntegrationConfig.class)
class SystemAdminConcurrencyTests extends SystemSecuritySupport {
    record Call(User user,String role,Runnable action) { }
    Call admin(User user,Runnable action) {return new Call(user,"ROLE_ADMIN",action);}
    int pair(Call a,Call b) throws Exception {
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var results=new ArrayList<Future<Boolean>>();for(var call:List.of(a,b))results.add(pool.submit(()->{
                as(call.user(),call.role());ready.countDown();
                try {assertTrue(start.await(5,TimeUnit.SECONDS));call.action().run();return true;}
                catch(AdminRuleException|AccessDeniedException|ObjectOptimisticLockingFailureException expectedConflict){return false;}
                finally {SecurityContextHolder.clearContext();}
            }));
            assertTrue(ready.await(5,TimeUnit.SECONDS));start.countDown();int successes=0;for(var result:results)if(result.get(15,TimeUnit.SECONDS))successes++;return successes;
        } finally {start.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}
    }
    long activeAdmins() {return db(()->em.createQuery("select count(r) from UserRole r where r.role.name='ROLE_ADMIN' and r.user.status=:state",Long.class).setParameter("state",UserStatus.ACTIVE).getSingleResult());}
    @Test void concurrentLockUnlockAuditsOnlySuccessfulTransitionsAndRetainsAllRoles() throws Exception {
        int successes=pair(admin(f.admin,()->adminUsers.lock(f.owner.getId())),admin(secondAdmin,()->adminUsers.unlock(f.owner.getId())));
        assertTrue(successes==1||successes==2);assertEquals(successes,auditRows.count());assertEquals(successes==2?UserStatus.ACTIVE:UserStatus.LOCKED,db(()->em.find(User.class,f.owner.getId()).getStatus()));assertEquals(List.of("ROLE_USER"),roles(f.owner.getId()));
        assertEquals("USER_LOCKED",auditRows.findAll(org.springframework.data.domain.Sort.by("id")).getFirst().getAction());
    }
    @Test void concurrentDifferentRoleSelectionsCommitWholeSetsWithCorrespondingAudit() throws Exception {
        var managers=selected("ROLE_USER","ROLE_MANAGER");var shippers=selected("ROLE_USER","ROLE_SHIPPER");
        assertEquals(2,pair(admin(f.admin,()->adminUsers.updateRoles(f.owner.getId(),managers)),admin(secondAdmin,()->adminUsers.updateRoles(f.owner.getId(),shippers))));
        assertTrue(Set.of(List.of("ROLE_MANAGER","ROLE_USER"),List.of("ROLE_SHIPPER","ROLE_USER")).contains(roles(f.owner.getId())));assertEquals(2,auditRows.count());assertEquals(2,roles(f.owner.getId()).size());
    }
    @Test void roleChangeAndAccountLockDoNotLoseEitherUpdateOrAudit() throws Exception {
        var form=selected("ROLE_USER","ROLE_MANAGER");assertEquals(2,pair(admin(f.admin,()->adminUsers.lock(f.owner.getId())),admin(secondAdmin,()->adminUsers.updateRoles(f.owner.getId(),form))));
        assertEquals(UserStatus.LOCKED,db(()->em.find(User.class,f.owner.getId()).getStatus()));assertEquals(List.of("ROLE_MANAGER","ROLE_USER"),roles(f.owner.getId()));assertEquals(Set.of("USER_LOCKED","USER_ROLES_CHANGED"),auditRows.findAll().stream().map(AuditLog::getAction).collect(java.util.stream.Collectors.toSet()));
    }
    @Test void twoAdminsCannotSimultaneouslyRemoveEachOthersLastAdminAuthority() throws Exception {
        var user=selected("ROLE_USER");assertEquals(1,pair(admin(f.admin,()->adminUsers.updateRoles(secondAdmin.getId(),user)),admin(secondAdmin,()->adminUsers.updateRoles(f.admin.getId(),user))));
        assertEquals(1,activeAdmins());assertEquals(1,auditRows.count());var row=auditRows.findAll().getFirst();assertNotEquals(row.getActor().getId().toString(),row.getResourceId());
    }
    @Test void twoAdminsLockingEachOtherLeaveOneActiveAdminAndOneAudit() throws Exception {
        assertEquals(1,pair(admin(f.admin,()->adminUsers.lock(secondAdmin.getId())),admin(secondAdmin,()->adminUsers.lock(f.admin.getId()))));assertEquals(1,activeAdmins());assertEquals(1,auditRows.count());assertEquals(0,notices.count());
    }
    @Test void simultaneousExportsAreConsistentReadOnlyDownloads() throws Exception {
        created(ShipmentMethod.BRANCH_DROPOFF);var files=new ConcurrentLinkedQueue<ReportViews.Download>();
        assertEquals(2,pair(admin(f.admin,()->files.add(csv.export(reportRequest(ReportViews.Dataset.SHIPMENTS),ReportViews.Export.DETAILS))),admin(secondAdmin,()->files.add(csv.export(reportRequest(ReportViews.Dataset.SHIPMENTS),ReportViews.Export.DETAILS)))));
        var results=new ArrayList<>(files);assertArrayEquals(results.get(0).bytes(),results.get(1).bytes());assertEquals(1,count(Shipment.class));assertEquals(1,count(ShipmentEvent.class));assertEquals(0,auditRows.count());assertEquals(0,notices.count());
    }
    @Test void notificationReadAndDeliveryRetryPreserveReadFlagAndSinglePersistentNotice() throws Exception {
        Long shipment=created(ShipmentMethod.BRANCH_DROPOFF);Long event=db(()->em.createQuery("select e.id from ShipmentEvent e where e.shipment.id=:id",Long.class).setParameter("id",shipment).getSingleResult());shipmentNotifications.process(event);
        as(f.owner,"ROLE_USER");Long id=inbox.list(0,50,false).items().getFirst().id();long count=notices.count();
        assertEquals(2,pair(new Call(f.owner,"ROLE_USER",()->inbox.read(id)),admin(f.admin,()->delivery.retry(id))));
        var n=notices.findById(id).orElseThrow();assertTrue(n.isRead());assertEquals(1,n.getDeliveryAttempts());assertEquals(count,notices.count());as(f.owner,"ROLE_USER");assertEquals(0,inbox.count());
    }
    @Test void managerReopenAndCustomerRequestUseSingleVersionedTicketHistory() throws Exception {
        Long ticket=customerTicket(created(ShipmentMethod.BRANCH_DROPOFF));operator(f.destination);
        tickets.assign(ticket,new TicketCommands.Assign(ticketVersion(ticket),f.destinationEmployee.getId(),null));tickets.changeStatus(ticket,new TicketCommands.ChangeStatus(ticketVersion(ticket),TicketStatus.PROCESSING,null));
        tickets.resolve(ticket,new TicketCommands.Decision(ticketVersion(ticket),"Public resolution"));tickets.close(ticket,new TicketCommands.Decision(ticketVersion(ticket),"Closed after review"));
        Long v=ticketVersion(ticket);long before=eventRows.count();var message=new CustomerTicketCommands.Message(v,"Customer requests another review",token());
        assertEquals(1,pair(new Call(f.owner,"ROLE_USER",()->tickets.requestCustomerReopen(ticket,message)),new Call(f.destinationManager,"ROLE_MANAGER",()->tickets.reopen(ticket,new TicketCommands.Decision(v,"Manager accepts review")))));
        assertEquals(before+1,eventRows.count());as(f.owner,"ROLE_USER");var view=customer.portalDetail(ticket,0);assertTrue(Set.of(TicketStatus.CLOSED,TicketStatus.REOPENED).contains(view.ticket().status()));assertEquals(view.ticket().status()==TicketStatus.CLOSED,view.reopenPending());assertEquals(0,notices.count());
    }
}
