package com.ute.uteexpress.dto.transport;

import com.ute.uteexpress.enums.*;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;

public final class TripViews {
    private TripViews() { }
    public record Route(Long id, String origin, String destination, TransportLeg leg) { }
    public record Row(Long id, String code, String origin, String destination, TransportLeg leg, TripStatus status, LocalDateTime createdAt) { }
    public record Candidate(Long id, String trackingNumber, String origin, String destination, ShipmentStatus status, Long version) { }
    public record Item(Long id, Long shipmentId, String trackingNumber, TripItemStatus status, ShipmentStatus shipmentStatus,
            String receiptReference, String receivedBy, LocalDateTime receivedAt) { }
    public record Issue(Long id, Long itemId, TripDiscrepancyKind kind, TripDiscrepancyStatus status,
            String reference, String notes, String actor, LocalDateTime recordedAt, String resolution, String resolvedBy, LocalDateTime resolvedAt) { }
    public record Event(TripEventType type, TripStatus fromStatus, TripStatus toStatus, String actor,
            String point, String reference, String notes, LocalDateTime occurredAt) { }
    public record Detail(Row trip, Long version, String creator, String sealedBy, String departedBy, String arrivedBy,
            String closedBy, LocalDateTime sealedAt, LocalDateTime departedAt, LocalDateTime arrivedAt, LocalDateTime closedAt,
            int expected, long manifest, long received, long missing, long damaged, long rejected, long openIssues,
            Page<Item> items, Page<Candidate> candidates, Page<Issue> issues, Page<Event> events,
            boolean canPlan, boolean canSeal, boolean canDepart, boolean canArrive, boolean canReceive, boolean canClose) { }
    public record History(Long tripId, String code, String origin, String destination, TransportLeg leg, TripStatus tripStatus,
            TripItemStatus itemStatus, LocalDateTime addedAt, String receiptReference, LocalDateTime receivedAt) { }
}
