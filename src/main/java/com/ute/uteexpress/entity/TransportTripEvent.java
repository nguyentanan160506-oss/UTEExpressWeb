package com.ute.uteexpress.entity;

import com.ute.uteexpress.enums.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "transport_trip_events", schema = "dbo",
    uniqueConstraints = {},
    indexes = {@Index(name = "IX_transport_trip_events_timeline", columnList = "trip_id, occurred_at DESC")},
    check = {
        @CheckConstraint(name = "CK_transport_trip_events_type", constraint = "event_type IN ('CREATED', 'ITEMS_ADDED', 'SEALED', 'DEPARTED', 'ARRIVED', 'ITEM_RECEIVED', 'DISCREPANCY_RECORDED', 'DISCREPANCY_RESOLVED', 'CLOSED')"),
        @CheckConstraint(name = "CK_transport_trip_events_from", constraint = "from_status IS NULL OR from_status IN ('DRAFT', 'SEALED', 'DEPARTED', 'RECONCILING', 'CLOSED')"),
        @CheckConstraint(name = "CK_transport_trip_events_to", constraint = "to_status IN ('DRAFT', 'SEALED', 'DEPARTED', 'RECONCILING', 'CLOSED')")
    })
public class TransportTripEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id", nullable = false)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false, foreignKey = @ForeignKey(name = "FK_transport_trip_events_trip_id"))
    private TransportTrip trip;
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "item_id", nullable = true, foreignKey = @ForeignKey(name = "FK_transport_trip_events_item_id"))
    private TransportTripItem item;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "event_type", nullable = false, length = 30)
    private TripEventType type;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "from_status", nullable = true, length = 30)
    private TripStatus fromStatus;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "to_status", nullable = false, length = 30)
    private TripStatus toStatus;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, foreignKey = @ForeignKey(name = "FK_transport_trip_events_actor_id"))
    private User actor;
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "employee_id", nullable = true, foreignKey = @ForeignKey(name = "FK_transport_trip_events_employee_id"))
    private Employee employee;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false, foreignKey = @ForeignKey(name = "FK_transport_trip_events_branch_id"))
    private Branch branch;
    @Nationalized
    @Column(name = "reference", nullable = true, length = 100)
    private String reference;
    @Nationalized
    @Column(name = "notes", nullable = true, length = 500)
    private String notes;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @ColumnDefault("SYSUTCDATETIME()")
    @Column(name = "occurred_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime occurredAt;
    @PrePersist void initializeDefaults() {
        if (occurredAt == null) { occurredAt = UtcTimestamps.now(); }
    }
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public TransportTrip getTrip() { return trip; }
    public void setTrip(TransportTrip value) { trip = value; }
    public TransportTripItem getItem() { return item; }
    public void setItem(TransportTripItem value) { item = value; }
    public TripEventType getType() { return type; }
    public void setType(TripEventType value) { type = value; }
    public TripStatus getFromStatus() { return fromStatus; }
    public void setFromStatus(TripStatus value) { fromStatus = value; }
    public TripStatus getToStatus() { return toStatus; }
    public void setToStatus(TripStatus value) { toStatus = value; }
    public User getActor() { return actor; }
    public void setActor(User value) { actor = value; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee value) { employee = value; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch value) { branch = value; }
    public String getReference() { return reference; }
    public void setReference(String value) { reference = value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { notes = value; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime value) { occurredAt = value; }
}
