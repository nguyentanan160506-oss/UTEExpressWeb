package com.ute.uteexpress.entity;

import com.ute.uteexpress.enums.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "return_requests", schema = "dbo",
    uniqueConstraints = {@UniqueConstraint(name = "UQ_return_requests_shipment", columnNames = {"shipment_id"})},
    indexes = {@Index(name = "IX_return_requests_stage", columnList = "stage, requested_at DESC")},
    check = {
        @CheckConstraint(name = "CK_return_requests_stage", constraint = "stage IN ('REQUESTED', 'READY_FOR_TRANSPORT', 'IN_TRANSIT', 'AT_ORIGIN', 'COMPLETED')"),
        @CheckConstraint(name = "CK_return_requests_reason", constraint = "LEN(LTRIM(RTRIM(reason))) > 0"),
        @CheckConstraint(name = "CK_return_requests_completion", constraint = "(stage = 'COMPLETED' AND completed_at IS NOT NULL) OR (stage <> 'COMPLETED' AND completed_at IS NULL)")
    })
public class ReturnRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id", nullable = false)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false, foreignKey = @ForeignKey(name = "FK_return_requests_shipment_id"))
    private Shipment shipment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false, foreignKey = @ForeignKey(name = "FK_return_requests_requested_by"))
    private User requestedBy;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "stage", nullable = false, length = 30)
    private ReturnStage stage;
    @Nationalized
    @Column(name = "reason", nullable = false, length = 500)
    private String reason;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @ColumnDefault("SYSUTCDATETIME()")
    @Column(name = "requested_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime requestedAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "completed_at", nullable = true, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime completedAt;
    @PrePersist void initializeDefaults() {
        if (requestedAt == null) { requestedAt = UtcTimestamps.now(); }
    }
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Shipment getShipment() { return shipment; }
    public void setShipment(Shipment value) { shipment = value; }
    public User getRequestedBy() { return requestedBy; }
    public void setRequestedBy(User value) { requestedBy = value; }
    public ReturnStage getStage() { return stage; }
    public void setStage(ReturnStage value) { stage = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime value) { requestedAt = value; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime value) { completedAt = value; }
}
