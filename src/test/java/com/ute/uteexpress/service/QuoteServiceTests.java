package com.ute.uteexpress.service;

import com.ute.uteexpress.dto.shipment.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import jakarta.validation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;

import static com.ute.uteexpress.shipment.ShipmentFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Persistence boundaries mocked; transaction/locking models are not SQL Server integration tests. */
class QuoteServiceTests {
    private static ValidatorFactory validation;
    private final UserRepository users = mock(UserRepository.class);
    private final UserRoleRepository roles = mock(UserRoleRepository.class);
    private final ShipmentQuoteRepository quotes = mock(ShipmentQuoteRepository.class);
    private final ShipmentRepository shipments = mock(ShipmentRepository.class);
    private final ShipmentEventRepository events = mock(ShipmentEventRepository.class);
    private final BranchRepository branches = mock(BranchRepository.class);
    private final ShippingServiceRepository services = mock(ShippingServiceRepository.class);
    private final ShippingRateRuleRepository rates = mock(ShippingRateRuleRepository.class);
    private final PickupFeeRuleRepository pickups = mock(PickupFeeRuleRepository.class);
    private final BranchNetworkService network = mock(BranchNetworkService.class);
    private final TrackingNumberGenerator tracking = mock(TrackingNumberGenerator.class);
    private final Clock clock = mock(Clock.class);
    private QuoteService service;
    private ShipmentQuote saved;
    private Shipment created;
    private ShippingRateRule rate;
    private PickupFeeRule pickup;
    private User owner;

    @BeforeAll static void validation() { validation = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void close() { validation.close(); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @BeforeEach void setup() {
        owner = owner(); authenticate();
        when(users.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        when(roles.findRoleNamesByUserId(1L)).thenReturn(List.of("ROLE_USER"));
        when(clock.instant()).thenReturn(NOW.toInstant(ZoneOffset.UTC));
        when(network.quoteServingBranches(anyString(), anyString(), anyString(), any())).thenReturn(List.of(option(11L)));
        when(network.quoteDeliveryBranches(anyString(), anyString(), anyString())).thenReturn(List.of(option(12L)));
        when(network.quoteConfiguredHub(11L)).thenReturn(101L); when(network.quoteConfiguredHub(12L)).thenReturn(102L);
        for (long id : new long[] {11L, 12L, 101L, 102L}) {
            when(branches.findByIdAndStatus(id, BranchStatus.ACTIVE)).thenReturn(Optional.of(branch(id)));
        }
        when(services.findByIdAndIsActiveTrue(21L)).thenReturn(Optional.of(catalog()));
        rate = rate(); rate.setId(41L);
        when(rates.findEffectiveRules(eq(21L), any(), any())).thenReturn(List.of(rate));
        pickup = new PickupFeeRule(); pickup.setId(61L); pickup.setAmount(new BigDecimal("7500.00"));
        pickup.setBranch(branch(11L)); pickup.setService(catalog()); pickup.setEffectiveFrom(NOW.minusDays(1));
        when(pickups.findEffectiveRules(eq(11L), eq(21L), any())).thenReturn(List.of(pickup));
        when(quotes.saveAndFlush(any())).thenAnswer(call -> {
            saved = call.getArgument(0); saved.setId(51L); return saved;
        });
        when(quotes.findOwnedForUpdate(anyString(), eq(1L))).thenAnswer(call ->
            saved != null && saved.getToken().equals(call.getArgument(0)) ? Optional.of(saved) : Optional.empty());
        when(quotes.findByTokenAndRequesterId(anyString(), eq(1L))).thenAnswer(call ->
            saved != null && saved.getToken().equals(call.getArgument(0)) ? Optional.of(saved) : Optional.empty());
        when(tracking.next()).thenReturn("UTE" + "A".repeat(32));
        when(shipments.saveAndFlush(any())).thenAnswer(call -> {
            created = call.getArgument(0); created.setId(31L); return created;
        });
        when(shipments.findByQuoteIdAndSenderId(51L, 1L)).thenAnswer(call -> Optional.ofNullable(created));
        service = new QuoteService(users, roles, quotes, shipments, events, branches, services, rates, pickups,
            network, tracking, clock, validation.getValidator(), Duration.ofMinutes(15), mock(jakarta.persistence.EntityManager.class), mock(PricingVersionRepository.class));
    }
    private ShipmentOptions.BranchOption option(Long id) { return new ShipmentOptions.BranchOption(id, "Branch", "Province"); }
    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            owner.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
    private String homeQuote() { var form = form(); form.setShipmentMethod(ShipmentMethod.HOME_PICKUP); return service.quote(form); }

    @Test void homePickupChargesDatabaseSurchargeWithoutCreatingShipmentAtQuoteTime() {
        homeQuote();
        assertEquals(new BigDecimal("25000.00"), saved.getShippingFee());
        assertEquals(new BigDecimal("7500.00"), saved.getPickupFee()); assertEquals(new BigDecimal("32500.00"), saved.getTotalFee());
        assertSame(pickup, saved.getPickupFeeRule()); assertEquals(1L, saved.getRequester().getId());
        assertEquals(NOW.plusMinutes(15), saved.getExpiresAt()); verifyNoInteractions(shipments, events, tracking);
    }
    @Test void dropoffNeverConsultsOrChargesPickupRules() {
        service.quote(form()); assertEquals(new BigDecimal("0.00"), saved.getPickupFee());
        assertEquals(new BigDecimal("25000.00"), saved.getTotalFee()); assertNull(saved.getPickupFeeRule()); verifyNoInteractions(pickups);
    }
    @Test void uniqueCoveredHomeBranchIsDeterminedByServer() {
        var form = form(); form.setShipmentMethod(ShipmentMethod.HOME_PICKUP); form.setOriginBranchId(null);
        service.quote(form); assertEquals(11L, saved.getOriginBranch().getId());
    }
    @Test void ambiguousHomeCoverageRequiresExplicitValidChoice() {
        when(network.quoteServingBranches(anyString(), anyString(), anyString(), any())).thenReturn(List.of(option(11L), option(13L)));
        var form = form(); form.setShipmentMethod(ShipmentMethod.HOME_PICKUP); form.setOriginBranchId(null);
        assertThrows(ShipmentCreationException.class, () -> service.quote(form)); verifyNoInteractions(quotes, shipments);
    }
    @Test void dropoffRequiresSelectedBranch() {
        var form = form(); form.setOriginBranchId(null); assertThrows(ShipmentCreationException.class, () -> service.quote(form));
        verifyNoInteractions(quotes, shipments);
    }
    @Test void missingMethodIsRejectedBeforeLookingUpPrices() {
        var form = form(); form.setShipmentMethod(null); assertThrows(ConstraintViolationException.class, () -> service.quote(form));
        verifyNoInteractions(rates, pickups, quotes);
    }
    @Test void missingOrOverlappingPickupRulesCannotBeHiddenWithZeroFee() {
        for (var matching : List.of(List.<PickupFeeRule>of(), List.of(pickup, pickup))) {
            when(pickups.findEffectiveRules(anyLong(), anyLong(), any())).thenReturn(matching);
            assertThrows(ShipmentCreationException.class, this::homeQuote);
        }
        verifyNoInteractions(quotes, shipments);
    }
    @Test void nonpositivePickupRuleIsInvalid() {
        pickup.setAmount(BigDecimal.ZERO); assertThrows(ShipmentCreationException.class, this::homeQuote);
    }
    @Test void totalOverflowFailsBeforeSavingAnyQuote() {
        pickup.setAmount(new BigDecimal("9999999999999999.99")); assertThrows(ShipmentCreationException.class, this::homeQuote);
        verifyNoInteractions(quotes, shipments);
    }
    @Test void shippingRuleWithZeroWeightStepIsRejected() {
        rate.setExtraWeightStepKg(BigDecimal.ZERO); assertThrows(ShipmentCreationException.class, () -> service.quote(form()));
    }
    @Test void quoteCannotOutliveEitherEffectivePriceRule() {
        rate.setEffectiveTo(NOW.plusMinutes(8)); pickup.setEffectiveTo(NOW.plusMinutes(4));
        homeQuote(); assertEquals(NOW.plusMinutes(4), saved.getExpiresAt());
    }
    @Test void expiredQuoteIncludingExactBoundaryCannotCreateShipment() {
        String token = service.quote(form()); when(clock.instant()).thenReturn(saved.getExpiresAt().toInstant(ZoneOffset.UTC));
        assertFalse(service.review(token).confirmable());
        assertThrows(ShipmentCreationException.class, () -> service.confirm(token)); verifyNoInteractions(shipments, events);
        assertNull(saved.getConsumedAt());
    }
    @Test void anotherOwnerAndUnknownQuoteHaveSame404WithoutLoadingShipment() {
        String token = service.quote(form());
        User other = owner(); other.setId(2L); when(users.findByEmail(owner.getEmail())).thenReturn(Optional.of(other));
        when(roles.findRoleNamesByUserId(2L)).thenReturn(List.of("ROLE_USER"));
        for (String input : List.of(token, "f".repeat(32))) {
            assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.confirm(input)).getStatusCode().value());
            assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.review(input)).getStatusCode().value());
        }
        verifyNoInteractions(shipments, events);
    }
    @Test void snapshotAllFeesInputRouteAndMethodAndCreateOnlyOneInitialEvent() {
        var form = form(); form.setShipmentMethod(ShipmentMethod.HOME_PICKUP); form.setNote("private note");
        form.setCodAmount(new BigDecimal("100000.00")); form.setLengthCm(new BigDecimal("12.50"));
        String token = service.quote(form);
        form.setSenderName("changed on client"); rate.setBasePrice(new BigDecimal("999999.00")); pickup.setAmount(BigDecimal.ONE);
        assertEquals(31L, service.confirm(token));
        assertEquals(saved.getSenderName(), created.getSenderName()); assertEquals(saved.getNote(), created.getNote());
        assertEquals(saved.getLengthCm(), created.getLengthCm()); assertEquals(saved.getCodAmount(), created.getCodAmount());
        assertEquals(ShipmentMethod.HOME_PICKUP, created.getShipmentMethod()); assertSame(saved, created.getQuote());
        assertEquals(101L, created.getOriginHub().getId()); assertEquals(102L, created.getDestinationHub().getId());
        assertEquals(new BigDecimal("32500.00"), created.getTotalFee()); assertEquals(new BigDecimal("7500.00"), created.getPickupFee());
        assertEquals(new BigDecimal("25000.00"), created.getShippingFee()); assertEquals(new BigDecimal("0.00"), created.getInsuranceFee());
        assertNull(created.getCurrentBranch()); assertEquals(ShipmentStatus.CREATED, created.getStatus());
        verify(rates, times(1)).findEffectiveRules(anyLong(), any(), any());
        var event = org.mockito.ArgumentCaptor.forClass(ShipmentEvent.class); verify(events).saveAndFlush(event.capture());
        assertEquals("Đã tạo vận đơn", event.getValue().getEventTitle()); assertNull(event.getValue().getBranch());
        assertSame(owner, event.getValue().getActor()); assertNull(event.getValue().getFromStatus());
    }
    @Test void repeatedConfirmationEvenAfterExpiryReturnsSameShipment() {
        String token = service.quote(form()); assertEquals(31L, service.confirm(token));
        when(clock.instant()).thenReturn(NOW.plusHours(1).toInstant(ZoneOffset.UTC));
        assertEquals(31L, service.confirm(token)); verify(shipments, times(1)).saveAndFlush(any()); verify(events, times(1)).saveAndFlush(any());
    }
    @Test void invalidOriginIdOrNoCoverageIsRejected() {
        var form = form(); form.setOriginBranchId(99L); assertThrows(ShipmentCreationException.class, () -> service.quote(form));
        when(network.quoteServingBranches(anyString(), anyString(), anyString(), any())).thenReturn(List.of());
        assertThrows(ShipmentCreationException.class, () -> service.quote(form())); verifyNoInteractions(quotes, shipments);
    }
    @Test void noExplicitDeliveryCoverageIsRejected() {
        when(network.quoteDeliveryBranches(anyString(), anyString(), anyString())).thenReturn(List.of());
        assertThrows(ShipmentCreationException.class, () -> service.quote(form())); verifyNoInteractions(quotes, shipments);
    }
    @Test void disabledServiceOrRemovedCoverageAfterQuoteRequiresNewQuote() {
        String token = service.quote(form()); when(services.findByIdAndIsActiveTrue(21L)).thenReturn(Optional.empty());
        assertThrows(ShipmentCreationException.class, () -> service.confirm(token));
        when(services.findByIdAndIsActiveTrue(21L)).thenReturn(Optional.of(catalog()));
        when(network.quoteDeliveryBranches(anyString(), anyString(), anyString())).thenReturn(List.of());
        assertThrows(ShipmentCreationException.class, () -> service.confirm(token)); verifyNoInteractions(shipments, events);
    }
    @Test void changedHubAfterQuoteIsRejectedWithoutRewritingSnapshot() {
        String token = service.quote(form()); when(network.quoteConfiguredHub(11L)).thenReturn(999L);
        assertThrows(ShipmentCreationException.class, () -> service.confirm(token));
        assertEquals(101L, saved.getOriginHub().getId()); verifyNoInteractions(shipments, events);
    }
    @Test void lockedOrRevokedUserCannotConfirmOldQuote() {
        String token = service.quote(form()); owner.setStatus(UserStatus.LOCKED);
        assertThrows(AccessDeniedException.class, () -> service.confirm(token)); owner.setStatus(UserStatus.ACTIVE);
        when(roles.findRoleNamesByUserId(1L)).thenReturn(List.of("ROLE_ADMIN"));
        assertThrows(AccessDeniedException.class, () -> service.confirm(token)); verifyNoInteractions(shipments, events);
    }
    @Test void eventFailureTriggersRollbackBeforeQuoteIsConsumed() {
        String token = service.quote(form()); var tx = new Transactions(null);
        doThrow(new DataIntegrityViolationException("test-only failure")).when(events).saveAndFlush(any());
        assertThrows(DataIntegrityViolationException.class, () -> transactional(tx).confirm(token));
        assertEquals(1, tx.rollbacks); assertEquals(0, tx.commits); assertNull(saved.getConsumedAt());
    }
    @Test void twoConcurrentConfirmationsCreateOneShipmentUnderSharedRowLockContract() throws Exception {
        String token = service.quote(form()); ReentrantLock rowLock = new ReentrantLock();
        when(quotes.findOwnedForUpdate(token, 1L)).thenAnswer(call -> {
            if (!rowLock.tryLock(5, TimeUnit.SECONDS)) { throw new IllegalStateException("lock timeout"); }
            return Optional.of(saved);
        });
        QuoteService proxy = transactional(new Transactions(rowLock));
        ExecutorService workers = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Long>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(workers.submit(() -> {
                    authenticate();
                    try { if (!start.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("start timeout"); } return proxy.confirm(token); }
                    finally { SecurityContextHolder.clearContext(); }
                }));
            }
            start.countDown(); for (var result : results) { assertEquals(31L, result.get(10, TimeUnit.SECONDS)); }
            verify(shipments, times(1)).saveAndFlush(any()); verify(events, times(1)).saveAndFlush(any());
        } finally { workers.shutdownNow(); }
    }
    private QuoteService transactional(Transactions manager) {
        TransactionInterceptor advice = new TransactionInterceptor(); advice.setTransactionManager(manager);
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource()); advice.afterPropertiesSet();
        ProxyFactory factory = new ProxyFactory(service); factory.setProxyTargetClass(true); factory.addAdvice(advice);
        return (QuoteService) factory.getProxy();
    }
    private static class Transactions extends AbstractPlatformTransactionManager {
        int commits, rollbacks; final ReentrantLock rowLock;
        Transactions(ReentrantLock rowLock) { this.rowLock = rowLock; }
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; release(); }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; release(); }
        private void release() { if (rowLock != null && rowLock.isHeldByCurrentThread()) { rowLock.unlock(); } }
    }
}
