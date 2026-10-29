package com.ute.uteexpress.operations;

import com.ute.uteexpress.dto.delivery.DeliveryCommands.*;
import com.ute.uteexpress.dto.shipment.*;
import com.ute.uteexpress.dto.transport.TripCommands.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.LogisticsRuleException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.server.ResponseStatusException;
import static com.ute.uteexpress.operations.OperationsJdbcFixture.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(LogisticsJourneyConfig.class)
class LogisticsGuardIntegrationTests extends LogisticsJourneySupport {
    @org.springframework.beans.factory.annotation.Autowired com.ute.uteexpress.service.LogisticsAccessService logisticsAccess;
    @Test void uncaughtAuthorizationDenialStillRollsBackTheOwningWriteTransaction() {
        Long id=created(ShipmentMethod.BRANCH_DROPOFF);String original=db(()->em.find(Shipment.class,id).getNote());as(f.outsider,"ROLE_MANAGER");
        assertThrows(AccessDeniedException.class,()->db(()->{em.find(Shipment.class,id).setNote("Must roll back");em.flush();logisticsAccess.requireManagerForBranch(f.source.getId());return null;}));
        assertEquals(original,db(()->em.find(Shipment.class,id).getNote()));assertCustody(id,null,ShipmentStatus.CREATED);
    }
    @ParameterizedTest @ValueSource(strings={"ADMIN","USER","SHIPPER","OTHER_BRANCH","LOCKED","ROLE_REVOKED","MEMBERSHIP_REVOKED"})
    void confirmationRequiresActualDatabaseManagerAndOriginScope(String condition) {
        Long id=created(ShipmentMethod.BRANCH_DROPOFF);operator(f.source);
        switch(condition) {
            case "ADMIN" -> as(f.admin,"ROLE_ADMIN");case "USER" -> as(f.owner,"ROLE_USER");case "SHIPPER" -> as(pickupCourier,"ROLE_SHIPPER");case "OTHER_BRANCH" -> as(f.outsider,"ROLE_MANAGER");
            case "LOCKED" -> db(()->{em.find(User.class,f.manager.getId()).setStatus(UserStatus.LOCKED);return null;});
            case "ROLE_REVOKED" -> db(()->{em.createQuery("delete from UserRole r where r.user.id=:id").setParameter("id",f.manager.getId()).executeUpdate();return null;});
            case "MEMBERSHIP_REVOKED" -> db(()->{em.createQuery("delete from ManagerBranch m where m.manager.id=:id").setParameter("id",f.manager.getId()).executeUpdate();return null;});
            default -> throw new AssertionError(condition);
        }
        assertThrows(AccessDeniedException.class,()->lifecycle.transition(new ShipmentTransitionCommand(id,shipmentVersion(id),ShipmentStatus.CONFIRMED)));
        assertCustody(id,null,ShipmentStatus.CREATED);assertEquals(1,count(ShipmentEvent.class));assertEquals(0,notices.count());
    }
    @ParameterizedTest @ValueSource(strings={"USER_LOCKED","EMPLOYEE_INACTIVE","WRONG_BRANCH","ROLE_REVOKED","WRONG_POSITION"})
    void pickupAssignmentValidatesCourierEmployeeRoleAndBranch(String condition) {
        Long id=confirmed(ShipmentMethod.HOME_PICKUP);
        db(()->{var e=em.find(Employee.class,pickupEmployee.getId());switch(condition){
            case "USER_LOCKED" -> em.find(User.class,pickupCourier.getId()).setStatus(UserStatus.LOCKED);
            case "EMPLOYEE_INACTIVE" -> e.setStatus(EmployeeStatus.INACTIVE);
            case "WRONG_BRANCH" -> e.setBranch(em.find(Branch.class,f.foreign.getId()));
            case "WRONG_POSITION" -> e.setPosition(EmployeePosition.WAREHOUSE_STAFF);
            case "ROLE_REVOKED" -> em.createQuery("delete from UserRole r where r.user.id=:id").setParameter("id",pickupCourier.getId()).executeUpdate();
            default -> throw new AssertionError(condition);
        }return null;});
        assertThrows(LogisticsRuleException.class,()->lifecycle.assignPickup(new PickupAssignmentCommand(id,shipmentVersion(id),pickupEmployee.getId(),null,null)));
        assertEquals(0,count(ShipmentAssignment.class));assertCustody(id,null,ShipmentStatus.CONFIRMED);
    }
    @Test void cancelIsOnlyAllowedBeforePhysicalIntakeWithoutActiveTasks() {
        Long cancelled=created(ShipmentMethod.BRANCH_DROPOFF);operator(f.source);
        lifecycle.transition(new ShipmentTransitionCommand(cancelled,shipmentVersion(cancelled),ShipmentStatus.CANCELLED,"Customer cancellation"));
        assertThrows(LogisticsRuleException.class,()->lifecycle.transition(new ShipmentTransitionCommand(cancelled,shipmentVersion(cancelled),ShipmentStatus.CONFIRMED)));
        Long active=pickupAssigned();assertThrows(LogisticsRuleException.class,()->lifecycle.transition(new ShipmentTransitionCommand(active,shipmentVersion(active),ShipmentStatus.CANCELLED,"Not while assigned")));
        Long received=intake(ShipmentMethod.BRANCH_DROPOFF);assertThrows(LogisticsRuleException.class,()->lifecycle.transition(new ShipmentTransitionCommand(received,shipmentVersion(received),ShipmentStatus.CANCELLED,"Too late")));
        assertCustody(received,f.source,ShipmentStatus.AT_ORIGIN_BRANCH);assertEquals(1,count(ShipmentIntake.class));
    }
    @Test void reassignmentClosesOldHistoryAndFormerCourierCannotCollect() {
        Long id=pickupAssigned(),old=assignment(id,AssignmentType.PICKUP);
        lifecycle.assignPickup(new PickupAssignmentCommand(id,shipmentVersion(id),replacementEmployee.getId(),old,"Replacement before collection"));
        db(()->{var a=em.find(ShipmentAssignment.class,old);assertEquals(AssignmentStatus.CANCELLED,a.getStatus());assertNotNull(a.getCompletedAt());return null;});
        as(pickupCourier,"ROLE_SHIPPER");assertThrows(AccessDeniedException.class,()->lifecycle.collectPickup(new PickupCollectionCommand(old,shipmentVersion(id),PickupResult.SUCCESS,true,"FORGED",null)));
        as(replacementCourier,"ROLE_SHIPPER");Long task=assignment(id,AssignmentType.PICKUP);lifecycle.collectPickup(new PickupCollectionCommand(task,shipmentVersion(id),PickupResult.SUCCESS,true,"VALID",null));
        assertEquals(2,count(ShipmentAssignment.class));assertEquals(1,count(PickupEvidence.class));assertCustody(id,null,ShipmentStatus.PICKED_UP);
    }
    @ParameterizedTest @ValueSource(strings={"NO_CONFIRMATION","NO_REFERENCE","FAILURE_WITH_COLLECTION","FAILURE_WITHOUT_REASON"})
    void invalidPickupEvidenceNeverMovesCustodyOrWritesEvidence(String condition) {
        Long id=pickupAssigned(),task=assignment(id,AssignmentType.PICKUP);as(pickupCourier,"ROLE_SHIPPER");
        boolean failed=condition.startsWith("FAILURE");
        var c=new PickupCollectionCommand(task,shipmentVersion(id),failed?PickupResult.FAILED:PickupResult.SUCCESS,
            condition.equals("FAILURE_WITH_COLLECTION") || (!failed && !condition.equals("NO_CONFIRMATION")),condition.equals("NO_REFERENCE")?null:"REF",condition.equals("FAILURE_WITH_COLLECTION")?"Reason":null);
        assertThrows(LogisticsRuleException.class,()->lifecycle.collectPickup(c));assertEquals(0,count(PickupEvidence.class));assertEquals(0,count(ShipmentIntake.class));assertCustody(id,null,ShipmentStatus.PICKUP_ASSIGNED);
    }
    @Test void failedPickupAndRejectedHandoffDoNotImplyBranchReceipt() {
        Long id=pickupAssigned(),task=assignment(id,AssignmentType.PICKUP);as(pickupCourier,"ROLE_SHIPPER");
        lifecycle.collectPickup(new PickupCollectionCommand(task,shipmentVersion(id),PickupResult.FAILED,false,null,"Sender unavailable"));assertCustody(id,null,ShipmentStatus.PICKUP_ASSIGNED);
        collect(id);Long handoff=pendingOriginHandoff(id);operator(f.source);
        lifecycle.reviewOriginHandoff(new HandoffReviewCommand(handoff,shipmentVersion(id),false,false,"Check packaging"));assertEquals(0,count(ShipmentIntake.class));assertCustody(id,null,ShipmentStatus.PICKED_UP);
        Long retry=pendingOriginHandoff(id);assertNotEquals(handoff,retry);operator(f.source);lifecycle.reviewOriginHandoff(new HandoffReviewCommand(retry,shipmentVersion(id),true,true,null));
        assertEquals(2,count(OriginHandoff.class));assertEquals(2,count(PickupEvidence.class));assertEquals(1,count(ShipmentIntake.class));
        as(pickupCourier,"ROLE_SHIPPER");assertThrows(AccessDeniedException.class,()->lifecycle.collectPickup(new PickupCollectionCommand(task,shipmentVersion(id),PickupResult.SUCCESS,true,"DUPLICATE",null)));
    }
    @Test void inactivePhysicalEmployeeCannotIntakeAndDropoffCannotUsePickupAssignment() {
        Long id=confirmed(ShipmentMethod.BRANCH_DROPOFF);
        assertThrows(LogisticsRuleException.class,()->lifecycle.assignPickup(new PickupAssignmentCommand(id,shipmentVersion(id),pickupEmployee.getId(),null,null)));
        db(()->{em.find(Employee.class,f.managerEmployee.getId()).setStatus(EmployeeStatus.INACTIVE);return null;});
        assertThrows(AccessDeniedException.class,()->lifecycle.receiveDropoff(new BranchIntakeCommand(id,shipmentVersion(id),true,null)));assertEquals(0,count(ShipmentIntake.class));
    }
    @Test void managerReadsCanProbeOtherEndAndNonPhysicalCapabilitiesWithoutRollbackOnly() {
        Long id=intake(ShipmentMethod.BRANCH_DROPOFF);var leg=manifest(List.of(id),f.source,f.hub);
        operator(f.source);assertNotNull(tripRead.detail(leg.trip(),0,0,0,0));
        operator(f.hub);assertNotNull(tripRead.detail(leg.trip(),0,0,0,0));
        as(f.outsider,"ROLE_MANAGER");assertEquals(404,assertThrows(ResponseStatusException.class,()->tripRead.detail(leg.trip(),0,0,0,0)).getStatusCode().value());
        Long returnId=atDestination();operator(f.destination);lastMile.initiateReturn(new ReturnStart(returnId,shipmentVersion(returnId),"Return for source review"));
        operator(f.source);assertNotNull(deliveryRead.managerDetail(returnId,0));
        db(()->{em.find(Employee.class,f.managerEmployee.getId()).setStatus(EmployeeStatus.INACTIVE);return null;});
        assertNotNull(managerRead.detail(id,0,0,0)); // planning/read permission differs from physical intake
    }
    @Test void transportCannotSkipCustodyOrSealAndCannotMutateSealedManifest() {
        Long id=intake(ShipmentMethod.BRANCH_DROPOFF);operator(f.hub);Long skipped=transport.create(new Create(routes.get(key(f.hub,hubB)),token()));
        assertThrows(LogisticsRuleException.class,()->transport.addItems(new Manifest(skipped,tripVersion(skipped),List.of(id))));assertEquals(0,count(TransportTripItem.class));
        var leg=manifest(List.of(id),f.source,f.hub);assertThrows(LogisticsRuleException.class,()->transport.depart(change(leg.trip())));
        transport.seal(change(leg.trip()));assertThrows(LogisticsRuleException.class,()->transport.addItems(new Manifest(leg.trip(),tripVersion(leg.trip()),List.of(id))));
        assertCustody(id,f.source,ShipmentStatus.AT_ORIGIN_BRANCH);assertEquals(0,count(TripReceipt.class));
    }
    @Test void deliveryRequiresDestinationReceiptAndAuthorizedCourierAndTerminalStateCannotReopen() {
        Long id=intake(ShipmentMethod.BRANCH_DROPOFF);var leg=manifest(List.of(id),f.source,f.hub);depart(leg);
        operator(f.destination);assertThrows(LogisticsRuleException.class,()->lastMile.assign(new Assign(id,shipmentVersion(id),deliveryEmployee(),null,null)));
        arrive(leg);transport.receive(receipt(leg,id));transport.close(change(leg.trip()));travel(id,f.hub,hubB,false);travel(id,hubB,f.destination,false);
        Long task=running(id);as(unauthorizedCourier,"ROLE_SHIPPER");assertEquals(404,assertThrows(ResponseStatusException.class,()->deliveryRead.shipperDetail(task,0)).getStatusCode().value());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->lastMile.complete(success(id,task))).getStatusCode().value());
        as(f.shipper,"ROLE_SHIPPER");lastMile.complete(success(id,task));operator(f.destination);
        assertThrows(LogisticsRuleException.class,()->lastMile.assign(new Assign(id,shipmentVersion(id),deliveryEmployee(),null,null)));
        assertThrows(LogisticsRuleException.class,()->lastMile.initiateReturn(new ReturnStart(id,shipmentVersion(id),"Cannot return delivered parcel")));
        assertEquals(0,count(ReturnRequest.class));assertEquals(1,count(DeliveryProof.class));
    }
    @ParameterizedTest @EnumSource(value=TripDiscrepancyKind.class,names={"MISSING","DAMAGED","REJECTED"})
    void itemDiscrepancyCannotBeClosedByAdministrativeResolutionWithoutPhysicalReceipt(TripDiscrepancyKind kind) {
        Long id=intake(ShipmentMethod.BRANCH_DROPOFF);var leg=manifest(List.of(id),f.source,f.hub);depart(leg);arrive(leg);
        var c=new Discrepancy(leg.trip(),tripVersion(leg.trip()),leg.item(),kind,"ISSUE","Requires physical verification");transport.recordDiscrepancy(c);
        assertThrows(LogisticsRuleException.class,()->transport.recordDiscrepancy(new Discrepancy(leg.trip(),tripVersion(leg.trip()),leg.item(),kind,"DUPLICATE","No duplicate open issue")));
        Long issue=db(()->em.createQuery("select d.id from TripDiscrepancy d",Long.class).getSingleResult());
        assertThrows(LogisticsRuleException.class,()->transport.resolveUnexpected(new Resolve(leg.trip(),tripVersion(leg.trip()),issue,true,"INVALID","Not an unexpected parcel")));
        assertThrows(LogisticsRuleException.class,()->transport.close(change(leg.trip())));assertEquals(0,count(TripReceipt.class));assertCustody(id,null,ShipmentStatus.IN_TRANSIT);
    }
    @Test void hibernateEnumParameterUsesMappedRouteForAllForwardAndReverseCandidatePages() {
        Long id=intake(ShipmentMethod.BRANCH_DROPOFF);
        candidateLeg(id,f.source,f.hub,false);candidateLeg(id,f.hub,hubB,false);candidateLeg(id,hubB,f.destination,false);
        operator(f.destination);lastMile.initiateReturn(new ReturnStart(id,shipmentVersion(id),"Return through configured reverse routes"));
        candidateLeg(id,f.destination,hubB,true);candidateLeg(id,hubB,f.hub,true);candidateLeg(id,f.hub,f.source,true);
        assertEquals(6,count(TripReceipt.class));assertCustody(id,f.source,ShipmentStatus.RETURNING);
    }
    void candidateLeg(Long id,Branch from,Branch to,boolean reverse) {
        operator(from);Long trip=transport.create(new Create(routes.get(key(from,to)),token()));
        var detail=tripRead.detail(trip,0,0,0,0);var candidates=reverse?detail.returnCandidates():detail.candidates();
        assertEquals(List.of(id),candidates.map(c->c.id()).getContent());
        assertTrue((reverse?detail.candidates():detail.returnCandidates()).isEmpty());
        transport.addItems(new Manifest(trip,tripVersion(trip),List.of(id)));var leg=new Leg(trip,item(trip,id),from,to);
        depart(leg);arrive(leg);transport.receive(receipt(leg,id));transport.close(change(trip));
    }
}
