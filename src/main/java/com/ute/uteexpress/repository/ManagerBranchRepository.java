package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.ManagerBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ute.uteexpress.entity.Branch;
import com.ute.uteexpress.enums.BranchStatus;
import java.util.List;

public interface ManagerBranchRepository extends JpaRepository<ManagerBranch, Long> {
    boolean existsByManagerIdAndBranchId(Long managerId, Long branchId);
    @Query("select m.branch from ManagerBranch m where m.manager.id = :managerId and m.branch.status = :status order by m.branch.name")
    List<Branch> findManagedBranches(@Param("managerId") Long managerId, @Param("status") BranchStatus status);
}
