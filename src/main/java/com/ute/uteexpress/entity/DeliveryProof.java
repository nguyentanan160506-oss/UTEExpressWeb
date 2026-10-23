package com.ute.uteexpress.entity;

import com.ute.uteexpress.enums.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "delivery_proofs", schema = "dbo",
    uniqueConstraints = {@UniqueConstraint(name = "UQ_delivery_proofs_shipment", columnNames = {"shipment_id"}), @UniqueConstraint(name = "UQ_delivery_proofs_attempt", columnNames = {"attempt_id"})},
    indexes = {@Index(name = "IX_delivery_proofs_employee", columnList = "employee_id, delivered_at DESC")},
    check = {
        @CheckConstraint(name = "CK_delivery_proofs_recipient", constraint = "recipient_type IN ('RECIPIENT', 'FAMILY', 'AUTHORIZED_REP')"),
        @CheckConstraint(name = "CK_delivery_proofs_text", constraint = "LEN(LTRIM(RTRIM(recipient_name))) > 0 AND LEN(LTRIM(RTRIM(reference))) > 0")
    })
public class DeliveryProof {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id", nullable = false)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false, foreignKey = @ForeignKey(name = "FK_delivery_proofs_shipment_id"))
    private Shipment shipment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false, foreignKey = @ForeignKey(name = "FK_delivery_proofs_assignment_id"))
    private ShipmentAssignment assignment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false, foreignKey = @ForeignKey(name = "FK_delivery_proofs_attempt_id"))
    private DeliveryAttempt attempt;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, foreignKey = @ForeignKey(name = "FK_delivery_proofs_actor_id"))
    private User actor;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, foreignKey = @ForeignKey(name = "FK_delivery_proofs_employee_id"))
    private Employee employee;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "recipient_type", nullable = false, length = 30)
    private DeliveryRecipientType recipientType;
    @Nationalized
    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;
    @Nationalized
    @Column(name = "reference", nullable = false, length = 100)
    private String reference;
    @Nationalized
    @Column(name = "notes", nullable = true, length = 500)
    private String notes;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @ColumnDefault("SYSUTCDATETIME()")
    @Column(name = "delivered_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime deliveredAt;
    @PrePersist void initializeDefaults() {
        if (deliveredAt == null) { deliveredAt = UtcTimestamps.now(); }
    }
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Shipment getShipment() { return shipment; }
    public void setShipment(Shipment value) { shipment = value; }
    public ShipmentAssignment getAssignment() { return assignment; }
    public void setAssignment(ShipmentAssignment value) { assignment = value; }
    public DeliveryAttempt getAttempt() { return attempt; }
    public void setAttempt(DeliveryAttempt value) { attempt = value; }
    public User getActor() { return actor; }
    public void setActor(User value) { actor = value; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee value) { employee = value; }
    public DeliveryRecipientType getRecipientType() { return recipientType; }
    public void setRecipientType(DeliveryRecipientType value) { recipientType = value; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String value) { recipientName = value; }
    public String getReference() { return reference; }
    public void setReference(String value) { reference = value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { notes = value; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime value) { deliveredAt = value; }
}
