package com.ute.uteexpress.operations;

import com.ute.uteexpress.dto.delivery.DeliveryCommands.*;
import com.ute.uteexpress.dto.shipment.*;
import com.ute.uteexpress.dto.transport.TripCommands.*;
import com.ute.uteexpress.dto.admin.ReportViews.Dataset;
import com.ute.uteexpress.dto.operations.OperationsDashboardViews.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.LogisticsRuleException;
import com.ute.uteexpress.service.notifications.NotificationWorker;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import static com.ute.uteexpress.operations.OperationsJdbcFixture.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(LogisticsJourneyConfig.class)
class LogisticsFullJourneyTests extends LogisticsJourneySupport {
    @Autowired NotificationWorker worker;
    @ParameterizedTest @EnumSource(ShipmentMethod.class)
    void realOriginIntakeThreeForwardLegsAndDeliveryPreserveUniqueShipmentAndPrice(ShipmentMethod method) {
        Long id=intake(method);forward(id);Long task=running(id);lastMile.complete(success(id,task));
        assertCustody(id,null,ShipmentStatus.DELIVERED);assertEquals(1,count(Shipment.class));assertEquals(3,count(TripReceipt.class));assertEquals(3,count(TransportTrip.class));
        assertEquals(1,count(DeliveryProof.class));assertEquals(1,count(DeliveryAttempt.class));
        db(()->{var a=em.find(ShipmentAssignment.class,task);assertEquals(AssignmentStatus.COMPLETED,a.getStatus());assertNotNull(a.getCompletedAt());return null;});
        as(f.owner,"ROLE_USER");var view=userShipments.detail(id,0);
        assertEquals(new BigDecimal(method==ShipmentMethod.HOME_PICKUP?"52500.00":"45000.00"),view.totalFee());assertEquals(ShipmentStatus.DELIVERED,view.status());
        operator(f.destination);var d=dashboard.read(f.destination.getId(),Period.TODAY,null,null,null,WorkGroup.DELIVERY,0);assertEquals(1,d.deliverySuccess());assertEquals(0,d.atDestination());
        var trips=report(Dataset.TRIPS);assertEquals(3,trips.details().total());assertEquals("3",metric(trips,"Lượt vận chuyển kiện/chặng"));assertEquals("1",metric(trips,"Vận đơn duy nhất qua các chặng"));
        assertEquals(1,report(Dataset.SHIPMENTS).details().total());assertEquals(1,report(Dataset.DELIVERED).details().total());
        worker.poll();long noticeCount=notices.count();worker.poll();assertEquals(noticeCount,notices.count());
        as(f.owner,"ROLE_USER");assertTrue(inbox.list(0,50,false).items().stream().anyMatch(n->n.title().contains("giao kiện")));
        as(f.other,"ROLE_USER");assertEquals(0,inbox.count());
    }
    @Test void failedDeliveryPhysicalRecoveryNewCourierAssignmentThenSuccessRetainsAttemptHistory() {
        Long id=atDestination();Long first=running(id);fail(id,first);recover(id,first);
        assertCustody(id,f.destination,ShipmentStatus.DELIVERY_FAILED);
        lastMile.redelivery(new Redelivery(id,shipmentVersion(id),deliveryEmployee(),"Recovered parcel can be dispatched again"));
        Long second=assignment(id,AssignmentType.DELIVERY);assertNotEquals(first,second);
        lastMile.prepare(new Prepare(id,shipmentVersion(id),second,true,"SECOND-HANDOFF",null));as(f.shipper,"ROLE_SHIPPER");
        lastMile.accept(new Action(second,shipmentVersion(id),true,"SECOND-ACCEPT"));lastMile.start(new Action(second,shipmentVersion(id),true,"SECOND-START"));lastMile.complete(success(id,second));
        db(()->{var attempts=em.createQuery("select a from DeliveryAttempt a order by a.attemptNumber",DeliveryAttempt.class).getResultList();
            assertEquals(List.of(1,2),attempts.stream().map(DeliveryAttempt::getAttemptNumber).toList());assertEquals(List.of(DeliveryAttemptStatus.FAILED,DeliveryAttemptStatus.SUCCESS),attempts.stream().map(DeliveryAttempt::getStatus).toList());return null;});
        operator(f.destination);var d=dashboard.read(f.destination.getId(),Period.TODAY,null,null,null,WorkGroup.DELIVERY,0);assertEquals(1,d.failedAttempts());assertEquals(1,d.failedShipments());assertEquals(1,d.deliverySuccess());
        var report=report(Dataset.DELIVERIES);assertEquals(2,report.details().total());assertEquals("1",metric(report,"Vận đơn có FAILED"));
    }
    @Test void failureSameCourierRedeliveryPreservesCustodyUntilSuccessfulProof() {
        Long id=atDestination();Long task=running(id);fail(id,task);operator(f.destination);
        lastMile.redelivery(new Redelivery(id,shipmentVersion(id),deliveryEmployee(),"Retry with current custodian"));assertCustody(id,null,ShipmentStatus.DELIVERY_FAILED);
        as(f.shipper,"ROLE_SHIPPER");lastMile.start(new Action(task,shipmentVersion(id),true,"RETRY-START"));lastMile.complete(success(id,task));assertEquals(2,count(DeliveryAttempt.class));assertEquals(1,count(ShipmentAssignment.class));
    }
    @Test void failedDeliveryReturnRecoveryThreeReverseLegsAndFinalSenderReceiptAreDistinct() {
        Long id=atDestination();Long task=running(id);fail(id,task);operator(f.destination);
        lastMile.initiateReturn(new ReturnStart(id,shipmentVersion(id),"Return requested after failed delivery"));
        assertCustody(id,null,ShipmentStatus.RETURNING);assertEquals(0,count(ReturnReceipt.class));
        assertEquals(0,report(Dataset.RETURNED).details().total());assertEquals(1,report(Dataset.RETURN_REQUESTS).details().total());
        recover(id,task);reverse(id);
        db(()->{var r=em.createQuery("select r from ReturnRequest r",ReturnRequest.class).getSingleResult();assertEquals(ReturnStage.AT_ORIGIN,r.getStage());assertNull(em.find(Shipment.class,id).getReturnedAt());return null;});
        assertEquals(0,report(Dataset.RETURNED).details().total());operator(f.source);lastMile.returnToSender(senderReceipt(id));
        assertCustody(id,null,ShipmentStatus.RETURNED);assertEquals(6,count(TripReceipt.class));assertEquals(6,count(TransportTrip.class));assertEquals(1,count(ReturnReceipt.class));
        assertEquals(1,report(Dataset.RETURNED).details().total());assertEquals(0,report(Dataset.DELIVERED).details().total());assertEquals(0,report(Dataset.PENDING_RETURNS).details().total());
        var trips=report(Dataset.TRIPS);assertEquals("6",metric(trips,"Lượt vận chuyển kiện/chặng"));assertEquals("1",metric(trips,"Vận đơn duy nhất qua các chặng"));
        as(f.owner,"ROLE_USER");assertEquals(new BigDecimal("45000.00"),userShipments.detail(id,0).totalFee());worker.poll();assertTrue(inbox.list(0,50,false).items().stream().anyMatch(n->n.title().contains("trả kiện")));
    }
    @Test void partialManifestReceiptMissingDiscrepancyResolutionAndCloseKeepCustodyAccurate() {
        var ids=List.of(intake(ShipmentMethod.BRANCH_DROPOFF),intake(ShipmentMethod.BRANCH_DROPOFF),intake(ShipmentMethod.BRANCH_DROPOFF));
        var leg=manifest(ids,f.source,f.hub);depart(leg);arrive(leg);
        transport.receive(receipt(leg,ids.get(0)));transport.receive(receipt(leg,ids.get(1)));
        Long missing=ids.get(2);transport.recordDiscrepancy(new Discrepancy(leg.trip(),tripVersion(leg.trip()),item(leg.trip(),missing),TripDiscrepancyKind.MISSING,"MISSING-REF","Internal investigation"));
        assertCustody(missing,null,ShipmentStatus.IN_TRANSIT);assertEquals(2,count(TripReceipt.class));assertThrows(LogisticsRuleException.class,()->transport.close(change(leg.trip())));
        operator(f.hub);assertEquals(1,dashboard.read(f.hub.getId(),Period.TODAY,null,null,null,WorkGroup.DISPATCH,0).openDiscrepancies());assertEquals(1,report(Dataset.OPEN_DISCREPANCIES).details().total());
        operator(f.hub);assertThrows(LogisticsRuleException.class,()->transport.receive(receipt(leg,missing)));
        transport.receive(new Receive(leg.trip(),tripVersion(leg.trip()),item(leg.trip(),missing),true,"FOUND-RECEIPT","Physically located and checked",true));transport.close(change(leg.trip()));
        assertCustody(missing,f.hub,ShipmentStatus.AT_ORIGIN_HUB);assertEquals(3,count(TripReceipt.class));assertEquals(0,report(Dataset.OPEN_DISCREPANCIES).details().total());assertEquals(1,report(Dataset.DISCREPANCIES).details().total());
        as(f.owner,"ROLE_USER");String code=userShipments.detail(missing,0).trackingNumber();assertFalse(trackingService.lookup(code).orElseThrow().toString().contains("Internal investigation"));
    }
}
