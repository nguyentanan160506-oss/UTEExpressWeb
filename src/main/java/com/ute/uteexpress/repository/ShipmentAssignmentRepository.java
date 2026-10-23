package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.ShipmentAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ute.uteexpress.enums.*;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface ShipmentAssignmentRepository extends JpaRepository<ShipmentAssignment, Long> {
    @Query("select a.shipment.id from ShipmentAssignment a where a.id = :id and a.employee.id = :employeeId and a.assignmentType = :type")
    Optional<Long> findShipmentIdForOwner(Long id, Long employeeId, AssignmentType type);
    @Query("select a.shipment.id from ShipmentAssignment a where a.id = :id and a.employee.id = :employeeId and a.assignmentType = com.ute.uteexpress.enums.AssignmentType.PICKUP")
    Optional<Long> findPickupShipmentIdForOwner(Long id, Long employeeId);
    Optional<ShipmentAssignment> findByIdAndEmployeeIdAndAssignmentType(Long id, Long employeeId, AssignmentType type);
    Page<ShipmentAssignment> findByEmployeeIdAndAssignmentType(Long employeeId, AssignmentType type, Pageable pageable);
    long countByEmployeeIdAndAssignmentTypeAndStatus(Long employeeId, AssignmentType type, AssignmentStatus status);
    List<ShipmentAssignment> findByShipmentIdAndEmployeeIdAndAssignmentTypeAndStatusIn(
        Long shipmentId, Long employeeId, AssignmentType type, Collection<AssignmentStatus> statuses);
    boolean existsByShipmentIdAndStatusIn(Long shipmentId, Collection<AssignmentStatus> statuses);
    List<ShipmentAssignment> findByShipmentIdAndAssignmentTypeAndStatusIn(
        Long shipmentId, AssignmentType type, Collection<AssignmentStatus> statuses);
    @EntityGraph(attributePaths = {"employee", "assignedBy"})
    Page<ShipmentAssignment> findByShipmentId(Long shipmentId, Pageable pageable);
}
