package com.ute.uteexpress.operations;

import com.ute.uteexpress.dto.shipment.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import static com.ute.uteexpress.operations.OperationsJdbcFixture.as;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

/** Actual transactions, entity mapping, DB uniqueness and parent-row locks; disposable H2 only. */
@SpringJUnitConfig(LogisticsJourneyConfig.class)
class OriginIntakeIntegrationTests extends LogisticsJourneySupport {
    @org.junit.jupiter.api.BeforeEach void generatedCodeUniqueConstraintOnH2() {
        // SQL Server filtered index excludes legacy NULLs. Explicit H2 NULLS DISTINCT models that rule.
        db(()->{em.createNativeQuery("CREATE UNIQUE NULLS DISTINCT INDEX IF NOT EXISTS UX_intakes_system_receipt_code ON dbo.shipment_intakes(system_receipt_code)").executeUpdate();return null;});
    }
    record Request(Long shipment,Long handoff,Long version) { }
    Request ready(ShipmentMethod method) {
        Long id=method==ShipmentMethod.HOME_PICKUP?collected():confirmed(method);
        Long handoff=method==ShipmentMethod.HOME_PICKUP?pendingOriginHandoff(id):null;
        operator(f.source);return new Request(id,handoff,shipmentVersion(id));
    }
    OriginIntakeResult receive(Request r,Long version,boolean physical,String notes) {
        return r.handoff()==null?lifecycle.receiveDropoff(new BranchIntakeCommand(r.shipment(),version,physical,notes)):
            lifecycle.reviewOriginHandoff(new HandoffReviewCommand(r.handoff(),version,true,physical,notes));
    }
    OriginIntakeResult receive(Request r){return receive(r,r.version(),true,null);}
    @ParameterizedTest @EnumSource(ShipmentMethod.class)
    void automaticallyPersistsCodeAndOneHistoryEntryAndGetNeverRegenerates(ShipmentMethod method) {
        Request r=ready(method);long before=count(ShipmentEvent.class);
        OriginIntakeResult result=receive(r);
        assertFalse(result.replay());assertTrue(result.reference().matches("RCV-20261024-[A-F0-9]{32}"));
        assertEquals(r.version()+1,result.version());assertEquals(1,count(ShipmentIntake.class));assertEquals(before+1,count(ShipmentEvent.class));
        db(()->{var row=em.createQuery("select r from ShipmentIntake r",ShipmentIntake.class).getSingleResult();
            assertEquals(result.reference(),row.getReceiptReference());assertEquals(result.reference(),row.getSystemReceiptCode());
            assertEquals(r.version(),row.getRequestVersion());
            var events=em.createQuery("select e.description from ShipmentEvent e where e.shipment.id=:id and e.toStatus=:status",String.class)
                .setParameter("id",r.shipment()).setParameter("status",ShipmentStatus.AT_ORIGIN_BRANCH).getResultList();
            assertEquals(1,events.size());assertTrue(events.getFirst().contains(result.reference()));return null;});
        assertEquals(result.reference(),managerRead.detail(r.shipment(),0,0,0).receipt().reference());
        assertEquals(result.reference(),managerRead.detail(r.shipment(),0,0,0).receipt().reference());
        if(r.handoff()!=null)assertEquals(result.reference(),appHandoff().detail(r.handoff()).receiptReference());
        assertEquals(before+1,count(ShipmentEvent.class));assertEquals(1,count(ShipmentIntake.class));
        assertCustody(r.shipment(),f.source,ShipmentStatus.AT_ORIGIN_BRANCH);
    }
    @org.springframework.beans.factory.annotation.Autowired ManagerHandoffService handoffRead;
    ManagerHandoffService appHandoff(){return handoffRead;}
    @ParameterizedTest @EnumSource(ShipmentMethod.class)
    void exactRetryReturnsSameReceiptWithoutAnotherTransition(ShipmentMethod method) {
        Request r=ready(method);OriginIntakeResult first=receive(r);long events=count(ShipmentEvent.class);
        for(int retry=0;retry<3;retry++){
            OriginIntakeResult replay=receive(r);assertTrue(replay.replay());assertEquals(first.reference(),replay.reference());assertEquals(first.version(),replay.version());
        }
        assertEquals(1,count(ShipmentIntake.class));assertEquals(events,count(ShipmentEvent.class));
    }
    @ParameterizedTest @EnumSource(ShipmentMethod.class)
    void replayCannotChangePayloadVersionActorOrSkipPhysicalConfirmation(ShipmentMethod method) {
        Request r=ready(method);receive(r);long events=count(ShipmentEvent.class);
        assertThrows(LogisticsRuleException.class,()->receive(r,r.version(),true,"Changed payload"));
        assertThrows(LogisticsRuleException.class,()->receive(r,r.version()+1,true,null));
        var invalid=assertThrows(RuntimeException.class,()->receive(r,r.version(),false,null));
        assertTrue(invalid instanceof LogisticsRuleException || invalid instanceof jakarta.validation.ConstraintViolationException);
        as(f.secondManager,"ROLE_MANAGER");assertThrows(LogisticsRuleException.class,()->receive(r));
        assertEquals(1,count(ShipmentIntake.class));assertEquals(events,count(ShipmentEvent.class));
    }
    @ParameterizedTest @EnumSource(ShipmentMethod.class)
    void replayRechecksBranchRoleAndEmployeeEvenAfterSuccess(ShipmentMethod method) {
        Request r=ready(method);receive(r);long events=count(ShipmentEvent.class);
        as(f.destinationManager,"ROLE_MANAGER");assertThrows(AccessDeniedException.class,()->receive(r));
        as(f.owner,"ROLE_USER");assertThrows(AccessDeniedException.class,()->receive(r));
        SecurityContextHolder.clearContext();assertThrows(org.springframework.security.core.AuthenticationException.class,()->receive(r));
        operator(f.source);
        db(()->{em.find(Employee.class,f.managerEmployee.getId()).setStatus(EmployeeStatus.INACTIVE);return null;});
        assertThrows(AccessDeniedException.class,()->receive(r));assertEquals(events,count(ShipmentEvent.class));
    }
    @ParameterizedTest @EnumSource(ShipmentMethod.class)
    void staleIntakeCannotAcknowledgeShipmentWhichAlreadyMovedOn(ShipmentMethod method) {
        Request r=ready(method);receive(r);var leg=manifest(List.of(r.shipment()),f.source,f.hub);depart(leg);
        long events=count(ShipmentEvent.class);operator(f.source);
        assertThrows(LogisticsRuleException.class,()->receive(r));assertEquals(events,count(ShipmentEvent.class));
        assertCustody(r.shipment(),null,ShipmentStatus.IN_TRANSIT);
    }
    @ParameterizedTest @EnumSource(ShipmentMethod.class)
    void concurrentExactRequestsCommitOnlyOneIntakeAndReturnTheSameCode(ShipmentMethod method)throws Exception {
        Request r=ready(method);long before=count(ShipmentEvent.class);
        var pool=Executors.newFixedThreadPool(2);var barrier=new CyclicBarrier(2);
        try {
            var jobs=new ArrayList<Future<OriginIntakeResult>>();
            for(int i=0;i<2;i++)jobs.add(pool.submit(()->{as(f.manager,"ROLE_MANAGER");try{barrier.await(5,TimeUnit.SECONDS);return receive(r);}finally{SecurityContextHolder.clearContext();}}));
            var one=jobs.get(0).get(15,TimeUnit.SECONDS);var two=jobs.get(1).get(15,TimeUnit.SECONDS);
            assertEquals(one.reference(),two.reference());assertNotEquals(one.replay(),two.replay());
        }finally{pool.shutdownNow();}
        assertEquals(1,count(ShipmentIntake.class));assertEquals(before+1,count(ShipmentEvent.class));
    }
    @Test void independentIntakesReceiveDifferentCodesAndDatabaseRejectsForcedCollision() {
        Request first=ready(ShipmentMethod.BRANCH_DROPOFF);var code=receive(first).reference();
        Request other=ready(ShipmentMethod.HOME_PICKUP);var otherCode=receive(other).reference();assertNotEquals(code,otherCode);
        Request blocked=ready(ShipmentMethod.BRANCH_DROPOFF);long events=count(ShipmentEvent.class);
        try(var generator=mockStatic(OriginReceiptCodes.class)) {
            generator.when(()->OriginReceiptCodes.create(org.mockito.ArgumentMatchers.any())).thenReturn(code);
            assertThrows(DataIntegrityViolationException.class,()->receive(blocked));
        }
        assertEquals(2,count(ShipmentIntake.class));assertEquals(events,count(ShipmentEvent.class));
        assertCustody(blocked.shipment(),null,ShipmentStatus.CONFIRMED);
    }
    @Test void historicalHandwrittenDuplicatesRemainReadableButAreNotTreatedAsAuthenticatedReplays() {
        Request a=ready(ShipmentMethod.BRANCH_DROPOFF),b=ready(ShipmentMethod.BRANCH_DROPOFF);
        for(Request r:List.of(a,b))db(()->{
            Shipment s=em.find(Shipment.class,r.shipment());s.setStatus(ShipmentStatus.AT_ORIGIN_BRANCH);s.setCurrentBranch(s.getOriginBranch());
            ShipmentIntake receipt=new ShipmentIntake();receipt.setShipment(s);receipt.setBranch(s.getOriginBranch());receipt.setEmployee(em.find(Employee.class,f.managerEmployee.getId()));
            receipt.setReceivedBy(em.find(User.class,f.manager.getId()));receipt.setShipmentMethod(ShipmentMethod.BRANCH_DROPOFF);receipt.setReceiptReference("OLD-DUPLICATE");em.persist(receipt);return null;
        });
        assertEquals("OLD-DUPLICATE",managerRead.detail(a.shipment(),0,0,0).receipt().reference());
        assertEquals("OLD-DUPLICATE",managerRead.detail(b.shipment(),0,0,0).receipt().reference());
        assertThrows(LogisticsRuleException.class,()->receive(a));assertEquals(2,count(ShipmentIntake.class));
    }
}
