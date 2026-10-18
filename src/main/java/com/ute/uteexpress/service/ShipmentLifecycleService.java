package com.ute.uteexpress.service;

import com.ute.uteexpress.dto.shipment.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.*;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

@Service @Validated
public class ShipmentLifecycleService {
    private final ShipmentRepository shipments;
    private final ShipmentEventRepository events;
    private final ShipmentAssignmentRepository assignments;
    private final LogisticsAccessService access;
    private final Clock clock;
    private final ShipmentIntakeRepository intakes;
    private final PickupEvidenceRepository evidence;
    private final OriginHandoffRepository handoffs;
    public ShipmentLifecycleService(ShipmentRepository shipments, ShipmentEventRepository events,
            ShipmentAssignmentRepository assignments, LogisticsAccessService access, Clock clock, ShipmentIntakeRepository intakes,
            PickupEvidenceRepository evidence, OriginHandoffRepository handoffs) {
        this.shipments = shipments; this.events = events; this.assignments = assignments;
        this.access = access; this.clock = clock; this.intakes = intakes;
        this.evidence = evidence; this.handoffs = handoffs;
    }

    @Transactional
    @PreAuthorize("hasRole('MANAGER')")
    public Long transition(@NotNull @Valid ShipmentTransitionCommand command) {
        Shipment shipment = shipments.findForLifecycle(command.shipmentId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy vận đơn."));
        User actor = access.requireManagerForBranch(shipment.getOriginBranch().getId());
        requireVersion(shipment, command.expectedVersion());
        ShipmentStatus from = shipment.getStatus(), to = command.targetStatus();
        if (!ShipmentTransitionPolicy.isPotentialEdge(from, to)) {
            throw new LogisticsRuleException("Chuyển trạng thái không hợp lệ.");
        }
        if (!ShipmentTransitionPolicy.isEnabled(from, to)) {
            throw new LogisticsRuleException("Thao tác cần phân công, bàn giao hoặc chuyến được triển khai trong Commit 10–13.");
        }
        if (to == ShipmentStatus.CANCELLED
                && assignments.existsByShipmentIdAndStatusIn(shipment.getId(), LogisticsAccessService.ACTIVE_ASSIGNMENTS)) {
            throw new LogisticsRuleException("Cần xử lý phân công đang hoạt động trước khi hủy vận đơn.");
        }
        String reason = clean(command.reason());
        if (to == ShipmentStatus.CANCELLED && (reason == null || reason.length() > 500)) {
            throw new LogisticsRuleException("Vui lòng ghi lý do hủy/từ chối (tối đa 500 ký tự).");
        }
        record(shipment, actor, from, to, "Trạng thái: " + ShipmentStatusLabels.VALUES.get(to), reason, now());
        return shipment.getVersion();
    }

    @Transactional
    @PreAuthorize("hasRole('MANAGER')")
    public Long assignPickup(@NotNull @Valid PickupAssignmentCommand command) {
        Shipment shipment = locked(command.shipmentId());
        User actor = access.requireManagerForBranch(shipment.getOriginBranch().getId());
        requireVersion(shipment, command.expectedVersion());
        boolean replacing = command.replacesAssignmentId() != null;
        if (shipment.getOriginBranch().getBranchType() != BranchType.LOCAL_BRANCH
                || !ShipmentTransitionPolicy.allowsPickupAssignment(shipment.getStatus(), shipment.getShipmentMethod(), replacing)) {
            throw new LogisticsRuleException("Chỉ phân công HOME_PICKUP đã xác nhận; không phân công lại sau khi bắt đầu lấy hàng.");
        }
        var active = assignments.findByShipmentIdAndAssignmentTypeAndStatusIn(
            shipment.getId(), AssignmentType.PICKUP, LogisticsAccessService.ACTIVE_ASSIGNMENTS);
        String reason = clean(command.reason());
        if (reason != null && reason.length() > 255) { throw new LogisticsRuleException("Lý do phân công tối đa 255 ký tự."); }
        if ((!replacing && !active.isEmpty()) || (replacing && (active.size() != 1
                || !command.replacesAssignmentId().equals(active.getFirst().getId())
                || active.getFirst().getStatus() != AssignmentStatus.ASSIGNED
                || active.getFirst().getCompletedAt() != null || reason == null))) {
            throw new LogisticsRuleException("Phân công đã thay đổi hoặc đã bắt đầu. Phân công lại cần đúng nhiệm vụ đang chờ và lý do.");
        }
        Employee employee = access.requireAssignableShipper(command.employeeId(), shipment.getOriginBranch().getId());
        LocalDateTime now = now();
        if (replacing) {
            ShipmentAssignment previous = active.getFirst();
            if (previous.getEmployee().getId().equals(employee.getId())) {
                throw new LogisticsRuleException("Vui lòng chọn Shipper khác khi phân công lại.");
            }
            previous.setStatus(AssignmentStatus.CANCELLED); previous.setCompletedAt(now);
            assignments.saveAndFlush(previous); // Free the filtered unique key before INSERT of the replacement.
        }
        ShipmentAssignment assignment = new ShipmentAssignment(); assignment.setShipment(shipment);
        assignment.setEmployee(employee); assignment.setAssignedBy(actor); assignment.setAssignmentType(AssignmentType.PICKUP);
        assignment.setStatus(AssignmentStatus.ASSIGNED); assignment.setAssignedAt(now); assignment.setNotes(reason);
        assignments.saveAndFlush(assignment);
        String description = "Nhân viên #" + employee.getId()
            + (replacing ? "; thay phân công #" + command.replacesAssignmentId() : "")
            + (reason == null ? "" : "; " + reason);
        record(shipment, actor, shipment.getStatus(), ShipmentStatus.PICKUP_ASSIGNED,
            replacing ? "Đã phân công lại lấy hàng" : "Đã phân công lấy hàng", description, now);
        return shipment.getVersion();
    }

    @Transactional
    @PreAuthorize("hasRole('MANAGER')")
    public Long receiveDropoff(@NotNull @Valid BranchIntakeCommand command) {
        Shipment shipment = locked(command.shipmentId());
        User actor = access.requireManagerForBranch(shipment.getOriginBranch().getId());
        requireVersion(shipment, command.expectedVersion());
        if (shipment.getOriginBranch().getBranchType() != BranchType.LOCAL_BRANCH
                || !ShipmentTransitionPolicy.allowsDropoffIntake(shipment.getStatus(), shipment.getShipmentMethod())) {
            throw new LogisticsRuleException("Chỉ tiếp nhận trực tiếp BRANCH_DROPOFF đã xác nhận. HOME_PICKUP cần bằng chứng lấy hàng và bàn giao riêng.");
        }
        Employee receiver = access.requireIntakeEmployee(shipment.getOriginBranch().getId());
        String reference = clean(command.receiptReference());
        if (!Boolean.TRUE.equals(command.physicallyReceived()) || reference == null || reference.length() > 100
                || (command.notes() != null && command.notes().length() > 500)) {
            throw new LogisticsRuleException("Cần xác nhận đã thực nhận kiện và nhập mã biên nhận hợp lệ.");
        }
        if (intakes.existsByShipmentId(shipment.getId())) { throw new LogisticsRuleException("Vận đơn đã có biên nhận nguồn."); }
        if (assignments.existsByShipmentIdAndStatusIn(shipment.getId(), LogisticsAccessService.ACTIVE_ASSIGNMENTS)) {
            throw new LogisticsRuleException("Vận đơn còn phân công hoạt động không phù hợp để tiếp nhận trực tiếp.");
        }
        LocalDateTime now = now();
        ShipmentIntake receipt = new ShipmentIntake(); receipt.setShipment(shipment); receipt.setBranch(shipment.getOriginBranch());
        receipt.setReceivedBy(actor); receipt.setEmployee(receiver); receipt.setShipmentMethod(ShipmentMethod.BRANCH_DROPOFF);
        receipt.setReceiptReference(reference); receipt.setNotes(clean(command.notes())); receipt.setReceivedAt(now);
        intakes.saveAndFlush(receipt);
        shipment.setCurrentBranch(shipment.getOriginBranch());
        record(shipment, actor, shipment.getStatus(), ShipmentStatus.AT_ORIGIN_BRANCH,
            "Chi nhánh nguồn đã tiếp nhận kiện", "Biên nhận: " + reference, now);
        return shipment.getVersion();
    }

    @Transactional @PreAuthorize("hasRole('SHIPPER')")
    public Long collectPickup(@NotNull @Valid PickupCollectionCommand command) {
        Shipment s = lockOwnedPickup(command.assignmentId());
        var context = access.requireAssignedShipper(s.getId(), AssignmentType.PICKUP);
        ShipmentAssignment a = context.assignment();
        requireVersion(s, command.expectedVersion());
        if (!a.getId().equals(command.assignmentId()) || a.getStatus() != AssignmentStatus.ASSIGNED
                || !ShipmentTransitionPolicy.allowsCollection(s.getStatus(), s.getShipmentMethod())
                || evidence.findByAssignmentIdAndResult(a.getId(), PickupResult.SUCCESS).isPresent()
                || intakes.existsByShipmentId(s.getId())) {
            throw new LogisticsRuleException("Nhiệm vụ không còn cho phép xác nhận lấy hàng.");
        }
        String reference = clean(command.reference()), notes = clean(command.notes());
        if (command.result() == null || (reference != null && reference.length() > 100) || (notes != null && notes.length() > 500)
                || (command.result() == PickupResult.SUCCESS && (!command.physicallyCollected() || reference == null))
                || (command.result() == PickupResult.FAILED && (command.physicallyCollected() || notes == null))) {
            throw new LogisticsRuleException("Thành công cần xác nhận thực nhận và mã tham chiếu; thất bại cần lý do, không xác nhận đã lấy kiện.");
        }
        LocalDateTime time = now();
        PickupEvidence proof = new PickupEvidence(); proof.setShipment(s); proof.setAssignment(a);
        proof.setEmployee(context.employee()); proof.setActor(context.actor()); proof.setResult(command.result());
        proof.setReference(reference); proof.setNotes(notes); proof.setRecordedAt(time);
        evidence.saveAndFlush(proof);
        boolean success = command.result() == PickupResult.SUCCESS;
        if (success) { a.setStatus(AssignmentStatus.IN_PROGRESS); assignments.saveAndFlush(a); }
        record(s, context.actor(), s.getStatus(), success ? ShipmentStatus.PICKED_UP : s.getStatus(),
            success ? "Shipper đã thực nhận kiện" : "Lấy hàng chưa thành công",
            "Phân công #" + a.getId() + (success ? "; tham chiếu: " + reference : "; " + notes), time);
        return s.getVersion();
    }

    @Transactional @PreAuthorize("hasRole('SHIPPER')")
    public Long requestOriginHandoff(@NotNull @Valid OriginHandoffCommand command) {
        Shipment s = lockOwnedPickup(command.assignmentId());
        var context = access.requireAssignedShipper(s.getId(), AssignmentType.PICKUP);
        ShipmentAssignment a = context.assignment(); requireVersion(s, command.expectedVersion());
        PickupEvidence proof = successfulProof(s, a);
        if (!a.getId().equals(command.assignmentId()) || a.getStatus() != AssignmentStatus.IN_PROGRESS
                || !context.actor().getId().equals(proof.getActor().getId())
                || !ShipmentTransitionPolicy.allowsOriginHandoff(s.getStatus(), s.getShipmentMethod())
                || handoffs.existsByShipmentIdAndStatus(s.getId(), HandoffStatus.PENDING)
                || intakes.existsByShipmentId(s.getId())) {
            throw new LogisticsRuleException("Cần nhiệm vụ đã lấy kiện thành công và chưa có bàn giao đang chờ.");
        }
        String notes = clean(command.notes());
        if (notes != null && notes.length() > 500) { throw new LogisticsRuleException("Ghi chú tối đa 500 ký tự."); }
        LocalDateTime time = now(); OriginHandoff h = new OriginHandoff();
        h.setShipment(s); h.setAssignment(a); h.setEvidence(proof); h.setBranch(s.getOriginBranch());
        h.setEmployee(context.employee()); h.setRequestedBy(context.actor()); h.setStatus(HandoffStatus.PENDING);
        h.setNotes(notes); h.setRequestedAt(time); handoffs.saveAndFlush(h);
        record(s, context.actor(), s.getStatus(), s.getStatus(), "Yêu cầu bàn giao chi nhánh nguồn",
            "Phân công #" + a.getId(), time);
        return s.getVersion();
    }

    @Transactional @PreAuthorize("hasRole('MANAGER')")
    public Long reviewOriginHandoff(@NotNull @Valid HandoffReviewCommand command) {
        // Scalar lookup avoids caching a stale handoff/assignment before acquiring the parent lock.
        Long shipmentId = handoffs.findShipmentId(command.handoffId()).orElseThrow(this::missingHandoff);
        Shipment s = locked(shipmentId);
        User actor = access.requireManagerForBranch(s.getOriginBranch().getId());
        Employee receiver = access.requireIntakeEmployee(s.getOriginBranch().getId());
        requireVersion(s, command.expectedVersion());
        OriginHandoff h = handoffs.findById(command.handoffId()).orElseThrow(this::missingHandoff);
        ShipmentAssignment a = h.getAssignment(); PickupEvidence proof = successfulProof(s, a);
        if (h.getStatus() != HandoffStatus.PENDING || !s.getId().equals(h.getShipment().getId())
                || !s.getOriginBranch().getId().equals(h.getBranch().getId())
                || !proof.getId().equals(h.getEvidence().getId())
                || !a.getEmployee().getId().equals(h.getEmployee().getId())
                || !proof.getActor().getId().equals(h.getRequestedBy().getId())
                || a.getStatus() != AssignmentStatus.IN_PROGRESS || a.getCompletedAt() != null
                || !ShipmentTransitionPolicy.allowsOriginHandoff(s.getStatus(), s.getShipmentMethod())
                || intakes.existsByShipmentId(s.getId())) {
            throw new LogisticsRuleException("Bàn giao không còn hợp lệ hoặc vận đơn đã được tiếp nhận.");
        }
        if (actor.getId().equals(h.getRequestedBy().getId()) || receiver.getId().equals(h.getEmployee().getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        }
        var active = assignments.findByShipmentIdAndAssignmentTypeAndStatusIn(s.getId(), AssignmentType.PICKUP,
            LogisticsAccessService.ACTIVE_ASSIGNMENTS);
        if (active.size() != 1 || !active.getFirst().getId().equals(a.getId())) {
            throw new LogisticsRuleException("Phân công bàn giao đã thay đổi.");
        }
        String reference = clean(command.receiptReference()), reason = clean(command.reason());
        if ((reference != null && reference.length() > 100) || (reason != null && reason.length() > 500)
                || (command.accept() && (!command.physicallyReceived() || reference == null))
                || (!command.accept() && (reason == null || command.physicallyReceived()))) {
            throw new LogisticsRuleException("Nhận kiện cần xác nhận thực nhận và mã biên nhận; từ chối cần lý do.");
        }
        LocalDateTime time = now(); h.setReviewedBy(actor); h.setReceiver(receiver); h.setReviewedAt(time); h.setReason(reason);
        h.setStatus(command.accept() ? HandoffStatus.ACCEPTED : HandoffStatus.REJECTED);
        handoffs.saveAndFlush(h);
        if (command.accept()) {
            ShipmentIntake receipt = new ShipmentIntake(); receipt.setShipment(s); receipt.setBranch(s.getOriginBranch());
            receipt.setReceivedBy(actor); receipt.setEmployee(receiver); receipt.setPickupAssignment(a);
            receipt.setShipmentMethod(ShipmentMethod.HOME_PICKUP); receipt.setReceiptReference(reference);
            receipt.setNotes(reason); receipt.setReceivedAt(time); intakes.saveAndFlush(receipt);
            a.setStatus(AssignmentStatus.COMPLETED); a.setCompletedAt(time); assignments.saveAndFlush(a);
            s.setCurrentBranch(s.getOriginBranch());
        }
        record(s, actor, s.getStatus(), command.accept() ? ShipmentStatus.AT_ORIGIN_BRANCH : s.getStatus(),
            command.accept() ? "Chi nhánh nhận bàn giao HOME_PICKUP" : "Chi nhánh từ chối bàn giao",
            "Bàn giao #" + h.getId() + (command.accept() ? "; biên nhận: " + reference : "; " + reason), time);
        return s.getVersion();
    }

    private Shipment lockOwnedPickup(Long assignmentId) {
        var profile = access.requireShipper();
        Long id = assignments.findPickupShipmentIdForOwner(assignmentId, profile.employee().getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy nhiệm vụ."));
        return locked(id);
    }
    private PickupEvidence successfulProof(Shipment s, ShipmentAssignment a) {
        PickupEvidence proof = evidence.findByAssignmentIdAndResult(a.getId(), PickupResult.SUCCESS)
            .orElseThrow(() -> new LogisticsRuleException("Chưa có bằng chứng lấy kiện thành công."));
        if (!s.getId().equals(a.getShipment().getId()) || !s.getId().equals(proof.getShipment().getId())
                || !a.getId().equals(proof.getAssignment().getId()) || a.getAssignmentType() != AssignmentType.PICKUP
                || !a.getEmployee().getId().equals(proof.getEmployee().getId()) || proof.getResult() != PickupResult.SUCCESS
                || s.getOriginBranch().getBranchType() != BranchType.LOCAL_BRANCH) {
            throw new LogisticsRuleException("Bằng chứng không khớp nhiệm vụ và vận đơn.");
        }
        return proof;
    }
    private ResponseStatusException missingHandoff() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bàn giao."); }

    private Shipment locked(Long id) {
        return shipments.findForLifecycle(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy vận đơn."));
    }
    private void requireVersion(Shipment shipment, Long expected) {
        if (expected == null || !Objects.equals(shipment.getVersion(), expected)) {
            throw new ObjectOptimisticLockingFailureException(Shipment.class, shipment.getId());
        }
    }
    private void record(Shipment shipment, User actor, ShipmentStatus from, ShipmentStatus to,
            String title, String description, LocalDateTime now) {
        shipment.setStatus(to);
        // A reassignment has the same status; still dirty the parent so @Version increments.
        shipment.setUpdatedAt(shipment.getUpdatedAt() != null && !now.isAfter(shipment.getUpdatedAt())
            ? shipment.getUpdatedAt().plusNanos(100) : now);
        shipments.saveAndFlush(shipment);
        ShipmentEvent event = new ShipmentEvent(); event.setShipment(shipment); event.setActor(actor);
        event.setBranch(shipment.getOriginBranch()); event.setFromStatus(from); event.setToStatus(to);
        // Full evidence/rejection text is retained in its own audit row; the event is a bounded summary.
        event.setEventTitle(title); event.setDescription(description != null && description.length() > 500 ? description.substring(0, 500) : description);
        event.setOccurredAt(now);
        events.saveAndFlush(event);
    }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private LocalDateTime now() {
        LocalDateTime time = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return time.withNano(time.getNano() / 100 * 100);
    }
}
