package com.ute.uteexpress.entity;

import com.ute.uteexpress.enums.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "transport_routes", schema = "dbo",
    uniqueConstraints = {@UniqueConstraint(name = "UQ_transport_routes_points", columnNames = {"origin_id", "destination_id"})},
    indexes = {@Index(name = "IX_transport_routes_origin", columnList = "origin_id, is_active")},
    check = {
        @CheckConstraint(name = "CK_transport_routes_points", constraint = "origin_id <> destination_id"),
        @CheckConstraint(name = "CK_transport_routes_leg", constraint = "leg IN ('COLLECTION', 'LINEHAUL', 'DISTRIBUTION')")
    })
public class TransportRoute {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id", nullable = false)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origin_id", nullable = false, foreignKey = @ForeignKey(name = "FK_transport_routes_origin_id"))
    private Branch origin;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_id", nullable = false, foreignKey = @ForeignKey(name = "FK_transport_routes_destination_id"))
    private Branch destination;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "leg", nullable = false, length = 30)
    private TransportLeg leg;
    @ColumnDefault("1")
    @Column(name = "is_active", nullable = false)
    private Boolean active = true;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @ColumnDefault("SYSUTCDATETIME()")
    @Column(name = "created_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime createdAt;
    @PrePersist void initializeDefaults() {
        if (active == null) { active = true; }
        if (createdAt == null) { createdAt = UtcTimestamps.now(); }
    }
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Branch getOrigin() { return origin; }
    public void setOrigin(Branch value) { origin = value; }
    public Branch getDestination() { return destination; }
    public void setDestination(Branch value) { destination = value; }
    public TransportLeg getLeg() { return leg; }
    public void setLeg(TransportLeg value) { leg = value; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean value) { active = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
}
