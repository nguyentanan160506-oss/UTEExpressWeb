package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.PickupEvidence;
import com.ute.uteexpress.enums.PickupResult;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PickupEvidenceRepository extends JpaRepository<PickupEvidence, Long> {
    Optional<PickupEvidence> findByAssignmentIdAndResult(Long assignmentId, PickupResult result);
    Page<PickupEvidence> findByAssignmentId(Long assignmentId, Pageable pageable);
}
