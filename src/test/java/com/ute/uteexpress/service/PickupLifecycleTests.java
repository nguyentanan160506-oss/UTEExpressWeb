package com.ute.uteexpress.service;

import com.ute.uteexpress.dto.shipment.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;

import static com.ute.uteexpress.manager.ManagerFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Tests Spring transaction decisions and a parent-lock model, not SQL Server JDBC/locking. */
class PickupLifecycleTests {
    final ShipmentRepository shipments = mock(ShipmentRepository.class);
    final ShipmentEventRepository events = mock(ShipmentEventRepository.class);
    final ShipmentAssignmentRepository assignments = mock(ShipmentAssignmentRepository.class);
    final ShipmentIntakeRepository intakes = mock(ShipmentIntakeRepository.class);
    final PickupEvidenceRepository evidence = mock(PickupEvidenceRepository.class);
    final OriginHandoffRepository handoffs = mock(OriginHandoffRepository.class);
    final LogisticsAccessService access = mock(LogisticsAccessService.class);
    final ShipmentLifecycleService service = new ShipmentLifecycleService(shipments, events, assignments, access,
        Clock.fixed(Instant.parse("2026-10-18T05:00:00Z"), ZoneOffset.UTC), intakes, evidence, handoffs);
    Shipment s;
    ShipmentAssignment a;
    PickupEvidence proof;
    OriginHandoff h;
    @BeforeEach void setup() {
        s = shipment(ShipmentStatus.PICKUP_ASSIGNED, ShipmentMethod.HOME_PICKUP);
        a = new ShipmentAssignment(); a.setId(41L); a.setShipment(s); a.setEmployee(courier(21L));
        a.setAssignmentType(AssignmentType.PICKUP); a.setStatus(AssignmentStatus.ASSIGNED);
        when(shipments.findForLifecycle(31L)).thenReturn(Optional.of(s));
        when(assignments.findPickupShipmentIdForOwner(41L, 21L)).thenReturn(Optional.of(31L));
        when(access.requireShipper()).thenReturn(new LogisticsAccessService.ShipperProfile(a.getEmployee().getUser(), a.getEmployee()));
        when(access.requireAssignedShipper(31L, AssignmentType.PICKUP)).thenReturn(
            new LogisticsAccessService.AssignedShipper(a.getEmployee().getUser(), a.getEmployee(), a));
        when(access.requireManagerForBranch(11L)).thenReturn(manager());
        when(access.requireIntakeEmployee(11L)).thenReturn(receiver());
        when(shipments.saveAndFlush(any())).thenAnswer(c -> {
            ReflectionTestUtils.setField(s, "version", s.getVersion() + 1); return s;
        });
        when(assignments.findByShipmentIdAndAssignmentTypeAndStatusIn(31L, AssignmentType.PICKUP, LogisticsAccessService.ACTIVE_ASSIGNMENTS))
            .thenReturn(List.of(a));
        when(evidence.findByAssignmentIdAndResult(41L, PickupResult.SUCCESS)).thenAnswer(c -> Optional.ofNullable(proof));
        when(evidence.saveAndFlush(any())).thenAnswer(c -> {
            proof = c.getArgument(0); proof.setId(51L); return proof;
        });
    }
    PickupCollectionCommand collect() { return new PickupCollectionCommand(41L, 0L, PickupResult.SUCCESS, true, "ACTUAL_REF", "Checked"); }
    HandoffReviewCommand accept() { return new HandoffReviewCommand(61L, 0L, true, true, "RECEIPT", null); }
    void pending() {
        s.setStatus(ShipmentStatus.PICKED_UP); a.setStatus(AssignmentStatus.IN_PROGRESS);
        proof = new PickupEvidence(); proof.setId(51L); proof.setShipment(s); proof.setAssignment(a); proof.setEmployee(a.getEmployee());
        proof.setActor(a.getEmployee().getUser()); proof.setResult(PickupResult.SUCCESS); proof.setReference("ACTUAL_REF");
        h = new OriginHandoff(); h.setId(61L); h.setShipment(s); h.setAssignment(a); h.setEvidence(proof); h.setEmployee(a.getEmployee());
        h.setRequestedBy(a.getEmployee().getUser()); h.setBranch(s.getOriginBranch()); h.setStatus(HandoffStatus.PENDING);
        when(handoffs.findShipmentId(61L)).thenReturn(Optional.of(31L)); when(handoffs.findById(61L)).thenReturn(Optional.of(h));
    }
    @Test void scalarOwnerLookupThenParentLockThenActiveAssignmentAndEvidence() {
        service.collectPickup(collect());
        var order = inOrder(access, assignments, shipments, evidence, events);
        order.verify(access).requireShipper(); order.verify(assignments).findPickupShipmentIdForOwner(41L, 21L);
        order.verify(shipments).findForLifecycle(31L); order.verify(access).requireAssignedShipper(31L, AssignmentType.PICKUP);
        order.verify(evidence).findByAssignmentIdAndResult(41L, PickupResult.SUCCESS);
        order.verify(evidence).saveAndFlush(any()); order.verify(assignments).saveAndFlush(a);
        order.verify(shipments).saveAndFlush(s); order.verify(events).saveAndFlush(any());
    }
    @Test void wrongCurrentAssignmentCannotCollectAfterReassignment() {
        a.setId(42L); assertThrows(LogisticsRuleException.class, () -> service.collectPickup(collect()));
        verify(evidence, never()).saveAndFlush(any());
    }
    @Test void mismatchedEvidenceCannotBeUsedForHandoffOrReceipt() {
        pending(); proof.setShipment(shipment(ShipmentStatus.PICKED_UP, ShipmentMethod.HOME_PICKUP)); proof.getShipment().setId(999L);
        assertThrows(LogisticsRuleException.class, () -> service.requestOriginHandoff(new OriginHandoffCommand(41L, 0L, null)));
        assertThrows(LogisticsRuleException.class, () -> service.reviewOriginHandoff(accept())); verifyNoInteractions(intakes);
    }
    @Test void staleVersionCannotCollectRequestOrReceive() {
        ReflectionTestUtils.setField(s, "version", 1L);
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> service.collectPickup(collect()));
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> service.requestOriginHandoff(new OriginHandoffCommand(41L, 0L, null)));
        pending(); assertThrows(ObjectOptimisticLockingFailureException.class, () -> service.reviewOriginHandoff(accept()));
        verify(evidence, never()).saveAndFlush(any()); verify(intakes, never()).saveAndFlush(any());
    }
    @Test void inconsistentOrAlreadyReceivedHandoffCannotCreateReceipt() {
        pending(); h.setStatus(HandoffStatus.REJECTED);
        assertThrows(LogisticsRuleException.class, () -> service.reviewOriginHandoff(accept()));
        h.setStatus(HandoffStatus.PENDING); when(intakes.existsByShipmentId(31L)).thenReturn(true);
        assertThrows(LogisticsRuleException.class, () -> service.reviewOriginHandoff(accept())); verify(intakes, never()).saveAndFlush(any());
    }
    @Test void receiverMustNotBeTheHandingOverEmployeeEvenWhenActorIsDifferent() {
        pending(); Employee same = receiver(); same.setId(21L); when(access.requireIntakeEmployee(11L)).thenReturn(same);
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.reviewOriginHandoff(accept()));
        verify(intakes, never()).saveAndFlush(any());
    }
    @ParameterizedTest @ValueSource(strings = {"evidence", "assignment", "event"})
    void collectionPersistenceFailureCausesSpringRollback(String step) {
        var failure = new DataIntegrityViolationException("synthetic persistence failure");
        if (step.equals("evidence")) { doThrow(failure).when(evidence).saveAndFlush(any()); }
        if (step.equals("assignment")) { when(assignments.saveAndFlush(any())).thenThrow(failure); }
        if (step.equals("event")) { when(events.saveAndFlush(any())).thenThrow(failure); }
        Transactions tx = new Transactions(null); ShipmentLifecycleService proxy = transactional(tx);
        assertThrows(DataIntegrityViolationException.class, () -> proxy.collectPickup(collect()));
        assertEquals(1, tx.rollbacks.get()); assertEquals(0, tx.commits.get());
    }
    @ParameterizedTest @ValueSource(strings = {"handoff", "receipt", "assignment", "event"})
    void acceptanceFailureRollsBackWholeTransactionDecision(String step) {
        pending(); var failure = new DataIntegrityViolationException("synthetic persistence failure");
        if (step.equals("handoff")) { when(handoffs.saveAndFlush(any())).thenThrow(failure); }
        if (step.equals("receipt")) { when(intakes.saveAndFlush(any())).thenThrow(failure); }
        if (step.equals("assignment")) { when(assignments.saveAndFlush(any())).thenThrow(failure); }
        if (step.equals("event")) { when(events.saveAndFlush(any())).thenThrow(failure); }
        Transactions tx = new Transactions(null); ShipmentLifecycleService proxy = transactional(tx);
        assertThrows(DataIntegrityViolationException.class, () -> proxy.reviewOriginHandoff(accept()));
        assertEquals(1, tx.rollbacks.get()); assertEquals(0, tx.commits.get());
    }
    @Test void simultaneousCollectionHasOneWinnerUnderParentLockContract() throws Exception { race(false); }
    @Test void simultaneousReceiptHasOneWinnerUnderParentLockContract() throws Exception { race(true); }
    void race(boolean receiving) throws Exception {
        if (receiving) { pending(); }
        ReentrantLock lock = new ReentrantLock();
        when(shipments.findForLifecycle(31L)).thenAnswer(c -> {
            if (!lock.tryLock(5, TimeUnit.SECONDS)) { throw new IllegalStateException("test timeout"); } return Optional.of(s);
        });
        Transactions tx = new Transactions(lock); ShipmentLifecycleService proxy = transactional(tx);
        ExecutorService workers = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) { results.add(workers.submit(() -> {
                if (!start.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("test start timeout"); }
                try { if (receiving) { proxy.reviewOriginHandoff(accept()); } else { proxy.collectPickup(collect()); } return true; }
                catch (ObjectOptimisticLockingFailureException conflict) { return false; }
            })); }
            start.countDown(); int successes = 0; for (var result : results) { if (result.get(10, TimeUnit.SECONDS)) { successes++; } }
            assertEquals(1, successes); assertEquals(1, tx.commits.get()); assertEquals(1, tx.rollbacks.get());
            verify(events, times(1)).saveAndFlush(any());
            if (receiving) { verify(intakes, times(1)).saveAndFlush(any()); }
            else { verify(evidence, times(1)).saveAndFlush(any()); }
        } finally { workers.shutdownNow(); }
    }
    ShipmentLifecycleService transactional(Transactions tx) {
        var advice = new TransactionInterceptor(); advice.setTransactionManager(tx);
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource()); advice.afterPropertiesSet();
        var factory = new ProxyFactory(service); factory.setProxyTargetClass(true); factory.addAdvice(advice);
        return (ShipmentLifecycleService) factory.getProxy();
    }
    static class Transactions extends AbstractPlatformTransactionManager {
        final AtomicInteger commits = new AtomicInteger(), rollbacks = new AtomicInteger(); final ReentrantLock lock;
        Transactions(ReentrantLock lock) { this.lock = lock; }
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits.incrementAndGet(); release(); }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks.incrementAndGet(); release(); }
        void release() { if (lock != null && lock.isHeldByCurrentThread()) { lock.unlock(); } }
    }
}
