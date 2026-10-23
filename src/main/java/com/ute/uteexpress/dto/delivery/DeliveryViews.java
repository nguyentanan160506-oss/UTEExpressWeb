package com.ute.uteexpress.dto.delivery;

import com.ute.uteexpress.enums.*;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;

public final class DeliveryViews {
    private DeliveryViews() { }
    public record Row(Long id, String trackingNumber, ShipmentStatus status, String origin, String destination,
            String currentPoint, int attempts, int maxAttempts) { }
    public record Task(Long id, Long shipmentId, String trackingNumber, AssignmentStatus status, String employee, LocalDateTime assignedAt, LocalDateTime completedAt) { }
    public record Contact(String name, String phone, String address) { }
    public record Dispatch(DeliveryDispatchStatus status, String preparedBy, String reference, LocalDateTime preparedAt,
            String acceptedBy, LocalDateTime acceptedAt, LocalDateTime startedAt) { }
    public record Attempt(int number, DeliveryAttemptStatus status, DeliveryFailureCode code, String reason, String notes, LocalDateTime startedAt, LocalDateTime attemptedAt) { }
    public record Proof(DeliveryRecipientType type, String recipient, String reference, String notes, LocalDateTime deliveredAt) { }
    public record Recovery(Long id, DeliveryRecoveryStatus status, String shipper, String notes, LocalDateTime requestedAt,
            String reviewer, String reference, String reason, LocalDateTime reviewedAt, boolean canReview) { }
    public record Decision(RedeliveryMode mode, int afterAttempt, String actor, String reason, LocalDateTime decidedAt) { }
    public record ReturnInfo(ReturnStage stage, String reason, LocalDateTime requestedAt, String reference, String recipient, LocalDateTime receivedAt) { }
    public record Candidate(Long id, String name, String code) { }
    public record Event(String title, String description, LocalDateTime occurredAt) { }
    public record ManagerDetail(Row shipment, Long version, Contact recipient, Page<Task> tasks, Long activeAssignmentId,
            Long activeEmployeeId, Dispatch dispatch, Page<Candidate> candidates, Page<Attempt> attempts, Page<Recovery> recoveries,
            Page<Decision> decisions, Page<Event> events, Proof proof, ReturnInfo returnInfo,
            boolean canAssign, boolean canReassign, boolean canPrepare, boolean canRedeliver, boolean canReturn, boolean canReturnToSender) { }
    public record ShipperDetail(Task task, Long version, Contact recipient, Dispatch dispatch, Page<Attempt> attempts,
            Page<Recovery> recoveries, boolean canAccept, boolean canStart, boolean canResult, boolean canRecover) { }
}
