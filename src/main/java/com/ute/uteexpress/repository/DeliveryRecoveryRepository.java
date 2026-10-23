package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.DeliveryRecovery;
import com.ute.uteexpress.enums.DeliveryRecoveryStatus;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryRecoveryRepository extends JpaRepository<DeliveryRecovery, Long> {
    boolean existsByShipmentIdAndStatus(Long shipmentId, DeliveryRecoveryStatus status);
    boolean existsByAssignmentIdAndStatus(Long assignmentId, DeliveryRecoveryStatus status);
    Optional<DeliveryRecovery> findByIdAndShipmentId(Long id, Long shipmentId);
    Page<DeliveryRecovery> findByShipmentId(Long shipmentId, Pageable pageable);
    Page<DeliveryRecovery> findByAssignmentId(Long assignmentId, Pageable pageable);
}
