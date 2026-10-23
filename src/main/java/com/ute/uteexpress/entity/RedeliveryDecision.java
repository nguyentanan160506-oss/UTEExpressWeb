package com.ute.uteexpress.entity;

import com.ute.uteexpress.enums.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "redelivery_decisions", schema = "dbo",
    uniqueConstraints = {@UniqueConstraint(name = "UQ_redelivery_decisions_attempt", columnNames = {"shipment_id", "after_attempt_number"})},
    indexes = {@Index(name = "IX_redelivery_decisions_shipment", columnList = "shipment_id, decided_at DESC")},
    check = {
        @CheckConstraint(name = "CK_redelivery_decisions_mode", constraint = "mode IN ('SAME_COURIER', 'NEW_ASSIGNMENT')"),
        @CheckConstraint(name = "CK_redelivery_decisions_attempt", constraint = "after_attempt_number >= 1"),
        @CheckConstraint(name = "CK_redelivery_decisions_reason", constraint = "LEN(LTRIM(RTRIM(reason))) > 0")
    })
public class RedeliveryDecision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id", nullable = false)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false, foreignKey = @ForeignKey(name = "FK_redelivery_decisions_shipment_id"))
    private Shipment shipment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false, foreignKey = @ForeignKey(name = "FK_redelivery_decisions_assignment_id"))
    private ShipmentAssignment assignment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, foreignKey = @ForeignKey(name = "FK_redelivery_decisions_actor_id"))
    private User actor;
    @Column(name = "after_attempt_number", nullable = false)
    private Integer afterAttemptNumber;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "mode", nullable = false, length = 30)
    private RedeliveryMode mode;
    @Nationalized
    @Column(name = "reason", nullable = false, length = 500)
    private String reason;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @ColumnDefault("SYSUTCDATETIME()")
    @Column(name = "decided_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime decidedAt;
    @PrePersist void initializeDefaults() {
        if (decidedAt == null) { decidedAt = UtcTimestamps.now(); }
    }
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Shipment getShipment() { return shipment; }
    public void setShipment(Shipment value) { shipment = value; }
    public ShipmentAssignment getAssignment() { return assignment; }
    public void setAssignment(ShipmentAssignment value) { assignment = value; }
    public User getActor() { return actor; }
    public void setActor(User value) { actor = value; }
    public Integer getAfterAttemptNumber() { return afterAttemptNumber; }
    public void setAfterAttemptNumber(Integer value) { afterAttemptNumber = value; }
    public RedeliveryMode getMode() { return mode; }
    public void setMode(RedeliveryMode value) { mode = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime value) { decidedAt = value; }
}
