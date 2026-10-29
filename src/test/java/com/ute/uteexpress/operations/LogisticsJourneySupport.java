package com.ute.uteexpress.operations;

import com.ute.uteexpress.dto.delivery.DeliveryCommands.*;
import com.ute.uteexpress.dto.shipment.*;
import com.ute.uteexpress.dto.transport.TripCommands.*;
import com.ute.uteexpress.dto.admin.ReportViews;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.*;
import com.ute.uteexpress.service.reports.AdminReportService;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import static com.ute.uteexpress.operations.OperationsJdbcFixture.*;
import static org.junit.jupiter.api.Assertions.*;

/** Per-test isolated network. All shipment/evidence/custody changes go through business services. */
abstract class LogisticsJourneySupport extends CustomerJourneySupport {
    @Autowired ShipperPickupService pickupRead;
    @Autowired DeliveryReadService deliveryRead;
    @Autowired ManagerLogisticsService managerRead;
    @Autowired TransportTripReadService tripRead;
    @Autowired AdminReportService reports;
    Branch hubB;
    User hubManagerA, hubManagerB, pickupCourier, replacementCourier, unauthorizedCourier, inactiveUser;
    Employee pickupEmployee, replacementEmployee, inactiveEmployee;
    final Map<Long,User> operators=new HashMap<>();
    final Map<String,Long> routes=new HashMap<>();
    record Leg(Long trip,Long item,Branch from,Branch to) { }

    @BeforeEach void logisticsNetwork() {
        db(()->{
            hubB=f.branch("HUB_DEST",BranchType.CENTRAL_HUB);
            em.find(Branch.class,f.destination.getId()).setParentHub(hubB);
            hubManagerA=f.user("hub-origin-manager","ROLE_MANAGER");f.employee(hubManagerA,f.hub,EmployeePosition.WAREHOUSE_STAFF);f.membership(hubManagerA,f.hub);
            hubManagerB=f.user("hub-dest-manager","ROLE_MANAGER");f.employee(hubManagerB,hubB,EmployeePosition.WAREHOUSE_STAFF);f.membership(hubManagerB,hubB);
            pickupCourier=f.user("pickup-courier","ROLE_SHIPPER");pickupEmployee=f.employee(pickupCourier,f.source,EmployeePosition.COURIER_DRIVER);
            replacementCourier=f.user("replacement-courier","ROLE_SHIPPER");replacementEmployee=f.employee(replacementCourier,f.source,EmployeePosition.COURIER_DRIVER);
            unauthorizedCourier=f.user("foreign-courier","ROLE_SHIPPER");f.employee(unauthorizedCourier,f.foreign,EmployeePosition.COURIER_DRIVER);
            inactiveUser=f.user("inactive-courier","ROLE_SHIPPER");inactiveUser.setStatus(UserStatus.LOCKED);
            inactiveEmployee=f.employee(inactiveUser,f.source,EmployeePosition.COURIER_DRIVER);inactiveEmployee.setStatus(EmployeeStatus.INACTIVE);
            route(f.source,f.hub,TransportLeg.COLLECTION);route(f.hub,hubB,TransportLeg.LINEHAUL);route(hubB,f.destination,TransportLeg.DISTRIBUTION);
            route(f.destination,hubB,TransportLeg.COLLECTION);route(hubB,f.hub,TransportLeg.LINEHAUL);route(f.hub,f.source,TransportLeg.DISTRIBUTION);
            em.flush();return null;
        });
        operators.put(f.source.getId(),f.manager);operators.put(f.hub.getId(),hubManagerA);
        operators.put(hubB.getId(),hubManagerB);operators.put(f.destination.getId(),f.destinationManager);
        as(f.owner,"ROLE_USER");
    }
    void route(Branch from,Branch to,TransportLeg leg) {
        var r=new TransportRoute();r.setOrigin(from);r.setDestination(to);r.setLeg(leg);em.persist(r);em.flush();routes.put(key(from,to),r.getId());
    }
    String key(Branch from,Branch to) {return from.getId()+":"+to.getId();}
    String token() {return UUID.randomUUID().toString().replace("-","");}
    void operator(Branch branch) {as(operators.get(branch.getId()),"ROLE_MANAGER");}
    Long created(ShipmentMethod method) {as(f.owner,"ROLE_USER");var input=form();input.setShipmentMethod(method);return quoteService.confirm(quoteService.quote(input));}
    Long confirmed(ShipmentMethod method) {Long id=created(method);operator(f.source);lifecycle.transition(new ShipmentTransitionCommand(id,shipmentVersion(id),ShipmentStatus.CONFIRMED));assertCustody(id,null,ShipmentStatus.CONFIRMED);return id;}
    Long pickupAssigned() {Long id=confirmed(ShipmentMethod.HOME_PICKUP);lifecycle.assignPickup(new PickupAssignmentCommand(id,shipmentVersion(id),pickupEmployee.getId(),null,null));return id;}
    Long assignment(Long id,AssignmentType type) {return db(()->em.createQuery("select a.id from ShipmentAssignment a where a.shipment.id=:id and a.assignmentType=:type and a.completedAt is null",Long.class).setParameter("id",id).setParameter("type",type).getSingleResult());}
    Long collected() {Long id=pickupAssigned();collect(id);return id;}
    void collect(Long id) {as(pickupCourier,"ROLE_SHIPPER");lifecycle.collectPickup(new PickupCollectionCommand(assignment(id,AssignmentType.PICKUP),shipmentVersion(id),PickupResult.SUCCESS,true,"PICKUP-PROOF",null));assertCustody(id,null,ShipmentStatus.PICKED_UP);}
    Long pendingOriginHandoff(Long id) {as(pickupCourier,"ROLE_SHIPPER");lifecycle.requestOriginHandoff(new OriginHandoffCommand(assignment(id,AssignmentType.PICKUP),shipmentVersion(id),null));return db(()->em.createQuery("select h.id from OriginHandoff h where h.shipment.id=:id and h.status=:status",Long.class).setParameter("id",id).setParameter("status",HandoffStatus.PENDING).getSingleResult());}
    Long intake(ShipmentMethod method) {
        Long id;
        if(method==ShipmentMethod.HOME_PICKUP) {id=collected();Long handoff=pendingOriginHandoff(id);operator(f.source);lifecycle.reviewOriginHandoff(new HandoffReviewCommand(handoff,shipmentVersion(id),true,true,null));}
        else {id=confirmed(method);lifecycle.receiveDropoff(new BranchIntakeCommand(id,shipmentVersion(id),true,null));}
        assertCustody(id,f.source,ShipmentStatus.AT_ORIGIN_BRANCH);return id;
    }
    Leg manifest(List<Long> ids,Branch from,Branch to) {
        operator(from);Long trip=transport.create(new Create(routes.get(key(from,to)),token()));
        transport.addItems(new Manifest(trip,tripVersion(trip),ids));
        return new Leg(trip,item(trip,ids.getFirst()),from,to);
    }
    Long item(Long trip,Long shipment) {return db(()->em.createQuery("select i.id from TransportTripItem i where i.trip.id=:trip and i.shipment.id=:shipment",Long.class).setParameter("trip",trip).setParameter("shipment",shipment).getSingleResult());}
    Long tripVersion(Long trip) {return db(()->em.find(TransportTrip.class,trip).getVersion());}
    Change change(Long trip) {return new Change(trip,tripVersion(trip),true,"TRIP-REFERENCE",null);}
    void depart(Leg leg) {operator(leg.from());transport.seal(change(leg.trip()));transport.depart(change(leg.trip()));}
    void arrive(Leg leg) {operator(leg.to());transport.arrive(change(leg.trip()));}
    Receive receipt(Leg leg,Long shipment) {return new Receive(leg.trip(),tripVersion(leg.trip()),item(leg.trip(),shipment),true,"PHYSICAL-RECEIPT",null,false);}
    void travel(Long id,Branch from,Branch to,boolean reverse) {
        var leg=manifest(List.of(id),from,to);depart(leg);
        assertCustody(id,null,reverse?ShipmentStatus.RETURNING:ShipmentStatus.IN_TRANSIT);
        long before=count(TripReceipt.class);arrive(leg);assertEquals(before,count(TripReceipt.class),"Vehicle arrival must not fabricate parcel receipt");
        assertCustody(id,null,reverse?ShipmentStatus.RETURNING:ShipmentStatus.IN_TRANSIT);
        transport.receive(receipt(leg,id));transport.close(change(leg.trip()));
        db(()->{assertEquals(to.getId(),em.find(Shipment.class,id).getCurrentBranch().getId());assertEquals(TripStatus.CLOSED,em.find(TransportTrip.class,leg.trip()).getStatus());return null;});
    }
    void forward(Long id) {travel(id,f.source,f.hub,false);travel(id,f.hub,hubB,false);travel(id,hubB,f.destination,false);assertCustody(id,f.destination,ShipmentStatus.AT_DESTINATION_BRANCH);}
    Long atDestination() {Long id=intake(ShipmentMethod.BRANCH_DROPOFF);forward(id);return id;}
    Long deliveryEmployee() {return db(()->em.createQuery("select e.id from Employee e where e.user.id=:id",Long.class).setParameter("id",f.shipper.getId()).getSingleResult());}
    Long running(Long id) {
        operator(f.destination);lastMile.assign(new Assign(id,shipmentVersion(id),deliveryEmployee(),null,null));Long task=assignment(id,AssignmentType.DELIVERY);
        lastMile.prepare(new Prepare(id,shipmentVersion(id),task,true,"DELIVERY-HANDOFF",null));
        assertCustody(id,f.destination,ShipmentStatus.AT_DESTINATION_BRANCH);as(f.shipper,"ROLE_SHIPPER");
        lastMile.accept(new Action(task,shipmentVersion(id),true,"COURIER-RECEIPT"));assertCustody(id,null,ShipmentStatus.AT_DESTINATION_BRANCH);
        lastMile.start(new Action(task,shipmentVersion(id),true,"START-ATTEMPT"));assertCustody(id,null,ShipmentStatus.OUT_FOR_DELIVERY);return task;
    }
    Complete success(Long id,Long task) {return new Complete(task,shipmentVersion(id),true,DeliveryRecipientType.RECIPIENT,"Synthetic recipient","DELIVERY-PROOF",null);}
    void fail(Long id,Long task) {as(f.shipper,"ROLE_SHIPPER");lastMile.fail(new Fail(task,shipmentVersion(id),true,DeliveryFailureCode.ABSENT,"Recipient absent",null));assertCustody(id,null,ShipmentStatus.DELIVERY_FAILED);}
    void recover(Long id,Long task) {
        as(f.shipper,"ROLE_SHIPPER");lastMile.requestRecovery(new Recover(task,shipmentVersion(id),null));
        Long recovery=db(()->em.createQuery("select r.id from DeliveryRecovery r where r.shipment.id=:id and r.status=:status",Long.class).setParameter("id",id).setParameter("status",DeliveryRecoveryStatus.PENDING).getSingleResult());
        operator(f.destination);lastMile.reviewRecovery(new ReviewRecovery(id,shipmentVersion(id),recovery,true,true,"RECOVERY-RECEIPT",null));
    }
    void reverse(Long id) {travel(id,f.destination,hubB,true);travel(id,hubB,f.hub,true);travel(id,f.hub,f.source,true);assertCustody(id,f.source,ShipmentStatus.RETURNING);}
    SenderReceipt senderReceipt(Long id) {return new SenderReceipt(id,shipmentVersion(id),true,ReturnRecipientType.SENDER,"Synthetic sender","SENDER-RECEIPT",null);}
    void assertCustody(Long id,Branch branch,ShipmentStatus status) {db(()->{var s=em.find(Shipment.class,id);assertEquals(status,s.getStatus(),"Shipment status for "+id);assertEquals(branch==null?null:branch.getId(),s.getCurrentBranch()==null?null:s.getCurrentBranch().getId(),"Custody for "+id);return null;});}
    ReportViews.Dashboard report(ReportViews.Dataset dataset) {as(f.admin,"ROLE_ADMIN");return reports.read(new ReportViews.Request(dataset,ReportViews.Period.TODAY,null,null,null,null,null,ReportViews.Trend.DAY),0,50);}
    String metric(ReportViews.Dashboard report,String label) {return report.metrics().stream().filter(m->label.equals(m.label())).findFirst().orElseThrow().value();}
}
