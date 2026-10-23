package com.ute.uteexpress.delivery;

import com.ute.uteexpress.dto.delivery.DeliveryCommands.*;
import com.ute.uteexpress.entity.User;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.LastMileService;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Spring transaction advice plus serialized root-lock model. No JDBC lock/row rollback claims. */
class DeliveryTransactionTests {
    DeliveryScenario f;
    @BeforeEach void setup() { f = new DeliveryScenario(); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    LastMileService proxy(Tx tx) {
        var advice = new TransactionInterceptor(); advice.setTransactionManager(tx);
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource()); advice.afterPropertiesSet();
        var factory = new ProxyFactory(f.service); factory.setProxyTargetClass(true); factory.addAdvice(advice); return (LastMileService) factory.getProxy();
    }
    @ParameterizedTest @ValueSource(strings = {"attempt", "proof", "assignment", "shipment", "event"})
    void successPersistenceFailureTriggersRollbackDecision(String boundary) {
        f.start(); var command = f.complete(); var error = new DataIntegrityViolationException("Synthetic persistence failure");
        switch (boundary) {
            case "attempt" -> doThrow(error).when(f.attempts).saveAndFlush(any());
            case "proof" -> doThrow(error).when(f.proofs).saveAndFlush(any());
            case "assignment" -> doThrow(error).when(f.assignments).saveAndFlush(any());
            case "shipment" -> doThrow(error).when(f.shipments).saveAndFlush(any());
            case "event" -> doThrow(error).when(f.events).saveAndFlush(any());
            default -> fail("Unknown boundary");
        }
        var tx = new Tx(null); assertThrows(DataIntegrityViolationException.class, () -> proxy(tx).complete(command));
        assertEquals(1, tx.rollbacks); assertEquals(0, tx.commits);
    }
    @Test void recoveryReceiptFailureTriggersRollbackDecision() {
        f.start(); f.fail(); f.service.requestRecovery(new Recover(f.current().getId(), f.s.getVersion(), null)); f.as(f.manager);
        doThrow(new DataIntegrityViolationException("Synthetic receipt failure")).when(f.recoveries).saveAndFlush(any());
        var tx = new Tx(null); assertThrows(DataIntegrityViolationException.class, () -> proxy(tx).reviewRecovery(
            new ReviewRecovery(31L, f.s.getVersion(), f.recoveryRows.getFirst().getId(), true, true, "RECEIPT", null)));
        assertEquals(1, tx.rollbacks); assertEquals(0, tx.commits);
    }
    @Test void senderReceiptFailureTriggersRollbackDecision() {
        f.s.setOriginBranch(f.destination); f.requestReturn();
        doThrow(new DataIntegrityViolationException("Synthetic receipt failure")).when(f.returnReceipts).saveAndFlush(any());
        var tx = new Tx(null); assertThrows(DataIntegrityViolationException.class, () -> proxy(tx).returnToSender(
            new SenderReceipt(31L, f.s.getVersion(), true, ReturnRecipientType.SENDER, "Actual sender", "FINAL", null)));
        assertEquals(1, tx.rollbacks); assertEquals(0, tx.commits);
    }
    @Test void competingAssignmentsHaveOnlyOneWinner() throws Exception {
        Long version = f.s.getVersion(); race(f.manager, p -> p.assign(new Assign(31L, version, 21L, null, null)),
            f.manager, p -> p.assign(new Assign(31L, version, 23L, null, null)));
        assertEquals(1, f.tasks.size()); assertEquals(1, f.eventRows.size());
    }
    @Test void doubleDeliveryConfirmationHasOnlyOneProof() throws Exception {
        f.start(); Complete command = f.complete(); race(f.courier.getUser(), p -> p.complete(command), f.courier.getUser(), p -> p.complete(command));
        assertEquals(1, f.proofRows.size()); assertEquals(1, f.attemptRows.size()); assertEquals(ShipmentStatus.DELIVERED, f.s.getStatus());
    }
    @Test void successAndFailureCannotBothWinSameAttempt() throws Exception {
        f.start(); Complete command = f.complete();
        race(f.courier.getUser(), p -> p.complete(command), f.courier.getUser(), p -> p.fail(new Fail(command.assignmentId(), command.expectedVersion(), true, DeliveryFailureCode.ABSENT, "Absent", null)));
        assertEquals(1, f.attemptRows.size()); assertEquals(f.s.getStatus() == ShipmentStatus.DELIVERED ? 1 : 0, f.proofRows.size());
    }
    @Test void successAndReturnDecisionAreMutuallyExclusive() throws Exception {
        f.start(); Complete command = f.complete(); race(f.courier.getUser(), p -> p.complete(command), f.manager,
            p -> p.initiateReturn(new ReturnStart(31L, command.expectedVersion(), "Return approved")));
        assertTrue((f.returnRequest == null) != f.proofRows.isEmpty());
        if (f.returnRequest != null) { assertEquals(ShipmentStatus.RETURNING, f.s.getStatus()); assertNull(f.s.getReturnedAt()); }
    }
    @Test void reassignmentAndOldCourierAcceptanceCannotBothWin() throws Exception {
        f.prepare(); Long id = f.current().getId(), version = f.s.getVersion();
        race(f.manager, p -> p.assign(new Assign(31L, version, 23L, id, "Reassign before acceptance")),
            f.courier.getUser(), p -> p.accept(new Action(id, version, true, "RECEIVED")));
        long active = f.tasks.values().stream().filter(a -> a.getCompletedAt() == null).count(); assertEquals(1, active);
        if (f.tasks.size() == 2) { assertEquals(AssignmentStatus.CANCELLED, f.tasks.get(id).getStatus()); assertNotNull(f.s.getCurrentBranch()); }
        else { assertEquals(AssignmentStatus.IN_PROGRESS, f.tasks.get(id).getStatus()); assertNull(f.s.getCurrentBranch()); }
    }
    @Test void duplicateReturnDecisionsHaveOneWinner() throws Exception {
        Long version = f.s.getVersion(); race(f.manager, p -> p.initiateReturn(new ReturnStart(31L, version, "First review")),
            f.manager, p -> p.initiateReturn(new ReturnStart(31L, version, "Second review")));
        assertNotNull(f.returnRequest); assertEquals(1, f.eventRows.size()); assertNull(f.s.getReturnedAt());
    }
    @Test void duplicateRecoveryRequestsHaveOnePendingRecord() throws Exception {
        f.start(); f.fail(); var c = new Recover(f.current().getId(), f.s.getVersion(), "Request physical return");
        race(f.courier.getUser(), p -> p.requestRecovery(c), f.courier.getUser(), p -> p.requestRecovery(c));
        assertEquals(1, f.recoveryRows.size()); assertNull(f.s.getCurrentBranch());
    }
    @Test void duplicateAcceptanceCannotTransferCustodyTwice() throws Exception {
        f.prepare(); var c = new Action(f.current().getId(), f.s.getVersion(), true, "ACTUAL-HANDOFF"); int before = f.eventRows.size();
        race(f.courier.getUser(), p -> p.accept(c), f.courier.getUser(), p -> p.accept(c));
        assertEquals(before + 1, f.eventRows.size()); assertEquals(1, f.dispatchRows.size()); assertNull(f.s.getCurrentBranch());
    }
    interface Operation { void run(LastMileService proxy); }
    void race(User actor1, Operation operation1, User actor2, Operation operation2) throws Exception {
        var lock = new ReentrantLock(); when(f.shipments.findForLifecycle(31L)).thenAnswer(c -> {
            if (!lock.tryLock(5, TimeUnit.SECONDS)) { throw new IllegalStateException("Root lock timeout"); } return Optional.of(f.s);
        });
        LastMileService p = proxy(new Tx(lock)); ExecutorService workers = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) { User actor = i == 0 ? actor1 : actor2; Operation operation = i == 0 ? operation1 : operation2;
                results.add(workers.submit(() -> {
                    f.as(actor); try {
                        if (!start.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("Start timeout"); }
                        try { operation.run(p); return true; }
                        catch (org.springframework.orm.ObjectOptimisticLockingFailureException | org.springframework.security.access.AccessDeniedException
                                | com.ute.uteexpress.service.LogisticsRuleException conflict) { return false; }
                    } finally { SecurityContextHolder.clearContext(); }
                }));
            }
            start.countDown(); int winners = 0; for (var result : results) { if (result.get(10, TimeUnit.SECONDS)) { winners++; } }
            assertEquals(1, winners);
        } finally { workers.shutdownNow(); }
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
