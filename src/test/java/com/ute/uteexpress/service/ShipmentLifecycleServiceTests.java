package com.ute.uteexpress.service;

import com.ute.uteexpress.dto.shipment.ShipmentTransitionCommand;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ute.uteexpress.shipment.ShipmentFixtures.*;

/** Transaction interceptor/CAS contract tests; SQL Server rollback and locking remain integration checks. */
class ShipmentLifecycleServiceTests {
    private final ShipmentRepository shipments = mock(ShipmentRepository.class);
    private final ShipmentEventRepository events = mock(ShipmentEventRepository.class);
    private final ShipmentAssignmentRepository assignments = mock(ShipmentAssignmentRepository.class);
    private final LogisticsAccessService access = mock(LogisticsAccessService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T01:02:03.123456789Z"), ZoneOffset.UTC);
    private final ShipmentLifecycleService service = new ShipmentLifecycleService(shipments, events, assignments, access, clock, mock(ShipmentIntakeRepository.class));
    private Shipment shipment;
    private User actor;
    @BeforeEach void setup() {
        shipment = versionedShipment(); actor = owner();
        when(shipments.findForLifecycle(31L)).thenReturn(Optional.of(shipment));
        when(access.requireManagerForBranch(11L)).thenReturn(actor);
        when(shipments.saveAndFlush(any())).thenAnswer(call -> {
            Shipment saved = call.getArgument(0); ReflectionTestUtils.setField(saved, "version", saved.getVersion() + 1); return saved;
        });
    }
    static Shipment versionedShipment() {
        Shipment result = shipment(); ReflectionTestUtils.setField(result, "version", 0L); return result;
    }
    private ShipmentTransitionCommand command(ShipmentStatus to) { return new ShipmentTransitionCommand(31L, 0L, to, to == ShipmentStatus.CANCELLED ? "Cancellation fixture reason" : null); }
    @Test void confirmationPreservesLegacyMethodHubsAndFrozenPriceAndRecordsRealActor() {
        var fee = shipment.getTotalFee();
        assertEquals(1L, service.transition(command(ShipmentStatus.CONFIRMED)));
        assertEquals(ShipmentStatus.CONFIRMED, shipment.getStatus());
        assertNull(shipment.getShipmentMethod()); assertNull(shipment.getOriginHub()); assertNull(shipment.getDestinationHub());
        assertEquals(fee, shipment.getTotalFee());
        var capture = org.mockito.ArgumentCaptor.forClass(ShipmentEvent.class); verify(events).saveAndFlush(capture.capture());
        ShipmentEvent event = capture.getValue(); assertSame(actor, event.getActor()); assertSame(shipment, event.getShipment());
        assertSame(shipment.getOriginBranch(), event.getBranch()); assertEquals(ShipmentStatus.CREATED, event.getFromStatus());
        assertEquals(ShipmentStatus.CONFIRMED, event.getToStatus());
        assertEquals(LocalDateTime.of(2026, 10, 15, 1, 2, 3, 123456700), event.getOccurredAt());
        var order = inOrder(shipments, events); order.verify(shipments).saveAndFlush(shipment); order.verify(events).saveAndFlush(event);
    }
    @Test void cancellationBeforeCustodyIsAllowed() {
        service.transition(command(ShipmentStatus.CANCELLED)); assertEquals(ShipmentStatus.CANCELLED, shipment.getStatus());
    }
    @Test void cancellationDoesNotSilentlyAbandonExistingAssignments() {
        when(assignments.existsByShipmentIdAndStatusIn(31L, LogisticsAccessService.ACTIVE_ASSIGNMENTS)).thenReturn(true);
        assertThrows(LogisticsRuleException.class, () -> service.transition(command(ShipmentStatus.CANCELLED)));
        assertEquals(ShipmentStatus.CREATED, shipment.getStatus()); verifyNoInteractions(events);
    }
    @Test void illegalStateJumpAndRepeatedStateAreRejected() {
        for (ShipmentStatus target : List.of(ShipmentStatus.DELIVERED, ShipmentStatus.CREATED, ShipmentStatus.AT_ORIGIN_HUB)) {
            assertThrows(LogisticsRuleException.class, () -> service.transition(command(target)));
        }
        verifyNoInteractions(events); verify(shipments, never()).saveAndFlush(any());
    }
    @Test void physicalHandoffAndAssignmentEdgesRemainDisabledUntilFutureModules() {
        shipment.setStatus(ShipmentStatus.CONFIRMED);
        for (ShipmentStatus target : List.of(ShipmentStatus.PICKUP_ASSIGNED, ShipmentStatus.AT_ORIGIN_BRANCH)) {
            assertTrue(ShipmentTransitionPolicy.isPotentialEdge(shipment.getStatus(), target));
            assertThrows(LogisticsRuleException.class, () -> service.transition(command(target)));
        }
        shipment.setStatus(ShipmentStatus.IN_TRANSIT);
        assertThrows(LogisticsRuleException.class, () -> service.transition(command(ShipmentStatus.AT_DESTINATION_BRANCH)));
        verifyNoInteractions(events);
    }
    @Test void staleVersionCannotWriteStateOrHistory() {
        assertThrows(ObjectOptimisticLockingFailureException.class,
            () -> service.transition(new ShipmentTransitionCommand(31L, 4L, ShipmentStatus.CONFIRMED)));
        assertEquals(ShipmentStatus.CREATED, shipment.getStatus()); verifyNoInteractions(events);
    }
    @Test void managerOutsideBranchCannotWrite() {
        when(access.requireManagerForBranch(11L)).thenThrow(new AccessDeniedException("Forbidden"));
        assertThrows(AccessDeniedException.class, () -> service.transition(command(ShipmentStatus.CONFIRMED)));
        verify(shipments, never()).saveAndFlush(any()); verifyNoInteractions(events);
    }
    @Test void eventFailureTriggersTransactionRollbackAfterShipmentFlush() {
        var transactionManager = new RecordingTransactions();
        var interceptor = new TransactionInterceptor(); interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource()); interceptor.afterPropertiesSet();
        ProxyFactory factory = new ProxyFactory(service); factory.setProxyTargetClass(true); factory.addAdvice(interceptor);
        ShipmentLifecycleService transactionalService = (ShipmentLifecycleService) factory.getProxy();
        when(events.saveAndFlush(any())).thenThrow(new IllegalStateException("simulated persistence failure"));
        assertThrows(IllegalStateException.class, () -> transactionalService.transition(command(ShipmentStatus.CONFIRMED)));
        assertEquals(1, transactionManager.rollbacks); assertEquals(0, transactionManager.commits);
        verify(shipments).saveAndFlush(shipment); verify(events).saveAndFlush(any());
        // A mock repository cannot prove row rollback. This verifies the real Spring transaction advice decision.
    }
    @Test void concurrentUpdatesUseOneVersionAndProduceOnlyOneEvent() throws Exception {
        AtomicLong persistedVersion = new AtomicLong(); CyclicBarrier loaded = new CyclicBarrier(2);
        when(shipments.findForLifecycle(31L)).thenAnswer(call -> {
            Shipment copy = versionedShipment(); loaded.await(5, TimeUnit.SECONDS); return Optional.of(copy);
        });
        doAnswer(call -> {
            Shipment copy = call.getArgument(0);
            if (!persistedVersion.compareAndSet(copy.getVersion(), copy.getVersion() + 1)) {
                throw new ObjectOptimisticLockingFailureException(Shipment.class, copy.getId());
            }
            ReflectionTestUtils.setField(copy, "version", copy.getVersion() + 1); return copy;
        }).when(shipments).saveAndFlush(any());
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (ShipmentStatus target : List.of(ShipmentStatus.CONFIRMED, ShipmentStatus.CANCELLED)) {
                results.add(workers.submit(() -> {
                    try { service.transition(command(target)); return true; }
                    catch (ObjectOptimisticLockingFailureException expected) { return false; }
                }));
            }
            int successes = 0; for (var result : results) { if (result.get(10, TimeUnit.SECONDS)) { successes++; } }
            assertEquals(1, successes); assertEquals(1L, persistedVersion.get()); verify(events, times(1)).saveAndFlush(any());
        } finally { workers.shutdownNow(); }
    }
    private static class RecordingTransactions extends AbstractPlatformTransactionManager {
        int commits, rollbacks;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
