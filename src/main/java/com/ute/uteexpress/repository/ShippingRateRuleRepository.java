package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.ShippingRateRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ute.uteexpress.enums.RateScopeType;
import java.time.LocalDateTime;
import java.util.List;

public interface ShippingRateRuleRepository extends JpaRepository<ShippingRateRule, Long> {
    @Query("select r from ShippingRateRule r where r.service.id = :serviceId and r.scopeType = :scope "
        + "and r.isActive = true and r.effectiveFrom <= :now and (r.effectiveTo is null or r.effectiveTo > :now)")
    List<ShippingRateRule> findEffectiveRules(@Param("serviceId") Long serviceId,
        @Param("scope") RateScopeType scope, @Param("now") LocalDateTime now);
}
