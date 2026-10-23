package com.ute.uteexpress.entity;

import com.ute.uteexpress.enums.ShipmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.CheckConstraint;
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
    name = "shipment_events",
    schema = "dbo",
    check = {
        @CheckConstraint(name = "CK_shipment_events_notification_assignment_status", constraint = "notification_assignment_status IS NULL OR notification_assignment_status IN ('ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'CANCELLED')"),
        @CheckConstraint(name = "CK_shipment_events_from_status", constraint = "from_status IS NULL OR from_status IN ('CREATED', 'CONFIRMED', 'PICKUP_ASSIGNED', 'PICKED_UP', 'AT_ORIGIN_BRANCH', 'AT_ORIGIN_HUB', 'IN_TRANSIT', 'AT_DESTINATION_HUB', 'AT_DESTINATION_BRANCH', 'OUT_FOR_DELIVERY', 'DELIVERED', 'DELIVERY_FAILED', 'RETURNING', 'RETURNED', 'CANCELLED')"),
        @CheckConstraint(name = "CK_shipment_events_to_status", constraint = "to_status IN ('CREATED', 'CONFIRMED', 'PICKUP_ASSIGNED', 'PICKED_UP', 'AT_ORIGIN_BRANCH', 'AT_ORIGIN_HUB', 'IN_TRANSIT', 'AT_DESTINATION_HUB', 'AT_DESTINATION_BRANCH', 'OUT_FOR_DELIVERY', 'DELIVERED', 'DELIVERY_FAILED', 'RETURNING', 'RETURNED', 'CANCELLED')")
    },
    indexes = {
        @Index(name = "IX_shipment_events_timeline", columnList = "shipment_id, occurred_at DESC"),
        @Index(name = "IX_shipment_events_notification_work", columnList = "notification_enabled, notification_processed_at, notification_retry_at, id")
    }
)
public class ShipmentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false, foreignKey = @ForeignKey(name = "FK_shipment_events_shipment"))
    private Shipment shipment;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "branch_id", nullable = true, foreignKey = @ForeignKey(name = "FK_shipment_events_branch"))
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "actor_id", nullable = true, foreignKey = @ForeignKey(name = "FK_shipment_events_actor"))
    private User actor;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "from_status", nullable = true, length = 50)
    private ShipmentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "to_status", nullable = false, length = 50)
    private ShipmentStatus toStatus;

    @Nationalized
    @Column(name = "event_title", nullable = false, length = 150)
    private String eventTitle;

    @Nationalized
    @Column(name = "description", nullable = true, length = 500)
    private String description;

    @Nationalized
    @Column(name = "location_text", nullable = true, length = 255)
    private String locationText;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "occurred_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)", updatable = false)
    @ColumnDefault("SYSUTCDATETIME()")
    private LocalDateTime occurredAt;


    // Persisted with the business event: no volatile after-commit hand-off or high-water ID cursor.
    @JdbcTypeCode(SqlTypes.BIT) @Column(name = "notification_enabled", nullable = false) @ColumnDefault("0")
    private boolean notificationEnabled = true;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name = "notification_processed_at", secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime notificationProcessedAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name = "notification_retry_at", secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime notificationRetryAt;
    @Column(name = "notification_attempts", nullable = false) @ColumnDefault("0")
    private int notificationAttempts;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_assignment_id", foreignKey = @ForeignKey(name = "FK_shipment_events_notification_assignment"))
    private ShipmentAssignment notificationAssignment;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_previous_assignment_id", foreignKey = @ForeignKey(name = "FK_shipment_events_notification_previous_assignment"))
    private ShipmentAssignment notificationPreviousAssignment;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_assignee_user_id", foreignKey = @ForeignKey(name = "FK_shipment_events_notification_assignee_user"))
    private User notificationAssigneeUser;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_previous_user_id", foreignKey = @ForeignKey(name = "FK_shipment_events_notification_previous_user"))
    private User notificationPreviousUser;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "notification_assignment_status", length = 30)
    private com.ute.uteexpress.enums.AssignmentStatus notificationAssignmentStatus;
    public com.ute.uteexpress.enums.AssignmentStatus getNotificationAssignmentStatus() { return notificationAssignmentStatus; }
    public User getNotificationAssigneeUser() { return notificationAssigneeUser; }
    public User getNotificationPreviousUser() { return notificationPreviousUser; }
    public boolean isNotificationEnabled() { return notificationEnabled; }
    public void setNotificationEnabled(boolean v) { notificationEnabled = v; }
    public LocalDateTime getNotificationProcessedAt() { return notificationProcessedAt; }
    public void setNotificationProcessedAt(LocalDateTime v) { notificationProcessedAt = v; }
    public LocalDateTime getNotificationRetryAt() { return notificationRetryAt; }
    public void setNotificationRetryAt(LocalDateTime v) { notificationRetryAt = v; }
    public int getNotificationAttempts() { return notificationAttempts; }
    public void setNotificationAttempts(int v) { notificationAttempts = v; }
    public ShipmentAssignment getNotificationAssignment() { return notificationAssignment; }
    public void setNotificationAssignment(ShipmentAssignment v) {
        notificationAssignment = v; notificationAssigneeUser = v == null ? null : v.getEmployee().getUser();
        notificationAssignmentStatus = v == null ? null : v.getStatus();
    }
    public ShipmentAssignment getNotificationPreviousAssignment() { return notificationPreviousAssignment; }
    public void setNotificationPreviousAssignment(ShipmentAssignment v) { notificationPreviousAssignment = v; notificationPreviousUser = v == null ? null : v.getEmployee().getUser(); }

    public ShipmentEvent() {
    }

    @PrePersist
    void initializeDefaults() {
        LocalDateTime now = UtcTimestamps.now();
        if (occurredAt == null) {
            occurredAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Shipment getShipment() {
        return shipment;
    }

    public void setShipment(Shipment shipment) {
        this.shipment = shipment;
    }

    public Branch getBranch() {
        return branch;
    }

    public void setBranch(Branch branch) {
        this.branch = branch;
    }

    public User getActor() {
        return actor;
    }

    public void setActor(User actor) {
        this.actor = actor;
    }

    public ShipmentStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(ShipmentStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public ShipmentStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(ShipmentStatus toStatus) {
        this.toStatus = toStatus;
    }

    public String getEventTitle() {
        return eventTitle;
    }

    public void setEventTitle(String eventTitle) {
        this.eventTitle = eventTitle;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocationText() {
        return locationText;
    }

    public void setLocationText(String locationText) {
        this.locationText = locationText;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }
}
