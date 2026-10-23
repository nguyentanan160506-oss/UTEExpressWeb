package com.ute.uteexpress.entity;

import com.ute.uteexpress.enums.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "transport_trip_items", schema = "dbo",
    uniqueConstraints = {@UniqueConstraint(name = "UQ_trip_items_manifest", columnNames = {"trip_id", "shipment_id"})},
    indexes = {@Index(name = "IX_trip_items_trip_status", columnList = "trip_id, status"), @Index(name = "IX_trip_items_shipment", columnList = "shipment_id, added_at DESC"), @Index(name = "IX_trip_items_return_request", columnList = "return_request_id")},
    check = {
        @CheckConstraint(name = "CK_trip_items_status", constraint = "status IN ('MANIFESTED', 'RECEIVED', 'MISSING', 'DAMAGED', 'REJECTED')"),
        @CheckConstraint(name = "CK_trip_items_active", constraint = "(status = 'RECEIVED' AND is_active = 0) OR (status IN ('MANIFESTED', 'MISSING', 'DAMAGED', 'REJECTED') AND is_active = 1)")
    })
public class TransportTripItem {
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "return_request_id", nullable = true, foreignKey = @ForeignKey(name = "FK_trip_items_return_request"))
    private ReturnRequest returnRequest;
    public ReturnRequest getReturnRequest() { return returnRequest; }
    public void setReturnRequest(ReturnRequest value) { returnRequest = value; }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id", nullable = false)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false, foreignKey = @ForeignKey(name = "FK_transport_trip_items_trip_id"))
    private TransportTrip trip;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false, foreignKey = @ForeignKey(name = "FK_transport_trip_items_shipment_id"))
    private Shipment shipment;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @ColumnDefault("'MANIFESTED'")
    @Column(name = "status", nullable = false, length = 30)
    private TripItemStatus status = TripItemStatus.MANIFESTED;
    @ColumnDefault("1")
    @Column(name = "is_active", nullable = false)
    private Boolean active = true;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @ColumnDefault("SYSUTCDATETIME()")
    @Column(name = "added_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime addedAt;
    @PrePersist void initializeDefaults() {
        if (status == null) { status = TripItemStatus.MANIFESTED; }
        if (active == null) { active = true; }
        if (addedAt == null) { addedAt = UtcTimestamps.now(); }
    }
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public TransportTrip getTrip() { return trip; }
    public void setTrip(TransportTrip value) { trip = value; }
    public Shipment getShipment() { return shipment; }
    public void setShipment(Shipment value) { shipment = value; }
    public TripItemStatus getStatus() { return status; }
    public void setStatus(TripItemStatus value) { status = value; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean value) { active = value; }
    public LocalDateTime getAddedAt() { return addedAt; }
    public void setAddedAt(LocalDateTime value) { addedAt = value; }
}
