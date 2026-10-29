package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BranchServiceAreaRepository extends JpaRepository<BranchServiceArea, Long> {
    @Query("select a from BranchServiceArea a join fetch a.branch b join fetch b.parentHub h "
        + "where a.isActive = true and b.status = :active and h.status = :active "
        + "and b.branchType = :localType and h.branchType = :hubType and h.id <> b.id "
        + "order by a.province, a.district, a.ward, b.name, a.shipmentMethod")
    List<BranchServiceArea> findVisibleCoverage(@Param("active") BranchStatus active,
        @Param("localType") BranchType localType, @Param("hubType") BranchType hubType,
        org.springframework.data.domain.Pageable limit);

    @Query("select distinct b from BranchServiceArea a join a.branch b join fetch b.parentHub h "
        + "where a.isActive = true and a.supportsDelivery = true and b.status = :active and h.status = :active "
        + "and b.branchType = :localType and h.branchType = :hubType and h.id <> b.id "
        + "and lower(a.province) = lower(:province) and lower(a.district) = lower(:district) "
        + "and (a.ward = '' or lower(a.ward) = lower(:ward)) order by b.name")
    List<Branch> findDeliveryBranches(@Param("province") String province, @Param("district") String district,
        @Param("ward") String ward, @Param("active") BranchStatus active,
        @Param("localType") BranchType localType, @Param("hubType") BranchType hubType);
    @Query("select distinct b from BranchServiceArea a join a.branch b join fetch b.parentHub h "
        + "where a.isActive = true and a.shipmentMethod = :method and b.status = :active and h.status = :active "
        + "and b.branchType = :localType and h.branchType = :hubType and h.id <> b.id "
        + "and lower(a.province) = lower(:province) and lower(a.district) = lower(:district) "
        + "and (a.ward = '' or lower(a.ward) = lower(:ward)) order by b.name")
    List<Branch> findServingBranches(@Param("province") String province, @Param("district") String district,
        @Param("ward") String ward, @Param("method") ShipmentMethod method, @Param("active") BranchStatus active,
        @Param("localType") BranchType localType, @Param("hubType") BranchType hubType);
}
