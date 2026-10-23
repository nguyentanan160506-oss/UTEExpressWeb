package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.PricingVersion;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface PricingVersionRepository extends JpaRepository<PricingVersion, Long> {
    @Query("select v.service.id from PricingVersion v where v.id = :id")
    Optional<Long> findServiceId(Long id);
    Page<PricingVersion> findByServiceId(Long serviceId, Pageable pageable);
    boolean existsByServiceIdAndCodeIgnoreCase(Long serviceId, String code);
}
