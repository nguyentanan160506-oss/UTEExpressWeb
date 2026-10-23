package com.ute.uteexpress.service;

import com.ute.uteexpress.dto.delivery.DeliveryCommands.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

/** Central last-mile/return lifecycle; every writer first locks the Shipment parent. */
@Service @Validated
public class LastMileService {
    private final ShipmentRepository shipments;
    private final ShipmentAssignmentRepository assignments;
    private final ShipmentEventRepository events;
    private final DeliveryAttemptRepository attempts;
    private final DeliveryDispatchRepository dispatches;
    private final DeliveryProofRepository proofs;
    private final DeliveryRecoveryRepository recoveries;
    private final RedeliveryDecisionRepository decisions;
    private final ReturnRequestRepository returns;
    private final ReturnReceiptRepository returnReceipts;
    private final TransportTripItemRepository tripItems;
    private final TripReceiptRepository tripReceipts;
    private final LogisticsAccessService access;
    private final DeliveryCustodyService custody;
    private final Clock clock;
    public LastMileService(ShipmentRepository shipments, ShipmentAssignmentRepository assignments, ShipmentEventRepository events,
            DeliveryAttemptRepository attempts, DeliveryDispatchRepository dispatches, DeliveryProofRepository proofs,
            DeliveryRecoveryRepository recoveries, RedeliveryDecisionRepository decisions, ReturnRequestRepository returns,
            ReturnReceiptRepository returnReceipts, TransportTripItemRepository tripItems, TripReceiptRepository tripReceipts,
            LogisticsAccessService access, DeliveryCustodyService custody, Clock clock) {
        this.shipments = shipments; this.assignments = assignments; this.events = events; this.attempts = attempts;
        this.dispatches = dispatches; this.proofs = proofs; this.recoveries = recoveries; this.decisions = decisions;
        this.returns = returns; this.returnReceipts = returnReceipts; this.tripItems = tripItems; this.tripReceipts = tripReceipts;
        this.access = access; this.custody = custody; this.clock = clock;
    }
    @Transactional @PreAuthorize("hasRole('MANAGER')")
    public void assign(@NotNull @Valid Assign c) {
        Shipment s = shipment(c.shipmentId()); User actor = manager(s, c.expectedVersion()); noReturn(s); atDestinationStage(s); custody.requireAtDestination(s); capacity(s);
        var active = active(s); boolean replacing = c.replacesAssignmentId() != null; String reason = text(c.reason(), 255, replacing);
        if ((!replacing && (!active.isEmpty() || s.getStatus() != ShipmentStatus.AT_DESTINATION_BRANCH || s.getDeliveryAttemptCount() != 0))
                || (replacing && (active.size() != 1 || !active.getFirst().getId().equals(c.replacesAssignmentId())
                || active.getFirst().getStatus() != AssignmentStatus.ASSIGNED || active.getFirst().getCompletedAt() != null))) { throw rule("Phân công hiện tại đã đổi hoặc đã bắt đầu; giao lại cần quyết định riêng."); }
        Employee employee = access.requireAssignableShipper(c.employeeId(), s.getDestinationBranch().getId());
        if (replacing) {
            ShipmentAssignment old = active.getFirst();
            if (old.getEmployee().getId().equals(employee.getId())) { throw rule("Chọn Shipper khác để phân công lại."); }
            cancelPrepared(old); closeAssignment(old, AssignmentStatus.CANCELLED);
        }
        newAssignment(s, employee, actor, reason);
        record(s, actor, s.getStatus(), s.getDestinationBranch(), replacing ? "Phân công lại giao hàng" : "Phân công giao hàng", reason);
    }
    @Transactional @PreAuthorize("hasRole('MANAGER')")
    public void prepare(@NotNull @Valid Prepare c) {
        Shipment s = shipment(c.shipmentId()); User actor = manager(s, c.expectedVersion()); Employee staff = access.requireIntakeEmployee(s.getDestinationBranch().getId());
        noReturn(s); atDestinationStage(s); custody.requireAtDestination(s); capacity(s);
        ShipmentAssignment a = sole(s, c.assignmentId());
        if (a.getStatus() != AssignmentStatus.ASSIGNED || dispatches.findByAssignmentId(a.getId()).isPresent() || !c.confirmed()) { throw rule("Cần nhiệm vụ chưa bắt đầu và xác nhận chuẩn bị bàn giao thực tế."); }
        access.requireAssignableShipper(a.getEmployee().getId(), s.getDestinationBranch().getId());
        if (actor.getId().equals(a.getEmployee().getUser().getId()) || staff.getId().equals(a.getEmployee().getId())) { throw denied(); }
        DeliveryDispatch d = new DeliveryDispatch(); d.setShipment(s); d.setAssignment(a); d.setBranch(s.getDestinationBranch());
        d.setPreparedBy(actor); d.setPreparedEmployee(staff); d.setReference(text(c.reference(), 100, true)); d.setNotes(text(c.notes(), 500, false)); d.setPreparedAt(now());
        dispatches.saveAndFlush(d); record(s, actor, s.getStatus(), s.getDestinationBranch(), "Chi nhánh chuẩn bị bàn giao giao hàng", d.getReference());
    }
    @Transactional @PreAuthorize("hasRole('SHIPPER')")
    public void accept(@NotNull @Valid Action c) {
        var ctx = owned(c.assignmentId(), c.expectedVersion()); Shipment s = ctx.assignment().getShipment(); ShipmentAssignment a = ctx.assignment();
        noReturn(s); atDestinationStage(s); custody.requireAtDestination(s); capacity(s); DeliveryDispatch d = dispatch(s, a);
        if (a.getStatus() != AssignmentStatus.ASSIGNED || d.getStatus() != DeliveryDispatchStatus.PREPARED || !c.confirmed()) { throw rule("Nhiệm vụ chưa có bàn giao hợp lệ hoặc đã được nhận."); }
        if (ctx.actor().getId().equals(d.getPreparedBy().getId()) || ctx.employee().getId().equals(d.getPreparedEmployee().getId())) { throw denied(); }
        d.setAcceptedBy(ctx.actor()); d.setAcceptedEmployee(ctx.employee()); d.setAcceptedAt(now()); d.setStatus(DeliveryDispatchStatus.ACCEPTED);
        dispatches.saveAndFlush(d); a.setStatus(AssignmentStatus.IN_PROGRESS); assignments.saveAndFlush(a); s.setCurrentBranch(null);
        record(s, ctx.actor(), s.getStatus(), s.getDestinationBranch(), "Shipper đã thực nhận kiện để giao", text(c.reference(), 100, true));
    }
    @Transactional @PreAuthorize("hasRole('SHIPPER')")
    public void start(@NotNull @Valid Action c) {
        var ctx = owned(c.assignmentId(), c.expectedVersion()); Shipment s = ctx.assignment().getShipment(); noReturn(s); capacity(s);
        DeliveryDispatch d = held(s, ctx);
        if (!c.confirmed() || d.getStatus() != DeliveryDispatchStatus.ACCEPTED
                || (s.getStatus() != ShipmentStatus.AT_DESTINATION_BRANCH && s.getStatus() != ShipmentStatus.DELIVERY_FAILED)) { throw rule("Cần thực nhận kiện và được phép giao/giao lại trước khi bắt đầu."); }
        d.setStatus(DeliveryDispatchStatus.STARTED); d.setStartedAt(now()); dispatches.saveAndFlush(d);
        record(s, ctx.actor(), ShipmentStatus.OUT_FOR_DELIVERY, s.getDestinationBranch(), "Shipper bắt đầu lần giao", text(c.reference(), 100, true));
    }
    @Transactional @PreAuthorize("hasRole('SHIPPER')")
    public void complete(@NotNull @Valid Complete c) {
        var ctx = owned(c.assignmentId(), c.expectedVersion()); Shipment s = ctx.assignment().getShipment(); noReturn(s); DeliveryDispatch d = running(s, ctx);
        if (!c.confirmed() || c.recipientType() == null || proofs.existsByShipmentId(s.getId())) { throw rule("Cần xác nhận người nhận thực sự đã nhận kiện, không tạo kết quả trùng."); }
        String name = text(c.recipientName(), 100, true), reference = text(c.reference(), 100, true), notes = text(c.notes(), 500, false);
        DeliveryAttempt attempt = result(s, ctx, d, DeliveryAttemptStatus.SUCCESS, null, null, notes);
        DeliveryProof p = new DeliveryProof(); p.setShipment(s); p.setAssignment(ctx.assignment()); p.setAttempt(attempt);
        p.setActor(ctx.actor()); p.setEmployee(ctx.employee()); p.setRecipientType(c.recipientType()); p.setRecipientName(name);
        p.setReference(reference); p.setNotes(notes); p.setDeliveredAt(now()); proofs.saveAndFlush(p);
        d.setStatus(DeliveryDispatchStatus.COMPLETED); d.setFinishedAt(now()); dispatches.saveAndFlush(d);
        closeAssignment(ctx.assignment(), AssignmentStatus.COMPLETED); s.setDeliveredAt(now());
        record(s, ctx.actor(), ShipmentStatus.DELIVERED, s.getDestinationBranch(), "Đã giao kiện cho người nhận", reference);
    }
    @Transactional @PreAuthorize("hasRole('SHIPPER')")
    public void fail(@NotNull @Valid Fail c) {
        var ctx = owned(c.assignmentId(), c.expectedVersion()); Shipment s = ctx.assignment().getShipment(); noReturn(s); DeliveryDispatch d = running(s, ctx);
        if (!c.confirmed() || c.code() == null) { throw rule("Cần xác nhận lần thử giao thực tế và lý do thất bại."); }
        result(s, ctx, d, DeliveryAttemptStatus.FAILED, c.code(), text(c.reason(), 255, true), text(c.notes(), 500, false));
        d.setStatus(DeliveryDispatchStatus.FAILED); dispatches.saveAndFlush(d);
        record(s, ctx.actor(), ShipmentStatus.DELIVERY_FAILED, s.getDestinationBranch(), "Lần giao chưa thành công", c.code().name() + ": " + c.reason());
        // Assignment remains IN_PROGRESS: the courier is still accountable for the parcel.
    }
    @Transactional @PreAuthorize("hasRole('MANAGER')")
    public void redelivery(@NotNull @Valid Redelivery c) {
        Shipment s = shipment(c.shipmentId()); User actor = manager(s, c.expectedVersion()); noReturn(s); capacity(s);
        String reason = text(c.reason(), 500, true);
        if (s.getStatus() != ShipmentStatus.DELIVERY_FAILED || s.getDeliveryAttemptCount() < 1
                || decisions.existsByShipmentIdAndAfterAttemptNumber(s.getId(), s.getDeliveryAttemptCount())) { throw rule("Không còn lần giao thất bại chưa được quyết định giao lại."); }
        var active = active(s); ShipmentAssignment a; RedeliveryMode mode;
        if (s.getCurrentBranch() == null) {
            if (active.size() != 1) { throw rule("Chưa xác định được Shipper đang giữ kiện."); }
            a = active.getFirst(); DeliveryDispatch d = dispatch(s, a);
            if (a.getStatus() != AssignmentStatus.IN_PROGRESS || d.getStatus() != DeliveryDispatchStatus.FAILED
                    || (c.employeeId() != null && !c.employeeId().equals(a.getEmployee().getId()))) { throw rule("Kiện còn ở Shipper: chỉ giao lại cùng người, đổi người cần nhận lại thực tế tại chi nhánh."); }
            access.requireAssignableShipper(a.getEmployee().getId(), s.getDestinationBranch().getId());
            d.setStatus(DeliveryDispatchStatus.ACCEPTED); d.setStartedAt(null); dispatches.saveAndFlush(d); mode = RedeliveryMode.SAME_COURIER;
        } else {
            custody.requireAtDestination(s);
            if (!active.isEmpty() || c.employeeId() == null) { throw rule("Chọn Shipper hợp lệ sau khi đã nhận lại kiện tại chi nhánh."); }
            Employee employee = access.requireAssignableShipper(c.employeeId(), s.getDestinationBranch().getId());
            a = newAssignment(s, employee, actor, null); mode = RedeliveryMode.NEW_ASSIGNMENT;
        }
        RedeliveryDecision decision = new RedeliveryDecision(); decision.setShipment(s); decision.setAssignment(a); decision.setActor(actor);
        decision.setAfterAttemptNumber(s.getDeliveryAttemptCount()); decision.setMode(mode); decision.setReason(reason); decision.setDecidedAt(now()); decisions.saveAndFlush(decision);
        record(s, actor, s.getStatus(), s.getDestinationBranch(), "Manager cho phép giao lại", reason);
    }
    @Transactional @PreAuthorize("hasRole('SHIPPER')")
    public void requestRecovery(@NotNull @Valid Recover c) {
        var ctx = owned(c.assignmentId(), c.expectedVersion()); Shipment s = ctx.assignment().getShipment(); DeliveryDispatch d = held(s, ctx);
        if ((d.getStatus() != DeliveryDispatchStatus.FAILED && d.getStatus() != DeliveryDispatchStatus.RECALL_REQUIRED)
                || recoveries.existsByShipmentIdAndStatus(s.getId(), DeliveryRecoveryStatus.PENDING)
                || (s.getStatus() != ShipmentStatus.DELIVERY_FAILED && s.getStatus() != ShipmentStatus.RETURNING)) { throw rule("Chỉ trả kiện đang giữ sau giao thất bại hoặc quyết định thu hồi/hoàn hợp lệ."); }
        DeliveryRecovery r = new DeliveryRecovery(); r.setShipment(s); r.setAssignment(ctx.assignment()); r.setBranch(s.getDestinationBranch());
        r.setRequestedBy(ctx.actor()); r.setEmployee(ctx.employee()); r.setNotes(text(c.notes(), 500, false)); r.setRequestedAt(now()); recoveries.saveAndFlush(r);
        d.setStatus(DeliveryDispatchStatus.RECOVERY_REQUESTED); dispatches.saveAndFlush(d);
        record(s, ctx.actor(), s.getStatus(), s.getDestinationBranch(), "Shipper yêu cầu bàn giao trả lại chi nhánh", "Phân công #" + ctx.assignment().getId());
    }
    @Transactional @PreAuthorize("hasRole('MANAGER')")
    public void reviewRecovery(@NotNull @Valid ReviewRecovery c) {
        Shipment s = shipment(c.shipmentId()); User actor = manager(s, c.expectedVersion()); Employee receiver = access.requireIntakeEmployee(s.getDestinationBranch().getId());
        DeliveryRecovery r = recoveries.findByIdAndShipmentId(c.recoveryId(), s.getId()).orElseThrow(this::missing);
        ShipmentAssignment a = sole(s, r.getAssignment().getId()); DeliveryDispatch d = dispatch(s, a);
        if (r.getStatus() != DeliveryRecoveryStatus.PENDING || d.getStatus() != DeliveryDispatchStatus.RECOVERY_REQUESTED || s.getCurrentBranch() != null
                || !TransportRouting.same(r.getBranch(), s.getDestinationBranch()) || !a.getEmployee().getId().equals(r.getEmployee().getId())) { throw rule("Bàn giao trả kiện không còn hợp lệ."); }
        if (actor.getId().equals(r.getRequestedBy().getId()) || receiver.getId().equals(r.getEmployee().getId())) { throw denied(); }
        String reference = text(c.reference(), 100, c.accept()), reason = text(c.reason(), 500, !c.accept());
        if (c.accept() != c.confirmed()) { throw rule("Chấp nhận phải xác nhận thực nhận; từ chối không được đánh dấu đã nhận."); }
        ReturnRequest ret = returns.findByShipmentId(s.getId()).orElse(null);
        r.setReviewedBy(actor); r.setReceiver(receiver); r.setReviewedAt(now()); r.setReference(reference); r.setReason(reason);
        r.setStatus(c.accept() ? DeliveryRecoveryStatus.RECEIVED : DeliveryRecoveryStatus.REJECTED); recoveries.saveAndFlush(r);
        if (c.accept()) {
            d.setStatus(DeliveryDispatchStatus.BRANCH_RETURNED); d.setFinishedAt(now()); dispatches.saveAndFlush(d);
            closeAssignment(a, ret == null ? AssignmentStatus.FAILED : AssignmentStatus.CANCELLED); s.setCurrentBranch(s.getDestinationBranch());
            if (ret != null) { ret.setStage(atSameBranch(s) ? ReturnStage.AT_ORIGIN : ReturnStage.READY_FOR_TRANSPORT); returns.saveAndFlush(ret); }
        } else { d.setStatus(ret == null ? DeliveryDispatchStatus.FAILED : DeliveryDispatchStatus.RECALL_REQUIRED); dispatches.saveAndFlush(d); }
        record(s, actor, s.getStatus(), s.getDestinationBranch(), c.accept() ? "Chi nhánh đã thực nhận lại kiện" : "Chi nhánh từ chối nhận lại kiện", c.accept() ? reference : reason);
    }
    @Transactional @PreAuthorize("hasRole('MANAGER')")
    public void initiateReturn(@NotNull @Valid ReturnStart c) {
        Shipment s = shipment(c.shipmentId()); User actor = manager(s, c.expectedVersion()); noReturn(s); noTrip(s);
        if (!Set.of(ShipmentStatus.AT_DESTINATION_BRANCH, ShipmentStatus.OUT_FOR_DELIVERY, ShipmentStatus.DELIVERY_FAILED).contains(s.getStatus())
                || proofs.existsByShipmentId(s.getId())) { throw rule("Không khởi tạo hoàn cho kiện đã giao/hoàn hoặc chưa tới giai đoạn giao cuối."); }
        ReturnRequest r = new ReturnRequest(); r.setShipment(s); r.setRequestedBy(actor); r.setReason(text(c.reason(), 500, true)); r.setRequestedAt(now());
        var active = active(s);
        if (s.getCurrentBranch() != null) {
            custody.requireAtDestination(s);
            if (s.getStatus() == ShipmentStatus.OUT_FOR_DELIVERY || active.size() > 1) { throw rule("Vị trí kiện hoặc phân công không nhất quán."); }
            for (ShipmentAssignment a : active) { if (a.getStatus() != AssignmentStatus.ASSIGNED) { throw rule("Cần thu hồi thực tế từ Shipper."); } cancelPrepared(a); closeAssignment(a, AssignmentStatus.CANCELLED); }
            r.setStage(atSameBranch(s) ? ReturnStage.AT_ORIGIN : ReturnStage.READY_FOR_TRANSPORT);
        } else {
            if (active.size() != 1 || active.getFirst().getStatus() != AssignmentStatus.IN_PROGRESS) { throw rule("Chưa xác định người đang giữ kiện để thu hồi."); }
            DeliveryDispatch d = dispatch(s, active.getFirst());
            if (!Set.of(DeliveryDispatchStatus.ACCEPTED, DeliveryDispatchStatus.STARTED, DeliveryDispatchStatus.FAILED, DeliveryDispatchStatus.RECOVERY_REQUESTED).contains(d.getStatus())) { throw rule("Nhiệm vụ không thể thu hồi."); }
            if (d.getStatus() != DeliveryDispatchStatus.RECOVERY_REQUESTED) { d.setStatus(DeliveryDispatchStatus.RECALL_REQUIRED); dispatches.saveAndFlush(d); }
            r.setStage(ReturnStage.REQUESTED);
        }
        returns.saveAndFlush(r); record(s, actor, ShipmentStatus.RETURNING, s.getDestinationBranch(), "Manager khởi tạo hoàn, chờ thu hồi/vận chuyển thực tế", r.getReason());
    }
    @Transactional @PreAuthorize("hasRole('MANAGER')")
    public void returnToSender(@NotNull @Valid SenderReceipt c) {
        Shipment s = shipment(c.shipmentId()); User actor = access.requireManagerForBranch(s.getOriginBranch().getId());
        Employee employee = access.requireIntakeEmployee(s.getOriginBranch().getId()); version(s, c.expectedVersion()); noTrip(s);
        ReturnRequest r = returns.findByShipmentId(s.getId()).orElseThrow(this::missing);
        if (!c.confirmed() || c.recipientType() == null || r.getStage() != ReturnStage.AT_ORIGIN || s.getStatus() != ShipmentStatus.RETURNING
                || !TransportRouting.same(s.getCurrentBranch(), s.getOriginBranch()) || !active(s).isEmpty() || returnReceipts.existsByReturnRequestId(r.getId())
                || (!atSameBranch(s) && tripReceipts.countReturnProofAt(r.getId(), s.getOriginBranch().getId()) == 0)) { throw rule("Chỉ trả người gửi sau khi nguồn đã thực nhận kiện hoàn; cần xác nhận và biên nhận hợp lệ."); }
        if (actor.getId().equals(s.getSender().getId())) { throw denied(); }
        ReturnReceipt receipt = new ReturnReceipt(); receipt.setReturnRequest(r); receipt.setActor(actor); receipt.setEmployee(employee); receipt.setBranch(s.getOriginBranch());
        receipt.setRecipientType(c.recipientType()); receipt.setRecipientName(text(c.recipientName(), 100, true)); receipt.setReference(text(c.reference(), 100, true));
        receipt.setNotes(text(c.notes(), 500, false)); receipt.setReceivedAt(now()); returnReceipts.saveAndFlush(receipt);
        r.setStage(ReturnStage.COMPLETED); r.setCompletedAt(now()); returns.saveAndFlush(r); s.setReturnedAt(now()); s.setCurrentBranch(null);
        record(s, actor, ShipmentStatus.RETURNED, s.getOriginBranch(), "Đã trả kiện thực tế cho người gửi/đại diện", receipt.getReference());
    }
    private Shipment shipment(Long id) { return shipments.findForLifecycle(id).orElseThrow(this::missing); }
    private User manager(Shipment s, Long expected) { User actor = access.requireManagerForBranch(s.getDestinationBranch().getId()); version(s, expected); return actor; }
    private LogisticsAccessService.AssignedShipper owned(Long assignmentId, Long expected) {
        var profile = access.requireShipper();
        Long id = assignments.findShipmentIdForOwner(assignmentId, profile.employee().getId(), AssignmentType.DELIVERY).orElseThrow(this::missing);
        Shipment s = shipment(id); var ctx = access.requireAssignedShipper(id, AssignmentType.DELIVERY);
        if (!ctx.assignment().getId().equals(assignmentId)) { throw denied(); } version(s, expected); noTrip(s); return ctx;
    }
    private List<ShipmentAssignment> active(Shipment s) { return assignments.findByShipmentIdAndAssignmentTypeAndStatusIn(s.getId(), AssignmentType.DELIVERY, LogisticsAccessService.ACTIVE_ASSIGNMENTS); }
    private ShipmentAssignment sole(Shipment s, Long id) {
        var found = active(s); if (found.size() != 1 || !found.getFirst().getId().equals(id) || found.getFirst().getCompletedAt() != null) { throw rule("Phân công DELIVERY hiện tại đã thay đổi."); } return found.getFirst();
    }
    private ShipmentAssignment newAssignment(Shipment s, Employee e, User actor, String notes) {
        ShipmentAssignment a = new ShipmentAssignment(); a.setShipment(s); a.setEmployee(e); a.setAssignedBy(actor); a.setAssignmentType(AssignmentType.DELIVERY);
        a.setStatus(AssignmentStatus.ASSIGNED); a.setAssignedAt(now()); a.setNotes(notes); return assignments.saveAndFlush(a);
    }
    private void closeAssignment(ShipmentAssignment a, AssignmentStatus status) { a.setStatus(status); a.setCompletedAt(now()); assignments.saveAndFlush(a); }
    private void cancelPrepared(ShipmentAssignment a) {
        dispatches.findByAssignmentId(a.getId()).ifPresent(d -> {
            if (d.getStatus() != DeliveryDispatchStatus.PREPARED) { throw rule("Không phân công lại khi Shipper đã nhận kiện."); }
            d.setStatus(DeliveryDispatchStatus.CANCELLED); d.setFinishedAt(now()); dispatches.saveAndFlush(d);
        });
    }
    private DeliveryDispatch dispatch(Shipment s, ShipmentAssignment a) {
        DeliveryDispatch d = dispatches.findByAssignmentId(a.getId()).orElseThrow(() -> rule("Chưa có biên bản bàn giao cho Shipper."));
        if (!d.getShipment().getId().equals(s.getId()) || !d.getAssignment().getId().equals(a.getId()) || !TransportRouting.same(d.getBranch(), s.getDestinationBranch())) { throw rule("Bàn giao không khớp kiện/nhiệm vụ."); } return d;
    }
    private DeliveryDispatch held(Shipment s, LogisticsAccessService.AssignedShipper ctx) {
        DeliveryDispatch d = dispatch(s, ctx.assignment());
        if (s.getCurrentBranch() != null || ctx.assignment().getStatus() != AssignmentStatus.IN_PROGRESS || d.getAcceptedAt() == null
                || !ctx.actor().getId().equals(d.getAcceptedBy().getId()) || !ctx.employee().getId().equals(d.getAcceptedEmployee().getId())) { throw rule("Chưa có bằng chứng Shipper đang thực sự giữ kiện."); } return d;
    }
    private DeliveryDispatch running(Shipment s, LogisticsAccessService.AssignedShipper ctx) {
        DeliveryDispatch d = held(s, ctx);
        if (s.getStatus() != ShipmentStatus.OUT_FOR_DELIVERY || d.getStatus() != DeliveryDispatchStatus.STARTED || d.getStartedAt() == null) { throw rule("Lần giao chưa bắt đầu hoặc đã có kết quả."); } return d;
    }
    private DeliveryAttempt result(Shipment s, LogisticsAccessService.AssignedShipper ctx, DeliveryDispatch d, DeliveryAttemptStatus status, DeliveryFailureCode code, String reason, String notes) {
        int count = capacity(s); DeliveryAttempt attempt = new DeliveryAttempt(); attempt.setShipment(s); attempt.setAssignment(ctx.assignment());
        attempt.setEmployee(ctx.employee()); attempt.setActor(ctx.actor()); attempt.setAttemptNumber(count + 1); attempt.setStatus(status);
        attempt.setFailureCode(code); attempt.setFailureReason(reason); attempt.setNotes(notes); attempt.setStartedAt(d.getStartedAt()); attempt.setAttemptTime(now());
        attempt = attempts.saveAndFlush(attempt); s.setDeliveryAttemptCount(count + 1); return attempt;
    }
    private int capacity(Shipment s) {
        long count = attempts.countByShipmentId(s.getId()); Integer max = attempts.maxAttemptNumber(s.getId());
        if (max == null || s.getDeliveryAttemptCount() == null || count != s.getDeliveryAttemptCount() || count != max
                || s.getMaxDeliveryAttempts() == null || count >= s.getMaxDeliveryAttempts()) { throw rule("Đã hết số lần thử giao hoặc lịch sử/bộ đếm chưa nhất quán; cần Manager xem xét."); } return Math.toIntExact(count);
    }
    private void noReturn(Shipment s) { if (returns.existsByShipmentId(s.getId())) { throw rule("Kiện đã có quy trình hoàn, không tiếp tục giao/giao lại."); } }
    private void noTrip(Shipment s) { if (tripItems.findByShipmentIdAndActiveTrue(s.getId()).isPresent()) { throw rule("Kiện đang thuộc manifest vận chuyển."); } }
    private void atDestinationStage(Shipment s) { if (s.getStatus() != ShipmentStatus.AT_DESTINATION_BRANCH && s.getStatus() != ShipmentStatus.DELIVERY_FAILED) { throw rule("Kiện chưa ở giai đoạn chờ giao tại đích."); } }
    private boolean atSameBranch(Shipment s) { return TransportRouting.same(s.getOriginBranch(), s.getDestinationBranch()); }
    private void version(Shipment s, Long expected) { if (expected == null || !Objects.equals(s.getVersion(), expected)) { throw new ObjectOptimisticLockingFailureException(Shipment.class, s.getId()); } }
    private void record(Shipment s, User actor, ShipmentStatus to, Branch point, String title, String description) {
        ShipmentStatus from = s.getStatus(); if (from != to && !ShipmentTransitionPolicy.isPotentialEdge(from, to)) { throw rule("Chuyển trạng thái giao/hoàn không hợp lệ."); }
        s.setStatus(to); s.setUpdatedAt(s.getUpdatedAt() != null && !now().isAfter(s.getUpdatedAt()) ? s.getUpdatedAt().plusNanos(100) : now()); shipments.saveAndFlush(s);
        ShipmentEvent e = new ShipmentEvent(); e.setShipment(s); e.setActor(actor); e.setBranch(point); e.setFromStatus(from); e.setToStatus(to);
        e.setEventTitle(title); e.setDescription(description == null ? null : description.substring(0, Math.min(500, description.length()))); e.setOccurredAt(now()); events.saveAndFlush(e);
    }
    private String text(String value, int max, boolean required) { if ((required && (value == null || value.isBlank())) || (value != null && value.length() > max)) { throw rule("Thiếu dữ liệu xác nhận/lý do hoặc vượt độ dài cho phép."); } return value == null || value.isBlank() ? null : value.strip(); }
    private LocalDateTime now() { var t = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); return t.withNano(t.getNano() / 100 * 100); }
    private AccessDeniedException denied() { return new AccessDeniedException("Forbidden"); }
    private LogisticsRuleException rule(String message) { return new LogisticsRuleException(message); }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy nhiệm vụ/kiện trong phạm vi."); }
}
