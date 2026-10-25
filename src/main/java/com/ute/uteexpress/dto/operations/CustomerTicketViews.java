package com.ute.uteexpress.dto.operations;

import com.ute.uteexpress.enums.*;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;

/** No employee identities, assignment history, internal content, audit detail or money. */
public final class CustomerTicketViews {
    private CustomerTicketViews() { }
    public enum Filter { ALL, WORKING, WAITING_CUSTOMER, RESOLVED, CLOSED }
    public record Row(Long id, String code, String subject, String trackingNumber, TicketCategory category,
            TicketStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) { }
    public record ShipmentOption(Long id, String trackingNumber) { }
    public record Message(String body, LocalDateTime at, boolean customer, boolean resolution, boolean reopenRequest) { }
    public record Detail(Row ticket, Long version, String initialContent, Page<Message> messages, Message resolution,
            boolean canMessage, boolean canRequestReopen, boolean reopenPending) { }
}
