package com.ute.uteexpress.dto.tracking;

import java.time.LocalDateTime;
import java.util.List;

/** Deliberately independent of User ShipmentDetailView; contains no entity ids or personal data. */
public record PublicTrackingView(String trackingNumber, String statusLabel,
        LocalDateTime updatedAt, List<Event> events) {
    public PublicTrackingView { events = List.copyOf(events); }
    public record Event(String label, LocalDateTime occurredAt) { }
}
