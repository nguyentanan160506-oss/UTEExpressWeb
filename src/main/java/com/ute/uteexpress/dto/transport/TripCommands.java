package com.ute.uteexpress.dto.transport;

import com.ute.uteexpress.enums.TripDiscrepancyKind;
import jakarta.validation.constraints.*;
import java.util.List;

public final class TripCommands {
    private TripCommands() { }
    public record Create(@NotNull @Positive Long routeId, @NotBlank @Pattern(regexp = "[a-f0-9]{32}") String requestToken) { }
    public record Change(@NotNull @Positive Long tripId, @NotNull @PositiveOrZero Long expectedVersion,
            boolean confirmed, @NotBlank @Size(max = 100) String reference, @Size(max = 500) String notes) { }
    public record Manifest(@NotNull @Positive Long tripId, @NotNull @PositiveOrZero Long expectedVersion,
            @NotEmpty @Size(max = 50) List<@NotNull @Positive Long> shipmentIds) { }
    public record Receive(@NotNull @Positive Long tripId, @NotNull @PositiveOrZero Long expectedVersion,
            @NotNull @Positive Long itemId, boolean physicallyReceived, @NotBlank @Size(max = 100) String reference,
            @Size(max = 500) String notes, boolean resolveDiscrepancy) { }
    public record Discrepancy(@NotNull @Positive Long tripId, @NotNull @PositiveOrZero Long expectedVersion,
            @Positive Long itemId, @NotNull TripDiscrepancyKind kind, @NotBlank @Size(max = 100) String reference,
            @NotBlank @Size(max = 500) String notes) { }
    public record Resolve(@NotNull @Positive Long tripId, @NotNull @PositiveOrZero Long expectedVersion,
            @NotNull @Positive Long discrepancyId, boolean confirmed, @NotBlank @Size(max = 100) String reference,
            @NotBlank @Size(max = 500) String notes) { }
    public record LocalReady(@NotNull @Positive Long shipmentId, @NotNull @PositiveOrZero Long expectedVersion,
            boolean physicallyPresent, @NotBlank @Size(max = 100) String reference, @Size(max = 500) String notes) { }
}
