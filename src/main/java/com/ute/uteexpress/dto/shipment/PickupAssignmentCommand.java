package com.ute.uteexpress.dto.shipment;

import jakarta.validation.constraints.*;

public record PickupAssignmentCommand(@NotNull @Positive Long shipmentId,
        @NotNull @PositiveOrZero Long expectedVersion, @NotNull @Positive Long employeeId,
        @Positive Long replacesAssignmentId, @Size(max = 255) String reason) { }
