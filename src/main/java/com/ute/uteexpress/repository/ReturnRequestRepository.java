package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.ReturnRequest;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {
    Optional<ReturnRequest> findByShipmentId(Long shipmentId);
    boolean existsByShipmentId(Long shipmentId);
}
