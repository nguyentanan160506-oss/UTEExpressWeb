package com.ute.uteexpress.entity;

import com.ute.uteexpress.enums.PricingVersionStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "pricing_versions", schema = "dbo",
    uniqueConstraints = @UniqueConstraint(name = "UQ_pricing_versions_code", columnNames = {"service_id", "code"}),
    indexes = @Index(name = "IX_pricing_versions_lookup", columnList = "service_id, status, valid_from, valid_to"),
    check = {
        @CheckConstraint(name = "CK_pricing_versions_status", constraint = "status IN ('DRAFT', 'PUBLISHED')"),
        @CheckConstraint(name = "CK_pricing_versions_currency", constraint = "currency = 'VND'"),
        @CheckConstraint(name = "CK_pricing_versions_code", constraint = "LEN(LTRIM(RTRIM(code))) > 0"),
        @CheckConstraint(name = "CK_pricing_versions_period", constraint = "valid_to IS NULL OR valid_to > valid_from"),
        @CheckConstraint(name = "CK_pricing_versions_publish", constraint = "(status = 'DRAFT' AND published_by IS NULL AND published_at IS NULL AND retired_from IS NULL) OR (status = 'PUBLISHED' AND published_by IS NOT NULL AND published_at IS NOT NULL)"),
        @CheckConstraint(name = "CK_pricing_versions_retirement", constraint = "retired_from IS NULL OR (retired_from > valid_from AND retired_from > published_at AND (valid_to IS NULL OR retired_from < valid_to))")
    })
public class PricingVersion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id", nullable = false)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "service_id", nullable = false, foreignKey = @ForeignKey(name = "FK_pricing_versions_service"))
    private ShippingService service;
    @Column(name = "code", nullable = false, length = 50, updatable = false)
    private String code;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @ColumnDefault("'DRAFT'") @Column(name = "status", nullable = false, length = 30)
    private PricingVersionStatus status = PricingVersionStatus.DRAFT;
    @ColumnDefault("'VND'") @Column(name = "currency", nullable = false, length = 3)
    private String currency = "VND";
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name = "valid_from", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime validFrom;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name = "valid_to", nullable = true, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime validTo;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name = "retired_from", nullable = true, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime retiredFrom;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by", nullable = false, foreignKey = @ForeignKey(name = "FK_pricing_versions_created_by"))
    private User createdBy;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "updated_by", nullable = false, foreignKey = @ForeignKey(name = "FK_pricing_versions_updated_by"))
    private User updatedBy;
    @ManyToOne(fetch = FetchType.LAZY, optional = true) @JoinColumn(name = "published_by", nullable = true, foreignKey = @ForeignKey(name = "FK_pricing_versions_published_by"))
    private User publishedBy;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @ColumnDefault("SYSUTCDATETIME()") @Column(name = "created_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime createdAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @ColumnDefault("SYSUTCDATETIME()") @Column(name = "updated_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime updatedAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name = "published_at", nullable = true, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime publishedAt;
    @Version @ColumnDefault("0") @Column(name = "version", nullable = false)
    private Long version = 0L;
    @PrePersist void initializeDefaults() {
        var now = UtcTimestamps.now();
        if (createdAt == null) { createdAt = now; }
        if (updatedAt == null) { updatedAt = now; }
        if (status == null) { status = PricingVersionStatus.DRAFT; }
        if (currency == null) { currency = "VND"; }
    }
    @PreUpdate void updateTimestamp() { updatedAt = UtcTimestamps.now(); }
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public ShippingService getService() { return service; }
    public void setService(ShippingService value) { service = value; }
    public String getCode() { return code; }
    public void setCode(String value) { code = value; }
    public PricingVersionStatus getStatus() { return status; }
    public void setStatus(PricingVersionStatus value) { status = value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { currency = value; }
    public LocalDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDateTime value) { validFrom = value; }
    public LocalDateTime getValidTo() { return validTo; }
    public void setValidTo(LocalDateTime value) { validTo = value; }
    public LocalDateTime getRetiredFrom() { return retiredFrom; }
    public void setRetiredFrom(LocalDateTime value) { retiredFrom = value; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User value) { createdBy = value; }
    public User getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(User value) { updatedBy = value; }
    public User getPublishedBy() { return publishedBy; }
    public void setPublishedBy(User value) { publishedBy = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime value) { publishedAt = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
}

