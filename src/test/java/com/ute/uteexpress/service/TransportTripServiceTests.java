package com.ute.uteexpress.service;

import com.ute.uteexpress.dto.transport.TripCommands.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;

import static com.ute.uteexpress.transport.TransportFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Stateful repository boundaries and Spring transaction advice, not JDBC/SQL Server integration. */
class TransportTripServiceTests {
    final TransportRouteRepository routes = mock(TransportRouteRepository.class);
    final TransportTripRepository trips = mock(TransportTripRepository.class);
    final TransportTripItemRepository items = mock(TransportTripItemRepository.class);
    final TripReceiptRepository receipts = mock(TripReceiptRepository.class);
    final TripDiscrepancyRepository discrepancies = mock(TripDiscrepancyRepository.class);
    final TransportTripEventRepository events = mock(TransportTripEventRepository.class);
    final ShipmentRepository shipments = mock(ShipmentRepository.class);
    final ShipmentEventRepository shipmentEvents = mock(ShipmentEventRepository.class);
    final ShipmentIntakeRepository intakes = mock(ShipmentIntakeRepository.class);
    final ShipmentAssignmentRepository assignments = mock(ShipmentAssignmentRepository.class);
    final LogisticsAccessService access = mock(LogisticsAccessService.class);
    final TransportTripService service = new TransportTripService(routes, trips, items, receipts, discrepancies, events,
        shipments, shipmentEvents, intakes, assignments, access, Clock.fixed(Instant.parse("2026-10-23T05:00:00Z"), ZoneOffset.UTC));
    final Branch origin = point(11, BranchType.LOCAL_BRANCH), destination = point(12, BranchType.LOCAL_BRANCH);
    final Branch h1 = point(101, BranchType.CENTRAL_HUB), h2 = point(102, BranchType.CENTRAL_HUB);
    final Map<Long, TransportTrip> tripRows = new LinkedHashMap<>();
    final Map<Long, TransportTripItem> itemRows = new LinkedHashMap<>();
    final Map<Long, TripReceipt> receiptRows = new LinkedHashMap<>();
    final Map<Long, TripDiscrepancy> issueRows = new LinkedHashMap<>();
    final Map<Long, Shipment> parcels = new LinkedHashMap<>();
    final List<ShipmentEvent> history = new ArrayList<>();
    final List<TransportTripEvent> tripHistory = new ArrayList<>();
    Branch acting;
    TransportTrip trip;
    Shipment s;
    @BeforeEach void setup() {
        origin.setParentHub(h1); destination.setParentHub(h2); acting = origin;
        when(access.requireManager()).thenAnswer(c -> actor(acting));
        when(access.requireManagerForBranch(anyLong())).thenAnswer(c -> {
            if (!acting.getId().equals(c.getArgument(0))) { throw new AccessDeniedException("Forbidden"); } return actor(acting);
        });
        when(access.requireIntakeEmployee(anyLong())).thenAnswer(c -> {
            if (!acting.getId().equals(c.getArgument(0))) { throw new AccessDeniedException("Forbidden"); } return employee(acting);
        });
        when(routes.findById(1L)).thenReturn(Optional.of(route(1, origin, h1, TransportLeg.COLLECTION)));
        when(routes.findById(2L)).thenReturn(Optional.of(route(2, h1, h2, TransportLeg.LINEHAUL)));
        when(routes.findById(3L)).thenReturn(Optional.of(route(3, h2, destination, TransportLeg.DISTRIBUTION)));
        when(trips.findByRequestTokenAndCreatedById(anyString(), anyLong())).thenAnswer(c -> tripRows.values().stream()
            .filter(t -> t.getRequestToken().equals(c.getArgument(0)) && t.getCreatedBy().getId().equals(c.getArgument(1))).findFirst());
        when(trips.findForUpdate(anyLong())).thenAnswer(c -> Optional.ofNullable(tripRows.get(c.getArgument(0))));
        when(trips.saveAndFlush(any())).thenAnswer(c -> {
            TransportTrip t = c.getArgument(0); if (t.getId() == null) { t.setId(1L + tripRows.size()); }
            ReflectionTestUtils.setField(t, "version", t.getVersion() == null ? 0L : t.getVersion() + 1);
            tripRows.put(t.getId(), t); return t;
        });
        when(shipments.findForLifecycle(anyLong())).thenAnswer(c -> Optional.ofNullable(parcels.get(c.getArgument(0))));
        when(shipments.saveAndFlush(any())).thenAnswer(c -> {
            Shipment p = c.getArgument(0); ReflectionTestUtils.setField(p, "version", p.getVersion() + 1); return p;
        });
        when(intakes.findByShipmentId(anyLong())).thenAnswer(c -> Optional.of(intake(parcels.get(c.getArgument(0)))));
        when(items.findByShipmentIdAndActiveTrue(anyLong())).thenAnswer(c -> itemRows.values().stream()
            .filter(i -> i.getShipment().getId().equals(c.getArgument(0)) && Boolean.TRUE.equals(i.getActive())).findFirst());
        when(items.findByTripIdAndShipmentId(anyLong(), anyLong())).thenAnswer(c -> itemRows.values().stream()
            .filter(i -> i.getTrip().getId().equals(c.getArgument(0)) && i.getShipment().getId().equals(c.getArgument(1))).findFirst());
        when(items.findByIdAndTripId(anyLong(), anyLong())).thenAnswer(c -> Optional.ofNullable(itemRows.get(c.getArgument(0)))
            .filter(i -> i.getTrip().getId().equals(c.getArgument(1))));
        when(items.findShipmentId(anyLong(), anyLong())).thenAnswer(c -> Optional.ofNullable(itemRows.get(c.getArgument(1)))
            .filter(i -> i.getTrip().getId().equals(c.getArgument(0))).map(i -> i.getShipment().getId()));
        when(items.findShipmentIds(anyLong())).thenAnswer(c -> itemRows.values().stream().filter(i -> i.getTrip().getId().equals(c.getArgument(0)))
            .map(i -> i.getShipment().getId()).sorted().toList());
        when(items.countByTripId(anyLong())).thenAnswer(c -> itemRows.values().stream().filter(i -> i.getTrip().getId().equals(c.getArgument(0))).count());
        when(items.countByTripIdAndStatus(anyLong(), any())).thenAnswer(c -> itemRows.values().stream()
            .filter(i -> i.getTrip().getId().equals(c.getArgument(0)) && i.getStatus() == c.getArgument(1)).count());
        when(items.saveAndFlush(any())).thenAnswer(c -> { TransportTripItem i = c.getArgument(0); if (i.getId() == null) { i.setId(101L + itemRows.size()); } itemRows.put(i.getId(), i); return i; });
        when(receipts.existsByItemId(anyLong())).thenAnswer(c -> receiptRows.values().stream().anyMatch(r -> r.getItem().getId().equals(c.getArgument(0))));
        when(receipts.countProofAt(anyLong(), anyLong())).thenAnswer(c -> receiptRows.values().stream()
            .filter(r -> r.getItem().getShipment().getId().equals(c.getArgument(0)) && r.getBranch().getId().equals(c.getArgument(1))).count());
        when(receipts.saveAndFlush(any())).thenAnswer(c -> { TripReceipt r = c.getArgument(0); r.setId(201L + receiptRows.size()); receiptRows.put(r.getId(), r); return r; });
        when(discrepancies.findByItemIdAndStatus(anyLong(), any())).thenAnswer(c -> issueRows.values().stream()
            .filter(d -> d.getItem() != null && d.getItem().getId().equals(c.getArgument(0)) && d.getStatus() == c.getArgument(1)).toList());
        when(discrepancies.existsByItemIdAndStatus(anyLong(), any())).thenAnswer(c -> issueRows.values().stream()
            .anyMatch(d -> d.getItem() != null && d.getItem().getId().equals(c.getArgument(0)) && d.getStatus() == c.getArgument(1)));
        when(discrepancies.countByTripIdAndStatus(anyLong(), any())).thenAnswer(c -> issueRows.values().stream()
            .filter(d -> d.getTrip().getId().equals(c.getArgument(0)) && d.getStatus() == c.getArgument(1)).count());
        when(discrepancies.findByIdAndTripId(anyLong(), anyLong())).thenAnswer(c -> Optional.ofNullable(issueRows.get(c.getArgument(0)))
            .filter(d -> d.getTrip().getId().equals(c.getArgument(1))));
        when(discrepancies.saveAndFlush(any())).thenAnswer(c -> { TripDiscrepancy d = c.getArgument(0); if (d.getId() == null) { d.setId(301L + issueRows.size()); } issueRows.put(d.getId(), d); return d; });
        when(shipmentEvents.saveAndFlush(any())).thenAnswer(c -> { ShipmentEvent e = c.getArgument(0); history.add(e); return e; });
        when(events.saveAndFlush(any())).thenAnswer(c -> { TransportTripEvent e = c.getArgument(0); tripHistory.add(e); return e; });
        s = parcel(31, origin, destination); parcels.put(31L, s); trip = create(1L);
    }
    TransportTrip create(Long routeId) { return tripRows.get(service.create(new Create(routeId, String.format("%032x", tripRows.size() + 1)))); }
    Change change() { return new Change(trip.getId(), trip.getVersion(), true, "ACTUAL-REF", "Checked"); }
    void add(long... ids) { service.addItems(new Manifest(trip.getId(), trip.getVersion(), Arrays.stream(ids).boxed().toList())); }
    void departure() { add(31); service.seal(change()); service.depart(change()); }
    void arrived() { departure(); acting = trip.getDestination(); service.arrive(change()); }
    TransportTripItem currentItem() { return itemRows.values().stream().filter(i -> i.getTrip().getId().equals(trip.getId())).findFirst().orElseThrow(); }
    Receive receiveCommand(boolean resolve) { return new Receive(trip.getId(), trip.getVersion(), currentItem().getId(), true, "RECEIPT", "Actual checked package", resolve); }
    void receive() { service.receive(receiveCommand(false)); }

    @Test void validCreationSnapshotsConfiguredRouteAndRetryTokenReturnsSameTrip() {
        assertEquals(TripStatus.DRAFT, trip.getStatus()); assertEquals(35, trip.getCode().length()); assertSame(origin, trip.getOrigin());
        assertEquals(trip.getId(), service.create(new Create(1L, trip.getRequestToken()))); assertEquals(1, tripRows.size());
        assertThrows(LogisticsRuleException.class, () -> service.create(new Create(2L, trip.getRequestToken())));
    }
    @Test void wrongPointManagerCannotCreateOrPlan() {
        acting = destination; assertThrows(AccessDeniedException.class, () -> create(1L));
        assertThrows(AccessDeniedException.class, () -> add(31)); assertTrue(itemRows.isEmpty());
    }
    @Test void disabledRouteOrWrongHubRefused() {
        trip.getRoute().setActive(false); assertThrows(LogisticsRuleException.class, () -> add(31));
        trip.getRoute().setActive(true); origin.setParentHub(h2); assertThrows(LogisticsRuleException.class, () -> add(31));
    }
    @Test void multipleParcelsAreReservedInDeterministicOrderAndAudited() {
        parcels.put(32L, parcel(32, origin, destination)); add(32, 31);
        assertEquals(List.of(31L, 32L), itemRows.values().stream().map(i -> i.getShipment().getId()).toList()); assertEquals(2, history.size());
        assertEquals(ShipmentStatus.AT_ORIGIN_BRANCH, s.getStatus()); assertSame(origin, s.getCurrentBranch());
    }
    @Test void allBatchInputsValidatedBeforeWritingAnyManifestItem() {
        parcels.put(32L, parcel(32, origin, destination)); parcels.get(32L).setStatus(ShipmentStatus.CONFIRMED);
        assertThrows(LogisticsRuleException.class, () -> add(31, 32)); assertTrue(itemRows.isEmpty()); assertTrue(history.isEmpty());
    }
    @Test void noIntakeLegacyOrIneligibleParcelCannotEnterManifest() {
        when(intakes.findByShipmentId(31L)).thenReturn(Optional.empty()); assertThrows(LogisticsRuleException.class, () -> add(31));
        when(intakes.findByShipmentId(31L)).thenReturn(Optional.of(intake(s))); s.setShipmentMethod(null);
        assertThrows(LogisticsRuleException.class, () -> add(31)); assertTrue(itemRows.isEmpty());
    }
    @Test void duplicateBatchAndExistingManifestAreRejected() {
        assertThrows(LogisticsRuleException.class, () -> add(31, 31)); add(31);
        assertThrows(LogisticsRuleException.class, () -> add(31)); assertEquals(1, itemRows.size());
    }
    @Test void anotherDraftCannotReserveSameParcel() {
        add(31); trip = create(1L); assertThrows(LogisticsRuleException.class, () -> add(31)); assertEquals(1, itemRows.size());
    }
    @Test void sealedManifestCannotChangeOrDepartWithoutExpectedCountAndValidCustody() {
        add(31); service.seal(change()); assertThrows(LogisticsRuleException.class, () -> add(31));
        trip.setExpectedCount(2); assertThrows(LogisticsRuleException.class, () -> service.depart(change())); trip.setExpectedCount(1);
        s.setStatus(ShipmentStatus.CONFIRMED); assertThrows(LogisticsRuleException.class, () -> service.depart(change()));
    }
    @Test void emptyDraftCannotSealOrDepart() {
        assertThrows(LogisticsRuleException.class, () -> service.seal(change()));
        assertThrows(LogisticsRuleException.class, () -> service.depart(change()));
    }
    @Test void departureAndVehicleArrivalDoNotReceiveParcels() {
        arrived(); assertEquals(ShipmentStatus.IN_TRANSIT, s.getStatus()); assertNull(s.getCurrentBranch()); assertTrue(receiptRows.isEmpty());
        assertEquals(TripStatus.RECONCILING, trip.getStatus()); assertEquals(ShipmentStatus.IN_TRANSIT, history.getLast().getToStatus());
    }
    @Test void receiptMustFollowActualDepartureArrivalAndMatchManifest() {
        add(31); acting = h1;
        assertThrows(LogisticsRuleException.class, () -> service.receive(receiveCommand(false)));
        acting = origin; service.seal(change()); service.depart(change()); acting = h1;
        assertThrows(LogisticsRuleException.class, () -> service.receive(receiveCommand(false))); service.arrive(change());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.receive(new Receive(trip.getId(), trip.getVersion(), 999L, true, "REF", null, false)));
    }
    @Test void receiptByWrongPointOrDeliveringActorIsForbidden() {
        arrived(); acting = destination; assertThrows(AccessDeniedException.class, this::receive);
        acting = h1; when(access.requireManagerForBranch(h1.getId())).thenReturn(trip.getDepartedBy());
        assertThrows(AccessDeniedException.class, this::receive); assertTrue(receiptRows.isEmpty());
    }
    @Test void independentEmployeeGuardAlsoBlocksSameEmployeeUnderDifferentActor() {
        arrived(); Employee same = employee(h1); same.setId(trip.getDepartedEmployee().getId());
        when(access.requireIntakeEmployee(h1.getId())).thenReturn(same); assertThrows(AccessDeniedException.class, this::receive);
    }
    @Test void eachPhysicalReceiptUpdatesPointAndReleasesReservationExactlyOnce() {
        arrived(); service.receive(receiveCommand(false)); assertEquals(ShipmentStatus.AT_ORIGIN_HUB, s.getStatus()); assertSame(h1, s.getCurrentBranch());
        assertEquals(TripItemStatus.RECEIVED, currentItem().getStatus()); assertFalse(currentItem().getActive()); assertEquals(1, receiptRows.size());
        assertThrows(LogisticsRuleException.class, this::receive); assertEquals(1, receiptRows.size());
    }
    @Test void missingParcelCannotBeReceivedAutomaticallyForwardedOrClosed() {
        arrived(); service.recordDiscrepancy(new Discrepancy(trip.getId(), trip.getVersion(), currentItem().getId(), TripDiscrepancyKind.MISSING, "COUNT", "Missing parcel"));
        assertEquals(ShipmentStatus.IN_TRANSIT, s.getStatus()); assertNull(s.getCurrentBranch()); assertTrue(currentItem().getActive());
        assertThrows(LogisticsRuleException.class, () -> service.close(change()));
        assertThrows(LogisticsRuleException.class, () -> service.receive(receiveCommand(false)));
        TransportTrip previous = trip; trip = create(2L); assertThrows(LogisticsRuleException.class, () -> add(31)); trip = previous;
        service.receive(receiveCommand(true)); assertEquals(TripDiscrepancyStatus.RESOLVED, issueRows.values().iterator().next().getStatus());
        service.close(change()); assertEquals(TripStatus.CLOSED, trip.getStatus());
    }
    @ParameterizedTest @ValueSource(strings = {"DAMAGED", "REJECTED"})
    void damagedOrRejectedNeedsExplicitRecoveryReceipt(String kind) {
        arrived(); service.recordDiscrepancy(new Discrepancy(trip.getId(), trip.getVersion(), currentItem().getId(), TripDiscrepancyKind.valueOf(kind), "INSPECT", "Needs inspection"));
        assertTrue(receiptRows.isEmpty()); assertThrows(LogisticsRuleException.class, () -> service.receive(receiveCommand(false)));
        service.receive(receiveCommand(true)); assertEquals(1, receiptRows.size());
    }
    @Test void unexpectedParcelIsAuditOnlyAndMustBeResolvedBeforeClosing() {
        arrived(); service.recordDiscrepancy(new Discrepancy(trip.getId(), trip.getVersion(), null, TripDiscrepancyKind.UNEXPECTED, "OBSERVED", "Wrong label"));
        assertEquals(1, itemRows.size()); assertTrue(receiptRows.isEmpty()); receive(); assertThrows(LogisticsRuleException.class, () -> service.close(change()));
        service.resolveUnexpected(new Resolve(trip.getId(), trip.getVersion(), 301L, true, "RESOLVE", "Quarantined and reconciled")); service.close(change());
        assertEquals(TripStatus.CLOSED, trip.getStatus());
    }
    @Test void ordinaryIssueCannotBeDismissedWithoutPhysicalReceipt() {
        arrived(); service.recordDiscrepancy(new Discrepancy(trip.getId(), trip.getVersion(), currentItem().getId(), TripDiscrepancyKind.MISSING, "REF", "Missing"));
        assertThrows(LogisticsRuleException.class, () -> service.resolveUnexpected(new Resolve(trip.getId(), trip.getVersion(), 301L, true, "REF", "Dismiss")));
        assertTrue(receiptRows.isEmpty()); assertEquals(TripDiscrepancyStatus.OPEN, issueRows.get(301L).getStatus());
    }
    @Test void threeLegsFinishOnlyAfterDestinationBranchPhysicalReceiptAndKeepFees() {
        var total = s.getTotalFee(); var shipping = s.getShippingFee(); var insurance = s.getInsuranceFee();
        arrived(); receive(); service.close(change()); trip = create(2L); arrived(); receive();
        assertEquals(ShipmentStatus.AT_DESTINATION_HUB, s.getStatus()); service.close(change()); trip = create(3L); arrived();
        assertEquals(ShipmentStatus.IN_TRANSIT, s.getStatus()); receive(); assertEquals(ShipmentStatus.AT_DESTINATION_BRANCH, s.getStatus());
        assertSame(destination, s.getCurrentBranch()); service.close(change()); assertEquals(3, receiptRows.size());
        assertEquals(total, s.getTotalFee()); assertEquals(shipping, s.getShippingFee()); assertEquals(insurance, s.getInsuranceFee());
        assertEquals(1, history.stream().filter(e -> e.getToStatus() == ShipmentStatus.AT_DESTINATION_BRANCH).count());
    }
    @Test void homePickupWithCompletedOriginHandoffUsesSameTransportFlow() {
        s.setShipmentMethod(ShipmentMethod.HOME_PICKUP); arrived(); receive();
        assertEquals(ShipmentStatus.AT_ORIGIN_HUB, s.getStatus()); assertEquals(1, receiptRows.size());
    }
    @Test void incompleteHomeOriginAssignmentCannotBeUsedAsCustodyProof() {
        s.setShipmentMethod(ShipmentMethod.HOME_PICKUP); ShipmentIntake invalid = intake(s);
        invalid.getPickupAssignment().setStatus(AssignmentStatus.IN_PROGRESS);
        when(intakes.findByShipmentId(31L)).thenReturn(Optional.of(invalid));
        assertThrows(LogisticsRuleException.class, () -> add(31)); assertTrue(itemRows.isEmpty());
    }
    @Test void sameHubUsesTwoActualLegsWithoutFakeLinehaulOrExtraReceipt() {
        destination.setParentHub(h1); s.setDestinationHub(h1);
        when(routes.findById(3L)).thenReturn(Optional.of(route(3, h1, destination, TransportLeg.DISTRIBUTION)));
        arrived(); receive(); assertEquals(ShipmentStatus.AT_DESTINATION_HUB, s.getStatus()); service.close(change());
        trip = create(3L); arrived(); receive(); assertEquals(ShipmentStatus.AT_DESTINATION_BRANCH, s.getStatus());
        assertEquals(2, tripRows.size()); assertEquals(2, receiptRows.size());
    }
    @Test void sameBranchUsesExistingIntakeAndClassificationEventWithoutTripOrReceipt() {
        s.setDestinationBranch(origin); s.setDestinationHub(h1); int count = tripRows.size();
        service.readyAtSameBranch(new LocalReady(31L, 0L, true, "SORT", null));
        assertEquals(ShipmentStatus.AT_DESTINATION_BRANCH, s.getStatus()); assertSame(origin, s.getCurrentBranch());
        assertEquals(count, tripRows.size()); assertTrue(receiptRows.isEmpty()); assertTrue(itemRows.isEmpty());
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> service.readyAtSameBranch(new LocalReady(31L, 0L, true, "SORT", null)));
    }
    @Test void versionAndPhysicalAcknowledgmentAreRequired() {
        add(31); assertThrows(ObjectOptimisticLockingFailureException.class, () -> service.seal(new Change(trip.getId(), 0L, true, "REF", null)));
        assertThrows(LogisticsRuleException.class, () -> service.seal(new Change(trip.getId(), trip.getVersion(), false, "REF", null)));
    }
    @ParameterizedTest @ValueSource(strings = {"receipt", "item", "shipmentEvent", "tripEvent"})
    void receiptFailureTriggersSpringRollbackDecision(String at) {
        arrived(); var failure = new DataIntegrityViolationException("synthetic failure");
        if (at.equals("receipt")) { doThrow(failure).when(receipts).saveAndFlush(any()); }
        if (at.equals("item")) { doThrow(failure).when(items).saveAndFlush(any()); }
        if (at.equals("shipmentEvent")) { doThrow(failure).when(shipmentEvents).saveAndFlush(any()); }
        if (at.equals("tripEvent")) { doThrow(failure).when(events).saveAndFlush(any()); }
        Tx tx = new Tx(null); TransportTripService proxy = transactional(tx);
        assertThrows(DataIntegrityViolationException.class, () -> proxy.receive(receiveCommand(false)));
        assertEquals(1, tx.rollbacks); assertEquals(0, tx.commits);
    }
    @Test void twoConcurrentReceiptsHaveOneWinnerUnderTripLockModel() throws Exception {
        arrived(); Long id = trip.getId(), version = trip.getVersion(), item = currentItem().getId(); ReentrantLock lock = new ReentrantLock();
        when(trips.findForUpdate(id)).thenAnswer(c -> { if (!lock.tryLock(5, TimeUnit.SECONDS)) { throw new IllegalStateException("test timeout"); } return Optional.of(trip); });
        TransportTripService proxy = transactional(new Tx(lock)); ExecutorService workers = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int n = 0; n < 2; n++) { futures.add(workers.submit(() -> {
                if (!start.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("test timeout"); }
                try { proxy.receive(new Receive(id, version, item, true, "REF", null, false)); return true; }
                catch (ObjectOptimisticLockingFailureException conflict) { return false; }
            })); }
            start.countDown(); int successes = 0; for (var f : futures) { if (f.get(10, TimeUnit.SECONDS)) { successes++; } }
            assertEquals(1, successes); assertEquals(1, receiptRows.size());
        } finally { workers.shutdownNow(); }
    }
    @Test void twoDifferentTripsCannotReserveOneParcelUnderShipmentLockModel() throws Exception {
        TransportTrip first = trip, second = create(1L); ReentrantLock lock = new ReentrantLock();
        when(items.countByTripId(anyLong())).thenReturn(0L);
        when(shipments.findForLifecycle(31L)).thenAnswer(c -> {
            if (!lock.tryLock(5, TimeUnit.SECONDS)) { throw new IllegalStateException("test timeout"); } return Optional.of(s);
        });
        TransportTripService proxy = transactional(new Tx(lock)); ExecutorService workers = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (TransportTrip target : List.of(first, second)) { futures.add(workers.submit(() -> {
                if (!start.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("test timeout"); }
                try { proxy.addItems(new Manifest(target.getId(), 0L, List.of(31L))); return true; }
                catch (LogisticsRuleException alreadyReserved) { return false; }
            })); }
            start.countDown(); int successes = 0; for (var f : futures) { if (f.get(10, TimeUnit.SECONDS)) { successes++; } }
            assertEquals(1, successes); assertEquals(1, itemRows.size());
        } finally { workers.shutdownNow(); }
    }
    @Test void shipmentLockOrderIsAscendingRegardlessOfClientSelectionOrder() {
        parcels.put(32L, parcel(32, origin, destination)); add(32, 31);
        var order = inOrder(trips, shipments, items); order.verify(trips).findForUpdate(trip.getId());
        order.verify(shipments).findForLifecycle(31L); order.verify(shipments).findForLifecycle(32L);
    }
    @ParameterizedTest @ValueSource(strings = {"item", "shipmentEvent", "tripEvent"})
    void manifestPersistenceFailureTriggersWholeBatchRollbackDecision(String at) {
        var failure = new DataIntegrityViolationException("synthetic failure");
        if (at.equals("item")) { doThrow(failure).when(items).saveAndFlush(any()); }
        if (at.equals("shipmentEvent")) { doThrow(failure).when(shipmentEvents).saveAndFlush(any()); }
        if (at.equals("tripEvent")) { doThrow(failure).when(events).saveAndFlush(any()); }
        Tx tx = new Tx(null); TransportTripService proxy = transactional(tx);
        assertThrows(DataIntegrityViolationException.class, () -> proxy.addItems(new Manifest(trip.getId(), trip.getVersion(), List.of(31L))));
        assertEquals(1, tx.rollbacks); assertEquals(0, tx.commits);
    }
    TransportTripService transactional(Tx tx) {
        var advice = new TransactionInterceptor(); advice.setTransactionManager(tx); advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource()); advice.afterPropertiesSet();
        var factory = new ProxyFactory(service); factory.setProxyTargetClass(true); factory.addAdvice(advice); return (TransportTripService) factory.getProxy();
    }
    static class Tx extends AbstractPlatformTransactionManager {
        final ReentrantLock lock; int commits, rollbacks;
        Tx(ReentrantLock lock) { this.lock = lock; }
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx, TransactionDefinition d) { }
        @Override protected void doCommit(DefaultTransactionStatus s) { commits++; release(); }
        @Override protected void doRollback(DefaultTransactionStatus s) { rollbacks++; release(); }
        void release() { if (lock != null && lock.isHeldByCurrentThread()) { lock.unlock(); } }
    }
}
