package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.PickupFeeRule;
import com.ute.uteexpress.enums.RateScopeType;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface PickupFeeRuleRepository extends JpaRepository<PickupFeeRule, Long> {
    @Query("select r from PickupFeeRule r left join r.pricingVersion v where r.service.id = :serviceId and (r.branch.id = :branchId or (:branchId is null and r.branch is null)) and r.isActive = true and (v.id is null or v.status = com.ute.uteexpress.enums.PricingVersionStatus.PUBLISHED) and r.effectiveFrom <= :now and (r.effectiveTo is null or r.effectiveTo > :now) and (r.retiredFrom is null or r.retiredFrom > :now) and (v.retiredFrom is null or v.retiredFrom > :now)")
    List<PickupFeeRule> findEffectiveRules(Long branchId, Long serviceId, LocalDateTime now);
    @Query("select r from PickupFeeRule r left join r.pricingVersion v where r.service.id = :serviceId and (r.branch.id = :branchId or (:branchId is null and r.branch is null)) and r.isActive = true and (v.id is null or v.status = com.ute.uteexpress.enums.PricingVersionStatus.PUBLISHED) and (v.id is null or v.id <> :versionId) and (:end is null or r.effectiveFrom < :end) and (r.effectiveTo is null or r.effectiveTo > :start) and (r.retiredFrom is null or r.retiredFrom > :start) and (v.retiredFrom is null or v.retiredFrom > :start)")
    List<PickupFeeRule> findConflicts(Long serviceId, Long branchId, Long versionId, LocalDateTime start, LocalDateTime end);
    List<PickupFeeRule> findByPricingVersionIdAndIsActiveTrue(Long versionId);
    Page<PickupFeeRule> findByPricingVersionId(Long versionId, Pageable pageable);
    Page<PickupFeeRule> findByServiceIdAndPricingVersionIsNull(Long serviceId, Pageable pageable);
    long countByPricingVersionId(Long versionId);
    Optional<PickupFeeRule> findByPricingVersionIdAndBranchId(Long versionId, Long branchId);
    Optional<PickupFeeRule> findByIdAndPricingVersionId(Long id, Long versionId);
    @Query("select r.service.id from PickupFeeRule r where r.id = :id")
    Optional<Long> findServiceId(Long id);
}
