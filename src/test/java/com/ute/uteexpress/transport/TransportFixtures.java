package com.ute.uteexpress.transport;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.manager.ManagerFixtures;
import com.ute.uteexpress.shipment.ShipmentFixtures;

/** Synthetic, isolated fixtures. No real database data or credentials. */
public final class TransportFixtures {
    private TransportFixtures() { }
    public static Branch point(long id, BranchType type) {
        Branch b = ShipmentFixtures.branch(id); b.setBranchType(type); return b;
    }
    public static User actor(Branch b) {
        User u = ManagerFixtures.manager(); u.setId(1000 + b.getId());
        u.setEmail("transport" + b.getId() + "@example.test"); u.setFullName("Nhân viên điểm " + b.getId()); return u;
    }
    public static Employee employee(Branch b) {
        Employee e = new Employee(); e.setId(2000 + b.getId()); e.setBranch(b); e.setUser(actor(b));
        e.setPosition(EmployeePosition.WAREHOUSE_STAFF); e.setStatus(EmployeeStatus.ACTIVE); e.setFullName(e.getUser().getFullName()); return e;
    }
    public static Shipment parcel(long id, Branch origin, Branch destination) {
        Shipment s = ManagerFixtures.shipment(ShipmentStatus.AT_ORIGIN_BRANCH, ShipmentMethod.BRANCH_DROPOFF);
        s.setId(id); s.setTrackingNumber("UTE" + String.format("%032X", id)); s.setOriginBranch(origin); s.setDestinationBranch(destination);
        s.setOriginHub(origin.getParentHub()); s.setDestinationHub(destination.getParentHub()); s.setCurrentBranch(origin); return s;
    }
    public static ShipmentIntake intake(Shipment s) {
        ShipmentIntake r = new ShipmentIntake(); r.setId(3000 + s.getId()); r.setShipment(s); r.setBranch(s.getOriginBranch());
        r.setShipmentMethod(s.getShipmentMethod()); r.setReceivedBy(actor(s.getOriginBranch())); r.setEmployee(employee(s.getOriginBranch()));
        r.setReceiptReference("ORIGIN-" + s.getId());
        if (s.getShipmentMethod() == ShipmentMethod.HOME_PICKUP) {
            ShipmentAssignment pickup = new ShipmentAssignment(); pickup.setId(4000 + s.getId()); pickup.setShipment(s);
            pickup.setAssignmentType(AssignmentType.PICKUP); pickup.setStatus(AssignmentStatus.COMPLETED);
            pickup.setCompletedAt(ShipmentFixtures.NOW); r.setPickupAssignment(pickup);
        }
        return r;
    }
    public static TransportRoute route(long id, Branch origin, Branch destination, TransportLeg leg) {
        TransportRoute r = new TransportRoute(); r.setId(id); r.setOrigin(origin); r.setDestination(destination); r.setLeg(leg); return r;
    }
}
