package com.ute.uteexpress.entity;

import com.ute.uteexpress.enums.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "origin_handoffs", schema = "dbo",
    indexes = {@Index(name = "IX_origin_handoffs_branch_status", columnList = "branch_id, status, requested_at DESC"), @Index(name = "IX_origin_handoffs_assignment", columnList = "assignment_id, requested_at DESC")},
    check = {
        @CheckConstraint(name = "CK_origin_handoffs_status", constraint = "status IN ('PENDING', 'ACCEPTED', 'REJECTED')"),
        @CheckConstraint(name = "CK_origin_handoffs_review", constraint = "(status = 'PENDING' AND reviewed_by IS NULL AND receiver_id IS NULL AND reviewed_at IS NULL AND reason IS NULL) OR (status IN ('ACCEPTED', 'REJECTED') AND reviewed_by IS NOT NULL AND receiver_id IS NOT NULL AND reviewed_at IS NOT NULL)"),
        @CheckConstraint(name = "CK_origin_handoffs_rejection", constraint = "status <> 'REJECTED' OR (reason IS NOT NULL AND LEN(LTRIM(RTRIM(reason))) > 0)"),
        @CheckConstraint(name = "CK_origin_handoffs_independent", constraint = "(reviewed_by IS NULL OR reviewed_by <> requested_by) AND (receiver_id IS NULL OR receiver_id <> employee_id)")
    })
public class OriginHandoff {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id", nullable = false)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false, foreignKey = @ForeignKey(name = "FK_origin_handoffs_shipment_id"))
    private Shipment shipment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false, foreignKey = @ForeignKey(name = "FK_origin_handoffs_assignment_id"))
    private ShipmentAssignment assignment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evidence_id", nullable = false, foreignKey = @ForeignKey(name = "FK_origin_handoffs_evidence_id"))
    private PickupEvidence evidence;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false, foreignKey = @ForeignKey(name = "FK_origin_handoffs_branch_id"))
    private Branch branch;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, foreignKey = @ForeignKey(name = "FK_origin_handoffs_employee_id"))
    private Employee employee;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false, foreignKey = @ForeignKey(name = "FK_origin_handoffs_requested_by"))
    private User requestedBy;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private HandoffStatus status;
    @Nationalized
    @Column(name = "notes", nullable = true, length = 500)
    private String notes;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @ColumnDefault("SYSUTCDATETIME()")
    @Column(name = "requested_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)", updatable = false)
    private LocalDateTime requestedAt;
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "reviewed_by", nullable = true, foreignKey = @ForeignKey(name = "FK_origin_handoffs_reviewed_by"))
    private User reviewedBy;
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "receiver_id", nullable = true, foreignKey = @ForeignKey(name = "FK_origin_handoffs_receiver_id"))
    private Employee receiver;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "reviewed_at", nullable = true, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime reviewedAt;
    @Nationalized
    @Column(name = "reason", nullable = true, length = 500)
    private String reason;
    @PrePersist void initializeDefaults() {
        if (requestedAt == null) { requestedAt = UtcTimestamps.now(); }
    }
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Shipment getShipment() { return shipment; }
    public void setShipment(Shipment value) { shipment = value; }
    public ShipmentAssignment getAssignment() { return assignment; }
    public void setAssignment(ShipmentAssignment value) { assignment = value; }
    public PickupEvidence getEvidence() { return evidence; }
    public void setEvidence(PickupEvidence value) { evidence = value; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch value) { branch = value; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee value) { employee = value; }
    public User getRequestedBy() { return requestedBy; }
    public void setRequestedBy(User value) { requestedBy = value; }
    public HandoffStatus getStatus() { return status; }
    public void setStatus(HandoffStatus value) { status = value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { notes = value; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime value) { requestedAt = value; }
    public User getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(User value) { reviewedBy = value; }
    public Employee getReceiver() { return receiver; }
    public void setReceiver(Employee value) { receiver = value; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime value) { reviewedAt = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
}
