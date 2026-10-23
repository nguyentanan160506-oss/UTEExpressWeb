package com.ute.uteexpress.delivery;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.service.*;
import com.ute.uteexpress.dto.delivery.DeliveryCommands.*;
import com.ute.uteexpress.dto.transport.TripCommands;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import static com.ute.uteexpress.transport.TransportFixtures.*;
import static org.mockito.Mockito.*;

/** Synthetic in-memory repository boundaries for actual business services/guards. Not JDBC. */
public final class DeliveryScenario {
    public final ShipmentRepository shipments = mock(ShipmentRepository.class);
    public final ShipmentAssignmentRepository assignments = mock(ShipmentAssignmentRepository.class);
    public final ShipmentEventRepository events = mock(ShipmentEventRepository.class);
    public final DeliveryAttemptRepository attempts = mock(DeliveryAttemptRepository.class);
    public final DeliveryDispatchRepository dispatches = mock(DeliveryDispatchRepository.class);
    public final DeliveryProofRepository proofs = mock(DeliveryProofRepository.class);
    public final DeliveryRecoveryRepository recoveries = mock(DeliveryRecoveryRepository.class);
    public final RedeliveryDecisionRepository decisions = mock(RedeliveryDecisionRepository.class);
    public final ReturnRequestRepository returns = mock(ReturnRequestRepository.class);
    public final ReturnReceiptRepository returnReceipts = mock(ReturnReceiptRepository.class);
    public final TripReceiptRepository tripReceipts = mock(TripReceiptRepository.class);
    public final TransportTripItemRepository tripItems = mock(TransportTripItemRepository.class);
    public final ShipmentIntakeRepository intakes = mock(ShipmentIntakeRepository.class);
    public final UserRepository users = mock(UserRepository.class);
    public final UserRoleRepository roles = mock(UserRoleRepository.class);
    public final EmployeeRepository employees = mock(EmployeeRepository.class);
    public final BranchRepository branches = mock(BranchRepository.class);
    public final ManagerBranchRepository memberships = mock(ManagerBranchRepository.class);
    public final TransportRouteRepository routes = mock(TransportRouteRepository.class);
    public final TransportTripRepository trips = mock(TransportTripRepository.class);
    public final TripDiscrepancyRepository discrepancies = mock(TripDiscrepancyRepository.class);
    public final TransportTripEventRepository tripEvents = mock(TransportTripEventRepository.class);
    public final Branch origin = point(11, BranchType.LOCAL_BRANCH), destination = point(12, BranchType.LOCAL_BRANCH);
    public final Branch h1 = point(101, BranchType.CENTRAL_HUB), h2 = point(102, BranchType.CENTRAL_HUB);
    public final User manager = actor(destination), sourceManager = actor(origin);
    public final Employee courier = com.ute.uteexpress.manager.ManagerFixtures.courier(21L), otherCourier = com.ute.uteexpress.manager.ManagerFixtures.courier(23L);
    public final Map<Long, User> userRows = new LinkedHashMap<>();
    public final Map<Long, List<String>> roleRows = new LinkedHashMap<>();
    public final Map<Long, Employee> employeeRows = new LinkedHashMap<>();
    public final Map<Long, Set<Long>> managed = new LinkedHashMap<>();
    public final Map<Long, ShipmentAssignment> tasks = new LinkedHashMap<>();
    public final Map<Long, DeliveryDispatch> dispatchRows = new LinkedHashMap<>();
    public final List<DeliveryAttempt> attemptRows = new ArrayList<>();
    public final List<DeliveryProof> proofRows = new ArrayList<>();
    public final List<DeliveryRecovery> recoveryRows = new ArrayList<>();
    public final List<RedeliveryDecision> decisionRows = new ArrayList<>();
    public final List<ShipmentEvent> eventRows = new ArrayList<>();
    public final Map<Long, TransportRoute> routeRows = new LinkedHashMap<>();
    public final Map<Long, TransportTrip> tripRows = new LinkedHashMap<>();
    public final Map<Long, TransportTripItem> tripItemRows = new LinkedHashMap<>();
    public final List<TripReceipt> tripReceiptRows = new ArrayList<>();
    public Shipment s;
    public ReturnRequest returnRequest;
    public ReturnReceipt senderReceipt;
    public boolean forwardReceipt = true, localClassification = false;
    public final LogisticsAccessService access;
    public final DeliveryCustodyService custody;
    public final LastMileService service;
    public final DeliveryReadService read;
    public final TransportTripService transport;
    public final Clock clock = Clock.fixed(Instant.parse("2026-10-23T09:00:00Z"), ZoneOffset.UTC);

    public DeliveryScenario() {
        origin.setParentHub(h1); destination.setParentHub(h2); courier.setBranch(destination); otherCourier.setBranch(destination);
        s = parcel(31, origin, destination); s.setCurrentBranch(destination); s.setStatus(ShipmentStatus.AT_DESTINATION_BRANCH);
        for (Branch b : List.of(origin, destination, h1, h2)) {
            User u = actor(b); if (b == origin) { u = sourceManager; } if (b == destination) { u = manager; }
            Employee staff = employee(b); staff.setUser(u); userRows.put(u.getId(), u); employeeRows.put(staff.getId(), staff);
            roleRows.put(u.getId(), List.of("ROLE_MANAGER")); managed.put(u.getId(), Set.of(b.getId()));
            when(branches.findByIdAndStatus(b.getId(), BranchStatus.ACTIVE)).thenAnswer(c -> b.getStatus() == BranchStatus.ACTIVE ? Optional.of(b) : Optional.empty());
        }
        for (Employee e : List.of(courier, otherCourier)) { userRows.put(e.getUser().getId(), e.getUser()); roleRows.put(e.getUser().getId(), List.of("ROLE_SHIPPER")); employeeRows.put(e.getId(), e); }
        when(users.findByEmail(anyString())).thenAnswer(c -> userRows.values().stream().filter(u -> u.getEmail().equals(c.getArgument(0))).findFirst());
        when(roles.findRoleNamesByUserId(anyLong())).thenAnswer(c -> roleRows.getOrDefault(c.getArgument(0), List.of()));
        when(memberships.existsByManagerIdAndBranchId(anyLong(), anyLong())).thenAnswer(c -> managed.getOrDefault(c.getArgument(0), Set.of()).contains(c.getArgument(1)));
        when(employees.findByUserId(anyLong())).thenAnswer(c -> employeeRows.values().stream().filter(e -> e.getUser() != null && e.getUser().getId().equals(c.getArgument(0))).findFirst());
        when(employees.findByIdAndBranchId(anyLong(), anyLong())).thenAnswer(c -> Optional.ofNullable(employeeRows.get(c.getArgument(0))).filter(e -> e.getBranch().getId().equals(c.getArgument(1))));
        when(employees.findPickupCandidates(anyLong(), any())).thenAnswer(c -> new PageImpl<>(employeeRows.values().stream()
            .filter(e -> e.getBranch().getId().equals(c.getArgument(0)) && e.getPosition() == EmployeePosition.COURIER_DRIVER && e.getStatus() == EmployeeStatus.ACTIVE
                && e.getUser().getStatus() == UserStatus.ACTIVE && roleRows.get(e.getUser().getId()).contains("ROLE_SHIPPER")).toList()));
        when(shipments.findForLifecycle(31L)).thenAnswer(c -> Optional.of(s));
        when(shipments.saveAndFlush(any())).thenAnswer(c -> { ReflectionTestUtils.setField(s, "version", s.getVersion() + 1); return s; });
        when(assignments.findByShipmentIdAndAssignmentTypeAndStatusIn(eq(31L), any(), any())).thenAnswer(c -> tasks.values().stream()
            .filter(a -> a.getAssignmentType() == c.getArgument(1) && ((Collection<?>) c.getArgument(2)).contains(a.getStatus())).toList());
        when(assignments.findByShipmentIdAndEmployeeIdAndAssignmentTypeAndStatusIn(eq(31L), anyLong(), any(), any())).thenAnswer(c -> tasks.values().stream()
            .filter(a -> a.getEmployee().getId().equals(c.getArgument(1)) && a.getAssignmentType() == c.getArgument(2) && ((Collection<?>) c.getArgument(3)).contains(a.getStatus())).toList());
        when(assignments.findShipmentIdForOwner(anyLong(), anyLong(), any())).thenAnswer(c -> Optional.ofNullable(tasks.get(c.getArgument(0)))
            .filter(a -> a.getEmployee().getId().equals(c.getArgument(1)) && a.getAssignmentType() == c.getArgument(2)).map(a -> 31L));
        when(assignments.findByIdAndEmployeeIdAndAssignmentType(anyLong(), anyLong(), any())).thenAnswer(c -> Optional.ofNullable(tasks.get(c.getArgument(0)))
            .filter(a -> a.getEmployee().getId().equals(c.getArgument(1)) && a.getAssignmentType() == c.getArgument(2)));
        when(assignments.findByShipmentId(eq(31L), any())).thenAnswer(c -> new PageImpl<>(List.copyOf(tasks.values())));
        when(assignments.findByEmployeeIdAndAssignmentType(anyLong(), any(), any())).thenAnswer(c -> new PageImpl<>(tasks.values().stream()
            .filter(a -> a.getEmployee().getId().equals(c.getArgument(0)) && a.getAssignmentType() == c.getArgument(1)).toList()));
        when(assignments.existsByShipmentIdAndStatusIn(eq(31L), any())).thenAnswer(c -> tasks.values().stream().anyMatch(a -> ((Collection<?>) c.getArgument(1)).contains(a.getStatus())));
        when(assignments.saveAndFlush(any())).thenAnswer(c -> { ShipmentAssignment a = c.getArgument(0); if (a.getId() == null) { a.setId(41L + tasks.size()); } tasks.put(a.getId(), a); return a; });
        when(dispatches.findByAssignmentId(anyLong())).thenAnswer(c -> Optional.ofNullable(dispatchRows.get(c.getArgument(0))));
        when(dispatches.findFirstByShipmentIdAndAcceptedAtIsNotNullOrderByAcceptedAtDescIdDesc(31L)).thenAnswer(c -> dispatchRows.values().stream()
            .filter(d -> d.getAcceptedAt() != null).reduce((a,b) -> b));
        when(dispatches.saveAndFlush(any())).thenAnswer(c -> { DeliveryDispatch d = c.getArgument(0); if (d.getId() == null) { d.setId(51L + dispatchRows.size()); } dispatchRows.put(d.getAssignment().getId(), d); return d; });
        when(attempts.countByShipmentId(31L)).thenAnswer(c -> (long) attemptRows.size());
        when(attempts.maxAttemptNumber(31L)).thenAnswer(c -> attemptRows.stream().mapToInt(DeliveryAttempt::getAttemptNumber).max().orElse(0));
        when(attempts.saveAndFlush(any())).thenAnswer(c -> { DeliveryAttempt a = c.getArgument(0); a.setId(61L + attemptRows.size()); attemptRows.add(a); return a; });
        when(attempts.findByShipmentId(eq(31L), any())).thenAnswer(c -> new PageImpl<>(List.copyOf(attemptRows)));
        when(attempts.findByAssignmentId(anyLong(), any())).thenAnswer(c -> new PageImpl<>(attemptRows.stream().filter(a -> a.getAssignment() != null && a.getAssignment().getId().equals(c.getArgument(0))).toList()));
        when(proofs.existsByShipmentId(31L)).thenAnswer(c -> !proofRows.isEmpty()); when(proofs.findByShipmentId(31L)).thenAnswer(c -> proofRows.stream().findFirst());
        when(proofs.saveAndFlush(any())).thenAnswer(c -> { DeliveryProof p = c.getArgument(0); p.setId(71L); proofRows.add(p); return p; });
        when(recoveries.existsByShipmentIdAndStatus(eq(31L), any())).thenAnswer(c -> recoveryRows.stream().anyMatch(r -> r.getStatus() == c.getArgument(1)));
        when(recoveries.existsByAssignmentIdAndStatus(anyLong(), any())).thenAnswer(c -> recoveryRows.stream().anyMatch(r -> r.getAssignment().getId().equals(c.getArgument(0)) && r.getStatus() == c.getArgument(1)));
        when(recoveries.findByIdAndShipmentId(anyLong(), eq(31L))).thenAnswer(c -> recoveryRows.stream().filter(r -> r.getId().equals(c.getArgument(0))).findFirst());
        when(recoveries.saveAndFlush(any())).thenAnswer(c -> { DeliveryRecovery r = c.getArgument(0); if (r.getId() == null) { r.setId(81L + recoveryRows.size()); recoveryRows.add(r); } return r; });
        when(recoveries.findByShipmentId(eq(31L), any())).thenAnswer(c -> new PageImpl<>(List.copyOf(recoveryRows)));
        when(recoveries.findByAssignmentId(anyLong(), any())).thenAnswer(c -> new PageImpl<>(recoveryRows.stream().filter(r -> r.getAssignment().getId().equals(c.getArgument(0))).toList()));
        when(decisions.existsByShipmentIdAndAfterAttemptNumber(eq(31L), anyInt())).thenAnswer(c -> decisionRows.stream().anyMatch(d -> d.getAfterAttemptNumber().equals(c.getArgument(1))));
        when(decisions.saveAndFlush(any())).thenAnswer(c -> { RedeliveryDecision d = c.getArgument(0); d.setId(91L + decisionRows.size()); decisionRows.add(d); return d; });
        when(decisions.findByShipmentId(eq(31L), any())).thenAnswer(c -> new PageImpl<>(List.copyOf(decisionRows)));
        when(returns.findByShipmentId(31L)).thenAnswer(c -> Optional.ofNullable(returnRequest)); when(returns.existsByShipmentId(31L)).thenAnswer(c -> returnRequest != null);
        when(returns.saveAndFlush(any())).thenAnswer(c -> { returnRequest = c.getArgument(0); returnRequest.setId(101L); return returnRequest; });
        when(returnReceipts.existsByReturnRequestId(101L)).thenAnswer(c -> senderReceipt != null);
        when(returnReceipts.findByReturnRequestId(101L)).thenAnswer(c -> Optional.ofNullable(senderReceipt));
        when(returnReceipts.saveAndFlush(any())).thenAnswer(c -> { senderReceipt = c.getArgument(0); senderReceipt.setId(111L); return senderReceipt; });
        when(events.saveAndFlush(any())).thenAnswer(c -> { ShipmentEvent e = c.getArgument(0); eventRows.add(e); return e; });
        when(events.findByShipmentId(eq(31L), any())).thenAnswer(c -> new PageImpl<>(List.copyOf(eventRows)));
        when(events.existsByShipmentIdAndFromStatusAndToStatusAndEventTitle(eq(31L), any(), any(), anyString())).thenAnswer(c -> localClassification);
        when(intakes.findByShipmentId(31L)).thenAnswer(c -> Optional.of(intake(s)));
        when(tripReceipts.countForwardProofAt(eq(31L), anyLong())).thenAnswer(c -> forwardReceipt && s.getDestinationBranch().getId().equals(c.getArgument(1)) ? 1L : 0L);
        when(tripReceipts.countReturnProofAt(eq(101L), anyLong())).thenAnswer(c -> tripReceiptRows.stream().filter(r -> r.getItem().getReturnRequest() != null && r.getBranch().getId().equals(c.getArgument(1))).count());
        when(tripReceipts.existsByItemId(anyLong())).thenAnswer(c -> tripReceiptRows.stream().anyMatch(r -> r.getItem().getId().equals(c.getArgument(0))));
        when(tripReceipts.saveAndFlush(any())).thenAnswer(c -> { TripReceipt r = c.getArgument(0); r.setId(121L + tripReceiptRows.size()); tripReceiptRows.add(r); return r; });
        when(tripItems.findByShipmentIdAndActiveTrue(31L)).thenAnswer(c -> tripItemRows.values().stream().filter(i -> Boolean.TRUE.equals(i.getActive())).findFirst());
        when(tripItems.countByTripId(anyLong())).thenAnswer(c -> tripItemRows.values().stream().filter(i -> i.getTrip().getId().equals(c.getArgument(0))).count());
        when(tripItems.countByTripIdAndStatus(anyLong(), any())).thenAnswer(c -> tripItemRows.values().stream().filter(i -> i.getTrip().getId().equals(c.getArgument(0)) && i.getStatus() == c.getArgument(1)).count());
        when(tripItems.findByTripIdAndShipmentId(anyLong(), eq(31L))).thenAnswer(c -> tripItemRows.values().stream().filter(i -> i.getTrip().getId().equals(c.getArgument(0))).findFirst());
        when(tripItems.findShipmentIds(anyLong())).thenAnswer(c -> tripItemRows.values().stream().filter(i -> i.getTrip().getId().equals(c.getArgument(0))).map(i -> 31L).toList());
        when(tripItems.findShipmentId(anyLong(), anyLong())).thenAnswer(c -> Optional.ofNullable(tripItemRows.get(c.getArgument(1))).filter(i -> i.getTrip().getId().equals(c.getArgument(0))).map(i -> 31L));
        when(tripItems.findByIdAndTripId(anyLong(), anyLong())).thenAnswer(c -> Optional.ofNullable(tripItemRows.get(c.getArgument(0))).filter(i -> i.getTrip().getId().equals(c.getArgument(1))));
        when(tripItems.saveAndFlush(any())).thenAnswer(c -> { TransportTripItem i = c.getArgument(0); if (i.getId() == null) { i.setId(131L + tripItemRows.size()); } tripItemRows.put(i.getId(), i); return i; });
        when(trips.findForUpdate(anyLong())).thenAnswer(c -> Optional.ofNullable(tripRows.get(c.getArgument(0))));
        when(trips.saveAndFlush(any())).thenAnswer(c -> { TransportTrip t = c.getArgument(0); if (t.getId() == null) { t.setId(1L + tripRows.size()); } ReflectionTestUtils.setField(t, "version", t.getVersion() == null ? 0L : t.getVersion() + 1); tripRows.put(t.getId(), t); return t; });
        routeRows.put(4L, route(4, destination, h2, TransportLeg.COLLECTION)); routeRows.put(5L, route(5, h2, h1, TransportLeg.LINEHAUL)); routeRows.put(6L, route(6, h1, origin, TransportLeg.DISTRIBUTION));
        when(routes.findById(anyLong())).thenAnswer(c -> Optional.ofNullable(routeRows.get(c.getArgument(0))));
        when(dispatches.findManagedShipment(eq(31L), anyLong())).thenAnswer(c -> canView(c.getArgument(1)) ? Optional.of(s) : Optional.empty());
        when(dispatches.findManaged(anyLong(), any(), any(), any())).thenAnswer(c -> canView(c.getArgument(0)) && (c.getArgument(1) == null || s.getStatus() == c.getArgument(1)) ? new PageImpl<>(List.of(s)) : Page.empty());
        access = new LogisticsAccessService(users, roles, employees, memberships, branches, assignments);
        custody = new DeliveryCustodyService(tripReceipts, intakes, dispatches, recoveries, tripItems, events);
        service = new LastMileService(shipments, assignments, events, attempts, dispatches, proofs, recoveries, decisions, returns, returnReceipts, tripItems, tripReceipts, access, custody, clock);
        read = new DeliveryReadService(access, dispatches, assignments, employees, attempts, recoveries, decisions, returns, returnReceipts, proofs, events, custody);
        transport = new TransportTripService(routes, trips, tripItems, tripReceipts, discrepancies, tripEvents, shipments, events, intakes, assignments, access, clock, returns, custody);
        as(manager);
    }
    boolean canView(Long actorId) { return managed.getOrDefault(actorId, Set.of()).contains(destination.getId()) || (returnRequest != null && managed.getOrDefault(actorId, Set.of()).contains(origin.getId())); }
    public void as(User user) { SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(user.getEmail(), null, roleRows.get(user.getId()).toArray(String[]::new))); }
    public ShipmentAssignment current() { return tasks.values().stream().filter(a -> LogisticsAccessService.ACTIVE_ASSIGNMENTS.contains(a.getStatus())).findFirst().orElseThrow(); }
    public void assign() { as(manager); service.assign(new Assign(31L, s.getVersion(), 21L, null, null)); }
    public void prepare() { assign(); service.prepare(new Prepare(31L, s.getVersion(), current().getId(), true, "OUTBOUND", null)); }
    public void accept() { prepare(); as(courier.getUser()); service.accept(new Action(current().getId(), s.getVersion(), true, "ACCEPTED")); }
    public void start() { accept(); startAgain(); }
    public void startAgain() { as(courier.getUser()); service.start(new Action(current().getId(), s.getVersion(), true, "START")); }
    public void fail() { service.fail(new Fail(current().getId(), s.getVersion(), true, DeliveryFailureCode.ABSENT, "Recipient absent", null)); }
    public Complete complete() { return new Complete(current().getId(), s.getVersion(), true, DeliveryRecipientType.RECIPIENT, "Actual recipient", "POD-REFERENCE", "Structured confirmation"); }
    public void requestReturn() { as(manager); service.initiateReturn(new ReturnStart(31L, s.getVersion(), "Return approved")); }
    public void recover() { as(courier.getUser()); service.requestRecovery(new Recover(current().getId(), s.getVersion(), "At branch for handoff")); as(manager); service.reviewRecovery(new ReviewRecovery(31L, s.getVersion(), recoveryRows.getLast().getId(), true, true, "RECOVERY-RECEIPT", null)); }
    public TransportTrip returnLeg(long routeId, boolean receive) {
        TransportRoute r = routeRows.get(routeId); as(userRows.get(1000 + r.getOrigin().getId()));
        long id = transport.create(new TripCommands.Create(routeId, UUID.randomUUID().toString().replace("-", ""))); TransportTrip t = tripRows.get(id);
        transport.addItems(new TripCommands.Manifest(id, t.getVersion(), List.of(31L))); transport.seal(new TripCommands.Change(id, t.getVersion(), true, "SEAL", null));
        transport.depart(new TripCommands.Change(id, t.getVersion(), true, "DEPART", null));
        if (receive) { receiveReturnLeg(t); } return t;
    }
    public void receiveReturnLeg(TransportTrip t) {
        as(userRows.get(1000 + t.getDestination().getId())); transport.arrive(new TripCommands.Change(t.getId(), t.getVersion(), true, "ARRIVE", null));
        long item = tripItemRows.values().stream().filter(i -> i.getTrip().getId().equals(t.getId())).findFirst().orElseThrow().getId();
        transport.receive(new TripCommands.Receive(t.getId(), t.getVersion(), item, true, "RETURN-LEG-RECEIPT", null, false));
        transport.close(new TripCommands.Change(t.getId(), t.getVersion(), true, "CLOSE", null));
    }
}
