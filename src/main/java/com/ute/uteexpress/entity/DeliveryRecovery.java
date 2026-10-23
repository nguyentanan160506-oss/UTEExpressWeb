package com.ute.uteexpress.entity;

import com.ute.uteexpress.enums.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "delivery_recoveries", schema = "dbo",
    uniqueConstraints = {},
    indexes = {@Index(name = "IX_delivery_recoveries_shipment", columnList = "shipment_id, requested_at DESC"), @Index(name = "IX_delivery_recoveries_branch", columnList = "branch_id, status")},
    check = {
        @CheckConstraint(name = "CK_delivery_recoveries_status", constraint = "status IN ('PENDING', 'RECEIVED', 'REJECTED')"),
        @CheckConstraint(name = "CK_delivery_recoveries_review", constraint = "status = 'PENDING' OR (reviewed_by IS NOT NULL AND receiver_id IS NOT NULL AND reviewed_at IS NOT NULL)"),
        @CheckConstraint(name = "CK_delivery_recoveries_receipt", constraint = "status <> 'RECEIVED' OR (reference IS NOT NULL AND LEN(LTRIM(RTRIM(reference))) > 0)"),
        @CheckConstraint(name = "CK_delivery_recoveries_reason", constraint = "status <> 'REJECTED' OR (reason IS NOT NULL AND LEN(LTRIM(RTRIM(reason))) > 0)"),
        @CheckConstraint(name = "CK_delivery_recoveries_independent", constraint = "(reviewed_by IS NULL OR reviewed_by <> requested_by) AND (receiver_id IS NULL OR receiver_id <> employee_id)")
    })
public class DeliveryRecovery {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id", nullable = false)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false, foreignKey = @ForeignKey(name = "FK_delivery_recoveries_shipment_id"))
    private Shipment shipment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false, foreignKey = @ForeignKey(name = "FK_delivery_recoveries_assignment_id"))
    private ShipmentAssignment assignment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false, foreignKey = @ForeignKey(name = "FK_delivery_recoveries_branch_id"))
    private Branch branch;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false, foreignKey = @ForeignKey(name = "FK_delivery_recoveries_requested_by"))
    private User requestedBy;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, foreignKey = @ForeignKey(name = "FK_delivery_recoveries_employee_id"))
    private Employee employee;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @ColumnDefault("'PENDING'")
    @Column(name = "status", nullable = false, length = 30)
    private DeliveryRecoveryStatus status = DeliveryRecoveryStatus.PENDING;
    @Nationalized
    @Column(name = "notes", nullable = true, length = 500)
    private String notes;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @ColumnDefault("SYSUTCDATETIME()")
    @Column(name = "requested_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime requestedAt;
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "reviewed_by", nullable = true, foreignKey = @ForeignKey(name = "FK_delivery_recoveries_reviewed_by"))
    private User reviewedBy;
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "receiver_id", nullable = true, foreignKey = @ForeignKey(name = "FK_delivery_recoveries_receiver_id"))
    private Employee receiver;
    @Nationalized
    @Column(name = "reference", nullable = true, length = 100)
    private String reference;
    @Nationalized
    @Column(name = "reason", nullable = true, length = 500)
    private String reason;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "reviewed_at", nullable = true, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime reviewedAt;
    @PrePersist void initializeDefaults() {
        if (status == null) { status = DeliveryRecoveryStatus.PENDING; }
        if (requestedAt == null) { requestedAt = UtcTimestamps.now(); }
    }
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Shipment getShipment() { return shipment; }
    public void setShipment(Shipment value) { shipment = value; }
    public ShipmentAssignment getAssignment() { return assignment; }
    public void setAssignment(ShipmentAssignment value) { assignment = value; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch value) { branch = value; }
    public User getRequestedBy() { return requestedBy; }
    public void setRequestedBy(User value) { requestedBy = value; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee value) { employee = value; }
    public DeliveryRecoveryStatus getStatus() { return status; }
    public void setStatus(DeliveryRecoveryStatus value) { status = value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { notes = value; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime value) { requestedAt = value; }
    public User getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(User value) { reviewedBy = value; }
    public Employee getReceiver() { return receiver; }
    public void setReceiver(Employee value) { receiver = value; }
    public String getReference() { return reference; }
    public void setReference(String value) { reference = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime value) { reviewedAt = value; }
}
