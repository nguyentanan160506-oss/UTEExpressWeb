package com.ute.uteexpress.dto.operations;

import com.ute.uteexpress.enums.*;
import jakarta.validation.constraints.*;

public final class TicketCommands {
    private TicketCommands() { }
    public record Create(@NotNull @Positive Long branchId,@NotBlank @Pattern(regexp="[A-Za-z0-9-]{10,50}") String trackingNumber,
        @NotBlank @Size(max=200) String subject,@NotBlank @Size(max=2000) String content,@NotNull TicketCategory category,
        @NotNull TicketPriority priority,@NotNull TicketSource source,@NotBlank @Pattern(regexp="[a-f0-9]{32}") String token) { }
    public record Assign(@NotNull @PositiveOrZero Long version,@NotNull @Positive Long employeeId,@Size(max=500) String reason) { }
    public record ChangeStatus(@NotNull @PositiveOrZero Long version,@NotNull TicketStatus status,@Size(max=500) String reason) { }
    public record Message(@NotNull @PositiveOrZero Long version,@NotBlank @Size(max=2000) String body) { }
    public record Decision(@NotNull @PositiveOrZero Long version,@NotBlank @Size(max=2000) String reason) { }
    public record Classify(@NotNull @PositiveOrZero Long version,@NotNull TicketCategory category,@NotNull TicketPriority priority,@NotBlank @Size(max=500) String reason) { }
}
