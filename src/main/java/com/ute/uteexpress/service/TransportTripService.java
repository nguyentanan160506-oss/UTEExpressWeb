package com.ute.uteexpress.service;

import com.ute.uteexpress.dto.transport.TripCommands.*;
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

/** Central transport lifecycle. Lock order: one trip, then Shipment IDs ascending, then child rows. */
@Service @Validated @PreAuthorize("hasRole('MANAGER')")
public class TransportTripService {
    private final TransportRouteRepository routes;
    private final TransportTripRepository trips;
    private final TransportTripItemRepository items;
    private final TripReceiptRepository receipts;
    private final TripDiscrepancyRepository discrepancies;
    private final TransportTripEventRepository events;
    private final ShipmentRepository shipments;
    private final ShipmentEventRepository shipmentEvents;
    private final ShipmentIntakeRepository intakes;
    private final ShipmentAssignmentRepository assignments;
    private final LogisticsAccessService access;
    private final Clock clock;
    public TransportTripService(TransportRouteRepository routes, TransportTripRepository trips, TransportTripItemRepository items,
            TripReceiptRepository receipts, TripDiscrepancyRepository discrepancies, TransportTripEventRepository events,
            ShipmentRepository shipments, ShipmentEventRepository shipmentEvents, ShipmentIntakeRepository intakes,
            ShipmentAssignmentRepository assignments, LogisticsAccessService access, Clock clock) {
        this.routes = routes; this.trips = trips; this.items = items; this.receipts = receipts; this.discrepancies = discrepancies;
        this.events = events; this.shipments = shipments; this.shipmentEvents = shipmentEvents; this.intakes = intakes;
        this.assignments = assignments; this.access = access; this.clock = clock;
    }
    @Transactional
    public Long create(@NotNull @Valid Create c) {
        User actor = access.requireManager();
        var previous = trips.findByRequestTokenAndCreatedById(c.requestToken(), actor.getId());
        if (previous.isPresent()) {
            access.requireManagerForBranch(previous.get().getOrigin().getId());
            if (!previous.get().getRoute().getId().equals(c.routeId())) { throw rule("Mã yêu cầu đã được dùng cho tuyến khác."); }
            return previous.get().getId();
        }
        TransportRoute r = routes.findById(c.routeId()).orElseThrow(() -> missing("tuyến"));
        access.requireManagerForBranch(r.getOrigin().getId()); requireRoute(r);
        TransportTrip t = new TransportTrip(); t.setRoute(r); t.setOrigin(r.getOrigin()); t.setDestination(r.getDestination());
        t.setLeg(r.getLeg()); t.setCode("TRP" + UUID.randomUUID().toString().replace("-", "")); t.setRequestToken(c.requestToken());
        t.setCreatedBy(actor); t.setCreatedAt(now()); t.setUpdatedAt(t.getCreatedAt()); t = trips.saveAndFlush(t);
        event(t, null, TripEventType.CREATED, null, actor, null, t.getOrigin(), null, "Tạo chuyến theo tuyến cấu hình.");
        return t.getId();
    }
    @Transactional
    public void addItems(@NotNull @Valid Manifest c) {
        TransportTrip t = locked(c.tripId(), c.expectedVersion()); User actor = access.requireManagerForBranch(t.getOrigin().getId());
        state(t, TripStatus.DRAFT); requireTripRoute(t);
        if (c.shipmentIds() == null || c.shipmentIds().isEmpty() || c.shipmentIds().size() > 50
                || c.shipmentIds().stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(c.shipmentIds()).size() != c.shipmentIds().size()) { throw rule("Chọn 1–50 kiện khác nhau mỗi lần."); }
        if (items.countByTripId(t.getId()) + c.shipmentIds().size() > 500) { throw rule("Mỗi chuyến tối đa 500 kiện."); }
        var locked = c.shipmentIds().stream().sorted().map(this::shipment).toList();
        for (Shipment s : locked) {
            requireCustody(s, t);
            if (items.findByShipmentIdAndActiveTrue(s.getId()).isPresent()
                    || items.findByTripIdAndShipmentId(t.getId(), s.getId()).isPresent()) { throw rule("Kiện đã nằm trong manifest hoặc chuyến đang hoạt động."); }
        }
        for (Shipment s : locked) {
            TransportTripItem i = new TransportTripItem(); i.setTrip(t); i.setShipment(s); i.setAddedAt(now()); items.saveAndFlush(i);
            shipmentEvent(s, s.getStatus(), t.getOrigin(), actor, "Đã đưa kiện vào manifest", t.getCode());
            event(t, i, TripEventType.ITEMS_ADDED, t.getStatus(), actor, null, t.getOrigin(), null, "Thêm kiện vào manifest.");
        }
        touch(t);
    }
    @Transactional
    public void seal(@NotNull @Valid Change c) {
        TransportTrip t = locked(c.tripId(), c.expectedVersion()); User actor = access.requireManagerForBranch(t.getOrigin().getId());
        Employee employee = access.requireIntakeEmployee(t.getOrigin().getId()); state(t, TripStatus.DRAFT); confirmation(c); requireTripRoute(t);
        List<Shipment> manifest = manifest(t); validateReserved(t, manifest);
        t.setExpectedCount(manifest.size()); t.setSealedBy(actor); t.setSealedAt(now()); t.setStatus(TripStatus.SEALED);
        touch(t); event(t, null, TripEventType.SEALED, TripStatus.DRAFT, actor, employee, t.getOrigin(), clean(c.reference()), clean(c.notes()));
    }
    @Transactional
    public void depart(@NotNull @Valid Change c) {
        TransportTrip t = locked(c.tripId(), c.expectedVersion()); User actor = access.requireManagerForBranch(t.getOrigin().getId());
        Employee employee = access.requireIntakeEmployee(t.getOrigin().getId()); state(t, TripStatus.SEALED); confirmation(c); requireTripRoute(t);
        List<Shipment> manifest = manifest(t);
        if (manifest.size() != t.getExpectedCount()) { throw rule("Số kiện không khớp manifest đã niêm phong."); }
        validateReserved(t, manifest);
        for (Shipment s : manifest) {
            s.setCurrentBranch(null);
            shipmentEvent(s, ShipmentStatus.IN_TRANSIT, t.getOrigin(), actor, "Kiện đã xuất chuyến", t.getCode() + "; " + clean(c.reference()));
        }
        t.setDepartedBy(actor); t.setDepartedEmployee(employee); t.setDepartedAt(now()); t.setStatus(TripStatus.DEPARTED);
        touch(t); event(t, null, TripEventType.DEPARTED, TripStatus.SEALED, actor, employee, t.getOrigin(), clean(c.reference()), clean(c.notes()));
    }
    @Transactional
    public void arrive(@NotNull @Valid Change c) {
        TransportTrip t = locked(c.tripId(), c.expectedVersion()); var receiver = destination(t); state(t, TripStatus.DEPARTED); confirmation(c);
        t.setArrivedBy(receiver.getUser()); t.setArrivedAt(now()); t.setStatus(TripStatus.RECONCILING);
        touch(t); event(t, null, TripEventType.ARRIVED, TripStatus.DEPARTED, receiver.getUser(), receiver, t.getDestination(), clean(c.reference()), clean(c.notes()));
        // Vehicle arrival is deliberately not a shipment receipt or a location update.
    }
    @Transactional
    public void receive(@NotNull @Valid Receive c) {
        TransportTrip t = locked(c.tripId(), c.expectedVersion()); Employee receiver = destination(t); state(t, TripStatus.RECONCILING);
        Shipment s = shipment(items.findShipmentId(t.getId(), c.itemId()).orElseThrow(() -> missing("kiện trong manifest")));
        TransportTripItem i = item(t, c.itemId()); requireInTransit(s, t, i); text(c.reference(), 100, true); text(c.notes(), 500, false);
        if (!c.physicallyReceived()) { throw rule("Cần xác nhận đã kiểm đếm và thực nhận đúng kiện."); }
        var issues = discrepancies.findByItemIdAndStatus(i.getId(), TripDiscrepancyStatus.OPEN);
        if (!issues.isEmpty() && (!c.resolveDiscrepancy() || clean(c.notes()) == null)) { throw rule("Kiện có sai lệch: cần xác nhận đã xử lý và ghi rõ kết quả kiểm tra."); }
        if (receipts.existsByItemId(i.getId())) { throw rule("Kiện đã có biên nhận tại chặng này."); }
        ShipmentStatus to = TransportRouting.receiptStatus(s, t);
        TripReceipt r = new TripReceipt(); r.setItem(i); r.setBranch(t.getDestination()); r.setActor(receiver.getUser());
        r.setEmployee(receiver); r.setReference(clean(c.reference())); r.setNotes(clean(c.notes())); r.setReceivedAt(now()); receipts.saveAndFlush(r);
        i.setStatus(TripItemStatus.RECEIVED); i.setActive(false); items.saveAndFlush(i);
        for (TripDiscrepancy issue : issues) { resolve(issue, receiver, "Đã thực nhận theo biên nhận: " + r.getReference()); }
        s.setCurrentBranch(t.getDestination()); shipmentEvent(s, to, t.getDestination(), receiver.getUser(), "Đã kiểm nhận kiện tại điểm đến", t.getCode() + "; " + r.getReference());
        touch(t); event(t, i, TripEventType.ITEM_RECEIVED, t.getStatus(), receiver.getUser(), receiver, t.getDestination(), r.getReference(), clean(c.notes()));
    }
    @Transactional
    public void recordDiscrepancy(@NotNull @Valid Discrepancy c) {
        TransportTrip t = locked(c.tripId(), c.expectedVersion()); Employee receiver = destination(t); state(t, TripStatus.RECONCILING);
        text(c.reference(), 100, true); text(c.notes(), 500, true);
        if (c.kind() == null || (c.kind() == TripDiscrepancyKind.UNEXPECTED) != (c.itemId() == null)) { throw rule("Sai lệch ngoài manifest không gán kiện; sai lệch còn lại cần đúng kiện trong chuyến."); }
        TransportTripItem i = null;
        if (c.itemId() != null) {
            Shipment s = shipment(items.findShipmentId(t.getId(), c.itemId()).orElseThrow(() -> missing("kiện trong manifest")));
            i = item(t, c.itemId()); requireInTransit(s, t, i);
            if (discrepancies.existsByItemIdAndStatus(i.getId(), TripDiscrepancyStatus.OPEN)) { throw rule("Kiện đã có sai lệch đang chờ xử lý."); }
            i.setStatus(TripItemStatus.valueOf(c.kind().name())); items.saveAndFlush(i);
            shipmentEvent(s, s.getStatus(), t.getDestination(), receiver.getUser(), "Ghi nhận sai lệch kiểm đếm", t.getCode() + "; " + c.kind());
        }
        TripDiscrepancy d = new TripDiscrepancy(); d.setTrip(t); d.setItem(i); d.setKind(c.kind()); d.setReference(clean(c.reference()));
        d.setNotes(clean(c.notes())); d.setActor(receiver.getUser()); d.setEmployee(receiver); d.setRecordedAt(now()); discrepancies.saveAndFlush(d);
        touch(t); event(t, i, TripEventType.DISCREPANCY_RECORDED, t.getStatus(), receiver.getUser(), receiver, t.getDestination(), d.getReference(), d.getNotes());
    }
    @Transactional
    public void resolveUnexpected(@NotNull @Valid Resolve c) {
        TransportTrip t = locked(c.tripId(), c.expectedVersion()); Employee receiver = destination(t); state(t, TripStatus.RECONCILING);
        TripDiscrepancy d = discrepancies.findByIdAndTripId(c.discrepancyId(), t.getId()).orElseThrow(() -> missing("sai lệch"));
        text(c.reference(), 100, true); text(c.notes(), 500, true);
        if (!c.confirmed() || d.getStatus() != TripDiscrepancyStatus.OPEN || d.getKind() != TripDiscrepancyKind.UNEXPECTED || d.getItem() != null) {
            throw rule("Chỉ kết thúc sai lệch ngoài manifest sau khi xử lý thực tế. Kiện thiếu/hỏng/từ chối phải qua kiểm nhận thực tế.");
        }
        resolve(d, receiver, clean(c.notes())); touch(t);
        event(t, null, TripEventType.DISCREPANCY_RESOLVED, t.getStatus(), receiver.getUser(), receiver, t.getDestination(), clean(c.reference()), "Sai lệch #" + d.getId() + ": " + clean(c.notes()));
    }
    @Transactional
    public void close(@NotNull @Valid Change c) {
        TransportTrip t = locked(c.tripId(), c.expectedVersion()); Employee receiver = destination(t); state(t, TripStatus.RECONCILING); confirmation(c);
        if (t.getExpectedCount() <= 0 || items.countByTripId(t.getId()) != t.getExpectedCount()
                || items.countByTripIdAndStatus(t.getId(), TripItemStatus.RECEIVED) != t.getExpectedCount()
                || discrepancies.countByTripIdAndStatus(t.getId(), TripDiscrepancyStatus.OPEN) != 0) {
            throw rule("Chỉ đóng khi mọi kiện đã có biên nhận và không còn sai lệch mở; không đóng bỏ qua kiện thiếu.");
        }
        t.setStatus(TripStatus.CLOSED); t.setClosedBy(receiver.getUser()); t.setClosedAt(now()); touch(t);
        event(t, null, TripEventType.CLOSED, TripStatus.RECONCILING, receiver.getUser(), receiver, t.getDestination(), clean(c.reference()), clean(c.notes()));
    }
    @Transactional
    public void readyAtSameBranch(@NotNull @Valid LocalReady c) {
        Shipment s = shipment(c.shipmentId()); User actor = access.requireManagerForBranch(s.getOriginBranch().getId());
        access.requireIntakeEmployee(s.getOriginBranch().getId());
        if (!Objects.equals(s.getVersion(), c.expectedVersion())) { throw new ObjectOptimisticLockingFailureException(Shipment.class, s.getId()); }
        TransportRouting.validateLinks(s); text(c.reference(), 100, true); text(c.notes(), 500, false);
        if (!c.physicallyPresent() || TransportRouting.path(s).size() != 1 || s.getShipmentMethod() == null
                || s.getStatus() != ShipmentStatus.AT_ORIGIN_BRANCH || !TransportRouting.same(s.getCurrentBranch(), s.getOriginBranch())
                || items.findByShipmentIdAndActiveTrue(s.getId()).isPresent()
                || assignments.existsByShipmentIdAndStatusIn(s.getId(), LogisticsAccessService.ACTIVE_ASSIGNMENTS)) { throw rule("Chỉ phân loại tại chỗ khi nguồn và đích cùng chi nhánh, kiện đã thực nhận và không thuộc chuyến."); }
        originProof(s);
        shipmentEvent(s, ShipmentStatus.AT_DESTINATION_BRANCH, s.getOriginBranch(), actor, "Phân loại chờ giao tại cùng chi nhánh", clean(c.reference()));
        // Reuses the real origin intake: no fake trip, handoff or second physical receipt.
    }
    private TransportTrip locked(Long id, Long version) {
        access.requireManager(); TransportTrip t = trips.findForUpdate(id).orElseThrow(() -> missing("chuyến"));
        try { access.requireManagerForBranch(t.getOrigin().getId()); }
        catch (AccessDeniedException notOrigin) { access.requireManagerForBranch(t.getDestination().getId()); }
        if (version == null || !Objects.equals(t.getVersion(), version)) { throw new ObjectOptimisticLockingFailureException(TransportTrip.class, id); }
        return t;
    }
    private Shipment shipment(Long id) { return shipments.findForLifecycle(id).orElseThrow(() -> missing("vận đơn")); }
    private TransportTripItem item(TransportTrip t, Long id) { return items.findByIdAndTripId(id, t.getId()).orElseThrow(() -> missing("kiện trong manifest")); }
    private List<Shipment> manifest(TransportTrip t) {
        List<Long> ids = items.findShipmentIds(t.getId());
        if (ids.isEmpty() || ids.size() > 500) { throw rule("Manifest cần 1–500 kiện."); }
        return ids.stream().sorted().map(this::shipment).toList();
    }
    private void validateReserved(TransportTrip t, List<Shipment> manifest) {
        for (Shipment s : manifest) {
            requireCustody(s, t); TransportTripItem i = items.findByShipmentIdAndActiveTrue(s.getId()).orElseThrow(() -> rule("Mất giữ chỗ kiện."));
            if (!i.getTrip().getId().equals(t.getId()) || i.getStatus() != TripItemStatus.MANIFESTED) { throw rule("Giữ chỗ hoặc tình trạng manifest đã thay đổi."); }
        }
    }
    private void requireCustody(Shipment s, TransportTrip t) {
        if (s.getShipmentMethod() == null || assignments.existsByShipmentIdAndStatusIn(s.getId(), LogisticsAccessService.ACTIVE_ASSIGNMENTS)) {
            throw rule("Kiện chưa xác định phương thức hoặc còn nhiệm vụ xung đột.");
        }
        TransportRouting.requireNext(s, t); originProof(s);
        if (!TransportRouting.same(s.getCurrentBranch(), s.getOriginBranch()) && receipts.countProofAt(s.getId(), t.getOrigin().getId()) == 0) {
            throw rule("Chưa có biên nhận chặng trước tại điểm xuất phát.");
        }
    }
    private void originProof(Shipment s) {
        ShipmentIntake r = intakes.findByShipmentId(s.getId()).orElseThrow(() -> rule("Chưa có biên nhận nguồn thực tế."));
        if (!TransportRouting.same(r.getBranch(), s.getOriginBranch()) || r.getShipmentMethod() != s.getShipmentMethod()) { throw rule("Biên nhận nguồn không khớp vận đơn."); }
        if (s.getShipmentMethod() == ShipmentMethod.HOME_PICKUP && (r.getPickupAssignment() == null
                || !s.getId().equals(r.getPickupAssignment().getShipment().getId())
                || r.getPickupAssignment().getAssignmentType() != AssignmentType.PICKUP
                || r.getPickupAssignment().getStatus() != AssignmentStatus.COMPLETED
                || r.getPickupAssignment().getCompletedAt() == null)) { throw rule("HOME_PICKUP chưa hoàn tất bàn giao nguồn hợp lệ."); }
    }
    private void requireInTransit(Shipment s, TransportTrip t, TransportTripItem i) {
        if (!i.getTrip().getId().equals(t.getId()) || !i.getShipment().getId().equals(s.getId())
                || !Boolean.TRUE.equals(i.getActive()) || i.getStatus() == TripItemStatus.RECEIVED
                || s.getStatus() != ShipmentStatus.IN_TRANSIT || s.getCurrentBranch() != null) { throw rule("Kiện không còn chờ kiểm nhận tại chuyến này."); }
    }
    private Employee destination(TransportTrip t) {
        User actor = access.requireManagerForBranch(t.getDestination().getId()); Employee receiver = access.requireIntakeEmployee(t.getDestination().getId());
        if (t.getDepartedBy() != null && (actor.getId().equals(t.getDepartedBy().getId())
                || receiver.getId().equals(t.getDepartedEmployee().getId()))) { throw new AccessDeniedException("Forbidden"); }
        return receiver;
    }
    private void requireRoute(TransportRoute r) {
        if (!Boolean.TRUE.equals(r.getActive())) { throw rule("Tuyến chưa hoạt động."); }
        TransportRouting.validateRoute(r.getOrigin(), r.getDestination(), r.getLeg());
    }
    private void requireTripRoute(TransportTrip t) {
        TransportRoute r = t.getRoute(); requireRoute(r);
        if (!TransportRouting.same(r.getOrigin(), t.getOrigin()) || !TransportRouting.same(r.getDestination(), t.getDestination()) || r.getLeg() != t.getLeg()) {
            throw rule("Tuyến cấu hình đã đổi; không xuất chuyến theo dữ liệu cũ.");
        }
    }
    private void resolve(TripDiscrepancy d, Employee receiver, String resolution) {
        d.setStatus(TripDiscrepancyStatus.RESOLVED); d.setResolvedBy(receiver.getUser()); d.setResolvedEmployee(receiver);
        d.setResolvedAt(now()); d.setResolution(resolution); discrepancies.saveAndFlush(d);
    }
    private void confirmation(Change c) { text(c.reference(), 100, true); text(c.notes(), 500, false); if (!c.confirmed()) { throw rule("Cần xác nhận thao tác thực tế."); } }
    private void state(TransportTrip t, TripStatus expected) { if (t.getStatus() != expected) { throw rule("Trạng thái chuyến không cho phép thao tác này."); } }
    private void touch(TransportTrip t) { t.setUpdatedAt(t.getUpdatedAt() != null && !now().isAfter(t.getUpdatedAt()) ? t.getUpdatedAt().plusNanos(100) : now()); trips.saveAndFlush(t); }
    private void shipmentEvent(Shipment s, ShipmentStatus to, Branch point, User actor, String title, String description) {
        ShipmentStatus from = s.getStatus();
        if (from != to && !ShipmentTransitionPolicy.isPotentialEdge(from, to)) { throw rule("Chặng vận chuyển không phù hợp lifecycle."); }
        s.setStatus(to);
        s.setUpdatedAt(s.getUpdatedAt() != null && !now().isAfter(s.getUpdatedAt()) ? s.getUpdatedAt().plusNanos(100) : now()); shipments.saveAndFlush(s);
        ShipmentEvent e = new ShipmentEvent(); e.setShipment(s); e.setBranch(point); e.setActor(actor); e.setFromStatus(from); e.setToStatus(to);
        e.setEventTitle(title); e.setDescription(description); e.setOccurredAt(now()); shipmentEvents.saveAndFlush(e);
    }
    private void event(TransportTrip t, TransportTripItem i, TripEventType type, TripStatus from, User actor, Employee employee, Branch point, String ref, String notes) {
        TransportTripEvent e = new TransportTripEvent(); e.setTrip(t); e.setItem(i); e.setType(type); e.setFromStatus(from); e.setToStatus(t.getStatus());
        e.setActor(actor); e.setEmployee(employee); e.setBranch(point); e.setReference(ref);
        e.setNotes(notes != null && notes.length() > 500 ? notes.substring(0, 500) : notes); e.setOccurredAt(now()); events.saveAndFlush(e);
    }
    private void text(String value, int max, boolean required) { if ((required && clean(value) == null) || (value != null && value.length() > max)) { throw rule("Thiếu tham chiếu/lý do hoặc vượt độ dài cho phép."); } }
    private String clean(String s) { return s == null || s.isBlank() ? null : s.strip(); }
    private LocalDateTime now() { var time = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); return time.withNano(time.getNano() / 100 * 100); }
    private LogisticsRuleException rule(String message) { return new LogisticsRuleException(message); }
    private ResponseStatusException missing(String what) { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy " + what + "."); }
}
