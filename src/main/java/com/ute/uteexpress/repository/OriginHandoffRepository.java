package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.OriginHandoff;
import com.ute.uteexpress.enums.HandoffStatus;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface OriginHandoffRepository extends JpaRepository<OriginHandoff, Long> {
    boolean existsByShipmentIdAndStatus(Long shipmentId, HandoffStatus status);
    long countByEmployeeIdAndStatus(Long employeeId, HandoffStatus status);
    Page<OriginHandoff> findByAssignmentId(Long assignmentId, Pageable pageable);
    @Query("select h.shipment.id from OriginHandoff h where h.id = :id")
    Optional<Long> findShipmentId(Long id);
    @Query(value = """
        select h from OriginHandoff h where h.status = :status and h.branch.status = com.ute.uteexpress.enums.BranchStatus.ACTIVE
        and exists (select m.id from ManagerBranch m where m.manager.id = :managerId and m.branch.id = h.branch.id)
        """, countQuery = """
        select count(h) from OriginHandoff h where h.status = :status and h.branch.status = com.ute.uteexpress.enums.BranchStatus.ACTIVE
        and exists (select m.id from ManagerBranch m where m.manager.id = :managerId and m.branch.id = h.branch.id)
        """)
    Page<OriginHandoff> findManaged(Long managerId, HandoffStatus status, Pageable pageable);
}
