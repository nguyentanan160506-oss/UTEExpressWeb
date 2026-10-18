package com.ute.uteexpress.service;

import com.ute.uteexpress.enums.ShipmentStatus;
import com.ute.uteexpress.repository.ShipmentEventRepository.PublicTrackingEvent;
import java.util.Set;

/** Explicit public allowlist. New workflow events must be reviewed before being added here. */
public final class PublicTrackingEventPolicy {
    private PublicTrackingEventPolicy() { }
    public static final String CREATED_TITLE = "Đã tạo vận đơn";

    // Do not silently expose a new status added to ShipmentStatus or its UI labels in a later commit.
    private static final Set<ShipmentStatus> PUBLIC_STATUSES = Set.of(
        ShipmentStatus.CREATED, ShipmentStatus.CONFIRMED, ShipmentStatus.PICKUP_ASSIGNED,
        ShipmentStatus.PICKED_UP, ShipmentStatus.AT_ORIGIN_HUB, ShipmentStatus.IN_TRANSIT,
        ShipmentStatus.AT_DESTINATION_HUB, ShipmentStatus.OUT_FOR_DELIVERY, ShipmentStatus.DELIVERED,
        ShipmentStatus.DELIVERY_FAILED, ShipmentStatus.RETURNING, ShipmentStatus.RETURNED, ShipmentStatus.CANCELLED,
        ShipmentStatus.AT_ORIGIN_BRANCH, ShipmentStatus.AT_DESTINATION_BRANCH);

    public static boolean allowsStatus(ShipmentStatus status) {
        return status != null && PUBLIC_STATUSES.contains(status);
    }

    // Only this event tuple is currently produced as a known user-facing milestone by Commit 06.
    // Matching a toStatus alone would expose internal notes that happen to have the same status.
    public static boolean allowsEvent(PublicTrackingEvent event) {
        return event != null && event.getFromStatus() == null && event.getToStatus() == ShipmentStatus.CREATED
            && CREATED_TITLE.equals(event.getTitle()) && event.getOccurredAt() != null;
    }
}
