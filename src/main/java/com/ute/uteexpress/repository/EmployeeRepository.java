package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    @EntityGraph(attributePaths = {"user", "branch"})
    Optional<Employee> findByUserId(Long userId);
    @EntityGraph(attributePaths = {"user", "branch"})
    Optional<Employee> findByIdAndBranchId(Long id, Long branchId);

    @Query(value = "select e from Employee e join fetch e.user u where e.branch.id = :branchId "
        + "and e.status = com.ute.uteexpress.enums.EmployeeStatus.ACTIVE "
        + "and e.position = com.ute.uteexpress.enums.EmployeePosition.COURIER_DRIVER "
        + "and u.status = com.ute.uteexpress.enums.UserStatus.ACTIVE "
        + "and exists (select r.id from UserRole r where r.user.id = u.id and r.role.name = 'ROLE_SHIPPER')",
        countQuery = "select count(e) from Employee e join e.user u where e.branch.id = :branchId "
        + "and e.status = com.ute.uteexpress.enums.EmployeeStatus.ACTIVE "
        + "and e.position = com.ute.uteexpress.enums.EmployeePosition.COURIER_DRIVER "
        + "and u.status = com.ute.uteexpress.enums.UserStatus.ACTIVE "
        + "and exists (select r.id from UserRole r where r.user.id = u.id and r.role.name = 'ROLE_SHIPPER')")
    Page<Employee> findPickupCandidates(@Param("branchId") Long branchId, Pageable pageable);
}
