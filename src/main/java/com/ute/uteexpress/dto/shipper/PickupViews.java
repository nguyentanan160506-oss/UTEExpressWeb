package com.ute.uteexpress.dto.shipper;

import com.ute.uteexpress.enums.*;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;

public final class PickupViews {
    private PickupViews() { }
    public record Dashboard(long assigned, long collected, long pendingHandoffs) { }
    public record Task(Long id, String trackingNumber, String senderName, String phone, String address,
            String originBranch, AssignmentStatus status, ShipmentStatus shipmentStatus, LocalDateTime assignedAt) { }
    public record Evidence(PickupResult result, String reference, String notes, String employee,
            LocalDateTime recordedAt) { }
    public record Handoff(Long id, Long assignmentId, String trackingNumber, String branch, String shipper,
            HandoffStatus status, String notes, LocalDateTime requestedAt, String reviewer,
            LocalDateTime reviewedAt, String reason) { }
    public record Detail(Task task, Long version, Page<Evidence> evidence, Page<Handoff> handoffs,
            boolean canCollect, boolean canHandoff) { }
    public record Review(Handoff handoff, Evidence evidence, Long shipmentId, Long version, boolean canReview,
            String receiptReference) { }
}
