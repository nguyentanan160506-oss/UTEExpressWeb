package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.TransportRoute;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface TransportRouteRepository extends JpaRepository<TransportRoute, Long> {
    @Query(value = """
        select r from TransportRoute r where r.active = true and r.origin.status = com.ute.uteexpress.enums.BranchStatus.ACTIVE
        and r.destination.status = com.ute.uteexpress.enums.BranchStatus.ACTIVE
        and exists (select m.id from ManagerBranch m where m.manager.id = :managerId and m.branch.id = r.origin.id)
        """, countQuery = """
        select count(r) from TransportRoute r where r.active = true and r.origin.status = com.ute.uteexpress.enums.BranchStatus.ACTIVE
        and r.destination.status = com.ute.uteexpress.enums.BranchStatus.ACTIVE
        and exists (select m.id from ManagerBranch m where m.manager.id = :managerId and m.branch.id = r.origin.id)
        """)
    Page<TransportRoute> findAvailable(Long managerId, Pageable pageable);
}
