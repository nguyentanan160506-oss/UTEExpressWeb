package com.ute.uteexpress.service;

import com.ute.uteexpress.dto.shipper.PickupViews.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

@Service @Validated @Transactional(readOnly = true) @PreAuthorize("hasRole('SHIPPER')")
public class ShipperPickupService {
    private final LogisticsAccessService access;
    private final ShipmentAssignmentRepository assignments;
    private final PickupEvidenceRepository evidence;
    private final OriginHandoffRepository handoffs;
    public ShipperPickupService(LogisticsAccessService access, ShipmentAssignmentRepository assignments,
            PickupEvidenceRepository evidence, OriginHandoffRepository handoffs) {
        this.access = access; this.assignments = assignments; this.evidence = evidence; this.handoffs = handoffs;
    }
    public Dashboard dashboard() {
        Long employee = access.requireShipper().employee().getId();
        return new Dashboard(assignments.countByEmployeeIdAndAssignmentTypeAndStatus(employee, AssignmentType.PICKUP, AssignmentStatus.ASSIGNED),
            assignments.countByEmployeeIdAndAssignmentTypeAndStatus(employee, AssignmentType.PICKUP, AssignmentStatus.IN_PROGRESS),
            handoffs.countByEmployeeIdAndStatus(employee, HandoffStatus.PENDING));
    }
    public Page<Task> tasks(@Min(0) @Max(1000000) int page) {
        Long employee = access.requireShipper().employee().getId();
        return assignments.findByEmployeeIdAndAssignmentType(employee, AssignmentType.PICKUP,
            PageRequest.of(page, 20, Sort.by(Sort.Order.desc("assignedAt"), Sort.Order.desc("id")))).map(ShipperPickupService::task);
    }
    public Detail detail(@NotNull @Positive Long id, @Min(0) @Max(1000000) int evidencePage,
            @Min(0) @Max(1000000) int handoffPage) {
        var profile = access.requireShipper();
        ShipmentAssignment a = assignments.findByIdAndEmployeeIdAndAssignmentType(id, profile.employee().getId(), AssignmentType.PICKUP)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy nhiệm vụ."));
        Shipment s = a.getShipment();
        boolean eligible = a.getCompletedAt() == null && s.getOriginBranch().getStatus() == BranchStatus.ACTIVE
            && s.getOriginBranch().getId().equals(profile.employee().getBranch().getId());
        boolean hasProof = evidence.findByAssignmentIdAndResult(id, PickupResult.SUCCESS).isPresent();
        return new Detail(task(a), s.getVersion(),
            evidence.findByAssignmentId(id, PageRequest.of(evidencePage, 20, Sort.by(Sort.Order.desc("recordedAt"), Sort.Order.desc("id"))))
                .map(ShipperPickupService::proof),
            handoffs.findByAssignmentId(id, PageRequest.of(handoffPage, 20, Sort.by(Sort.Order.desc("requestedAt"), Sort.Order.desc("id"))))
                .map(ShipperPickupService::handoff),
            eligible && a.getStatus() == AssignmentStatus.ASSIGNED && !hasProof
                && ShipmentTransitionPolicy.allowsCollection(s.getStatus(), s.getShipmentMethod()),
            eligible && a.getStatus() == AssignmentStatus.IN_PROGRESS && hasProof
                && ShipmentTransitionPolicy.allowsOriginHandoff(s.getStatus(), s.getShipmentMethod())
                && !handoffs.existsByShipmentIdAndStatus(s.getId(), HandoffStatus.PENDING));
    }
    static Task task(ShipmentAssignment a) {
        Shipment s = a.getShipment();
        return new Task(a.getId(), s.getTrackingNumber(), s.getSenderName(), s.getSenderPhone(),
            String.join(", ", s.getSenderAddress(), s.getSenderWard(), s.getSenderDistrict(), s.getSenderProvince()),
            s.getOriginBranch().getName(), a.getStatus(), s.getStatus(), a.getAssignedAt());
    }
    static Evidence proof(PickupEvidence e) {
        return new Evidence(e.getResult(), e.getReference(), e.getNotes(), e.getEmployee().getFullName(), e.getRecordedAt());
    }
    static Handoff handoff(OriginHandoff h) {
        return new Handoff(h.getId(), h.getAssignment().getId(), h.getShipment().getTrackingNumber(), h.getBranch().getName(),
            h.getEmployee().getFullName(), h.getStatus(), h.getNotes(), h.getRequestedAt(),
            h.getReceiver() == null ? null : h.getReceiver().getFullName(), h.getReviewedAt(), h.getReason());
    }
}
