package com.ute.uteexpress.operations;

import com.ute.uteexpress.dto.shipment.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import java.util.concurrent.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import static com.ute.uteexpress.operations.OperationsJdbcFixture.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(SystemSecurityIntegrationConfig.class)
class SystemAccountRevocationTests extends SystemSecuritySupport {
    @ParameterizedTest @CsvSource({"PICKUP,ACCOUNT_LOCK","PICKUP,EMPLOYEE_INACTIVE","PICKUP,BRANCH_DISABLED","PICKUP,ROLE_REMOVED","DELIVERY,ACCOUNT_LOCK","DELIVERY,EMPLOYEE_INACTIVE","DELIVERY,BRANCH_DISABLED","DELIVERY,ROLE_REMOVED"})
    void revocationAfterIdentityReadBeforeShipmentLockPreventsOperationalCommit(String phase,String change) throws Exception {
        boolean pickup=phase.equals("PICKUP");Long id=pickup?pickupAssigned():atDestination(),task=pickup?assignment(id,AssignmentType.PICKUP):running(id),version=shipmentVersion(id);long events=count(ShipmentEvent.class);
        User actor=pickup?pickupCourier:f.shipper;Long employee=pickup?pickupEmployee.getId():deliveryEmployee();Branch branch=pickup?f.source:f.destination;
        var reached=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();
        try {
            var writer=pool.submit(()->{as(actor,"ROLE_SHIPPER");workflowProbe.arm(reached,release);
                try{if(pickup)lifecycle.collectPickup(new PickupCollectionCommand(task,version,PickupResult.SUCCESS,true,"SHOULD-NOT-COMMIT",null));else lastMile.complete(success(id,task));return true;}
                finally {workflowProbe.clear();SecurityContextHolder.clearContext();}});
            assertTrue(reached.await(10,TimeUnit.SECONDS),"Identity was read before pausing at the parent-lock boundary");
            as(f.admin,"ROLE_ADMIN");
            switch(change) {
                case "ACCOUNT_LOCK" -> adminUsers.lock(actor.getId());
                case "EMPLOYEE_INACTIVE" -> db(()->{em.find(Employee.class,employee).setStatus(EmployeeStatus.INACTIVE);return null;});
                case "BRANCH_DISABLED" -> db(()->{em.find(Branch.class,branch.getId()).setStatus(BranchStatus.INACTIVE);return null;});
                case "ROLE_REMOVED" -> adminUsers.updateRoles(actor.getId(),selected());
                default -> throw new AssertionError(change);
            }
            release.countDown();var error=assertThrows(ExecutionException.class,()->writer.get(10,TimeUnit.SECONDS));assertInstanceOf(AccessDeniedException.class,error.getCause());
            assertCustody(id,null,pickup?ShipmentStatus.PICKUP_ASSIGNED:ShipmentStatus.OUT_FOR_DELIVERY);assertEquals(events,count(ShipmentEvent.class));assertEquals(0,count(PickupEvidence.class));assertEquals(0,count(DeliveryProof.class));assertEquals(0,count(DeliveryAttempt.class));assertEquals(0,notices.count());
            assertEquals(change.equals("EMPLOYEE_INACTIVE")||change.equals("BRANCH_DISABLED")?0:1,auditRows.count());
        } finally {release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}
    }
}
