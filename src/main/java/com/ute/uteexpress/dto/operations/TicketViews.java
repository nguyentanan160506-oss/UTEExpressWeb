package com.ute.uteexpress.dto.operations;

import com.ute.uteexpress.enums.*;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;

public final class TicketViews {
    private TicketViews() { }
    public record Row(Long id,String code,String subject,TicketStatus status,TicketPriority priority,TicketCategory category,
        Long branchId,String branchName,String assignedName,LocalDateTime createdAt,LocalDateTime warningDueAt,boolean overdue) { }
    public record Candidate(Long employeeId,Long userId,String name) { }
    public record Message(Long id,TicketMessageKind kind,String body,String author,LocalDateTime at) { }
    public record Event(Long id,TicketAction action,String actor,TicketStatus fromStatus,TicketStatus toStatus,String detail,LocalDateTime at) { }
    public record Detail(Row ticket,Long version,String content,String trackingNumber,ShipmentStatus shipmentStatus,TicketSource source,
        String creator,LocalDateTime resolvedAt,LocalDateTime closedAt,Page<Message> messages,Page<Event> events,Page<Candidate> candidates,boolean editable) { }
    /** Commit17 reads this dedicated DTO: no internal content, assignment, actor IDs or audit details. */
    public record CustomerDetail(String code,String subject,TicketStatus status,Page<PublicMessage> messages) { }
    public record PublicMessage(String body,LocalDateTime at) { }
}
