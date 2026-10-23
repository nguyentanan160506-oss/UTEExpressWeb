package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.ShippingRateRule;
import com.ute.uteexpress.enums.RateScopeType;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface ShippingRateRuleRepository extends JpaRepository<ShippingRateRule, Long> {
    @Query("select r from ShippingRateRule r left join r.pricingVersion v where r.service.id = :serviceId and r.scopeType = :scope and r.isActive = true and (v.id is null or v.status = com.ute.uteexpress.enums.PricingVersionStatus.PUBLISHED) and r.effectiveFrom <= :now and (r.effectiveTo is null or r.effectiveTo > :now) and (r.retiredFrom is null or r.retiredFrom > :now) and (v.retiredFrom is null or v.retiredFrom > :now)")
    List<ShippingRateRule> findEffectiveRules(Long serviceId, RateScopeType scope, LocalDateTime now);
    @Query("select r from ShippingRateRule r left join r.pricingVersion v where r.service.id = :serviceId and r.scopeType = :scope and r.isActive = true and (v.id is null or v.status = com.ute.uteexpress.enums.PricingVersionStatus.PUBLISHED) and (v.id is null or v.id <> :versionId) and (:end is null or r.effectiveFrom < :end) and (r.effectiveTo is null or r.effectiveTo > :start) and (r.retiredFrom is null or r.retiredFrom > :start) and (v.retiredFrom is null or v.retiredFrom > :start)")
    List<ShippingRateRule> findConflicts(Long serviceId, RateScopeType scope, Long versionId, LocalDateTime start, LocalDateTime end);
    List<ShippingRateRule> findByPricingVersionIdAndIsActiveTrue(Long versionId);
    Page<ShippingRateRule> findByPricingVersionId(Long versionId, Pageable pageable);
    Page<ShippingRateRule> findByServiceIdAndPricingVersionIsNull(Long serviceId, Pageable pageable);
    long countByPricingVersionId(Long versionId);
    Optional<ShippingRateRule> findByPricingVersionIdAndScopeType(Long versionId, RateScopeType scope);
    Optional<ShippingRateRule> findByIdAndPricingVersionId(Long id, Long versionId);
    @Query("select r.service.id from ShippingRateRule r where r.id = :id")
    Optional<Long> findServiceId(Long id);
}
