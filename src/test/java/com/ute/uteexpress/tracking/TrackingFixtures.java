package com.ute.uteexpress.tracking;

import com.ute.uteexpress.enums.ShipmentStatus;
import com.ute.uteexpress.repository.ShipmentRepository.PublicTrackingSummary;
import com.ute.uteexpress.repository.ShipmentEventRepository.PublicTrackingEvent;
import java.time.LocalDateTime;

/** Isolated public-tracking test records; never seed the database. */
public final class TrackingFixtures {
    private TrackingFixtures() { }
    public static final String CODE = "UTE" + "A".repeat(32);
    public static final LocalDateTime TIME = LocalDateTime.of(2026, 10, 16, 0, 0);
    public static PublicTrackingSummary summary(ShipmentStatus status) {
        return new PublicTrackingSummary() {
            public ShipmentStatus getStatus() { return status; }
            public LocalDateTime getUpdatedAt() { return TIME; }
        };
    }
    public static PublicTrackingEvent event(String title, ShipmentStatus from, ShipmentStatus to) {
        return new PublicTrackingEvent() {
            public String getTitle() { return title; }
            public ShipmentStatus getFromStatus() { return from; }
            public ShipmentStatus getToStatus() { return to; }
            public LocalDateTime getOccurredAt() { return TIME; }
        };
    }
}
