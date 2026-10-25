package com.ute.uteexpress.entity;

import com.ute.uteexpress.enums.DeliveryAttemptStatus;
import com.ute.uteexpress.enums.DeliveryFailureCode;
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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "delivery_attempts",
    schema = "dbo",
    uniqueConstraints = {
        @UniqueConstraint(name = "UQ_delivery_attempts", columnNames = {"shipment_id", "attempt_number"})
    },
    indexes = {@Index(name = "IX_delivery_attempts_operations_period", columnList = "attempt_time, shipment_id, status"),
        @Index(name = "IX_delivery_attempts_assignment", columnList = "assignment_id, attempt_time DESC"),
        @Index(name = "IX_delivery_attempts_employee", columnList = "employee_id")
    },
    check = {
        @CheckConstraint(name = "CK_delivery_attempts_code", constraint = "failure_code IS NULL OR failure_code IN ('UNREACHABLE', 'ABSENT', 'REFUSED', 'INVALID_ADDRESS', 'OPERATIONAL', 'DAMAGED')"),
        @CheckConstraint(name = "CK_delivery_attempts_context", constraint = "(assignment_id IS NULL AND actor_id IS NULL AND failure_code IS NULL AND started_at IS NULL) OR (assignment_id IS NOT NULL AND actor_id IS NOT NULL AND started_at IS NOT NULL AND ((status = 'SUCCESS' AND failure_code IS NULL) OR (status = 'FAILED' AND failure_code IS NOT NULL AND failure_reason IS NOT NULL)))"),
        @CheckConstraint(name = "CK_delivery_attempts_number", constraint = "attempt_number >= 1"),
        @CheckConstraint(name = "CK_delivery_attempts_status", constraint = "status IN ('SUCCESS', 'FAILED')")
    }
)
public class DeliveryAttempt {
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "assignment_id", nullable = true, foreignKey = @ForeignKey(name = "FK_delivery_attempts_assignment"))
    private ShipmentAssignment assignment;
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "actor_id", nullable = true, foreignKey = @ForeignKey(name = "FK_delivery_attempts_actor"))
    private User actor;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "failure_code", nullable = true, length = 30)
    private DeliveryFailureCode failureCode;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "started_at", nullable = true, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime startedAt;
    public ShipmentAssignment getAssignment() { return assignment; }
    public void setAssignment(ShipmentAssignment value) { assignment = value; }
    public User getActor() { return actor; }
    public void setActor(User value) { actor = value; }
    public DeliveryFailureCode getFailureCode() { return failureCode; }
    public void setFailureCode(DeliveryFailureCode value) { failureCode = value; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime value) { startedAt = value; }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false, foreignKey = @ForeignKey(name = "FK_delivery_attempts_shipment"))
    private Shipment shipment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, foreignKey = @ForeignKey(name = "FK_delivery_attempts_employee"))
    private Employee employee;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 30)
    private DeliveryAttemptStatus status;

    @Nationalized
    @Column(name = "failure_reason", nullable = true, length = 255)
    private String failureReason;

    @Nationalized
    @Column(name = "recipient_signature", nullable = true, length = 255)
    private String recipientSignature;

    @Nationalized
    @Column(name = "photo_proof_url", nullable = true, length = 500)
    private String photoProofUrl;

    @Nationalized
    @Column(name = "notes", nullable = true, length = 500)
    private String notes;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "attempt_time", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)", updatable = false)
    @ColumnDefault("SYSUTCDATETIME()")
    private LocalDateTime attemptTime;

    public DeliveryAttempt() {
    }

    @PrePersist
    void initializeDefaults() {
        LocalDateTime now = UtcTimestamps.now();
        if (attemptTime == null) {
            attemptTime = now;
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

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public Integer getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(Integer attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public DeliveryAttemptStatus getStatus() {
        return status;
    }

    public void setStatus(DeliveryAttemptStatus status) {
        this.status = status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getRecipientSignature() {
        return recipientSignature;
    }

    public void setRecipientSignature(String recipientSignature) {
        this.recipientSignature = recipientSignature;
    }

    public String getPhotoProofUrl() {
        return photoProofUrl;
    }

    public void setPhotoProofUrl(String photoProofUrl) {
        this.photoProofUrl = photoProofUrl;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getAttemptTime() {
        return attemptTime;
    }

    public void setAttemptTime(LocalDateTime attemptTime) {
        this.attemptTime = attemptTime;
    }
}
