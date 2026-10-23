package com.ute.uteexpress.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "shipping_services",
    schema = "dbo",
    uniqueConstraints = {
        @UniqueConstraint(name = "UQ_shipping_services_code", columnNames = {"code"})
    },
    check = {
        @CheckConstraint(name = "CK_services_hours", constraint = "estimated_delivery_hours > 0"),
        @CheckConstraint(name = "CK_services_limits", constraint = "(max_weight_kg IS NULL OR max_weight_kg > 0) AND (max_length_cm IS NULL OR max_length_cm > 0) AND (max_width_cm IS NULL OR max_width_cm > 0) AND (max_height_cm IS NULL OR max_height_cm > 0)")
    }
)
public class ShippingService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "code", nullable = false, length = 50, updatable = false)
    private String code;

    @Nationalized
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Nationalized
    @Column(name = "description", nullable = true, length = 255)
    private String description;

    @Column(name = "estimated_delivery_hours", nullable = false)
    private Integer estimatedDeliveryHours;

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

    @Version @ColumnDefault("0") @Column(name = "version", nullable = false)
    private Long version = 0L;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by", foreignKey = @ForeignKey(name = "FK_services_created_by"))
    private User createdBy;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "updated_by", foreignKey = @ForeignKey(name = "FK_services_updated_by"))
    private User updatedBy;
    @JdbcTypeCode(SqlTypes.DECIMAL) @Column(name = "max_weight_kg", precision = 10, scale = 2)
    private BigDecimal maxWeightKg;
    @JdbcTypeCode(SqlTypes.DECIMAL) @Column(name = "max_length_cm", precision = 10, scale = 2)
    private BigDecimal maxLengthCm;
    @JdbcTypeCode(SqlTypes.DECIMAL) @Column(name = "max_width_cm", precision = 10, scale = 2)
    private BigDecimal maxWidthCm;
    @JdbcTypeCode(SqlTypes.DECIMAL) @Column(name = "max_height_cm", precision = 10, scale = 2)
    private BigDecimal maxHeightCm;

    public ShippingService() {
    }

    @PrePersist
    void initializeDefaults() {
        LocalDateTime now = UtcTimestamps.now();
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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getEstimatedDeliveryHours() {
        return estimatedDeliveryHours;
    }

    public void setEstimatedDeliveryHours(Integer estimatedDeliveryHours) {
        this.estimatedDeliveryHours = estimatedDeliveryHours;
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
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User value) { createdBy = value; }
    public User getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(User value) { updatedBy = value; }
    public BigDecimal getMaxWeightKg() { return maxWeightKg; }
    public void setMaxWeightKg(BigDecimal value) { maxWeightKg = value; }
    public BigDecimal getMaxLengthCm() { return maxLengthCm; }
    public void setMaxLengthCm(BigDecimal value) { maxLengthCm = value; }
    public BigDecimal getMaxWidthCm() { return maxWidthCm; }
    public void setMaxWidthCm(BigDecimal value) { maxWidthCm = value; }
    public BigDecimal getMaxHeightCm() { return maxHeightCm; }
    public void setMaxHeightCm(BigDecimal value) { maxHeightCm = value; }
}
