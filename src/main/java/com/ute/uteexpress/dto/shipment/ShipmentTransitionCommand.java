package com.ute.uteexpress.dto.shipment;

import com.ute.uteexpress.enums.ShipmentStatus;
import jakarta.validation.constraints.*;

/** Internal application command; contains no actor, role, location or arbitrary event text from a client. */
public record ShipmentTransitionCommand(@NotNull @Positive Long shipmentId,
        @NotNull @PositiveOrZero Long expectedVersion, @NotNull ShipmentStatus targetStatus) { }
