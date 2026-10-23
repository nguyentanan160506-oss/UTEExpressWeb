package com.ute.uteexpress.entity;

import com.ute.uteexpress.enums.NotificationType;
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
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "notifications",
    schema = "dbo",
    indexes = {
        @Index(name = "IX_notifications_user_read", columnList = "user_id, is_read, created_at DESC"),
        @Index(name = "IX_notifications_inbox", columnList = "user_id, created_at DESC, id DESC"),
        @Index(name = "IX_notifications_delivery", columnList = "delivered_at, delivery_retry_at, id")
    },
    check = {
        @CheckConstraint(name = "CK_notifications_audience", constraint = "audience IS NULL OR audience IN ('USER', 'SHIPPER', 'MANAGER')"),
        @CheckConstraint(name = "CK_notifications_type", constraint = "notification_type IN ('SHIPMENT_STATUS', 'PAYMENT', 'SUPPORT', 'SYSTEM')")
    }
)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "FK_notifications_user"))
    private User user;

    @Nationalized
    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Nationalized
    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "notification_type", nullable = false, length = 50)
    private NotificationType notificationType;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "related_shipment_id", nullable = true, foreignKey = @ForeignKey(name = "FK_notifications_shipment"))
    private Shipment relatedShipment;

    @JdbcTypeCode(SqlTypes.BIT)
    @Column(name = "is_read", nullable = false)
    @ColumnDefault("0")
    private boolean isRead = false;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)", updatable = false)
    @ColumnDefault("SYSUTCDATETIME()")
    private LocalDateTime createdAt;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_event_id", foreignKey = @ForeignKey(name = "FK_notifications_source_event"))
    private ShipmentEvent sourceEvent;
    @Column(name = "audience", length = 20)
    private String audience;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scope_branch_id", foreignKey = @ForeignKey(name = "FK_notifications_scope_branch"))
    private Branch scopeBranch;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", foreignKey = @ForeignKey(name = "FK_notifications_assignment"))
    private ShipmentAssignment assignment;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name = "read_at", secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime readAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name = "delivered_at", secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime deliveredAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name = "delivery_retry_at", secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime deliveryRetryAt;
    @Column(name = "delivery_attempts", nullable = false) @ColumnDefault("0")
    private int deliveryAttempts;
    public ShipmentEvent getSourceEvent() { return sourceEvent; }
    public void setSourceEvent(ShipmentEvent v) { sourceEvent = v; }
    public String getAudience() { return audience; }
    public void setAudience(String v) { audience = v; }
    public Branch getScopeBranch() { return scopeBranch; }
    public void setScopeBranch(Branch v) { scopeBranch = v; }
    public ShipmentAssignment getAssignment() { return assignment; }
    public void setAssignment(ShipmentAssignment v) { assignment = v; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime v) { readAt = v; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime v) { deliveredAt = v; }
    public LocalDateTime getDeliveryRetryAt() { return deliveryRetryAt; }
    public void setDeliveryRetryAt(LocalDateTime v) { deliveryRetryAt = v; }
    public int getDeliveryAttempts() { return deliveryAttempts; }
    public void setDeliveryAttempts(int v) { deliveryAttempts = v; }

    public Notification() {
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

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(NotificationType notificationType) {
        this.notificationType = notificationType;
    }

    public Shipment getRelatedShipment() {
        return relatedShipment;
    }

    public void setRelatedShipment(Shipment relatedShipment) {
        this.relatedShipment = relatedShipment;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean isRead) {
        this.isRead = isRead;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
