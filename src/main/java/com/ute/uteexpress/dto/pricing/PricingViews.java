package com.ute.uteexpress.dto.pricing;

import com.ute.uteexpress.enums.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;

public final class PricingViews {
    private PricingViews() { }
    public record Catalog(Long id, String code, String name, String description, int hours, boolean active, Long version,
        BigDecimal maxWeightKg, BigDecimal maxLengthCm, BigDecimal maxWidthCm, BigDecimal maxHeightCm) { }
    public record Book(Long id, Long serviceId, String service, String code, PricingVersionStatus status, String currency,
        LocalDateTime validFrom, LocalDateTime validTo, LocalDateTime retiredFrom, Long version, String effectiveState,
        LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime publishedAt) { }
    public record Rule(Long id, RateScopeType scope, BigDecimal baseWeightKg, BigDecimal basePrice,
        BigDecimal extraWeightStepKg, BigDecimal extraPricePerStep, boolean active, LocalDateTime from, LocalDateTime to, LocalDateTime retiredFrom) { }
    public record Pickup(Long id, Long branchId, String branch, BigDecimal amount, boolean active,
        LocalDateTime from, LocalDateTime to, LocalDateTime retiredFrom) { }
    public record Audit(String action, Long actorId, String oldValue, String newValue, LocalDateTime occurredAt) { }
    public record Detail(Book book, Page<Rule> rules, Page<Pickup> pickups, Page<Audit> audit) { }
    public record ServiceDetail(Catalog service, Page<Rule> legacyRules, Page<Pickup> legacyPickups, Page<Audit> audit) { }
}
