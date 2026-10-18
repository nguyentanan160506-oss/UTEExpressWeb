package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.ShipmentAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ute.uteexpress.enums.*;
import java.util.Collection;
import java.util.List;

public interface ShipmentAssignmentRepository extends JpaRepository<ShipmentAssignment, Long> {
    List<ShipmentAssignment> findByShipmentIdAndEmployeeIdAndAssignmentTypeAndStatusIn(
        Long shipmentId, Long employeeId, AssignmentType type, Collection<AssignmentStatus> statuses);
    boolean existsByShipmentIdAndStatusIn(Long shipmentId, Collection<AssignmentStatus> statuses);
}
