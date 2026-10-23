package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.DeliveryDispatch;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.*;
import com.ute.uteexpress.entity.Shipment;
import com.ute.uteexpress.enums.ShipmentStatus;

public interface DeliveryDispatchRepository extends JpaRepository<DeliveryDispatch, Long> {
    String SCOPE = """
         exists (select m.id from ManagerBranch m where m.manager.id = :managerId and m.branch.status = com.ute.uteexpress.enums.BranchStatus.ACTIVE
         and (m.branch.id = s.destinationBranch.id or (m.branch.id = s.originBranch.id and exists (select r.id from ReturnRequest r where r.shipment.id = s.id))))
        """;
    @Query(value = "select s from Shipment s where " + SCOPE + " and s.status in (com.ute.uteexpress.enums.ShipmentStatus.AT_DESTINATION_BRANCH, com.ute.uteexpress.enums.ShipmentStatus.OUT_FOR_DELIVERY, com.ute.uteexpress.enums.ShipmentStatus.DELIVERY_FAILED, com.ute.uteexpress.enums.ShipmentStatus.RETURNING, com.ute.uteexpress.enums.ShipmentStatus.RETURNED, com.ute.uteexpress.enums.ShipmentStatus.DELIVERED) and (:status is null or s.status = :status) and (:tracking is null or s.trackingNumber = :tracking)",
        countQuery = "select count(s) from Shipment s where " + SCOPE + " and s.status in (com.ute.uteexpress.enums.ShipmentStatus.AT_DESTINATION_BRANCH, com.ute.uteexpress.enums.ShipmentStatus.OUT_FOR_DELIVERY, com.ute.uteexpress.enums.ShipmentStatus.DELIVERY_FAILED, com.ute.uteexpress.enums.ShipmentStatus.RETURNING, com.ute.uteexpress.enums.ShipmentStatus.RETURNED, com.ute.uteexpress.enums.ShipmentStatus.DELIVERED) and (:status is null or s.status = :status) and (:tracking is null or s.trackingNumber = :tracking)")
    Page<Shipment> findManaged(Long managerId, ShipmentStatus status, String tracking, Pageable pageable);
    @Query("select s from Shipment s where s.id = :id and " + SCOPE)
    Optional<Shipment> findManagedShipment(Long id, Long managerId);
    Optional<DeliveryDispatch> findByAssignmentId(Long assignmentId);
    Optional<DeliveryDispatch> findFirstByShipmentIdAndAcceptedAtIsNotNullOrderByAcceptedAtDescIdDesc(Long shipmentId);
}
