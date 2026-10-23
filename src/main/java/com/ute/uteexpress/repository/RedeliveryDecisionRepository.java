package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.RedeliveryDecision;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RedeliveryDecisionRepository extends JpaRepository<RedeliveryDecision, Long> {
    boolean existsByShipmentIdAndAfterAttemptNumber(Long shipmentId, Integer afterAttemptNumber);
    Page<RedeliveryDecision> findByShipmentId(Long shipmentId, Pageable pageable);
}
