package com.ute.uteexpress.entity;

import com.ute.uteexpress.enums.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "trip_receipts", schema = "dbo",
    uniqueConstraints = {@UniqueConstraint(name = "UQ_trip_receipts_item", columnNames = {"item_id"})},
    indexes = {@Index(name = "IX_trip_receipts_reports_time", columnList = "received_at, id"), @Index(name = "IX_trip_receipts_operations_period", columnList = "branch_id, received_at"), @Index(name = "IX_trip_receipts_branch", columnList = "branch_id, received_at DESC")},
    check = {
        @CheckConstraint(name = "CK_trip_receipts_reference", constraint = "LEN(LTRIM(RTRIM(reference))) > 0")
    })
public class TripReceipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id", nullable = false)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false, foreignKey = @ForeignKey(name = "FK_trip_receipts_item_id"))
    private TransportTripItem item;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false, foreignKey = @ForeignKey(name = "FK_trip_receipts_branch_id"))
    private Branch branch;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, foreignKey = @ForeignKey(name = "FK_trip_receipts_actor_id"))
    private User actor;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, foreignKey = @ForeignKey(name = "FK_trip_receipts_employee_id"))
    private Employee employee;
    @Nationalized
    @Column(name = "reference", nullable = false, length = 100)
    private String reference;
    @Nationalized
    @Column(name = "notes", nullable = true, length = 500)
    private String notes;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @ColumnDefault("SYSUTCDATETIME()")
    @Column(name = "received_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime receivedAt;
    @PrePersist void initializeDefaults() {
        if (receivedAt == null) { receivedAt = UtcTimestamps.now(); }
    }
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public TransportTripItem getItem() { return item; }
    public void setItem(TransportTripItem value) { item = value; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch value) { branch = value; }
    public User getActor() { return actor; }
    public void setActor(User value) { actor = value; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee value) { employee = value; }
    public String getReference() { return reference; }
    public void setReference(String value) { reference = value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { notes = value; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime value) { receivedAt = value; }
}
