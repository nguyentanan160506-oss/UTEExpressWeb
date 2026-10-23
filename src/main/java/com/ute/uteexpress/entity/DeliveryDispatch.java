package com.ute.uteexpress.entity;

import com.ute.uteexpress.enums.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "delivery_dispatches", schema = "dbo",
    uniqueConstraints = {@UniqueConstraint(name = "UQ_delivery_dispatch_assignment", columnNames = {"assignment_id"})},
    indexes = {@Index(name = "IX_delivery_dispatch_shipment", columnList = "shipment_id, prepared_at DESC")},
    check = {
        @CheckConstraint(name = "CK_delivery_dispatch_status", constraint = "status IN ('PREPARED', 'ACCEPTED', 'STARTED', 'FAILED', 'RECOVERY_REQUESTED', 'BRANCH_RETURNED', 'RECALL_REQUIRED', 'COMPLETED', 'CANCELLED')"),
        @CheckConstraint(name = "CK_delivery_dispatch_accept", constraint = "status IN ('PREPARED', 'CANCELLED') OR (accepted_by IS NOT NULL AND accepted_employee_id IS NOT NULL AND accepted_at IS NOT NULL)"),
        @CheckConstraint(name = "CK_delivery_dispatch_independent", constraint = "(accepted_by IS NULL OR accepted_by <> prepared_by) AND (accepted_employee_id IS NULL OR accepted_employee_id <> prepared_employee_id)"),
        @CheckConstraint(name = "CK_delivery_dispatch_reference", constraint = "LEN(LTRIM(RTRIM(reference))) > 0")
    })
public class DeliveryDispatch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id", nullable = false)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false, foreignKey = @ForeignKey(name = "FK_delivery_dispatches_shipment_id"))
    private Shipment shipment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false, foreignKey = @ForeignKey(name = "FK_delivery_dispatches_assignment_id"))
    private ShipmentAssignment assignment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false, foreignKey = @ForeignKey(name = "FK_delivery_dispatches_branch_id"))
    private Branch branch;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @ColumnDefault("'PREPARED'")
    @Column(name = "status", nullable = false, length = 30)
    private DeliveryDispatchStatus status = DeliveryDispatchStatus.PREPARED;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prepared_by", nullable = false, foreignKey = @ForeignKey(name = "FK_delivery_dispatches_prepared_by"))
    private User preparedBy;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prepared_employee_id", nullable = false, foreignKey = @ForeignKey(name = "FK_delivery_dispatches_prepared_employee_id"))
    private Employee preparedEmployee;
    @Nationalized
    @Column(name = "reference", nullable = false, length = 100)
    private String reference;
    @Nationalized
    @Column(name = "notes", nullable = true, length = 500)
    private String notes;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @ColumnDefault("SYSUTCDATETIME()")
    @Column(name = "prepared_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime preparedAt;
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "accepted_by", nullable = true, foreignKey = @ForeignKey(name = "FK_delivery_dispatches_accepted_by"))
    private User acceptedBy;
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "accepted_employee_id", nullable = true, foreignKey = @ForeignKey(name = "FK_delivery_dispatches_accepted_employee_id"))
    private Employee acceptedEmployee;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "accepted_at", nullable = true, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime acceptedAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "started_at", nullable = true, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime startedAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "finished_at", nullable = true, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime finishedAt;
    @PrePersist void initializeDefaults() {
        if (status == null) { status = DeliveryDispatchStatus.PREPARED; }
        if (preparedAt == null) { preparedAt = UtcTimestamps.now(); }
    }
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Shipment getShipment() { return shipment; }
    public void setShipment(Shipment value) { shipment = value; }
    public ShipmentAssignment getAssignment() { return assignment; }
    public void setAssignment(ShipmentAssignment value) { assignment = value; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch value) { branch = value; }
    public DeliveryDispatchStatus getStatus() { return status; }
    public void setStatus(DeliveryDispatchStatus value) { status = value; }
    public User getPreparedBy() { return preparedBy; }
    public void setPreparedBy(User value) { preparedBy = value; }
    public Employee getPreparedEmployee() { return preparedEmployee; }
    public void setPreparedEmployee(Employee value) { preparedEmployee = value; }
    public String getReference() { return reference; }
    public void setReference(String value) { reference = value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { notes = value; }
    public LocalDateTime getPreparedAt() { return preparedAt; }
    public void setPreparedAt(LocalDateTime value) { preparedAt = value; }
    public User getAcceptedBy() { return acceptedBy; }
    public void setAcceptedBy(User value) { acceptedBy = value; }
    public Employee getAcceptedEmployee() { return acceptedEmployee; }
    public void setAcceptedEmployee(Employee value) { acceptedEmployee = value; }
    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime value) { acceptedAt = value; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime value) { startedAt = value; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime value) { finishedAt = value; }
}
