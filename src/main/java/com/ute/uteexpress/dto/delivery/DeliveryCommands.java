package com.ute.uteexpress.dto.delivery;

import com.ute.uteexpress.enums.*;
import jakarta.validation.constraints.*;

public final class DeliveryCommands {
    private DeliveryCommands() { }
    public record Assign(@NotNull @Positive Long shipmentId, @NotNull @PositiveOrZero Long expectedVersion,
            @NotNull @Positive Long employeeId, @Positive Long replacesAssignmentId, @Size(max = 255) String reason) { }
    public record Prepare(@NotNull @Positive Long shipmentId, @NotNull @PositiveOrZero Long expectedVersion,
            @NotNull @Positive Long assignmentId, boolean confirmed, @NotBlank @Size(max = 100) String reference, @Size(max = 500) String notes) { }
    public record Action(@NotNull @Positive Long assignmentId, @NotNull @PositiveOrZero Long expectedVersion,
            boolean confirmed, @NotBlank @Size(max = 100) String reference) { }
    public record Complete(@NotNull @Positive Long assignmentId, @NotNull @PositiveOrZero Long expectedVersion,
            boolean confirmed, @NotNull DeliveryRecipientType recipientType, @NotBlank @Size(max = 100) String recipientName,
            @NotBlank @Size(max = 100) String reference, @Size(max = 500) String notes) { }
    public record Fail(@NotNull @Positive Long assignmentId, @NotNull @PositiveOrZero Long expectedVersion,
            boolean confirmed, @NotNull DeliveryFailureCode code, @NotBlank @Size(max = 255) String reason, @Size(max = 500) String notes) { }
    public record Recover(@NotNull @Positive Long assignmentId, @NotNull @PositiveOrZero Long expectedVersion, @Size(max = 500) String notes) { }
    public record ReviewRecovery(@NotNull @Positive Long shipmentId, @NotNull @PositiveOrZero Long expectedVersion,
            @NotNull @Positive Long recoveryId, boolean accept, boolean confirmed, @Size(max = 100) String reference, @Size(max = 500) String reason) { }
    public record Redelivery(@NotNull @Positive Long shipmentId, @NotNull @PositiveOrZero Long expectedVersion,
            @Positive Long employeeId, @NotBlank @Size(max = 500) String reason) { }
    public record ReturnStart(@NotNull @Positive Long shipmentId, @NotNull @PositiveOrZero Long expectedVersion, @NotBlank @Size(max = 500) String reason) { }
    public record SenderReceipt(@NotNull @Positive Long shipmentId, @NotNull @PositiveOrZero Long expectedVersion,
            boolean confirmed, @NotNull ReturnRecipientType recipientType, @NotBlank @Size(max = 100) String recipientName,
            @NotBlank @Size(max = 100) String reference, @Size(max = 500) String notes) { }
}
