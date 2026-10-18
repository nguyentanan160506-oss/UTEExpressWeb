package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.PickupFeeRule;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PickupFeeRuleRepository extends JpaRepository<PickupFeeRule, Long> {
    @Query("select r from PickupFeeRule r where r.branch.id = :branchId and r.service.id = :serviceId "
        + "and r.isActive = true and r.effectiveFrom <= :now and (r.effectiveTo is null or r.effectiveTo > :now)")
    List<PickupFeeRule> findEffectiveRules(@Param("branchId") Long branchId, @Param("serviceId") Long serviceId,
        @Param("now") LocalDateTime now);
}
