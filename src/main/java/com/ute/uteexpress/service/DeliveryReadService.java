package com.ute.uteexpress.service;

import com.ute.uteexpress.dto.delivery.DeliveryViews.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

@Service @Validated @Transactional(readOnly = true)
public class DeliveryReadService {
    private final LogisticsAccessService access;
    private final DeliveryDispatchRepository dispatches;
    private final ShipmentAssignmentRepository assignments;
    private final EmployeeRepository employees;
    private final DeliveryAttemptRepository attempts;
    private final DeliveryRecoveryRepository recoveries;
    private final RedeliveryDecisionRepository decisions;
    private final ReturnRequestRepository returns;
    private final ReturnReceiptRepository returnReceipts;
    private final DeliveryProofRepository proofs;
    private final ShipmentEventRepository events;
    private final DeliveryCustodyService custody;
    public DeliveryReadService(LogisticsAccessService access, DeliveryDispatchRepository dispatches, ShipmentAssignmentRepository assignments,
            EmployeeRepository employees, DeliveryAttemptRepository attempts, DeliveryRecoveryRepository recoveries, RedeliveryDecisionRepository decisions,
            ReturnRequestRepository returns, ReturnReceiptRepository returnReceipts, DeliveryProofRepository proofs, ShipmentEventRepository events, DeliveryCustodyService custody) {
        this.access = access; this.dispatches = dispatches; this.assignments = assignments; this.employees = employees; this.attempts = attempts;
        this.recoveries = recoveries; this.decisions = decisions; this.returns = returns; this.returnReceipts = returnReceipts;
        this.proofs = proofs; this.events = events; this.custody = custody;
    }
    @PreAuthorize("hasRole('MANAGER')")
    public Page<Row> managerList(ShipmentStatus status, @Size(max = 50) String tracking, @Min(0) @Max(1000000) int page) {
        return dispatches.findManaged(access.requireManager().getId(), status, tracking == null || tracking.isBlank() ? null : tracking.strip().toUpperCase(java.util.Locale.ROOT), page(page, "createdAt")).map(this::row);
    }
    @PreAuthorize("hasRole('MANAGER')")
    public ManagerDetail managerDetail(@NotNull @Positive Long id, @Min(0) @Max(1000000) int page) {
        User actor = access.requireManager(); Shipment s = dispatches.findManagedShipment(id, actor.getId()).orElseThrow(this::missing);
        boolean atDest = manages(s.getDestinationBranch().getId()); Employee staff = atDest ? staff(s.getDestinationBranch().getId()) : null;
        var active = assignments.findByShipmentIdAndAssignmentTypeAndStatusIn(id, AssignmentType.DELIVERY, LogisticsAccessService.ACTIVE_ASSIGNMENTS);
        ShipmentAssignment current = active.size() == 1 ? active.getFirst() : null;
        DeliveryDispatch d = current == null ? dispatches.findFirstByShipmentIdAndAcceptedAtIsNotNullOrderByAcceptedAtDescIdDesc(id).orElse(null)
            : dispatches.findByAssignmentId(current.getId()).orElse(null);
        boolean branch = false;
        if (atDest) { try { custody.requireAtDestination(s); branch = true; } catch (LogisticsRuleException unavailable) { branch = false; } }
        ReturnRequest ret = returns.findByShipmentId(id).orElse(null); boolean available = ret == null && s.getDeliveryAttemptCount() < s.getMaxDeliveryAttempts();
        boolean waiting = s.getStatus() == ShipmentStatus.AT_DESTINATION_BRANCH || s.getStatus() == ShipmentStatus.DELIVERY_FAILED;
        boolean canAssign = atDest && branch && available && s.getStatus() == ShipmentStatus.AT_DESTINATION_BRANCH && active.isEmpty() && s.getDeliveryAttemptCount() == 0;
        boolean reassign = atDest && branch && waiting && available && current != null && current.getStatus() == AssignmentStatus.ASSIGNED && (d == null || d.getStatus() == DeliveryDispatchStatus.PREPARED);
        boolean prepare = atDest && staff != null && branch && waiting && available && current != null && current.getStatus() == AssignmentStatus.ASSIGNED && d == null;
        boolean retry = atDest && available && s.getStatus() == ShipmentStatus.DELIVERY_FAILED
            && ((branch && active.isEmpty()) || (s.getCurrentBranch() == null && current != null && d != null && d.getStatus() == DeliveryDispatchStatus.FAILED));
        Page<Candidate> candidates = canAssign || reassign || (retry && branch)
            ? employees.findPickupCandidates(s.getDestinationBranch().getId(), page(page, "id")).map(e -> new Candidate(e.getId(), e.getFullName(), e.getEmployeeCode())) : Page.empty();
        DeliveryProof p = proofs.findByShipmentId(id).orElse(null);
        ReturnReceipt rr = ret == null ? null : returnReceipts.findByReturnRequestId(ret.getId()).orElse(null);
        boolean returnable = atDest && ret == null && p == null && java.util.Set.of(ShipmentStatus.AT_DESTINATION_BRANCH, ShipmentStatus.OUT_FOR_DELIVERY, ShipmentStatus.DELIVERY_FAILED).contains(s.getStatus());
        boolean sender = ret != null && ret.getStage() == ReturnStage.AT_ORIGIN && s.getStatus() == ShipmentStatus.RETURNING
            && manages(s.getOriginBranch().getId()) && staff(s.getOriginBranch().getId()) != null && !actor.getId().equals(s.getSender().getId());
        return new ManagerDetail(row(s), s.getVersion(), contact(s), assignments.findByShipmentId(id, page(page, "assignedAt")).map(this::task),
            current == null ? null : current.getId(), current == null ? null : current.getEmployee().getId(), dispatch(d), candidates,
            attempts.findByShipmentId(id, page(page, "attemptTime")).map(this::attempt),
            recoveries.findByShipmentId(id, page(page, "requestedAt")).map(r -> recovery(r, actor, staff)),
            decisions.findByShipmentId(id, page(page, "decidedAt")).map(r -> new Decision(r.getMode(), r.getAfterAttemptNumber(), name(r.getActor()), r.getReason(), r.getDecidedAt())),
            events.findByShipmentId(id, page(page, "occurredAt")).map(e -> new Event(e.getEventTitle(), e.getDescription(), e.getOccurredAt())),
            p == null ? null : new Proof(p.getRecipientType(), p.getRecipientName(), p.getReference(), p.getNotes(), p.getDeliveredAt()),
            ret == null ? null : new ReturnInfo(ret.getStage(), ret.getReason(), ret.getRequestedAt(), rr == null ? null : rr.getReference(), rr == null ? null : rr.getRecipientName(), rr == null ? null : rr.getReceivedAt()),
            canAssign, reassign, prepare, retry, returnable, sender);
    }
    @PreAuthorize("hasRole('SHIPPER')")
    public Page<Task> shipperList(@Min(0) @Max(1000000) int page) {
        return assignments.findByEmployeeIdAndAssignmentType(access.requireShipper().employee().getId(), AssignmentType.DELIVERY, page(page, "assignedAt")).map(this::task);
    }
    @PreAuthorize("hasRole('SHIPPER')")
    public ShipperDetail shipperDetail(@NotNull @Positive Long id, @Min(0) @Max(1000000) int page) {
        var profile = access.requireShipper();
        ShipmentAssignment a = assignments.findByIdAndEmployeeIdAndAssignmentType(id, profile.employee().getId(), AssignmentType.DELIVERY).orElseThrow(this::missing);
        Shipment s = a.getShipment(); DeliveryDispatch d = dispatches.findByAssignmentId(id).orElse(null);
        boolean valid = LogisticsAccessService.ACTIVE_ASSIGNMENTS.contains(a.getStatus()) && a.getCompletedAt() == null
            && TransportRouting.same(profile.employee().getBranch(), s.getDestinationBranch()) && s.getDestinationBranch().getStatus() == BranchStatus.ACTIVE;
        boolean returning = returns.existsByShipmentId(s.getId());
        boolean accept = valid && !returning && a.getStatus() == AssignmentStatus.ASSIGNED && d != null && d.getStatus() == DeliveryDispatchStatus.PREPARED;
        boolean start = valid && !returning && d != null && d.getStatus() == DeliveryDispatchStatus.ACCEPTED;
        boolean result = valid && !returning && d != null && d.getStatus() == DeliveryDispatchStatus.STARTED && s.getStatus() == ShipmentStatus.OUT_FOR_DELIVERY;
        boolean recover = valid && d != null && (d.getStatus() == DeliveryDispatchStatus.FAILED || d.getStatus() == DeliveryDispatchStatus.RECALL_REQUIRED);
        return new ShipperDetail(task(a), s.getVersion(), valid ? contact(s) : null, dispatch(d),
            attempts.findByAssignmentId(id, page(page, "attemptTime")).map(this::attempt),
            recoveries.findByAssignmentId(id, page(page, "requestedAt")).map(r -> recovery(r, null, null)), accept, start, result, recover);
    }
    private Row row(Shipment s) { return new Row(s.getId(), s.getTrackingNumber(), s.getStatus(), s.getOriginBranch().getName(), s.getDestinationBranch().getName(),
        s.getStatus() == ShipmentStatus.DELIVERED ? "Đã bàn giao người nhận" : s.getStatus() == ShipmentStatus.RETURNED ? "Đã trả người gửi/đại diện"
            : s.getCurrentBranch() == null ? "Đang do Shipper/chuyến giữ theo bàn giao" : s.getCurrentBranch().getName(), s.getDeliveryAttemptCount(), s.getMaxDeliveryAttempts()); }
    private Task task(ShipmentAssignment a) { return new Task(a.getId(), a.getShipment().getId(), a.getShipment().getTrackingNumber(), a.getStatus(), a.getEmployee().getFullName(), a.getAssignedAt(), a.getCompletedAt()); }
    private Contact contact(Shipment s) { return new Contact(s.getReceiverName(), s.getReceiverPhone(), String.join(", ", s.getReceiverAddress(), s.getReceiverWard(), s.getReceiverDistrict(), s.getReceiverProvince())); }
    private Dispatch dispatch(DeliveryDispatch d) { return d == null ? null : new Dispatch(d.getStatus(), name(d.getPreparedBy()), d.getReference(), d.getPreparedAt(), name(d.getAcceptedBy()), d.getAcceptedAt(), d.getStartedAt()); }
    private Attempt attempt(DeliveryAttempt a) { return new Attempt(a.getAttemptNumber(), a.getStatus(), a.getFailureCode(), a.getFailureReason(), a.getNotes(), a.getStartedAt(), a.getAttemptTime()); }
    private Recovery recovery(DeliveryRecovery r, User actor, Employee employee) {
        boolean canReview = actor != null && employee != null && r.getStatus() == DeliveryRecoveryStatus.PENDING
            && !actor.getId().equals(r.getRequestedBy().getId()) && !employee.getId().equals(r.getEmployee().getId());
        return new Recovery(r.getId(), r.getStatus(), r.getEmployee().getFullName(), r.getNotes(), r.getRequestedAt(), name(r.getReviewedBy()), r.getReference(), r.getReason(), r.getReviewedAt(), canReview);
    }
    private boolean manages(Long branch) { try { access.requireManagerForBranch(branch); return true; } catch (AccessDeniedException denied) { return false; } }
    private Employee staff(Long branch) { try { return access.requireIntakeEmployee(branch); } catch (AccessDeniedException denied) { return null; } }
    private PageRequest page(int page, String time) { return PageRequest.of(page, 20, time.equals("id") ? Sort.by(Sort.Order.desc("id")) : Sort.by(Sort.Order.desc(time), Sort.Order.desc("id"))); }
    private String name(User u) { return u == null ? null : (u.getFullName() == null ? u.getUsername() : u.getFullName()); }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy kiện/nhiệm vụ trong phạm vi."); }
}
