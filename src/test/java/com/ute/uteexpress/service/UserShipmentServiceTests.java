package com.ute.uteexpress.service;

import com.ute.uteexpress.dto.shipment.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import jakarta.persistence.criteria.*;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.ConstraintViolationException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
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

class UserShipmentServiceTests {
    private static ValidatorFactory validation;
    private UserRepository users;
    private UserRoleRepository roles;
    private ShipmentRepository shipments;
    private ShipmentEventRepository events;
    private BranchRepository branches;
    private ShippingServiceRepository services;
    private ShippingRateRuleRepository rates;
    private TrackingNumberGenerator tracking;
    private UserShipmentService service;
    private User owner;
    private ShippingRateRule rate;

    @BeforeAll static void validators() { validation = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void closeValidators() { validation.close(); }
    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @BeforeEach void setup() {
        users = mock(UserRepository.class); roles = mock(UserRoleRepository.class);
        shipments = mock(ShipmentRepository.class); events = mock(ShipmentEventRepository.class);
        branches = mock(BranchRepository.class); services = mock(ShippingServiceRepository.class);
        rates = mock(ShippingRateRuleRepository.class); tracking = mock(TrackingNumberGenerator.class);
        owner = owner(); rate = rate();
        authenticate("ROLE_USER");
        when(users.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        when(roles.findRoleNamesByUserId(1L)).thenReturn(List.of("ROLE_USER"));
        when(branches.findByIdAndStatus(11L, BranchStatus.ACTIVE)).thenReturn(Optional.of(branch(11L)));
        when(branches.findByIdAndStatus(12L, BranchStatus.ACTIVE)).thenReturn(Optional.of(branch(12L)));
        when(services.findByIdAndIsActiveTrue(21L)).thenReturn(Optional.of(catalog()));
        when(rates.findEffectiveRules(eq(21L), any(RateScopeType.class), eq(NOW))).thenReturn(List.of(rate));
        when(tracking.next()).thenReturn("UTE_TEST_TRACKING");
        when(shipments.saveAndFlush(any(Shipment.class))).thenAnswer(invocation -> {
            Shipment saved = invocation.getArgument(0); saved.setId(31L); return saved;
        });
        // Deliberately non-UTC clock zone: stored DATETIME2 values must still be UTC.
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneId.of("Asia/Bangkok"));
        service = new UserShipmentService(users, roles, shipments, events, branches, services, rates,
            tracking, clock, validation.getValidator());
    }

    @Test void createsOwnedShipmentAndRealCreatedEventUsingIntraProvinceDatabaseRate() {
        ShipmentCreateForm form = form(); form.setCodAmount(new BigDecimal("100000.00"));
        assertEquals(31L, service.create(form));
        var saved = org.mockito.ArgumentCaptor.forClass(Shipment.class);
        verify(shipments).saveAndFlush(saved.capture());
        Shipment shipment = saved.getValue();
        assertSame(owner, shipment.getSender());
        assertEquals(ShipmentStatus.CREATED, shipment.getStatus());
        assertEquals(ShipmentPaymentStatus.UNPAID, shipment.getPaymentStatus());
        assertEquals(new BigDecimal("25000.00"), shipment.getShippingFee());
        assertEquals(shipment.getShippingFee(), shipment.getTotalFee());
        assertEquals(new BigDecimal("0.00"), shipment.getInsuranceFee());
        assertNull(shipment.getCurrentBranch());
        assertEquals(NOW, shipment.getCreatedAt());
        verify(rates).findEffectiveRules(21L, RateScopeType.INTRA_PROVINCE, NOW);
        var event = org.mockito.ArgumentCaptor.forClass(ShipmentEvent.class);
        verify(events).saveAndFlush(event.capture());
        assertSame(shipment, event.getValue().getShipment());
        assertSame(owner, event.getValue().getActor());
        assertNull(event.getValue().getFromStatus());
        assertNull(event.getValue().getBranch());
        assertEquals(ShipmentStatus.CREATED, event.getValue().getToStatus());
        assertEquals(NOW, event.getValue().getOccurredAt());
    }

    @Test void differentProvincesUseInterProvinceNeverInterRegion() {
        ShipmentCreateForm form = form(); form.setReceiverProvince("Đà Nẵng");
        Branch destination = branch(12L); destination.setProvince("Đà Nẵng");
        when(branches.findByIdAndStatus(12L, BranchStatus.ACTIVE)).thenReturn(Optional.of(destination));
        service.create(form);
        verify(rates).findEffectiveRules(21L, RateScopeType.INTER_PROVINCE, NOW);
        verify(rates, never()).findEffectiveRules(anyLong(), eq(RateScopeType.INTER_REGION), any());
    }

    @Test void missingOrExpiredRateDoesNotPersistShipmentOrEvent() {
        when(rates.findEffectiveRules(anyLong(), any(), any())).thenReturn(List.of());
        var exception = assertThrows(ShipmentCreationException.class, () -> service.create(form()));
        assertEquals("serviceId", exception.getField());
        assertTrue(exception.getMessage().contains("Chưa có bảng giá"));
        verify(shipments, never()).saveAndFlush(any());
        verifyNoInteractions(events, tracking);
    }

    @Test void overlappingEffectiveRulesAreRejectedRatherThanChoosingAnArbitraryPrice() {
        when(rates.findEffectiveRules(anyLong(), any(), any())).thenReturn(List.of(rate, rate()));
        assertThrows(ShipmentCreationException.class, () -> service.create(form()));
        verify(shipments, never()).saveAndFlush(any());
    }

    @Test void roundsUpOnlyExcessWeightStepsAndUsesDatabasePrices() {
        for (String[] sample : new String[][] {
                {"0.50", "20000.00"}, {"1.00", "20000.00"}, {"1.01", "25000.00"},
                {"1.50", "25000.00"}, {"1.51", "30000.00"}}) {
            ShipmentCreateForm form = form(); form.setWeightKg(new BigDecimal(sample[0]));
            service.create(form);
            verify(shipments).saveAndFlush(argThat(shipment ->
                shipment.getWeightKg().equals(form.getWeightKg())
                    && shipment.getShippingFee().equals(new BigDecimal(sample[1]))));
        }
    }

    @Test void noActiveBranchIsRejectedWithoutInsertingAnything() {
        when(branches.findByIdAndStatus(11L, BranchStatus.ACTIVE)).thenReturn(Optional.empty());
        assertEquals("originBranchId", assertThrows(ShipmentCreationException.class,
            () -> service.create(form())).getField());
        verifyNoInteractions(shipments, events, tracking);
    }

    @Test void invalidDestinationAndMismatchedProvinceAreRejected() {
        when(branches.findByIdAndStatus(12L, BranchStatus.ACTIVE)).thenReturn(Optional.empty());
        assertEquals("destinationBranchId", assertThrows(ShipmentCreationException.class,
            () -> service.create(form())).getField());
        ShipmentCreateForm mismatch = form(); mismatch.setSenderProvince("Hà Nội");
        assertEquals("originBranchId", assertThrows(ShipmentCreationException.class,
            () -> service.create(mismatch)).getField());
        verifyNoInteractions(shipments, events);
    }

    @Test void inactiveOrMissingShippingServiceIsRejected() {
        when(services.findByIdAndIsActiveTrue(21L)).thenReturn(Optional.empty());
        assertEquals("serviceId", assertThrows(ShipmentCreationException.class,
            () -> service.create(form())).getField());
        verifyNoInteractions(shipments, events, rates);
    }

    @Test void invalidFormIsRejectedByServiceEvenWithoutControllerValidation() {
        ShipmentCreateForm form = form(); form.setWeightKg(BigDecimal.ZERO);
        assertThrows(ConstraintViolationException.class, () -> service.create(form));
        verifyNoInteractions(branches, services, rates, shipments, events);
    }

    @Test void calculatedFeeOverflowIsRejectedBeforePersisting() {
        rate.setBasePrice(new BigDecimal("9999999999999999.99"));
        assertEquals("weightKg", assertThrows(ShipmentCreationException.class,
            () -> service.create(form())).getField());
        verifyNoInteractions(shipments, events);
    }

    @Test void uniqueConstraintCollisionCannotCreateAnEventOrReturnSuccess() {
        when(shipments.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("test-only duplicate"));
        assertThrows(DataIntegrityViolationException.class, () -> service.create(form()));
        verifyNoInteractions(events);
    }

    @Test void eventFailureRollsBackTheServiceTransaction() {
        doThrow(new DataIntegrityViolationException("test-only event failure")).when(events).saveAndFlush(any());
        RecordingTransactions transactions = new RecordingTransactions();
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactions);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        interceptor.afterPropertiesSet();
        ProxyFactory factory = new ProxyFactory(service); factory.setProxyTargetClass(true);
        factory.addAdvice(interceptor);
        var proxy = (UserShipmentService) factory.getProxy();
        assertThrows(DataIntegrityViolationException.class, () -> proxy.create(form()));
        assertEquals(1, transactions.rollbacks); assertEquals(0, transactions.commits);
        assertFalse(transactions.readOnly);
    }

    @Test void optionsContainOnlyQueriedActiveCatalogData() {
        when(branches.findByStatusOrderByNameAsc(BranchStatus.ACTIVE)).thenReturn(List.of(branch(11L)));
        when(services.findByIsActiveTrueOrderByNameAsc()).thenReturn(List.of(catalog()));
        assertEquals(options(), service.options());
        verify(branches).findByStatusOrderByNameAsc(BranchStatus.ACTIVE);
        verify(services).findByIsActiveTrueOrderByNameAsc();
    }

    @Test void detailAndHistoryAreOwnershipScopedAndUseBoundedTimeline() {
        when(shipments.findByIdAndSenderId(31L, 1L)).thenReturn(Optional.of(shipment()));
        ShipmentEvent event = new ShipmentEvent(); event.setEventTitle("Sự kiện đã lưu");
        event.setToStatus(ShipmentStatus.CREATED); event.setOccurredAt(NOW);
        when(events.findByShipmentIdAndShipmentSenderId(eq(31L), eq(1L), any(Pageable.class)))
            .thenAnswer(invocation -> new PageImpl<>(List.of(event), invocation.getArgument(2), 41));
        var result = service.detail(31L, 1);
        assertEquals("UTE_TEST_TRACKING", result.trackingNumber());
        assertEquals("Sự kiện đã lưu", result.events().getContent().getFirst().title());
        assertEquals(1, result.events().getNumber()); assertEquals(20, result.events().getSize());
        assertNull(result.currentBranch());
        verify(shipments, never()).findById(anyLong());
        var page = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(events).findByShipmentIdAndShipmentSenderId(eq(31L), eq(1L), page.capture());
        assertEquals(Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")), page.getValue().getSort());
    }

    @Test void anotherOwnersShipmentAndMissingShipmentAreIndistinguishable404WithoutTimelineLookup() {
        when(shipments.findByIdAndSenderId(anyLong(), eq(1L))).thenReturn(Optional.empty());
        for (long id : new long[] {99L, 999L}) {
            assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.detail(id, 0))
                .getStatusCode().value());
        }
        verifyNoInteractions(events);
    }

    @Test void legacyShipmentWithoutEventsDoesNotInventATimeline() {
        when(shipments.findByIdAndSenderId(31L, 1L)).thenReturn(Optional.of(shipment()));
        when(events.findByShipmentIdAndShipmentSenderId(eq(31L), eq(1L), any(Pageable.class))).thenReturn(Page.empty());
        assertTrue(service.detail(31L, 0).events().isEmpty());
        verify(events, never()).saveAndFlush(any());
    }

    @Test void listUsesFixedNewestFirstSortAndNeverClientSortFields() {
        when(shipments.findAll(org.mockito.ArgumentMatchers.<Specification<Shipment>>any(), any(Pageable.class)))
            .thenAnswer(invocation -> new PageImpl<>(List.of(shipment()), invocation.getArgument(1), 42));
        var result = service.list("UTE", ShipmentStatus.CREATED, PageRequest.of(1, 20, Sort.by("sender.passwordHash")));
        assertEquals(42, result.getTotalElements()); assertEquals(1, result.getNumber());
        assertEquals("UTE_TEST_TRACKING", result.getContent().getFirst().trackingNumber());
        assertEquals(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")), result.getSort());
    }

    @SuppressWarnings("unchecked")
    @Test void searchAlwaysAndsOwnershipWithStatusAndEscapedTrackingFilter() {
        Root<Shipment> root = mock(Root.class); CriteriaBuilder builder = mock(CriteriaBuilder.class);
        Path<User> sender = mock(Path.class); Path<Object> ownerId = mock(Path.class);
        Path<Object> status = mock(Path.class); Path<String> code = mock(Path.class);
        Expression<String> upper = mock(Expression.class);
        Predicate owned = mock(Predicate.class), filtered = mock(Predicate.class), matched = mock(Predicate.class);
        when(root.<User>get("sender")).thenReturn(sender); when(sender.get("id")).thenReturn(ownerId);
        when(root.get("status")).thenReturn(status); when(root.<String>get("trackingNumber")).thenReturn(code);
        when(builder.upper(code)).thenReturn(upper);
        when(builder.equal(ownerId, 1L)).thenReturn(owned);
        when(builder.equal(status, ShipmentStatus.CREATED)).thenReturn(filtered);
        when(builder.like(upper, "%UTE\\%\\_\\[%", '\\')).thenReturn(matched);
        UserShipmentService.ownedSearch(1L, " ute%_[ ", ShipmentStatus.CREATED).toPredicate(root, null, builder);
        verify(builder).and(new Predicate[] {owned, filtered, matched});
    }

    @Test void lockedUnverifiedOrRevokedUserCannotUseTheModuleWithAStaleSession() {
        for (UserStatus status : List.of(UserStatus.LOCKED, UserStatus.UNVERIFIED)) {
            owner.setStatus(status);
            assertThrows(AccessDeniedException.class, () -> service.create(form()));
            assertThrows(AccessDeniedException.class, service::options);
        }
        owner.setStatus(UserStatus.ACTIVE);
        when(roles.findRoleNamesByUserId(1L)).thenReturn(List.of("ROLE_MANAGER"));
        assertThrows(AccessDeniedException.class, () -> service.detail(31L, 0));
        verifyNoInteractions(shipments, events);
    }

    @Test void multiRoleUserIsAcceptedButAdminOnlyAndGuestsAreDenied() {
        authenticate("ROLE_USER", "ROLE_ADMIN");
        when(roles.findRoleNamesByUserId(1L)).thenReturn(List.of("ROLE_USER", "ROLE_ADMIN"));
        assertEquals(31L, service.create(form()));
        authenticate("ROLE_ADMIN");
        assertThrows(AccessDeniedException.class, () -> service.create(form()));
        SecurityContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, service::options);
    }

    @Test void generatedTrackingCodesHaveCorrectLengthAndAreDistinct() {
        var generator = new TrackingNumberGenerator(); Set<String> generated = new HashSet<>();
        for (int i = 0; i < 10000; i++) {
            String code = generator.next(); assertTrue(code.matches("UTE[A-F0-9]{32}")); assertTrue(generated.add(code));
        }
        var constraints = Shipment.class.getAnnotation(jakarta.persistence.Table.class).uniqueConstraints();
        assertTrue(Arrays.stream(constraints).anyMatch(constraint ->
            Arrays.equals(new String[] {"tracking_number"}, constraint.columnNames())));
    }

    private void authenticate(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            "shipment-fixture@example.test", null, Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    private static class RecordingTransactions extends AbstractPlatformTransactionManager {
        int commits, rollbacks; boolean readOnly;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { readOnly = definition.isReadOnly(); }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
