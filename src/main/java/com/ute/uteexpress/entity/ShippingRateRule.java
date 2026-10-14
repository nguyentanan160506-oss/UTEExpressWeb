package com.ute.uteexpress.entity;

import com.ute.uteexpress.enums.RateScopeType;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "shipping_rate_rules",
    schema = "dbo",
    indexes = {
        @Index(name = "IX_rate_rules_search", columnList = "service_id, scope_type, is_active, effective_from, effective_to")
    },
    check = {
        @CheckConstraint(name = "CK_rate_rules_scope", constraint = "scope_type IN ('INTRA_PROVINCE', 'INTER_PROVINCE', 'INTER_REGION')"),
        @CheckConstraint(name = "CK_rate_rules_base_weight", constraint = "base_weight_kg > 0"),
        @CheckConstraint(name = "CK_rate_rules_base_price", constraint = "base_price >= 0"),
        @CheckConstraint(name = "CK_rate_rules_step_weight", constraint = "extra_weight_step_kg > 0"),
        @CheckConstraint(name = "CK_rate_rules_step_price", constraint = "extra_price_per_step >= 0"),
        @CheckConstraint(name = "CK_rate_rules_period", constraint = "effective_to IS NULL OR effective_to > effective_from")
    }
)
public class ShippingRateRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false, foreignKey = @ForeignKey(name = "FK_rate_rules_service"))
    private ShippingService service;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "scope_type", nullable = false, length = 30)
    private RateScopeType scopeType;

    @JdbcTypeCode(SqlTypes.DECIMAL)
    @Column(name = "base_weight_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseWeightKg;

    @JdbcTypeCode(SqlTypes.DECIMAL)
    @Column(name = "base_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal basePrice;

    @JdbcTypeCode(SqlTypes.DECIMAL)
    @Column(name = "extra_weight_step_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal extraWeightStepKg;

    @JdbcTypeCode(SqlTypes.DECIMAL)
    @Column(name = "extra_price_per_step", nullable = false, precision = 18, scale = 2)
    private BigDecimal extraPricePerStep;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "effective_from", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)")
    @ColumnDefault("SYSUTCDATETIME()")
    private LocalDateTime effectiveFrom;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "effective_to", nullable = true, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime effectiveTo;

    @JdbcTypeCode(SqlTypes.BIT)
    @Column(name = "is_active", nullable = false)
    @ColumnDefault("1")
    private boolean isActive = true;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)", updatable = false)
    @ColumnDefault("SYSUTCDATETIME()")
    private LocalDateTime createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "updated_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)")
    @ColumnDefault("SYSUTCDATETIME()")
    private LocalDateTime updatedAt;

    public ShippingRateRule() {
    }

    @PrePersist
    void initializeDefaults() {
        LocalDateTime now = UtcTimestamps.now();
        if (effectiveFrom == null) {
            effectiveFrom = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = UtcTimestamps.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ShippingService getService() {
        return service;
    }

    public void setService(ShippingService service) {
        this.service = service;
    }

    public RateScopeType getScopeType() {
        return scopeType;
    }

    public void setScopeType(RateScopeType scopeType) {
        this.scopeType = scopeType;
    }

    public BigDecimal getBaseWeightKg() {
        return baseWeightKg;
    }

    public void setBaseWeightKg(BigDecimal baseWeightKg) {
        this.baseWeightKg = baseWeightKg;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public BigDecimal getExtraWeightStepKg() {
        return extraWeightStepKg;
    }

    public void setExtraWeightStepKg(BigDecimal extraWeightStepKg) {
        this.extraWeightStepKg = extraWeightStepKg;
    }

    public BigDecimal getExtraPricePerStep() {
        return extraPricePerStep;
    }

    public void setExtraPricePerStep(BigDecimal extraPricePerStep) {
        this.extraPricePerStep = extraPricePerStep;
    }

    public LocalDateTime getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDateTime effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDateTime getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDateTime effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean isActive) {
        this.isActive = isActive;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
