package com.ute.uteexpress.pricing;

import com.ute.uteexpress.dto.pricing.PricingCommands;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;
import static com.ute.uteexpress.shipment.ShipmentFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Spring transaction advice + service gate lock model. Does not verify JDBC rollback/isolation. */
class PricingTransactionTests {
    PricingScenario f;
    @BeforeEach void setup() { f = new PricingScenario(); f.legacy.setActive(false); }
    @AfterEach void close() { f.close(); }
    PricingVersion configured() { var v = f.draft(NOW.plusHours(1), NOW.plusHours(2)); f.shipping(v, RateScopeType.INTRA_PROVINCE, "30000.00"); return v; }
    <T> T proxy(T target, Tx tx) {
        var advice = new TransactionInterceptor(); advice.setTransactionManager(tx); advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource()); advice.afterPropertiesSet();
        var factory = new ProxyFactory(target); factory.setProxyTargetClass(true); factory.addAdvice(advice); return (T) factory.getProxy();
    }
    @ParameterizedTest @ValueSource(strings = {"version", "audit"})
    void failedPublicationTriggersRollbackDecision(String at) {
        var v = configured(); var failure = new DataIntegrityViolationException("Synthetic persistence failure");
        if (at.equals("version")) { doThrow(failure).when(f.versions).saveAndFlush(any()); } else { doThrow(failure).when(f.audits).saveAndFlush(any()); }
        var tx = new Tx(null); AdminPricingService p = proxy(f.management, tx);
        assertThrows(DataIntegrityViolationException.class, () -> p.publish(v.getId(), v.getVersion(), true)); assertEquals(1, tx.rollbacks); assertEquals(0, tx.commits);
    }
    @Test void failedRuleAuditTriggersRollbackDecisionForEntireDraftChange() {
        var v = configured(); doThrow(new DataIntegrityViolationException("Synthetic audit failure")).when(f.audits).saveAndFlush(any());
        var tx = new Tx(null); AdminPricingService p = proxy(f.management, tx);
        assertThrows(DataIntegrityViolationException.class, () -> p.editPeriod(v.getId(), v.getVersion(), new PricingCommands.Period(NOW.plusHours(3), null)));
        assertEquals(1, tx.rollbacks); assertEquals(0, tx.commits);
    }
    @Test void twoAdminsCannotPublishOverlappingBooksUnderGateModel() throws Exception {
        var a = configured(); var b = configured(); Long av = a.getVersion(), bv = b.getVersion();
        race(p -> p.publish(a.getId(), av, true), p -> p.publish(b.getId(), bv, true));
        assertEquals(1, f.books.values().stream().filter(v -> v.getStatus() == PricingVersionStatus.PUBLISHED).count());
        assertEquals(1, f.auditRows.stream().filter(audit -> audit.getAction().equals("PRICE_PUBLISHED")).count());
    }
    @Test void concurrentDraftEditsHaveOneWinnerUnderGateModel() throws Exception {
        var v = configured(); Long expected = v.getVersion();
        race(p -> p.editPeriod(v.getId(), expected, new PricingCommands.Period(NOW.plusHours(3), null)),
            p -> p.editPeriod(v.getId(), expected, new PricingCommands.Period(NOW.plusHours(4), null)));
        assertEquals(1, f.auditRows.stream().filter(audit -> audit.getAction().equals("PRICE_DRAFT_PERIOD")).count());
    }
    @Test void doubleSubmitPublishHasOneAuditAndOneWinner() throws Exception {
        var v = configured(); Long expected = v.getVersion(); race(p -> p.publish(v.getId(), expected, true), p -> p.publish(v.getId(), expected, true));
        assertEquals(1, f.auditRows.stream().filter(a -> a.getAction().equals("PRICE_PUBLISHED")).count());
    }
    @Test void refreshOccursAfterServiceLockAndBeforeExpectedVersionCheck() {
        var v = configured(); Long expected = v.getVersion(); clearInvocations(f.services, f.entityManager, f.versions);
        doAnswer(c -> { v.setVersion(v.getVersion() + 1); return null; }).when(f.entityManager).refresh(v);
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> f.management.publish(v.getId(), expected, true));
        var order = inOrder(f.versions, f.services, f.entityManager); order.verify(f.versions).findServiceId(v.getId()); order.verify(f.services).findForPricingUpdate(21L);
        order.verify(f.entityManager).refresh(f.catalog, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE); order.verify(f.versions).findById(v.getId()); order.verify(f.entityManager).refresh(v);
        assertEquals(PricingVersionStatus.DRAFT, v.getStatus());
    }
    @Test void confirmationRechecksExpiryAfterWaitingForCatalogGate() {
        f.legacy.setActive(true); f.as(f.customer); String token = f.quoting.quote(form()); var q = f.quoteRows.get(token);
        when(f.services.findByIdAndIsActiveTrue(21L)).thenAnswer(c -> { f.time = q.getExpiresAt(); return Optional.of(f.catalog); });
        assertThrows(ShipmentCreationException.class, () -> f.quoting.confirm(token)); assertTrue(f.shipmentRows.isEmpty()); assertNull(q.getConsumedAt());
    }
    @Test void refreshedCatalogConditionsAreUsedAfterGateWait() {
        f.legacy.setActive(true); doAnswer(c -> { f.catalog.setActive(false); return null; }).when(f.entityManager).refresh(f.catalog, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        assertThrows(ShipmentCreationException.class, () -> f.quoting.estimate(f.guest())); assertTrue(f.quoteRows.isEmpty());
    }
    @Test void publicationAndUserQuoteCannotMixOldAndNewComponentsUnderGateModel() throws Exception {
        f.legacy.setActive(true); var v = f.draft(NOW.plusMinutes(1), null); f.shipping(v, RateScopeType.INTRA_PROVINCE, "30000.00"); f.pickup(v, "9000.00");
        f.management.retireLegacy(41L, false, v.getValidFrom(), true); f.management.retireLegacy(61L, true, v.getValidFrom(), true); f.time = v.getValidFrom();
        var lock = new ReentrantLock(); var gate = (org.mockito.stubbing.Answer<Optional<ShippingService>>) call -> {
            if (!lock.tryLock(5, TimeUnit.SECONDS)) { throw new IllegalStateException("Gate timeout"); } return Optional.of(f.catalog);
        };
        when(f.services.findForPricingUpdate(21L)).thenAnswer(gate); when(f.services.findByIdAndIsActiveTrue(21L)).thenAnswer(gate);
        AdminPricingService publisher = proxy(f.management, new Tx(lock)); QuoteService quoting = proxy(f.quoting, new Tx(lock));
        var start = new CountDownLatch(1); var workers = Executors.newFixedThreadPool(2); Long expected = v.getVersion();
        try {
            var publication = workers.submit(() -> { f.as(f.admin); try { if (!start.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("Timeout"); } publisher.publish(v.getId(), expected, true); return true; } finally { SecurityContextHolder.clearContext(); } });
            var quote = workers.submit(() -> { f.as(f.customer); try {
                if (!start.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("Timeout"); }
                var form = form(); form.setShipmentMethod(ShipmentMethod.HOME_PICKUP);
                try { return quoting.quote(form); } catch (ShipmentCreationException notYetPublished) { return null; }
            } finally { SecurityContextHolder.clearContext(); } });
            start.countDown(); assertTrue(publication.get(10, TimeUnit.SECONDS)); String token = quote.get(10, TimeUnit.SECONDS);
            if (token != null) { var q = f.quoteRows.get(token); assertEquals(new java.math.BigDecimal("44000.00"), q.getTotalFee()); assertSame(v, q.getShippingRateRule().getPricingVersion()); assertSame(v, q.getPickupFeeRule().getPricingVersion()); }
            assertTrue(f.shipmentRows.isEmpty());
        } finally { workers.shutdownNow(); }
    }
    interface Change { void run(AdminPricingService proxy); }
    void race(Change a, Change b) throws Exception {
        var lock = new ReentrantLock(); when(f.services.findForPricingUpdate(21L)).thenAnswer(c -> {
            if (!lock.tryLock(5, TimeUnit.SECONDS)) { throw new IllegalStateException("Gate timeout"); } return Optional.of(f.catalog);
        });
        AdminPricingService p = proxy(f.management, new Tx(lock)); User second = owner(); second.setId(3L); second.setEmail("second-pricing-admin@example.test");
        f.actors.put(3L, second); f.permissions.put(3L, List.of("ROLE_ADMIN")); ExecutorService workers = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> outcomes = new ArrayList<>();
            for (int i = 0; i < 2; i++) { Change change = i == 0 ? a : b; User actor = i == 0 ? f.admin : second;
                outcomes.add(workers.submit(() -> { f.as(actor); try {
                    if (!start.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("Start timeout"); }
                    try { change.run(p); return true; } catch (ObjectOptimisticLockingFailureException | AdminRuleException conflict) { return false; }
                } finally { SecurityContextHolder.clearContext(); } }));
            }
            start.countDown(); int winners = 0; for (var outcome : outcomes) { if (outcome.get(10, TimeUnit.SECONDS)) { winners++; } } assertEquals(1, winners);
        } finally { workers.shutdownNow(); }
    }
    static class Tx extends AbstractPlatformTransactionManager {
        final ReentrantLock lock; int commits, rollbacks;
        Tx(ReentrantLock lock) { this.lock = lock; }
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; release(); }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; release(); }
        void release() { if (lock != null && lock.isHeldByCurrentThread()) { lock.unlock(); } }
    }
}
