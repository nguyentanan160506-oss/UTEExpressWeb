package com.ute.uteexpress.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "pickup_fee_rules",
    schema = "dbo",
    indexes = {@Index(name = "IX_pickup_rules_version", columnList = "pricing_version_id, branch_id"), @Index(name = "IX_pickup_fee_rules_lookup", columnList = "branch_id, service_id, is_active, effective_from, effective_to")},
    check = {@CheckConstraint(name = "CK_pickup_rules_retirement", constraint = "retired_from IS NULL OR (pricing_version_id IS NULL AND retired_from > effective_from AND (effective_to IS NULL OR retired_from < effective_to))"), @CheckConstraint(name = "CK_pickup_fee_rules_amount", constraint = "amount > 0"),
        @CheckConstraint(name = "CK_pickup_fee_rules_period", constraint = "effective_to IS NULL OR effective_to > effective_from")})
public class PickupFeeRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", foreignKey = @ForeignKey(name = "FK_pickup_fee_rules_branch"))
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false, foreignKey = @ForeignKey(name = "FK_pickup_fee_rules_service"))
    private ShippingService service;

    @JdbcTypeCode(SqlTypes.DECIMAL) @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.TIMESTAMP) @ColumnDefault("SYSUTCDATETIME()") @Column(name = "effective_from", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime effectiveFrom;

    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name = "effective_to", nullable = true, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime effectiveTo;

    @JdbcTypeCode(SqlTypes.BIT) @ColumnDefault("1") @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @JdbcTypeCode(SqlTypes.TIMESTAMP) @ColumnDefault("SYSUTCDATETIME()") @Column(name = "created_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP) @ColumnDefault("SYSUTCDATETIME()") @Column(name = "updated_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime updatedAt;

    @PrePersist void initializeDefaults() {
        LocalDateTime now = UtcTimestamps.now();
        if (effectiveFrom == null) { effectiveFrom = now; }
        if (createdAt == null) { createdAt = now; }
        if (updatedAt == null) { updatedAt = now; }
    }
    @PreUpdate void updateTimestamp() { updatedAt = UtcTimestamps.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch value) { this.branch = value; }
    public ShippingService getService() { return service; }
    public void setService(ShippingService value) { this.service = value; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { this.amount = value; }
    public LocalDateTime getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDateTime value) { this.effectiveFrom = value; }
    public LocalDateTime getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDateTime value) { this.effectiveTo = value; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean value) { this.isActive = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { this.updatedAt = value; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pricing_version_id", foreignKey = @ForeignKey(name = "FK_pickup_rules_pricing_version"))
    private PricingVersion pricingVersion;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "retired_from", secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime retiredFrom;
    public PricingVersion getPricingVersion() { return pricingVersion; }
    public void setPricingVersion(PricingVersion value) { pricingVersion = value; }
    public LocalDateTime getRetiredFrom() { return retiredFrom; }
    public void setRetiredFrom(LocalDateTime value) { retiredFrom = value; }
}
