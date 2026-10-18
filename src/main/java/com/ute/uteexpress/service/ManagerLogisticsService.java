package com.ute.uteexpress.service;

import com.ute.uteexpress.dto.manager.ManagerViews.*;
import com.ute.uteexpress.dto.shipment.ShipmentDetailView;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

@Service @Validated @Transactional(readOnly = true) @PreAuthorize("hasRole('MANAGER')")
public class ManagerLogisticsService {
    private final LogisticsAccessService access;
    private final ManagerBranchRepository memberships;
    private final ShipmentRepository shipments;
    private final ShipmentEventRepository events;
    private final ShipmentAssignmentRepository assignments;
    private final EmployeeRepository employees;
    private final ShipmentIntakeRepository intakes;
    public ManagerLogisticsService(LogisticsAccessService access, ManagerBranchRepository memberships,
            ShipmentRepository shipments, ShipmentEventRepository events, ShipmentAssignmentRepository assignments,
            EmployeeRepository employees, ShipmentIntakeRepository intakes) {
        this.access = access; this.memberships = memberships; this.shipments = shipments; this.events = events;
        this.assignments = assignments; this.employees = employees; this.intakes = intakes;
    }

    public List<BranchOption> branches() {
        User manager = access.requireManager();
        return memberships.findManagedBranches(manager.getId(), BranchStatus.ACTIVE).stream()
            .map(branch -> new BranchOption(branch.getId(), branch.getName())).toList();
    }
    public Dashboard dashboard(@Positive Long branchId) {
        User manager = scope(branchId);
        Specification<Shipment> scoped = managedSearch(manager.getId(), branchId, null, null, null);
        return new Dashboard(shipments.count(scoped), count(scoped, ShipmentStatus.CREATED), count(scoped, ShipmentStatus.CONFIRMED),
            count(scoped, ShipmentStatus.PICKUP_ASSIGNED), count(scoped, ShipmentStatus.AT_ORIGIN_BRANCH));
    }
    public Page<Row> list(@Positive Long branchId, ShipmentStatus status, ShipmentMethod method,
            @Size(max = 50) String q, Pageable requested) {
        User manager = scope(branchId);
        return shipments.findAll(managedSearch(manager.getId(), branchId, status, method, q),
                PageRequest.of(Math.max(0, requested.getPageNumber()), Math.clamp(requested.getPageSize(), 1, 100),
                    Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
            .map(this::row);
    }
    public Detail detail(@NotNull @Positive Long id, @Min(0) @Max(1000000) int eventPage,
            @Min(0) @Max(1000000) int assignmentPage, @Min(0) @Max(1000000) int employeePage) {
        User manager = access.requireManager();
        Shipment s = shipments.findOne(managedSearch(manager.getId(), null, null, null, null)
            .and((root, query, cb) -> cb.equal(root.get("id"), id)))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy vận đơn trong phạm vi quản lý."));
        access.requireManagerForBranch(s.getOriginBranch().getId());
        var timeline = events.findByShipmentId(id, PageRequest.of(eventPage, 20, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))))
            .map(e -> new ShipmentDetailView.EventView(e.getEventTitle(), e.getDescription(), e.getLocationText(), e.getToStatus(), e.getOccurredAt()));
        var history = assignments.findByShipmentId(id, PageRequest.of(assignmentPage, 20, Sort.by(Sort.Order.desc("assignedAt"), Sort.Order.desc("id"))))
            .map(a -> new Assignment(a.getId(), a.getAssignmentType(), a.getStatus(), a.getEmployee().getId(), a.getEmployee().getFullName(),
                name(a.getAssignedBy()), a.getAssignedAt(), a.getCompletedAt(), a.getNotes()));
        var active = assignments.findByShipmentIdAndAssignmentTypeAndStatusIn(id, AssignmentType.PICKUP, LogisticsAccessService.ACTIVE_ASSIGNMENTS);
        boolean local = s.getOriginBranch().getBranchType() == BranchType.LOCAL_BRANCH;
        boolean home = local && s.getShipmentMethod() == ShipmentMethod.HOME_PICKUP;
        boolean assign = home && s.getStatus() == ShipmentStatus.CONFIRMED && active.isEmpty();
        boolean reassign = home && s.getStatus() == ShipmentStatus.PICKUP_ASSIGNED && active.size() == 1
            && active.getFirst().getStatus() == AssignmentStatus.ASSIGNED && active.getFirst().getCompletedAt() == null;
        Page<EmployeeOption> candidates = assign || reassign
            ? employees.findPickupCandidates(s.getOriginBranch().getId(), PageRequest.of(employeePage, 20, Sort.by("id")))
                .map(e -> new EmployeeOption(e.getId(), e.getEmployeeCode(), e.getFullName()))
            : Page.empty();
        Receipt receipt = intakes.findByShipmentId(id).map(r -> new Receipt(r.getReceiptReference(), r.getEmployee().getFullName(),
            name(r.getReceivedBy()), r.getReceivedAt(), r.getNotes())).orElse(null);
        boolean hasTasks = assignments.existsByShipmentIdAndStatusIn(id, LogisticsAccessService.ACTIVE_ASSIGNMENTS);
        boolean receive = local && !hasTasks && ShipmentTransitionPolicy.allowsDropoffIntake(s.getStatus(), s.getShipmentMethod()) && receipt == null;
        if (receive) {
            try { access.requireIntakeEmployee(s.getOriginBranch().getId()); }
            catch (AccessDeniedException denied) { receive = false; }
        }
        boolean cancellable = (s.getStatus() == ShipmentStatus.CREATED || s.getStatus() == ShipmentStatus.CONFIRMED)
            && !hasTasks;
        return new Detail(row(s), s.getVersion(),
            new ShipmentDetailView.Contact(s.getSenderName(), s.getSenderPhone(), s.getSenderProvince(), s.getSenderDistrict(), s.getSenderWard(), s.getSenderAddress()),
            new ShipmentDetailView.Contact(s.getReceiverName(), s.getReceiverPhone(), s.getReceiverProvince(), s.getReceiverDistrict(), s.getReceiverWard(), s.getReceiverAddress()),
            s.getDestinationBranch().getName(), s.getPackageDescription(), s.getWeightKg(), s.getShippingFee(), s.getPickupFee(),
            s.getInsuranceFee(), s.getTotalFee(), timeline, history, candidates, receipt,
            s.getStatus() == ShipmentStatus.CREATED, cancellable, assign, reassign, reassign ? active.getFirst().getId() : null, receive);
    }
    private long count(Specification<Shipment> scope, ShipmentStatus status) {
        return shipments.count(scope.and((root, query, cb) -> cb.equal(root.get("status"), status)));
    }
    private User scope(Long branchId) {
        User manager = access.requireManager();
        if (branchId != null) { access.requireManagerForBranch(branchId); }
        return manager;
    }
    private Row row(Shipment s) {
        return new Row(s.getId(), s.getTrackingNumber(), s.getStatus(), s.getShipmentMethod(), s.getOriginBranch().getId(),
            s.getOriginBranch().getName(), s.getCreatedAt());
    }
    private String name(User user) { return user.getFullName() == null ? user.getUsername() : user.getFullName(); }

    static Specification<Shipment> managedSearch(Long actorId, Long branchId, ShipmentStatus status, ShipmentMethod method, String q) {
        return (root, query, cb) -> {
            var membership = query.subquery(Long.class);
            var m = membership.from(ManagerBranch.class);
            membership.select(m.get("id")).where(cb.equal(m.get("manager").get("id"), actorId),
                cb.equal(m.get("branch").get("id"), root.get("originBranch").get("id")),
                cb.equal(m.get("branch").get("status"), BranchStatus.ACTIVE));
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.exists(membership));
            if (branchId != null) { predicates.add(cb.equal(root.get("originBranch").get("id"), branchId)); }
            if (status != null) { predicates.add(cb.equal(root.get("status"), status)); }
            if (method != null) { predicates.add(cb.equal(root.get("shipmentMethod"), method)); }
            if (q != null && !q.isBlank()) {
                String escaped = q.strip().toUpperCase(java.util.Locale.ROOT).replace("\\", "\\\\")
                    .replace("%", "\\%").replace("_", "\\_").replace("[", "\\[");
                predicates.add(cb.like(cb.upper(root.get("trackingNumber")), "%" + escaped + "%", '\\'));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
