package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.ShipmentAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ute.uteexpress.enums.*;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.EntityGraph;

public interface ShipmentAssignmentRepository extends JpaRepository<ShipmentAssignment, Long> {
    List<ShipmentAssignment> findByShipmentIdAndEmployeeIdAndAssignmentTypeAndStatusIn(
        Long shipmentId, Long employeeId, AssignmentType type, Collection<AssignmentStatus> statuses);
    boolean existsByShipmentIdAndStatusIn(Long shipmentId, Collection<AssignmentStatus> statuses);
    List<ShipmentAssignment> findByShipmentIdAndAssignmentTypeAndStatusIn(
        Long shipmentId, AssignmentType type, Collection<AssignmentStatus> statuses);
    @EntityGraph(attributePaths = {"employee", "assignedBy"})
    Page<ShipmentAssignment> findByShipmentId(Long shipmentId, Pageable pageable);
}
