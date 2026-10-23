package com.ute.uteexpress.entity;

import com.ute.uteexpress.enums.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "transport_trips", schema = "dbo",
    uniqueConstraints = {@UniqueConstraint(name = "UQ_transport_trips_code", columnNames = {"code"}), @UniqueConstraint(name = "UQ_transport_trips_token", columnNames = {"request_token"})},
    indexes = {@Index(name = "IX_transport_trips_origin", columnList = "origin_id, status, created_at DESC"), @Index(name = "IX_transport_trips_destination", columnList = "destination_id, status, created_at DESC")},
    check = {
        @CheckConstraint(name = "CK_transport_trips_points", constraint = "origin_id <> destination_id"),
        @CheckConstraint(name = "CK_transport_trips_leg", constraint = "leg IN ('COLLECTION', 'LINEHAUL', 'DISTRIBUTION')"),
        @CheckConstraint(name = "CK_transport_trips_status", constraint = "status IN ('DRAFT', 'SEALED', 'DEPARTED', 'RECONCILING', 'CLOSED')"),
        @CheckConstraint(name = "CK_transport_trips_count", constraint = "expected_count >= 0 AND expected_count <= 500"),
        @CheckConstraint(name = "CK_transport_trips_version", constraint = "version >= 0"),
        @CheckConstraint(name = "CK_transport_trips_seal", constraint = "status = 'DRAFT' OR (sealed_by IS NOT NULL AND sealed_at IS NOT NULL AND expected_count > 0)"),
        @CheckConstraint(name = "CK_transport_trips_depart", constraint = "status IN ('DRAFT', 'SEALED') OR (departed_by IS NOT NULL AND departed_employee_id IS NOT NULL AND departed_at IS NOT NULL)"),
        @CheckConstraint(name = "CK_transport_trips_arrive", constraint = "status IN ('DRAFT', 'SEALED', 'DEPARTED') OR (arrived_by IS NOT NULL AND arrived_at IS NOT NULL)"),
        @CheckConstraint(name = "CK_transport_trips_close", constraint = "status <> 'CLOSED' OR (closed_by IS NOT NULL AND closed_at IS NOT NULL)")
    })
public class TransportTrip {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id", nullable = false)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false, foreignKey = @ForeignKey(name = "FK_transport_trips_route_id"))
    private TransportRoute route;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origin_id", nullable = false, foreignKey = @ForeignKey(name = "FK_transport_trips_origin_id"))
    private Branch origin;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_id", nullable = false, foreignKey = @ForeignKey(name = "FK_transport_trips_destination_id"))
    private Branch destination;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "leg", nullable = false, length = 30)
    private TransportLeg leg;
    @Column(name = "code", nullable = false, length = 35)
    private String code;
    @Column(name = "request_token", nullable = false, length = 32)
    private String requestToken;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @ColumnDefault("'DRAFT'")
    @Column(name = "status", nullable = false, length = 30)
    private TripStatus status = TripStatus.DRAFT;
    @Version
    @ColumnDefault("0")
    @Column(name = "version", nullable = false)
    private Long version;
    @ColumnDefault("0")
    @Column(name = "expected_count", nullable = false)
    private Integer expectedCount = 0;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, foreignKey = @ForeignKey(name = "FK_transport_trips_created_by"))
    private User createdBy;
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "sealed_by", nullable = true, foreignKey = @ForeignKey(name = "FK_transport_trips_sealed_by"))
    private User sealedBy;
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "departed_by", nullable = true, foreignKey = @ForeignKey(name = "FK_transport_trips_departed_by"))
    private User departedBy;
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "departed_employee_id", nullable = true, foreignKey = @ForeignKey(name = "FK_transport_trips_departed_employee_id"))
    private Employee departedEmployee;
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "arrived_by", nullable = true, foreignKey = @ForeignKey(name = "FK_transport_trips_arrived_by"))
    private User arrivedBy;
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "closed_by", nullable = true, foreignKey = @ForeignKey(name = "FK_transport_trips_closed_by"))
    private User closedBy;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @ColumnDefault("SYSUTCDATETIME()")
    @Column(name = "created_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime createdAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @ColumnDefault("SYSUTCDATETIME()")
    @Column(name = "updated_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime updatedAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "sealed_at", nullable = true, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime sealedAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "departed_at", nullable = true, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime departedAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "arrived_at", nullable = true, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime arrivedAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "closed_at", nullable = true, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime closedAt;
    @PrePersist void initializeDefaults() {
        if (status == null) { status = TripStatus.DRAFT; }
        if (version == null) { version = 0L; }
        if (expectedCount == null) { expectedCount = 0; }
        if (createdAt == null) { createdAt = UtcTimestamps.now(); }
        if (updatedAt == null) { updatedAt = UtcTimestamps.now(); }
    }
    public Long getId() { return id; }
    @PreUpdate void refreshUpdatedAt() { updatedAt = UtcTimestamps.now(); }
    public void setId(Long value) { id = value; }
    public TransportRoute getRoute() { return route; }
    public void setRoute(TransportRoute value) { route = value; }
    public Branch getOrigin() { return origin; }
    public void setOrigin(Branch value) { origin = value; }
    public Branch getDestination() { return destination; }
    public void setDestination(Branch value) { destination = value; }
    public TransportLeg getLeg() { return leg; }
    public void setLeg(TransportLeg value) { leg = value; }
    public String getCode() { return code; }
    public void setCode(String value) { code = value; }
    public String getRequestToken() { return requestToken; }
    public void setRequestToken(String value) { requestToken = value; }
    public TripStatus getStatus() { return status; }
    public void setStatus(TripStatus value) { status = value; }
    public Long getVersion() { return version; }
    public Integer getExpectedCount() { return expectedCount; }
    public void setExpectedCount(Integer value) { expectedCount = value; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User value) { createdBy = value; }
    public User getSealedBy() { return sealedBy; }
    public void setSealedBy(User value) { sealedBy = value; }
    public User getDepartedBy() { return departedBy; }
    public void setDepartedBy(User value) { departedBy = value; }
    public Employee getDepartedEmployee() { return departedEmployee; }
    public void setDepartedEmployee(Employee value) { departedEmployee = value; }
    public User getArrivedBy() { return arrivedBy; }
    public void setArrivedBy(User value) { arrivedBy = value; }
    public User getClosedBy() { return closedBy; }
    public void setClosedBy(User value) { closedBy = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
    public LocalDateTime getSealedAt() { return sealedAt; }
    public void setSealedAt(LocalDateTime value) { sealedAt = value; }
    public LocalDateTime getDepartedAt() { return departedAt; }
    public void setDepartedAt(LocalDateTime value) { departedAt = value; }
    public LocalDateTime getArrivedAt() { return arrivedAt; }
    public void setArrivedAt(LocalDateTime value) { arrivedAt = value; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime value) { closedAt = value; }
}
