package com.ute.uteexpress.dto.shipment;

import com.ute.uteexpress.enums.ShipmentStatus;
import jakarta.validation.constraints.*;

/** Actor, role, location and event title are server-owned; reason is the operator's bounded note. */
public record ShipmentTransitionCommand(@NotNull @Positive Long shipmentId,
        @NotNull @PositiveOrZero Long expectedVersion, @NotNull ShipmentStatus targetStatus,
        @Size(max = 500) String reason) {
    public ShipmentTransitionCommand(Long shipmentId, Long expectedVersion, ShipmentStatus targetStatus) {
        this(shipmentId, expectedVersion, targetStatus, null);
    }
}
