package com.ute.uteexpress.entity;

import com.ute.uteexpress.enums.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "trip_discrepancies", schema = "dbo",
    uniqueConstraints = {},
    indexes = {@Index(name = "IX_trip_discrepancies_reports_time", columnList = "recorded_at, id"), @Index(name = "IX_trip_discrepancies_trip", columnList = "trip_id, status, recorded_at DESC"), @Index(name = "IX_trip_discrepancies_item", columnList = "item_id, status")},
    check = {
        @CheckConstraint(name = "CK_trip_discrepancies_kind", constraint = "kind IN ('MISSING', 'DAMAGED', 'REJECTED', 'UNEXPECTED')"),
        @CheckConstraint(name = "CK_trip_discrepancies_status", constraint = "status IN ('OPEN', 'RESOLVED')"),
        @CheckConstraint(name = "CK_trip_discrepancies_item", constraint = "(kind = 'UNEXPECTED' AND item_id IS NULL) OR (kind IN ('MISSING', 'DAMAGED', 'REJECTED') AND item_id IS NOT NULL)"),
        @CheckConstraint(name = "CK_trip_discrepancies_text", constraint = "LEN(LTRIM(RTRIM(reference))) > 0 AND LEN(LTRIM(RTRIM(notes))) > 0"),
        @CheckConstraint(name = "CK_trip_discrepancies_resolution", constraint = "(status = 'OPEN' AND resolved_by IS NULL AND resolved_employee_id IS NULL AND resolved_at IS NULL AND resolution IS NULL) OR (status = 'RESOLVED' AND resolved_by IS NOT NULL AND resolved_employee_id IS NOT NULL AND resolved_at IS NOT NULL AND resolution IS NOT NULL AND LEN(LTRIM(RTRIM(resolution))) > 0)")
    })
public class TripDiscrepancy {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id", nullable = false)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false, foreignKey = @ForeignKey(name = "FK_trip_discrepancies_trip_id"))
    private TransportTrip trip;
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "item_id", nullable = true, foreignKey = @ForeignKey(name = "FK_trip_discrepancies_item_id"))
    private TransportTripItem item;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "kind", nullable = false, length = 30)
    private TripDiscrepancyKind kind;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @ColumnDefault("'OPEN'")
    @Column(name = "status", nullable = false, length = 30)
    private TripDiscrepancyStatus status = TripDiscrepancyStatus.OPEN;
    @Nationalized
    @Column(name = "reference", nullable = false, length = 100)
    private String reference;
    @Nationalized
    @Column(name = "notes", nullable = false, length = 500)
    private String notes;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, foreignKey = @ForeignKey(name = "FK_trip_discrepancies_actor_id"))
    private User actor;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, foreignKey = @ForeignKey(name = "FK_trip_discrepancies_employee_id"))
    private Employee employee;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @ColumnDefault("SYSUTCDATETIME()")
    @Column(name = "recorded_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime recordedAt;
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "resolved_by", nullable = true, foreignKey = @ForeignKey(name = "FK_trip_discrepancies_resolved_by"))
    private User resolvedBy;
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "resolved_employee_id", nullable = true, foreignKey = @ForeignKey(name = "FK_trip_discrepancies_resolved_employee_id"))
    private Employee resolvedEmployee;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "resolved_at", nullable = true, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime resolvedAt;
    @Nationalized
    @Column(name = "resolution", nullable = true, length = 500)
    private String resolution;
    @PrePersist void initializeDefaults() {
        if (status == null) { status = TripDiscrepancyStatus.OPEN; }
        if (recordedAt == null) { recordedAt = UtcTimestamps.now(); }
    }
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public TransportTrip getTrip() { return trip; }
    public void setTrip(TransportTrip value) { trip = value; }
    public TransportTripItem getItem() { return item; }
    public void setItem(TransportTripItem value) { item = value; }
    public TripDiscrepancyKind getKind() { return kind; }
    public void setKind(TripDiscrepancyKind value) { kind = value; }
    public TripDiscrepancyStatus getStatus() { return status; }
    public void setStatus(TripDiscrepancyStatus value) { status = value; }
    public String getReference() { return reference; }
    public void setReference(String value) { reference = value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { notes = value; }
    public User getActor() { return actor; }
    public void setActor(User value) { actor = value; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee value) { employee = value; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime value) { recordedAt = value; }
    public User getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(User value) { resolvedBy = value; }
    public Employee getResolvedEmployee() { return resolvedEmployee; }
    public void setResolvedEmployee(Employee value) { resolvedEmployee = value; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime value) { resolvedAt = value; }
    public String getResolution() { return resolution; }
    public void setResolution(String value) { resolution = value; }
}
