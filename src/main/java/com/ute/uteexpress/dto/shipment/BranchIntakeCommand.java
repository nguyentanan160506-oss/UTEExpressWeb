package com.ute.uteexpress.dto.shipment;

import jakarta.validation.constraints.*;

public record BranchIntakeCommand(@NotNull @Positive Long shipmentId,
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotBlank @Size(max = 100) String receiptReference,
        @NotNull @AssertTrue Boolean physicallyReceived, @Size(max = 500) String notes) { }
