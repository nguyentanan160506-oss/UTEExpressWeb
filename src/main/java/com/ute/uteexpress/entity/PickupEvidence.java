package com.ute.uteexpress.entity;

import com.ute.uteexpress.enums.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "pickup_evidence", schema = "dbo",
    indexes = {@Index(name = "IX_pickup_evidence_assignment", columnList = "assignment_id, recorded_at DESC")},
    check = {
        @CheckConstraint(name = "CK_pickup_evidence_result", constraint = "result IN ('SUCCESS', 'FAILED')"),
        @CheckConstraint(name = "CK_pickup_evidence_confirmation", constraint = "(result = 'SUCCESS' AND reference IS NOT NULL AND LEN(LTRIM(RTRIM(reference))) > 0) OR (result = 'FAILED' AND notes IS NOT NULL AND LEN(LTRIM(RTRIM(notes))) > 0)")
    })
public class PickupEvidence {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id", nullable = false)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false, foreignKey = @ForeignKey(name = "FK_pickup_evidence_shipment_id"))
    private Shipment shipment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false, foreignKey = @ForeignKey(name = "FK_pickup_evidence_assignment_id"))
    private ShipmentAssignment assignment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, foreignKey = @ForeignKey(name = "FK_pickup_evidence_employee_id"))
    private Employee employee;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, foreignKey = @ForeignKey(name = "FK_pickup_evidence_actor_id"))
    private User actor;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "result", nullable = false, length = 20)
    private PickupResult result;
    @Nationalized
    @Column(name = "reference", nullable = true, length = 100)
    private String reference;
    @Nationalized
    @Column(name = "notes", nullable = true, length = 500)
    private String notes;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @ColumnDefault("SYSUTCDATETIME()")
    @Column(name = "recorded_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)", updatable = false)
    private LocalDateTime recordedAt;
    @PrePersist void initializeDefaults() {
        if (recordedAt == null) { recordedAt = UtcTimestamps.now(); }
    }
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Shipment getShipment() { return shipment; }
    public void setShipment(Shipment value) { shipment = value; }
    public ShipmentAssignment getAssignment() { return assignment; }
    public void setAssignment(ShipmentAssignment value) { assignment = value; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee value) { employee = value; }
    public User getActor() { return actor; }
    public void setActor(User value) { actor = value; }
    public PickupResult getResult() { return result; }
    public void setResult(PickupResult value) { result = value; }
    public String getReference() { return reference; }
    public void setReference(String value) { reference = value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { notes = value; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime value) { recordedAt = value; }
}
