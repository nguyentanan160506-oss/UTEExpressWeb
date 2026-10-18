package com.ute.uteexpress.manager;

import com.ute.uteexpress.dto.manager.ManagerViews.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.shipment.ShipmentFixtures;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;

/** Isolated synthetic test data only; never persisted to SQL Server. */
public final class ManagerFixtures {
    private ManagerFixtures() { }
    public static User manager() {
        User user = ShipmentFixtures.owner(); user.setEmail("manager-fixture@example.test");
        user.setUsername("manager-fixture"); user.setFullName("Quản lý thử nghiệm"); return user;
    }
    public static Employee courier(Long id) {
        User user = ShipmentFixtures.owner(); user.setId(id + 100); user.setEmail("courier" + id + "@example.test");
        Employee employee = new Employee(); employee.setId(id); employee.setUser(user); employee.setEmployeeCode("EMP" + id);
        employee.setFullName("Shipper thử nghiệm " + id); employee.setBranch(ShipmentFixtures.branch(11L));
        employee.setPosition(EmployeePosition.COURIER_DRIVER); employee.setStatus(EmployeeStatus.ACTIVE); return employee;
    }
    public static Employee receiver() {
        Employee employee = courier(22L); employee.setUser(manager()); employee.setPosition(EmployeePosition.DISPATCHER); return employee;
    }
    public static Shipment shipment(ShipmentStatus status, ShipmentMethod method) {
        Shipment shipment = ShipmentFixtures.shipment(); shipment.setStatus(status); shipment.setShipmentMethod(method);
        ReflectionTestUtils.setField(shipment, "version", 0L); return shipment;
    }
    public static Detail detail(ShipmentStatus status, ShipmentMethod method, boolean intake) {
        var old = ShipmentFixtures.detail();
        Row row = new Row(31L, "UTE_TEST_TRACKING", status, method, 11L, "Chi nhánh thử nghiệm 11", ShipmentFixtures.NOW);
        boolean assigning = status == ShipmentStatus.CONFIRMED && method == ShipmentMethod.HOME_PICKUP;
        return new Detail(row, 0L, old.sender(), old.receiver(), old.destinationBranch(), old.packageDescription(),
            old.weightKg(), old.shippingFee(), null, old.insuranceFee(), old.totalFee(),
            old.events(), Page.empty(), assigning ? new PageImpl<>(List.of(new EmployeeOption(21L, "EMP21", "Shipper thử nghiệm"))) : Page.empty(),
            null, status == ShipmentStatus.CREATED, status == ShipmentStatus.CREATED || status == ShipmentStatus.CONFIRMED,
            assigning, false, null, intake);
    }
}
