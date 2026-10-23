package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.DeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.*;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, Long> {
    long countByShipmentId(Long shipmentId);
    @Query("select coalesce(max(a.attemptNumber), 0) from DeliveryAttempt a where a.shipment.id = :shipmentId")
    Integer maxAttemptNumber(Long shipmentId);
    Page<DeliveryAttempt> findByShipmentId(Long shipmentId, Pageable pageable);
    Page<DeliveryAttempt> findByAssignmentId(Long assignmentId, Pageable pageable);
}
