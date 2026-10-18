package com.ute.uteexpress.entity;

import com.ute.uteexpress.enums.ShipmentMethod;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "shipment_intakes", schema = "dbo",
    uniqueConstraints = @UniqueConstraint(name = "UQ_shipment_intakes_shipment", columnNames = "shipment_id"),
    indexes = @Index(name = "IX_intakes_branch_received", columnList = "branch_id, received_at DESC"),
    check = {
        @CheckConstraint(name = "CK_intakes_method", constraint = "shipment_method IN ('HOME_PICKUP', 'BRANCH_DROPOFF')"),
        @CheckConstraint(name = "CK_intakes_pickup", constraint = "(shipment_method = 'BRANCH_DROPOFF' AND pickup_assignment_id IS NULL) OR (shipment_method = 'HOME_PICKUP' AND pickup_assignment_id IS NOT NULL)")
    })
public class ShipmentIntake {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id", nullable = false)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false, foreignKey = @ForeignKey(name = "FK_intakes_shipment"))
    private Shipment shipment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false, foreignKey = @ForeignKey(name = "FK_intakes_branch"))
    private Branch branch;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "received_by", nullable = false, foreignKey = @ForeignKey(name = "FK_intakes_actor"))
    private User receivedBy;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, foreignKey = @ForeignKey(name = "FK_intakes_employee"))
    private Employee employee;
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "pickup_assignment_id", nullable = true, foreignKey = @ForeignKey(name = "FK_intakes_pickup"))
    private ShipmentAssignment pickupAssignment;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "shipment_method", nullable = false, length = 30)
    private ShipmentMethod shipmentMethod;
    @Nationalized @Column(name = "receipt_reference", nullable = false, length = 100)
    private String receiptReference;
    @Nationalized @Column(name = "notes", nullable = true, length = 500)
    private String notes;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @ColumnDefault("SYSUTCDATETIME()")
    @Column(name = "received_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)", updatable = false)
    private LocalDateTime receivedAt;
    @PrePersist void initializeDefaults() { if (receivedAt == null) { receivedAt = UtcTimestamps.now(); } }

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Shipment getShipment() { return shipment; }
    public void setShipment(Shipment value) { shipment = value; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch value) { branch = value; }
    public User getReceivedBy() { return receivedBy; }
    public void setReceivedBy(User value) { receivedBy = value; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee value) { employee = value; }
    public ShipmentAssignment getPickupAssignment() { return pickupAssignment; }
    public void setPickupAssignment(ShipmentAssignment value) { pickupAssignment = value; }
    public ShipmentMethod getShipmentMethod() { return shipmentMethod; }
    public void setShipmentMethod(ShipmentMethod value) { shipmentMethod = value; }
    public String getReceiptReference() { return receiptReference; }
    public void setReceiptReference(String value) { receiptReference = value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { notes = value; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime value) { receivedAt = value; }
}
