package com.ute.uteexpress.dto.shipment;

import jakarta.validation.constraints.*;

public record OriginHandoffCommand(@NotNull @Positive Long assignmentId,
        @NotNull @PositiveOrZero Long expectedVersion, @Size(max = 500) String notes) { }
