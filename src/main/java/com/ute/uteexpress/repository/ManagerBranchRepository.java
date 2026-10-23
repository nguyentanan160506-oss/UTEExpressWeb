package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.ManagerBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ute.uteexpress.entity.Branch;
import com.ute.uteexpress.enums.BranchStatus;
import java.util.List;

public interface ManagerBranchRepository extends JpaRepository<ManagerBranch, Long> {
    @Query("select m.manager from ManagerBranch m where m.branch.id = :branchId and m.branch.status = com.ute.uteexpress.enums.BranchStatus.ACTIVE and m.manager.status = com.ute.uteexpress.enums.UserStatus.ACTIVE and exists (select r.id from UserRole r where r.user.id = m.manager.id and r.role.name = 'ROLE_MANAGER') order by m.manager.id")
    List<com.ute.uteexpress.entity.User> findNotificationManagers(Long branchId, org.springframework.data.domain.Pageable page);
    boolean existsByManagerIdAndBranchId(Long managerId, Long branchId);
    @Query("select m.branch from ManagerBranch m where m.manager.id = :managerId and m.branch.status = :status order by m.branch.name")
    List<Branch> findManagedBranches(@Param("managerId") Long managerId, @Param("status") BranchStatus status);
}
