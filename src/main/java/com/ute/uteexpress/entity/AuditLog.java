package com.ute.uteexpress.entity;

import com.ute.uteexpress.enums.AuditResourceType;
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
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.Length;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "audit_logs",
    schema = "dbo",
    indexes = {@Index(name = "IX_audit_logs_reports_time", columnList = "created_at DESC, id DESC"),
        @Index(name = "IX_audit_logs_actor_time", columnList = "actor_id, created_at DESC"),
        @Index(name = "IX_audit_logs_resource", columnList = "resource_type, resource_id")
    },
    check = {
        @CheckConstraint(name = "CK_audit_logs_resource", constraint = "resource_type IN ('SHIPMENT', 'USER', 'RATE_RULE', 'BRANCH', 'EMPLOYEE', 'ROLE', 'SHIPPING_SERVICE', 'PRICE_VERSION', 'PICKUP_RULE')")
    }
)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "actor_id", nullable = true, foreignKey = @ForeignKey(name = "FK_audit_logs_actor"))
    private User actor;

    @Column(name = "actor_username", nullable = true, length = 50)
    private String actorUsername;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "resource_type", nullable = false, length = 100)
    private AuditResourceType resourceType;

    @Column(name = "resource_id", nullable = true, length = 50)
    private String resourceId;

    @Column(name = "ip_address", nullable = true, length = 50)
    private String ipAddress;

    @Nationalized
    @JdbcTypeCode(SqlTypes.NVARCHAR)
    @Column(name = "old_value", nullable = true, length = Length.LONG32, columnDefinition = "nvarchar(max)")
    private String oldValue;

    @Nationalized
    @JdbcTypeCode(SqlTypes.NVARCHAR)
    @Column(name = "new_value", nullable = true, length = Length.LONG32, columnDefinition = "nvarchar(max)")
    private String newValue;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)", updatable = false)
    @ColumnDefault("SYSUTCDATETIME()")
    private LocalDateTime createdAt;

    public AuditLog() {
    }

    @PrePersist
    void initializeDefaults() {
        LocalDateTime now = UtcTimestamps.now();
        if (createdAt == null) {
            createdAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getActor() {
        return actor;
    }

    public void setActor(User actor) {
        this.actor = actor;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public void setActorUsername(String actorUsername) {
        this.actorUsername = actorUsername;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public AuditResourceType getResourceType() {
        return resourceType;
    }

    public void setResourceType(AuditResourceType resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getOldValue() {
        return oldValue;
    }

    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
