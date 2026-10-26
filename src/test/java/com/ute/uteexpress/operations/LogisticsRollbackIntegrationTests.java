package com.ute.uteexpress.operations;

import com.ute.uteexpress.dto.delivery.DeliveryCommands.*;
import com.ute.uteexpress.dto.shipment.*;
import com.ute.uteexpress.dto.transport.TripCommands.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import static com.ute.uteexpress.operations.OperationsJdbcFixture.*;
import static org.junit.jupiter.api.Assertions.*;

/** Fault injection touches only this test's H2 schema, never a migration or external database. */
@SpringJUnitConfig(LogisticsJourneyConfig.class)
class LogisticsRollbackIntegrationTests extends LogisticsJourneySupport {
    List<Object> snapshot(Long id) {
        return db(()->{
            var s=em.find(Shipment.class,id);var result=new ArrayList<Object>();
            result.addAll(Arrays.asList(s.getStatus(),s.getVersion(),s.getCurrentBranch()==null?null:s.getCurrentBranch().getId(),s.getDeliveryAttemptCount(),s.getDeliveredAt(),s.getReturnedAt()));
            for(Class<?> type:List.of(ShipmentEvent.class,TransportTripEvent.class,PickupEvidence.class,OriginHandoff.class,ShipmentIntake.class,TransportTripItem.class,TripReceipt.class,TripDiscrepancy.class,DeliveryAttempt.class,DeliveryProof.class,ReturnRequest.class,ReturnReceipt.class,Notification.class))result.add(count(type));
            result.add(em.createQuery("select h.status from OriginHandoff h order by h.id").getResultList().toString());
            result.add(em.createQuery("select a.status from ShipmentAssignment a order by a.id").getResultList().toString());
            result.add(em.createQuery("select r.stage from ReturnRequest r order by r.id").getResultList().toString());
            result.add(em.createQuery("select t.status,t.version from TransportTrip t order by t.id",Object[].class).getResultList().stream().map(Arrays::toString).toList());return result;
        });
    }
    @ParameterizedTest @ValueSource(strings={"PICKUP_PROOF","HOME_INTAKE","MANIFEST","DEPART_EVENT","PHYSICAL_RECEIPT","DISCREPANCY","DELIVERY_PROOF","DELIVERY_EVENT","RETURN_REQUEST","SENDER_RECEIPT","RETURN_EVENT"})
    void constraintFailureRollsBackEntireBusinessOperationIncludingHistoryAndOutbox(String scenario) {
        Long id;String table,check="1=0";Runnable operation;
        switch(scenario) {
            case "PICKUP_PROOF" -> {
                id=pickupAssigned();Long task=assignment(id,AssignmentType.PICKUP),v=shipmentVersion(id);as(pickupCourier,"ROLE_SHIPPER");
                operation=()->lifecycle.collectPickup(new PickupCollectionCommand(task,v,PickupResult.SUCCESS,true,"PROOF",null));table="pickup_evidence";
            }
            case "HOME_INTAKE" -> {
                id=collected();Long handoff=pendingOriginHandoff(id),v=shipmentVersion(id);operator(f.source);
                operation=()->lifecycle.reviewOriginHandoff(new HandoffReviewCommand(handoff,v,true,true,"INTAKE",null));table="shipment_intakes";
            }
            case "MANIFEST" -> {
                id=intake(ShipmentMethod.BRANCH_DROPOFF);operator(f.source);Long trip=transport.create(new Create(routes.get(key(f.source,f.hub)),token())),v=tripVersion(trip);
                operation=()->transport.addItems(new Manifest(trip,v,List.of(id)));table="transport_trip_items";
            }
            case "DEPART_EVENT" -> {
                id=intake(ShipmentMethod.BRANCH_DROPOFF);var leg=manifest(List.of(id),f.source,f.hub);transport.seal(change(leg.trip()));var c=change(leg.trip());
                operation=()->transport.depart(c);table="transport_trip_events";check="event_type <> 'DEPARTED'";
            }
            case "PHYSICAL_RECEIPT","DISCREPANCY" -> {
                id=intake(ShipmentMethod.BRANCH_DROPOFF);var leg=manifest(List.of(id),f.source,f.hub);depart(leg);arrive(leg);
                if(scenario.equals("PHYSICAL_RECEIPT")){var c=receipt(leg,id);operation=()->transport.receive(c);table="trip_receipts";}
                else {var c=new Discrepancy(leg.trip(),tripVersion(leg.trip()),leg.item(),TripDiscrepancyKind.MISSING,"MISSING","Investigate");operation=()->transport.recordDiscrepancy(c);table="trip_discrepancies";}
            }
            case "DELIVERY_PROOF","DELIVERY_EVENT" -> {
                id=atDestination();Long task=running(id);var c=success(id,task);operation=()->lastMile.complete(c);
                table=scenario.equals("DELIVERY_PROOF")?"delivery_proofs":"shipment_events";if(scenario.equals("DELIVERY_EVENT"))check="to_status <> 'DELIVERED'";
            }
            case "RETURN_REQUEST" -> {
                id=atDestination();operator(f.destination);var c=new ReturnStart(id,shipmentVersion(id),"Return requested");operation=()->lastMile.initiateReturn(c);table="return_requests";
            }
            default -> {
                id=atDestination();operator(f.destination);lastMile.initiateReturn(new ReturnStart(id,shipmentVersion(id),"Not delivered"));reverse(id);operator(f.source);
                var c=senderReceipt(id);operation=()->lastMile.returnToSender(c);table=scenario.equals("RETURN_EVENT")?"shipment_events":"return_receipts";if(scenario.equals("RETURN_EVENT"))check="to_status <> 'RETURNED'";
            }
        }
        var before=snapshot(id);
        constraint("ALTER TABLE dbo."+table+" ADD CONSTRAINT TEST_LOGISTICS_FAILURE CHECK ("+check+")");
        try {assertThrows(DataAccessException.class,operation::run);assertEquals(before,snapshot(id),"Atomic rollback for "+scenario);}
        finally {constraint("ALTER TABLE dbo."+table+" DROP CONSTRAINT TEST_LOGISTICS_FAILURE");}
        assertDoesNotThrow(operation::run,"Same operation should be recoverable after infrastructure failure");
    }
}
