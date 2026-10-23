package com.ute.uteexpress.service;

import com.ute.uteexpress.dto.transport.TripViews.*;
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

@Service @Validated @Transactional(readOnly = true) @PreAuthorize("hasRole('MANAGER')")
public class TransportTripReadService {
    private final LogisticsAccessService access;
    private final TransportRouteRepository routes;
    private final TransportTripRepository trips;
    private final TransportTripItemRepository items;
    private final TripReceiptRepository receipts;
    private final TripDiscrepancyRepository discrepancies;
    private final TransportTripEventRepository events;
    public TransportTripReadService(LogisticsAccessService access, TransportRouteRepository routes, TransportTripRepository trips,
            TransportTripItemRepository items, TripReceiptRepository receipts, TripDiscrepancyRepository discrepancies, TransportTripEventRepository events) {
        this.access = access; this.routes = routes; this.trips = trips; this.items = items; this.receipts = receipts;
        this.discrepancies = discrepancies; this.events = events;
    }
    public Page<Row> list(@Min(0) @Max(1000000) int page) {
        return trips.findManaged(access.requireManager().getId(), page(page, "createdAt")).map(TransportTripReadService::row);
    }
    public Page<Route> routes(@Min(0) @Max(1000000) int page) {
        return routes.findAvailable(access.requireManager().getId(), PageRequest.of(page, 20, Sort.by("id")))
            .map(r -> new Route(r.getId(), r.getOrigin().getName(), r.getDestination().getName(), r.getLeg()));
    }
    public Page<Candidate> localReady(@Min(0) @Max(1000000) int page) {
        return items.findLocalReady(access.requireManager().getId(), page(page, "createdAt")).map(this::candidate);
    }
    public Detail detail(@NotNull @Positive Long id, @Min(0) @Max(1000000) int itemPage, @Min(0) @Max(1000000) int candidatePage,
            @Min(0) @Max(1000000) int issuePage, @Min(0) @Max(1000000) int eventPage) {
        User actor = access.requireManager();
        TransportTrip t = trips.findManagedById(id, actor.getId()).orElseThrow(this::missing);
        boolean origin = permits(t.getOrigin().getId()), destination = permits(t.getDestination().getId());
        boolean canReceive = destination && (t.getDepartedBy() == null || (!actor.getId().equals(t.getDepartedBy().getId())
            && !access.requireIntakeEmployee(t.getDestination().getId()).getId().equals(t.getDepartedEmployee().getId())));
        long count = items.countByTripId(id), received = items.countByTripIdAndStatus(id, TripItemStatus.RECEIVED);
        long open = discrepancies.countByTripIdAndStatus(id, TripDiscrepancyStatus.OPEN);
        boolean draft = t.getStatus() == TripStatus.DRAFT;
        // Planning only needs source management; physical operations additionally need a linked qualified employee.
        boolean canPlan = draft && managed(t.getOrigin().getId());
        Page<Candidate> candidates = canPlan
            ? items.findCandidates(t.getOrigin().getId(), t.getDestination().getId(), t.getLeg(), page(candidatePage, "createdAt")).map(this::candidate)
            : Page.empty();
        return new Detail(row(t), t.getVersion(), name(t.getCreatedBy()), name(t.getSealedBy()), name(t.getDepartedBy()), name(t.getArrivedBy()), name(t.getClosedBy()),
            t.getSealedAt(), t.getDepartedAt(), t.getArrivedAt(), t.getClosedAt(), t.getExpectedCount(), count, received,
            items.countByTripIdAndStatus(id, TripItemStatus.MISSING), items.countByTripIdAndStatus(id, TripItemStatus.DAMAGED),
            items.countByTripIdAndStatus(id, TripItemStatus.REJECTED), open,
            items.findByTripId(id, page(itemPage, "addedAt")).map(this::item), candidates,
            discrepancies.findByTripId(id, page(issuePage, "recordedAt")).map(d -> new Issue(d.getId(), d.getItem() == null ? null : d.getItem().getId(),
                d.getKind(), d.getStatus(), d.getReference(), d.getNotes(), name(d.getActor()), d.getRecordedAt(), d.getResolution(), name(d.getResolvedBy()), d.getResolvedAt())),
            events.findByTripId(id, page(eventPage, "occurredAt")).map(e -> new Event(e.getType(), e.getFromStatus(), e.getToStatus(), name(e.getActor()),
                e.getBranch().getName(), e.getReference(), e.getNotes(), e.getOccurredAt())),
            canPlan, origin && draft && count > 0, origin && t.getStatus() == TripStatus.SEALED,
            canReceive && t.getStatus() == TripStatus.DEPARTED, canReceive && t.getStatus() == TripStatus.RECONCILING,
            canReceive && t.getStatus() == TripStatus.RECONCILING && count > 0 && received == count && open == 0);
    }
    public Page<History> history(@NotNull @Positive Long shipmentId, @Min(0) @Max(1000000) int page) {
        return items.findManagedHistory(shipmentId, access.requireManager().getId(), page(page, "addedAt")).map(i -> {
            TripReceipt r = receipts.findByItemId(i.getId()).orElse(null); TransportTrip t = i.getTrip();
            return new History(t.getId(), t.getCode(), t.getOrigin().getName(), t.getDestination().getName(), t.getLeg(), t.getStatus(), i.getStatus(),
                i.getAddedAt(), r == null ? null : r.getReference(), r == null ? null : r.getReceivedAt());
        });
    }
    private boolean permits(Long branchId) { try { access.requireIntakeEmployee(branchId); return true; } catch (AccessDeniedException denied) { return false; } }
    private boolean managed(Long branchId) { try { access.requireManagerForBranch(branchId); return true; } catch (AccessDeniedException denied) { return false; } }
    private Candidate candidate(Shipment s) { return new Candidate(s.getId(), s.getTrackingNumber(), s.getOriginBranch().getName(), s.getDestinationBranch().getName(), s.getStatus(), s.getVersion()); }
    private Item item(TransportTripItem i) {
        TripReceipt r = receipts.findByItemId(i.getId()).orElse(null);
        return new Item(i.getId(), i.getShipment().getId(), i.getShipment().getTrackingNumber(), i.getStatus(), i.getShipment().getStatus(),
            r == null ? null : r.getReference(), r == null ? null : name(r.getActor()), r == null ? null : r.getReceivedAt());
    }
    private static Row row(TransportTrip t) { return new Row(t.getId(), t.getCode(), t.getOrigin().getName(), t.getDestination().getName(), t.getLeg(), t.getStatus(), t.getCreatedAt()); }
    private String name(User u) { return u == null ? null : (u.getFullName() == null ? u.getUsername() : u.getFullName()); }
    private PageRequest page(int page, String time) { return PageRequest.of(page, 20, Sort.by(Sort.Order.desc(time), Sort.Order.desc("id"))); }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy chuyến trong phạm vi quản lý."); }
}
