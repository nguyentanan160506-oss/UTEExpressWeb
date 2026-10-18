package com.ute.uteexpress.dto.shipment;

import com.ute.uteexpress.enums.PickupResult;
import jakarta.validation.constraints.*;

public record PickupCollectionCommand(@NotNull @Positive Long assignmentId,
        @NotNull @PositiveOrZero Long expectedVersion, @NotNull PickupResult result,
        boolean physicallyCollected, @Size(max = 100) String reference, @Size(max = 500) String notes) { }
