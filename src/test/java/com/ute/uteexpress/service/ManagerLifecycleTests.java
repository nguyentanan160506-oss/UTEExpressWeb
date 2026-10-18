package com.ute.uteexpress.service;

import com.ute.uteexpress.dto.shipment.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;

import static com.ute.uteexpress.manager.ManagerFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Parent-row lock/rollback models, not a SQL Server integration test. */
class ManagerLifecycleTests {
    final ShipmentRepository shipments = mock(ShipmentRepository.class);
    final ShipmentEventRepository events = mock(ShipmentEventRepository.class);
    final ShipmentAssignmentRepository assignments = mock(ShipmentAssignmentRepository.class);
    final ShipmentIntakeRepository intakes = mock(ShipmentIntakeRepository.class);
    final LogisticsAccessService access = mock(LogisticsAccessService.class);
    final Clock clock = Clock.fixed(Instant.parse("2026-10-15T12:01:00Z"), ZoneOffset.UTC);
    final ShipmentLifecycleService service = new ShipmentLifecycleService(shipments, events, assignments, access, clock, intakes, mock(PickupEvidenceRepository.class), mock(OriginHandoffRepository.class));
    Shipment shipment;
    ShipmentAssignment current;
    @BeforeEach void setup() {
        shipment = shipment(ShipmentStatus.CONFIRMED, ShipmentMethod.HOME_PICKUP);
        when(shipments.findForLifecycle(31L)).thenReturn(Optional.of(shipment));
        when(access.requireManagerForBranch(11L)).thenReturn(manager());
        when(access.requireAssignableShipper(21L, 11L)).thenReturn(courier(21L));
        when(access.requireAssignableShipper(23L, 11L)).thenReturn(courier(23L));
        when(access.requireIntakeEmployee(11L)).thenReturn(receiver());
        when(shipments.saveAndFlush(any())).thenAnswer(call -> {
            Shipment s = call.getArgument(0); ReflectionTestUtils.setField(s, "version", s.getVersion() + 1); return s;
        });
        when(assignments.saveAndFlush(any())).thenAnswer(call -> {
            ShipmentAssignment a = call.getArgument(0); if (a.getId() == null) { a.setId(42L); } return a;
        });
    }
    PickupAssignmentCommand assignment(Long replaces, Long employee, String reason) {
        return new PickupAssignmentCommand(31L, 0L, employee, replaces, reason);
    }
    BranchIntakeCommand intake() { return new BranchIntakeCommand(31L, 0L, "RECEIPT-TEST", true, "Checked package"); }
    void pendingAssignment(AssignmentStatus status) {
        shipment.setStatus(ShipmentStatus.PICKUP_ASSIGNED);
        current = new ShipmentAssignment(); current.setId(41L); current.setShipment(shipment); current.setEmployee(courier(21L));
        current.setAssignmentType(AssignmentType.PICKUP); current.setStatus(status);
        when(assignments.findByShipmentIdAndAssignmentTypeAndStatusIn(31L, AssignmentType.PICKUP, LogisticsAccessService.ACTIVE_ASSIGNMENTS))
            .thenReturn(List.of(current));
    }
    @Test void confirmationIsNotPhysicalReceipt() {
        shipment.setStatus(ShipmentStatus.CREATED);
        service.transition(new ShipmentTransitionCommand(31L, 0L, ShipmentStatus.CONFIRMED));
        assertEquals(ShipmentStatus.CONFIRMED, shipment.getStatus()); assertNull(shipment.getCurrentBranch()); verifyNoInteractions(intakes);
    }
    @Test void cancellationRequiresReasonAndKeepsItInPrivateEvent() {
        shipment.setStatus(ShipmentStatus.CREATED);
        assertThrows(LogisticsRuleException.class, () -> service.transition(new ShipmentTransitionCommand(31L, 0L, ShipmentStatus.CANCELLED)));
        service.transition(new ShipmentTransitionCommand(31L, 0L, ShipmentStatus.CANCELLED, "  Customer declined  "));
        var event = org.mockito.ArgumentCaptor.forClass(ShipmentEvent.class); verify(events).saveAndFlush(event.capture());
        assertEquals("Customer declined", event.getValue().getDescription()); assertEquals(1L, event.getValue().getActor().getId());
        assertNull(shipment.getCurrentBranch());
    }
    @Test void assignmentUsesActualActorBranchAndEmployeeWithoutMovingPhysicalLocation() {
        assertEquals(1L, service.assignPickup(assignment(null, 21L, "Pickup requested")));
        var task = org.mockito.ArgumentCaptor.forClass(ShipmentAssignment.class); verify(assignments).saveAndFlush(task.capture());
        assertEquals(21L, task.getValue().getEmployee().getId()); assertEquals(1L, task.getValue().getAssignedBy().getId());
        assertEquals(AssignmentType.PICKUP, task.getValue().getAssignmentType()); assertEquals(AssignmentStatus.ASSIGNED, task.getValue().getStatus());
        assertEquals(ShipmentStatus.PICKUP_ASSIGNED, shipment.getStatus()); assertNull(shipment.getCurrentBranch());
        verifyNoInteractions(intakes); verify(events).saveAndFlush(argThat(e -> e.getFromStatus() == ShipmentStatus.CONFIRMED
            && e.getToStatus() == ShipmentStatus.PICKUP_ASSIGNED && e.getBranch().getId() == 11L));
    }
    @Test void dropoffAndLegacyCannotGetPickupAssignments() {
        for (ShipmentMethod method : Arrays.asList(ShipmentMethod.BRANCH_DROPOFF, null)) {
            shipment.setShipmentMethod(method); assertThrows(LogisticsRuleException.class, () -> service.assignPickup(assignment(null, 21L, null)));
        }
        verify(assignments, never()).saveAndFlush(any());
    }
    @ParameterizedTest @EnumSource(value = ShipmentStatus.class, names = {"CREATED", "PICKED_UP", "AT_ORIGIN_BRANCH", "DELIVERED", "CANCELLED"})
    void cannotAssignAcrossInvalidStates(ShipmentStatus status) {
        shipment.setStatus(status); assertThrows(LogisticsRuleException.class, () -> service.assignPickup(assignment(null, 21L, null)));
        verify(assignments, never()).saveAndFlush(any());
    }
    @Test void existingActiveTaskCannotBeDuplicatedEvenWithInconsistentConfirmedState() {
        pendingAssignment(AssignmentStatus.ASSIGNED); shipment.setStatus(ShipmentStatus.CONFIRMED);
        assertThrows(LogisticsRuleException.class, () -> service.assignPickup(assignment(null, 21L, null)));
        verify(assignments, never()).saveAndFlush(any());
    }
    @Test void reassignmentCancelsPendingTaskAndFlushesItBeforeReplacementWithSameStateEvent() {
        pendingAssignment(AssignmentStatus.ASSIGNED);
        assertEquals(1L, service.assignPickup(assignment(41L, 23L, "Replacement reason")));
        assertEquals(AssignmentStatus.CANCELLED, current.getStatus()); assertNotNull(current.getCompletedAt());
        var order = inOrder(assignments, shipments, events); order.verify(assignments).saveAndFlush(current);
        order.verify(assignments).saveAndFlush(argThat(a -> a != current && a.getEmployee().getId() == 23L));
        order.verify(shipments).saveAndFlush(shipment);
        order.verify(events).saveAndFlush(argThat(e -> e.getFromStatus() == ShipmentStatus.PICKUP_ASSIGNED
            && e.getToStatus() == ShipmentStatus.PICKUP_ASSIGNED && e.getDescription().contains("Replacement reason")));
    }
    @Test void reassignmentCannotSpoofAssignmentIdSkipReasonOrKeepSameEmployee() {
        pendingAssignment(AssignmentStatus.ASSIGNED);
        for (var command : List.of(assignment(999L, 23L, "Reason"), assignment(41L, 23L, " "), assignment(41L, 21L, "Reason"))) {
            assertThrows(LogisticsRuleException.class, () -> service.assignPickup(command));
        }
        assertEquals(AssignmentStatus.ASSIGNED, current.getStatus()); verify(assignments, never()).saveAndFlush(any());
    }
    @Test void inProgressTaskCannotBeReassigned() {
        pendingAssignment(AssignmentStatus.IN_PROGRESS);
        assertThrows(LogisticsRuleException.class, () -> service.assignPickup(assignment(41L, 23L, "Reason")));
    }
    @Test void foreignOrInactiveEmployeeGuardFailureDoesNotChangeTaskOrShipment() {
        when(access.requireAssignableShipper(21L, 11L)).thenThrow(new LogisticsRuleException("Unavailable"));
        assertThrows(LogisticsRuleException.class, () -> service.assignPickup(assignment(null, 21L, null)));
        verify(assignments, never()).saveAndFlush(any()); verifyNoInteractions(events);
    }
    @Test void managerOutsideOriginBranchCannotMutate() {
        when(access.requireManagerForBranch(11L)).thenThrow(new AccessDeniedException("Forbidden"));
        assertThrows(AccessDeniedException.class, () -> service.assignPickup(assignment(null, 21L, null)));
        assertThrows(AccessDeniedException.class, () -> service.receiveDropoff(intake()));
        verify(assignments, never()).saveAndFlush(any()); verifyNoInteractions(events, intakes);
    }
    @Test void staleVersionStopsAssignmentAndIntake() {
        ReflectionTestUtils.setField(shipment, "version", 1L);
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> service.assignPickup(assignment(null, 21L, null)));
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> service.receiveDropoff(intake()));
        verifyNoInteractions(events, intakes);
    }
    @Test void dropoffIntakeWritesEmployeeReceiptPhysicalBranchAndEventAtomically() {
        shipment.setShipmentMethod(ShipmentMethod.BRANCH_DROPOFF);
        assertEquals(1L, service.receiveDropoff(intake()));
        assertEquals(ShipmentStatus.AT_ORIGIN_BRANCH, shipment.getStatus()); assertSame(shipment.getOriginBranch(), shipment.getCurrentBranch());
        verify(intakes).saveAndFlush(argThat(r -> r.getEmployee().getId() == 22L && r.getReceivedBy().getId() == 1L
            && r.getPickupAssignment() == null && r.getReceiptReference().equals("RECEIPT-TEST")));
        verify(events).saveAndFlush(argThat(e -> e.getToStatus() == ShipmentStatus.AT_ORIGIN_BRANCH && e.getDescription().contains("RECEIPT-TEST")));
    }
    @ParameterizedTest @EnumSource(value = ShipmentStatus.class, names = {"CREATED", "CONFIRMED", "PICKUP_ASSIGNED", "PICKED_UP", "AT_ORIGIN_BRANCH"})
    void homePickupCannotBeReceivedBeforeCommit11Evidence(ShipmentStatus status) {
        shipment.setStatus(status); assertThrows(LogisticsRuleException.class, () -> service.receiveDropoff(intake()));
        verifyNoInteractions(intakes, events);
    }
    @Test void dropoffIntakeNeedsActualReceiptConfirmationAndStaffIdentity() {
        shipment.setShipmentMethod(ShipmentMethod.BRANCH_DROPOFF);
        assertThrows(LogisticsRuleException.class, () -> service.receiveDropoff(new BranchIntakeCommand(31L, 0L, "REF", false, null)));
        when(access.requireIntakeEmployee(11L)).thenThrow(new AccessDeniedException("Forbidden"));
        assertThrows(AccessDeniedException.class, () -> service.receiveDropoff(intake())); verifyNoInteractions(intakes, events);
    }
    @Test void duplicateIntakeAndUnexpectedActiveTaskAreDenied() {
        shipment.setShipmentMethod(ShipmentMethod.BRANCH_DROPOFF);
        when(intakes.existsByShipmentId(31L)).thenReturn(true);
        assertThrows(LogisticsRuleException.class, () -> service.receiveDropoff(intake()));
        when(intakes.existsByShipmentId(31L)).thenReturn(false);
        when(assignments.existsByShipmentIdAndStatusIn(31L, LogisticsAccessService.ACTIVE_ASSIGNMENTS)).thenReturn(true);
        assertThrows(LogisticsRuleException.class, () -> service.receiveDropoff(intake())); verify(intakes, never()).saveAndFlush(any());
    }
    @Test void eventFailureRollsBackAssignmentOrReceiptTransaction() {
        var transactions = new Transactions(null);
        when(events.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("test event failure"));
        ShipmentLifecycleService proxy = transactional(transactions);
        assertThrows(DataIntegrityViolationException.class, () -> proxy.assignPickup(assignment(null, 21L, null)));
        assertEquals(1, transactions.rollbacks); assertEquals(0, transactions.commits);
        shipment.setStatus(ShipmentStatus.CONFIRMED); shipment.setShipmentMethod(ShipmentMethod.BRANCH_DROPOFF);
        ReflectionTestUtils.setField(shipment, "version", 0L);
        assertThrows(DataIntegrityViolationException.class, () -> proxy.receiveDropoff(intake()));
        assertEquals(2, transactions.rollbacks); assertEquals(0, transactions.commits);
    }
    @Test void concurrentAssignmentsHaveOneWinnerUnderParentLockContract() throws Exception { race(false); }
    @Test void concurrentDropoffReceiptsHaveOneWinnerUnderParentLockContract() throws Exception { race(true); }
    void race(boolean receiving) throws Exception {
        if (receiving) { shipment.setShipmentMethod(ShipmentMethod.BRANCH_DROPOFF); }
        ReentrantLock lock = new ReentrantLock();
        when(shipments.findForLifecycle(31L)).thenAnswer(call -> {
            if (!lock.tryLock(5, TimeUnit.SECONDS)) { throw new IllegalStateException("test lock timeout"); }
            return Optional.of(shipment);
        });
        ShipmentLifecycleService proxy = transactional(new Transactions(lock));
        ExecutorService workers = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            var futures = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 2; i++) { futures.add(workers.submit(() -> {
                if (!start.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("test start timeout"); }
                try { if (receiving) { proxy.receiveDropoff(intake()); } else { proxy.assignPickup(assignment(null, 21L, null)); } return true; }
                catch (ObjectOptimisticLockingFailureException conflict) { return false; }
            })); }
            start.countDown(); int succeeded = 0; for (var result : futures) { if (result.get(10, TimeUnit.SECONDS)) { succeeded++; } }
            assertEquals(1, succeeded); verify(events, times(1)).saveAndFlush(any());
            if (receiving) { verify(intakes, times(1)).saveAndFlush(any()); }
            else { verify(assignments, times(1)).saveAndFlush(any()); }
        } finally { workers.shutdownNow(); }
    }
    ShipmentLifecycleService transactional(Transactions manager) {
        var advice = new TransactionInterceptor(); advice.setTransactionManager(manager);
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource()); advice.afterPropertiesSet();
        var factory = new ProxyFactory(service); factory.setProxyTargetClass(true); factory.addAdvice(advice);
        return (ShipmentLifecycleService) factory.getProxy();
    }
    static class Transactions extends AbstractPlatformTransactionManager {
        int commits, rollbacks; final ReentrantLock lock;
        Transactions(ReentrantLock lock) { this.lock = lock; }
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; release(); }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; release(); }
        void release() { if (lock != null && lock.isHeldByCurrentThread()) { lock.unlock(); } }
    }
}
