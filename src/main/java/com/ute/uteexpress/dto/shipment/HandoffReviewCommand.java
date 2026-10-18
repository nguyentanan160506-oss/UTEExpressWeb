package com.ute.uteexpress.dto.shipment;

import jakarta.validation.constraints.*;

public record HandoffReviewCommand(@NotNull @Positive Long handoffId,
        @NotNull @PositiveOrZero Long expectedVersion, boolean accept, boolean physicallyReceived,
        @Size(max = 100) String receiptReference, @Size(max = 500) String reason) { }
