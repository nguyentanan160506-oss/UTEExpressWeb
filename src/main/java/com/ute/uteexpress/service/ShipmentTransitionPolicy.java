package com.ute.uteexpress.service;

import com.ute.uteexpress.enums.ShipmentStatus;
import java.util.Map;
import java.util.Set;
import static com.ute.uteexpress.enums.ShipmentStatus.*;

/** Graph of potential edges. Physical edges remain disabled until custody/trip evidence exists. */
public final class ShipmentTransitionPolicy {
    private ShipmentTransitionPolicy() { }
    private static final Map<ShipmentStatus, Set<ShipmentStatus>> EDGES = Map.ofEntries(
        Map.entry(CREATED, Set.of(CONFIRMED, CANCELLED)),
        Map.entry(CONFIRMED, Set.of(PICKUP_ASSIGNED, AT_ORIGIN_BRANCH, CANCELLED)),
        Map.entry(PICKUP_ASSIGNED, Set.of(PICKED_UP, CANCELLED)),
        Map.entry(PICKED_UP, Set.of(AT_ORIGIN_BRANCH)),
        Map.entry(AT_ORIGIN_BRANCH, Set.of(IN_TRANSIT)),
        Map.entry(AT_ORIGIN_HUB, Set.of(IN_TRANSIT)),
        Map.entry(IN_TRANSIT, Set.of(AT_ORIGIN_HUB, AT_DESTINATION_HUB, AT_DESTINATION_BRANCH)),
        Map.entry(AT_DESTINATION_HUB, Set.of(IN_TRANSIT)),
        Map.entry(AT_DESTINATION_BRANCH, Set.of(OUT_FOR_DELIVERY)),
        Map.entry(OUT_FOR_DELIVERY, Set.of(DELIVERED, DELIVERY_FAILED)),
        Map.entry(DELIVERY_FAILED, Set.of(OUT_FOR_DELIVERY, RETURNING)),
        Map.entry(RETURNING, Set.of(RETURNED)));

    public static boolean isPotentialEdge(ShipmentStatus from, ShipmentStatus to) {
        return from != null && to != null && EDGES.getOrDefault(from, Set.of()).contains(to);
    }
    public static boolean isEnabled(ShipmentStatus from, ShipmentStatus to) {
        return (from == CREATED && to == CONFIRMED)
            || ((from == CREATED || from == CONFIRMED) && to == CANCELLED);
    }
}
