package com.ute.uteexpress.entity;

import com.ute.uteexpress.enums.ShipmentMethod;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "branch_service_areas", schema = "dbo",
    uniqueConstraints = @UniqueConstraint(name = "UQ_branch_service_areas_scope",
        columnNames = {"branch_id", "province", "district", "ward", "shipment_method"}),
    indexes = @Index(name = "IX_branch_service_areas_lookup", columnList = "province, district, shipment_method, is_active"),
    check = @CheckConstraint(name = "CK_branch_service_areas_method",
        constraint = "shipment_method IN ('HOME_PICKUP', 'BRANCH_DROPOFF')"))
public class BranchServiceArea {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false, foreignKey = @ForeignKey(name = "FK_branch_service_areas_branch"))
    private Branch branch;

    @Nationalized @Column(name = "province", nullable = false, length = 100)
    private String province;
    @Nationalized @Column(name = "district", nullable = false, length = 100)
    private String district;
    @Nationalized @Column(name = "ward", nullable = false, length = 100) @ColumnDefault("''")
    private String ward = "";

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "shipment_method", nullable = false, length = 30)
    private ShipmentMethod shipmentMethod;

    @JdbcTypeCode(SqlTypes.BIT) @Column(name = "is_active", nullable = false) @ColumnDefault("1")
    private boolean isActive = true;

    @JdbcTypeCode(SqlTypes.BIT) @Column(name = "supports_delivery", nullable = false) @ColumnDefault("0")
    private boolean supportsDelivery;

    public boolean isSupportsDelivery() { return supportsDelivery; }
    public void setSupportsDelivery(boolean value) { supportsDelivery = value; }

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)", updatable = false)
    @ColumnDefault("SYSUTCDATETIME()")
    private LocalDateTime createdAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "updated_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)")
    @ColumnDefault("SYSUTCDATETIME()")
    private LocalDateTime updatedAt;

    @PrePersist void initializeDefaults() {
        LocalDateTime now = UtcTimestamps.now();
        if (ward == null) { ward = ""; }
        if (createdAt == null) { createdAt = now; }
        if (updatedAt == null) { updatedAt = now; }
    }
    @PreUpdate void updateTimestamp() { updatedAt = UtcTimestamps.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getWard() { return ward; }
    public void setWard(String ward) { this.ward = ward; }
    public ShipmentMethod getShipmentMethod() { return shipmentMethod; }
    public void setShipmentMethod(ShipmentMethod shipmentMethod) { this.shipmentMethod = shipmentMethod; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
