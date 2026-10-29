package com.ute.uteexpress.operations;

import com.ute.uteexpress.dto.delivery.DeliveryCommands.*;
import com.ute.uteexpress.dto.shipment.*;
import com.ute.uteexpress.dto.transport.TripCommands.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.LogisticsRuleException;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.server.ResponseStatusException;
import static com.ute.uteexpress.operations.OperationsJdbcFixture.*;
import static org.junit.jupiter.api.Assertions.*;

/** Barrier-started real transactions; assertions accept either legal winner, never a specific schedule. */
@SpringJUnitConfig(LogisticsJourneyConfig.class)
class LogisticsConcurrencyIntegrationTests extends LogisticsJourneySupport {
    record Call(User actor,String role,Runnable work) { }
    Call manager(User user,Runnable work) {return new Call(user,"ROLE_MANAGER",work);}
    Call courier(User user,Runnable work) {return new Call(user,"ROLE_SHIPPER",work);}
    void race(Call a,Call b) throws Exception {
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var results=new ArrayList<Future<Boolean>>();
            for(var call:List.of(a,b))results.add(pool.submit(()->{
                as(call.actor(),call.role());ready.countDown();
                try {assertTrue(start.await(5,TimeUnit.SECONDS));call.work().run();return true;}
                catch(ObjectOptimisticLockingFailureException|LogisticsRuleException|AccessDeniedException rejected){return false;}
                catch(ResponseStatusException rejected){if(rejected.getStatusCode().value()!=404)throw rejected;return false;}
                finally {SecurityContextHolder.clearContext();}
            }));
            assertTrue(ready.await(5,TimeUnit.SECONDS));start.countDown();
            int succeeded=0;for(var result:results)if(result.get(15,TimeUnit.SECONDS))succeeded++;
            assertEquals(1,succeeded,"Exactly one conflicting operation may commit");
        } finally {start.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}
    }
    @ParameterizedTest @ValueSource(strings={"CONFIRM_CONFIRM","CONFIRM_CANCEL","ASSIGN_ASSIGN","REASSIGN_COLLECT","COLLECT_COLLECT","COLLECT_CANCEL","INTAKE_INTAKE"})
    void originRacesPreserveSingleEvidenceAssignmentAndLegalLifecycle(String scenario) throws Exception {
        Long id;Call a,b;
        switch(scenario) {
            case "CONFIRM_CONFIRM","CONFIRM_CANCEL" -> {
                id=created(ShipmentMethod.BRANCH_DROPOFF);Long v=shipmentVersion(id);
                a=manager(f.manager,()->lifecycle.transition(new ShipmentTransitionCommand(id,v,ShipmentStatus.CONFIRMED)));
                b=manager(f.secondManager,()->lifecycle.transition(new ShipmentTransitionCommand(id,v,scenario.equals("CONFIRM_CANCEL")?ShipmentStatus.CANCELLED:ShipmentStatus.CONFIRMED,"Concurrent decision")));
            }
            case "ASSIGN_ASSIGN" -> {
                id=confirmed(ShipmentMethod.HOME_PICKUP);Long v=shipmentVersion(id);
                a=manager(f.manager,()->lifecycle.assignPickup(new PickupAssignmentCommand(id,v,pickupEmployee.getId(),null,null)));
                b=manager(f.secondManager,()->lifecycle.assignPickup(new PickupAssignmentCommand(id,v,replacementEmployee.getId(),null,null)));
            }
            case "INTAKE_INTAKE" -> {
                id=confirmed(ShipmentMethod.BRANCH_DROPOFF);Long v=shipmentVersion(id);
                a=manager(f.manager,()->lifecycle.receiveDropoff(new BranchIntakeCommand(id,v,true,null)));
                b=manager(f.secondManager,()->lifecycle.receiveDropoff(new BranchIntakeCommand(id,v,true,null)));
            }
            default -> {
                id=pickupAssigned();Long v=shipmentVersion(id),task=assignment(id,AssignmentType.PICKUP);
                a=courier(pickupCourier,()->lifecycle.collectPickup(new PickupCollectionCommand(task,v,PickupResult.SUCCESS,true,"PROOF",null)));
                b=switch(scenario) {
                    case "REASSIGN_COLLECT" -> manager(f.manager,()->lifecycle.assignPickup(new PickupAssignmentCommand(id,v,replacementEmployee.getId(),task,"Replace before pickup")));
                    case "COLLECT_CANCEL" -> manager(f.manager,()->lifecycle.transition(new ShipmentTransitionCommand(id,v,ShipmentStatus.CANCELLED,"Cannot cancel active task")));
                    default -> courier(pickupCourier,()->lifecycle.collectPickup(new PickupCollectionCommand(task,v,PickupResult.SUCCESS,true,"SECOND",null)));
                };
            }
        }
        long events=count(ShipmentEvent.class);race(a,b);assertEquals(events+1,count(ShipmentEvent.class));
        assertTrue(count(PickupEvidence.class)<=1);assertTrue(count(ShipmentIntake.class)<=1);
        db(()->{assertTrue(em.createQuery("select count(a) from ShipmentAssignment a where a.shipment.id=:id and a.completedAt is null",Long.class).setParameter("id",id).getSingleResult()<=1);return null;});
        db(()->{var s=em.find(Shipment.class,id);switch(scenario){
            case "CONFIRM_CONFIRM" -> assertEquals(ShipmentStatus.CONFIRMED,s.getStatus());
            case "CONFIRM_CANCEL" -> assertTrue(Set.of(ShipmentStatus.CONFIRMED,ShipmentStatus.CANCELLED).contains(s.getStatus()));
            case "ASSIGN_ASSIGN" -> assertEquals(ShipmentStatus.PICKUP_ASSIGNED,s.getStatus());
            case "INTAKE_INTAKE" -> {assertEquals(ShipmentStatus.AT_ORIGIN_BRANCH,s.getStatus());assertEquals(f.source.getId(),s.getCurrentBranch().getId());}
            case "REASSIGN_COLLECT" -> assertTrue(Set.of(ShipmentStatus.PICKUP_ASSIGNED,ShipmentStatus.PICKED_UP).contains(s.getStatus()));
            default -> assertEquals(ShipmentStatus.PICKED_UP,s.getStatus());
        }return null;});
        assertEquals(0,notices.count());
    }
    @ParameterizedTest @ValueSource(strings={"TWO_ACTIVE_MANIFESTS","SEAL_MANIFEST","DEPART_DEPART","RECEIVE_RECEIVE","RECEIVE_DISCREPANCY","CLOSE_CLOSE","RESOLVE_CLOSE"})
    void tripRacesPreserveReservationPhysicalReceiptAndReconciliation(String scenario) throws Exception {
        Long id=intake(ShipmentMethod.BRANCH_DROPOFF);Call a,b;
        if(scenario.equals("TWO_ACTIVE_MANIFESTS")) {
            operator(f.source);Long t1=transport.create(new Create(routes.get(key(f.source,f.hub)),token()));Long t2=transport.create(new Create(routes.get(key(f.source,f.hub)),token()));Long v1=tripVersion(t1),v2=tripVersion(t2);
            a=manager(f.manager,()->transport.addItems(new Manifest(t1,v1,List.of(id))));b=manager(f.secondManager,()->transport.addItems(new Manifest(t2,v2,List.of(id))));
            race(a,b);assertEquals(1,count(TransportTripItem.class));
        } else {
            var leg=manifest(List.of(id),f.source,f.hub);
            switch(scenario) {
                case "SEAL_MANIFEST" -> {
                    Long extra=intake(ShipmentMethod.BRANCH_DROPOFF),v=tripVersion(leg.trip());
                    a=manager(f.manager,()->transport.seal(new Change(leg.trip(),v,true,"SEAL",null)));
                    b=manager(f.secondManager,()->transport.addItems(new Manifest(leg.trip(),v,List.of(extra))));
                }
                case "DEPART_DEPART" -> {
                    transport.seal(change(leg.trip()));Long v=tripVersion(leg.trip());
                    a=manager(f.manager,()->transport.depart(new Change(leg.trip(),v,true,"FIRST",null)));b=manager(f.secondManager,()->transport.depart(new Change(leg.trip(),v,true,"SECOND",null)));
                }
                default -> {
                    depart(leg);arrive(leg);
                    if(scenario.equals("CLOSE_CLOSE"))transport.receive(receipt(leg,id));
                    if(scenario.equals("RESOLVE_CLOSE")) {
                        transport.receive(receipt(leg,id));transport.recordDiscrepancy(new Discrepancy(leg.trip(),tripVersion(leg.trip()),null,TripDiscrepancyKind.UNEXPECTED,"EXTRA","Unknown package"));
                        Long issue=db(()->em.createQuery("select d.id from TripDiscrepancy d",Long.class).getSingleResult()),v=tripVersion(leg.trip());
                        a=manager(hubManagerA,()->transport.resolveUnexpected(new Resolve(leg.trip(),v,issue,true,"VERIFIED","Removed unrelated package")));
                        b=manager(hubManagerA,()->transport.close(new Change(leg.trip(),v,true,"CLOSE",null)));
                    } else if(scenario.equals("CLOSE_CLOSE")) {
                        Long v=tripVersion(leg.trip());a=manager(hubManagerA,()->transport.close(new Change(leg.trip(),v,true,"FIRST",null)));b=manager(hubManagerA,()->transport.close(new Change(leg.trip(),v,true,"SECOND",null)));
                    } else {
                        Long v=tripVersion(leg.trip());a=manager(hubManagerA,()->transport.receive(new Receive(leg.trip(),v,leg.item(),true,"FIRST",null,false)));
                        b=scenario.equals("RECEIVE_RECEIVE")?manager(hubManagerA,()->transport.receive(new Receive(leg.trip(),v,leg.item(),true,"SECOND",null,false))):manager(hubManagerA,()->transport.recordDiscrepancy(new Discrepancy(leg.trip(),v,leg.item(),TripDiscrepancyKind.MISSING,"ISSUE","Not found during count")));
                    }
                }
            }
            race(a,b);
        }
        assertTrue(count(TripReceipt.class)<=1);assertTrue(count(TripDiscrepancy.class)<=1);
        db(()->{long active=em.createQuery("select count(i) from TransportTripItem i where i.shipment.id=:id and i.active=true",Long.class).setParameter("id",id).getSingleResult();assertTrue(active<=1);return null;});
        if(scenario.equals("RECEIVE_RECEIVE"))assertCustody(id,f.hub,ShipmentStatus.AT_ORIGIN_HUB);
        if(scenario.equals("RECEIVE_DISCREPANCY"))assertCustody(id,count(TripReceipt.class)==1?f.hub:null,count(TripReceipt.class)==1?ShipmentStatus.AT_ORIGIN_HUB:ShipmentStatus.IN_TRANSIT);
        if(scenario.equals("CLOSE_CLOSE"))assertEquals(TripStatus.CLOSED,db(()->em.createQuery("select t.status from TransportTrip t",TripStatus.class).getSingleResult()));
        if(scenario.equals("RESOLVE_CLOSE")){assertEquals(TripStatus.RECONCILING,db(()->em.createQuery("select t.status from TransportTrip t",TripStatus.class).getSingleResult()));assertEquals(TripDiscrepancyStatus.RESOLVED,db(()->em.createQuery("select d.status from TripDiscrepancy d",TripDiscrepancyStatus.class).getSingleResult()));}
        assertEquals(0,notices.count());
    }
    @ParameterizedTest @ValueSource(strings={"ACCEPT_ACCEPT","SUCCESS_SUCCESS","SUCCESS_FAILED","SUCCESS_RETURN","REDELIVERY_REDELIVERY","RETURN_RETURN","FINAL_FINAL","FINAL_ASSIGN"})
    void deliveryAndReturnRacesNeverCreateContradictoryProofOrDuplicateReceipts(String scenario) throws Exception {
        Long id=atDestination();Call a,b;
        if(scenario.equals("ACCEPT_ACCEPT")) {
            operator(f.destination);lastMile.assign(new Assign(id,shipmentVersion(id),deliveryEmployee(),null,null));Long task=assignment(id,AssignmentType.DELIVERY);
            lastMile.prepare(new Prepare(id,shipmentVersion(id),task,true,"PREPARE",null));Long v=shipmentVersion(id);
            a=courier(f.shipper,()->lastMile.accept(new Action(task,v,true,"FIRST")));b=courier(f.shipper,()->lastMile.accept(new Action(task,v,true,"SECOND")));
        } else {
            Long task=running(id);
            if(scenario.startsWith("FINAL")) {
                fail(id,task);operator(f.destination);lastMile.initiateReturn(new ReturnStart(id,shipmentVersion(id),"Return"));recover(id,task);reverse(id);Long v=shipmentVersion(id);
                a=manager(f.manager,()->lastMile.returnToSender(new SenderReceipt(id,v,true,ReturnRecipientType.SENDER,"Sender","FIRST",null)));
                b=scenario.equals("FINAL_FINAL")?manager(f.secondManager,()->lastMile.returnToSender(new SenderReceipt(id,v,true,ReturnRecipientType.SENDER,"Sender","SECOND",null))):manager(f.destinationManager,()->lastMile.assign(new Assign(id,v,deliveryEmployee(),null,null)));
            } else if(scenario.equals("REDELIVERY_REDELIVERY")) {
                fail(id,task);Long v=shipmentVersion(id),employee=deliveryEmployee();a=manager(f.destinationManager,()->lastMile.redelivery(new Redelivery(id,v,employee,"FIRST")));b=manager(f.destinationManager,()->lastMile.redelivery(new Redelivery(id,v,employee,"SECOND")));
            } else if(scenario.equals("RETURN_RETURN")) {
                Long v=shipmentVersion(id);a=manager(f.destinationManager,()->lastMile.initiateReturn(new ReturnStart(id,v,"FIRST")));b=manager(f.destinationManager,()->lastMile.initiateReturn(new ReturnStart(id,v,"SECOND")));
            } else {
                Long v=shipmentVersion(id);a=courier(f.shipper,()->lastMile.complete(new Complete(task,v,true,DeliveryRecipientType.RECIPIENT,"Recipient","FIRST",null)));
                b=switch(scenario) {
                    case "SUCCESS_FAILED" -> courier(f.shipper,()->lastMile.fail(new Fail(task,v,true,DeliveryFailureCode.ABSENT,"Absent",null)));
                    case "SUCCESS_RETURN" -> manager(f.destinationManager,()->lastMile.initiateReturn(new ReturnStart(id,v,"Recall requested")));
                    default -> courier(f.shipper,()->lastMile.complete(new Complete(task,v,true,DeliveryRecipientType.RECIPIENT,"Recipient","SECOND",null)));
                };
            }
        }
        race(a,b);assertTrue(count(DeliveryProof.class)<=1);assertTrue(count(ReturnRequest.class)<=1);assertTrue(count(ReturnReceipt.class)<=1);assertTrue(count(RedeliveryDecision.class)<=1);
        db(()->{var s=em.find(Shipment.class,id);if(s.getStatus()==ShipmentStatus.DELIVERED){assertEquals(1,count(DeliveryProof.class));assertEquals(0,count(ReturnRequest.class));}
            if(s.getStatus()==ShipmentStatus.RETURNING || s.getStatus()==ShipmentStatus.RETURNED)assertEquals(0,count(DeliveryProof.class));
            switch(scenario){
                case "ACCEPT_ACCEPT" -> {assertEquals(ShipmentStatus.AT_DESTINATION_BRANCH,s.getStatus());assertNull(s.getCurrentBranch());assertEquals(0,count(DeliveryAttempt.class));}
                case "SUCCESS_SUCCESS" -> {assertEquals(ShipmentStatus.DELIVERED,s.getStatus());assertEquals(1,count(DeliveryAttempt.class));}
                case "SUCCESS_FAILED" -> {assertTrue(Set.of(ShipmentStatus.DELIVERED,ShipmentStatus.DELIVERY_FAILED).contains(s.getStatus()));assertEquals(1,count(DeliveryAttempt.class));}
                case "SUCCESS_RETURN" -> assertTrue(Set.of(ShipmentStatus.DELIVERED,ShipmentStatus.RETURNING).contains(s.getStatus()));
                case "REDELIVERY_REDELIVERY" -> {assertEquals(ShipmentStatus.DELIVERY_FAILED,s.getStatus());assertEquals(1,count(RedeliveryDecision.class));assertEquals(1,count(DeliveryAttempt.class));}
                case "RETURN_RETURN" -> {assertEquals(ShipmentStatus.RETURNING,s.getStatus());assertEquals(1,count(ReturnRequest.class));}
                default -> {assertEquals(ShipmentStatus.RETURNED,s.getStatus());assertEquals(1,count(ReturnReceipt.class));assertNull(s.getCurrentBranch());}
            }return null;});
        assertEquals(0,notices.count());
    }
}
