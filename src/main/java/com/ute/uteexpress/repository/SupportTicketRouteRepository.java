package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.SupportTicketRoute;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface SupportTicketRouteRepository extends JpaRepository<SupportTicketRoute,Long> {
    @Lock(LockModeType.PESSIMISTIC_READ)
    @EntityGraph(attributePaths={"originBranch","handlingBranch"})
    Optional<SupportTicketRoute> findByOriginBranchIdAndActiveTrue(Long originBranchId);
}
