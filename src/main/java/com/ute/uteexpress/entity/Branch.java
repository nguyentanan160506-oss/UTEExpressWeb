package com.ute.uteexpress.entity;

import com.ute.uteexpress.enums.BranchStatus;
import com.ute.uteexpress.enums.BranchType;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "branches",
    schema = "dbo",
    uniqueConstraints = {
        @UniqueConstraint(name = "UQ_branches_code", columnNames = {"code"})
    },
    indexes = {
        @Index(name = "IX_branches_location", columnList = "province, district"),
        @Index(name = "IX_branches_parent_hub", columnList = "parent_hub_id")
    },
    check = {
        @CheckConstraint(name = "CK_branches_type", constraint = "branch_type IN ('CENTRAL_HUB', 'LOCAL_BRANCH')"),
        @CheckConstraint(name = "CK_branches_status", constraint = "status IN ('ACTIVE', 'INACTIVE')"),
        @CheckConstraint(name = "CK_branches_parent_hub", constraint = "parent_hub_id IS NULL OR (branch_type = 'LOCAL_BRANCH' AND parent_hub_id <> id)")
    }
)
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "parent_hub_id", nullable = true, foreignKey = @ForeignKey(name = "FK_branches_parent_hub"))
    private Branch parentHub;

    public Branch getParentHub() { return parentHub; }
    public void setParentHub(Branch parentHub) { this.parentHub = parentHub; }

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Nationalized
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "branch_type", nullable = false, length = 30)
    @ColumnDefault("'LOCAL_BRANCH'")
    private BranchType branchType = BranchType.LOCAL_BRANCH;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "email", nullable = true, length = 150)
    private String email;

    @Nationalized
    @Column(name = "province", nullable = false, length = 100)
    private String province;

    @Nationalized
    @Column(name = "district", nullable = false, length = 100)
    private String district;

    @Nationalized
    @Column(name = "ward", nullable = false, length = 100)
    private String ward;

    @Nationalized
    @Column(name = "detail_address", nullable = false, length = 255)
    private String detailAddress;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    @ColumnDefault("'ACTIVE'")
    private BranchStatus status = BranchStatus.ACTIVE;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)", updatable = false)
    @ColumnDefault("SYSUTCDATETIME()")
    private LocalDateTime createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "updated_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)")
    @ColumnDefault("SYSUTCDATETIME()")
    private LocalDateTime updatedAt;

    public Branch() {
    }

    @PrePersist
    void initializeDefaults() {
        LocalDateTime now = UtcTimestamps.now();
        if (branchType == null) {
            branchType = BranchType.LOCAL_BRANCH;
        }
        if (status == null) {
            status = BranchStatus.ACTIVE;
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

    public BranchType getBranchType() {
        return branchType;
    }

    public void setBranchType(BranchType branchType) {
        this.branchType = branchType;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getWard() {
        return ward;
    }

    public void setWard(String ward) {
        this.ward = ward;
    }

    public String getDetailAddress() {
        return detailAddress;
    }

    public void setDetailAddress(String detailAddress) {
        this.detailAddress = detailAddress;
    }

    public BranchStatus getStatus() {
        return status;
    }

    public void setStatus(BranchStatus status) {
        this.status = status;
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
