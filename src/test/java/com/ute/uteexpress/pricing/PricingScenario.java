package com.ute.uteexpress.pricing;

import com.ute.uteexpress.dto.pricing.*;
import com.ute.uteexpress.dto.shipment.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.service.*;
import jakarta.validation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static com.ute.uteexpress.shipment.ShipmentFixtures.*;
import static org.mockito.Mockito.*;

/** Synthetic repository state for actual admin and quote services. No JDBC guarantees. */
public class PricingScenario implements AutoCloseable {
    public final ShippingServiceRepository services = mock(ShippingServiceRepository.class);
    public final PricingVersionRepository versions = mock(PricingVersionRepository.class);
    public final ShippingRateRuleRepository rates = mock(ShippingRateRuleRepository.class);
    public final PickupFeeRuleRepository pickups = mock(PickupFeeRuleRepository.class);
    public final BranchRepository branches = mock(BranchRepository.class);
    public final UserRepository users = mock(UserRepository.class);
    public final UserRoleRepository roles = mock(UserRoleRepository.class);
    public final AuditLogRepository audits = mock(AuditLogRepository.class);
    public final ShipmentQuoteRepository quotes = mock(ShipmentQuoteRepository.class);
    public final ShipmentRepository shipments = mock(ShipmentRepository.class);
    public final ShipmentEventRepository events = mock(ShipmentEventRepository.class);
    public final BranchNetworkService network = mock(BranchNetworkService.class);
    public final TrackingNumberGenerator tracking = mock(TrackingNumberGenerator.class);
    public final jakarta.persistence.EntityManager entityManager = mock(jakarta.persistence.EntityManager.class);
    public final Clock clock = mock(Clock.class);
    public final Map<Long, ShippingService> catalogs = new LinkedHashMap<>();
    public final Map<Long, PricingVersion> books = new LinkedHashMap<>();
    public final Map<Long, ShippingRateRule> shippingRows = new LinkedHashMap<>();
    public final Map<Long, PickupFeeRule> pickupRows = new LinkedHashMap<>();
    public final Map<Long, Branch> points = new LinkedHashMap<>();
    public final Map<Long, User> actors = new LinkedHashMap<>();
    public final Map<Long, List<String>> permissions = new LinkedHashMap<>();
    public final List<AuditLog> auditRows = new ArrayList<>();
    public final Map<String, ShipmentQuote> quoteRows = new LinkedHashMap<>();
    public final List<Shipment> shipmentRows = new ArrayList<>();
    public LocalDateTime time = NOW;
    public final User admin = owner(), customer = owner();
    public final ShippingService catalog = catalog();
    public final ShippingRateRule legacy = rate();
    public final PickupFeeRule legacyPickup = new PickupFeeRule();
    public final AdminPricingService management;
    public final QuoteService quoting;
    private final ValidatorFactory validator = Validation.buildDefaultValidatorFactory();
    public PricingScenario() {
        admin.setId(2L); admin.setEmail("pricing-admin@example.test"); actors.put(1L, customer); actors.put(2L, admin);
        permissions.put(1L, List.of("ROLE_USER")); permissions.put(2L, List.of("ROLE_ADMIN"));
        catalog.setCode("STANDARD"); catalog.setEstimatedDeliveryHours(24); catalog.setCreatedAt(NOW.minusDays(1)); catalog.setUpdatedAt(NOW.minusDays(1)); catalogs.put(21L, catalog);
        legacy.setId(41L); legacy.setService(catalog); shippingRows.put(41L, legacy);
        for (long id : new long[] {11, 12, 101, 102}) { Branch b = branch(id); b.setBranchType(id < 100 ? BranchType.LOCAL_BRANCH : BranchType.CENTRAL_HUB); points.put(id, b); }
        points.get(11L).setParentHub(points.get(101L)); points.get(12L).setParentHub(points.get(102L));
        legacyPickup.setId(61L); legacyPickup.setService(catalog); legacyPickup.setBranch(points.get(11L)); legacyPickup.setAmount(new BigDecimal("7500.00"));
        legacyPickup.setEffectiveFrom(NOW.minusDays(1)); pickupRows.put(61L, legacyPickup);
        when(clock.instant()).thenAnswer(c -> time.toInstant(ZoneOffset.UTC));
        when(users.findByEmail(anyString())).thenAnswer(c -> actors.values().stream().filter(u -> u.getEmail().equals(c.getArgument(0))).findFirst());
        when(roles.findRoleNamesByUserId(anyLong())).thenAnswer(c -> permissions.getOrDefault(c.getArgument(0), List.of()));
        when(services.findById(anyLong())).thenAnswer(c -> Optional.ofNullable(catalogs.get(c.getArgument(0))));
        when(services.findForPricingUpdate(anyLong())).thenAnswer(c -> Optional.ofNullable(catalogs.get(c.getArgument(0))));
        when(services.findByIdAndIsActiveTrue(anyLong())).thenAnswer(c -> Optional.ofNullable(catalogs.get(c.getArgument(0))).filter(ShippingService::isActive));
        when(services.findByIsActiveTrueOrderByNameAsc()).thenAnswer(c -> catalogs.values().stream().filter(ShippingService::isActive).toList());
        when(services.existsByCodeIgnoreCase(anyString())).thenAnswer(c -> catalogs.values().stream().anyMatch(s -> s.getCode().equalsIgnoreCase(c.getArgument(0))));
        when(services.findCatalog(any(), any())).thenAnswer(c -> page(catalogs.values().stream().filter(s -> c.getArgument(0) == null || s.getCode().equalsIgnoreCase(c.getArgument(0)) || s.getName().equalsIgnoreCase(c.getArgument(0))).toList(), c.getArgument(1)));
        when(services.saveAndFlush(any())).thenAnswer(c -> { ShippingService s = c.getArgument(0); if (s.getId() == null) { s.setId(21L + catalogs.size()); } else { s.setVersion(s.getVersion() + 1); } catalogs.put(s.getId(), s); return s; });
        when(versions.findServiceId(anyLong())).thenAnswer(c -> Optional.ofNullable(books.get(c.getArgument(0))).map(v -> v.getService().getId()));
        when(versions.findById(anyLong())).thenAnswer(c -> Optional.ofNullable(books.get(c.getArgument(0))));
        when(versions.findAll(any(Pageable.class))).thenAnswer(c -> page(List.copyOf(books.values()), c.getArgument(0)));
        when(versions.findByServiceId(anyLong(), any())).thenAnswer(c -> page(books.values().stream().filter(v -> v.getService().getId().equals(c.getArgument(0))).toList(), c.getArgument(1)));
        when(versions.existsByServiceIdAndCodeIgnoreCase(anyLong(), anyString())).thenAnswer(c -> books.values().stream().anyMatch(v -> v.getService().getId().equals(c.getArgument(0)) && v.getCode().equalsIgnoreCase(c.getArgument(1))));
        when(versions.saveAndFlush(any())).thenAnswer(c -> { PricingVersion v = c.getArgument(0); if (v.getId() == null) { v.setId(101L + books.size()); } else { v.setVersion(v.getVersion() + 1); } books.put(v.getId(), v); return v; });
        when(rates.findEffectiveRules(anyLong(), any(), any())).thenAnswer(c -> shippingRows.values().stream().filter(r -> r.getService().getId().equals(c.getArgument(0)) && r.getScopeType() == c.getArgument(1) && effective(r.isActive(), r.getEffectiveFrom(), r.getEffectiveTo(), r.getRetiredFrom(), r.getPricingVersion(), c.getArgument(2))).toList());
        when(pickups.findEffectiveRules(anyLong(), anyLong(), any())).thenAnswer(c -> pickupRows.values().stream().filter(r -> r.getBranch().getId().equals(c.getArgument(0)) && r.getService().getId().equals(c.getArgument(1)) && effective(r.isActive(), r.getEffectiveFrom(), r.getEffectiveTo(), r.getRetiredFrom(), r.getPricingVersion(), c.getArgument(2))).toList());
        when(rates.findConflicts(anyLong(), any(), anyLong(), any(), any())).thenAnswer(c -> shippingRows.values().stream().filter(r -> r.getService().getId().equals(c.getArgument(0)) && r.getScopeType() == c.getArgument(1) && conflicts(r.isActive(), r.getEffectiveFrom(), r.getEffectiveTo(), r.getRetiredFrom(), r.getPricingVersion(), c.getArgument(2), c.getArgument(3), c.getArgument(4))).toList());
        when(pickups.findConflicts(anyLong(), anyLong(), anyLong(), any(), any())).thenAnswer(c -> pickupRows.values().stream().filter(r -> r.getService().getId().equals(c.getArgument(0)) && r.getBranch().getId().equals(c.getArgument(1)) && conflicts(r.isActive(), r.getEffectiveFrom(), r.getEffectiveTo(), r.getRetiredFrom(), r.getPricingVersion(), c.getArgument(2), c.getArgument(3), c.getArgument(4))).toList());
        when(rates.findById(anyLong())).thenAnswer(c -> Optional.ofNullable(shippingRows.get(c.getArgument(0))));
        when(pickups.findById(anyLong())).thenAnswer(c -> Optional.ofNullable(pickupRows.get(c.getArgument(0))));
        when(rates.findServiceId(anyLong())).thenAnswer(c -> Optional.ofNullable(shippingRows.get(c.getArgument(0))).map(r -> r.getService().getId()));
        when(pickups.findServiceId(anyLong())).thenAnswer(c -> Optional.ofNullable(pickupRows.get(c.getArgument(0))).map(r -> r.getService().getId()));
        when(rates.findByIdAndPricingVersionId(anyLong(), anyLong())).thenAnswer(c -> Optional.ofNullable(shippingRows.get(c.getArgument(0))).filter(r -> belongs(r.getPricingVersion(), c.getArgument(1))));
        when(pickups.findByIdAndPricingVersionId(anyLong(), anyLong())).thenAnswer(c -> Optional.ofNullable(pickupRows.get(c.getArgument(0))).filter(r -> belongs(r.getPricingVersion(), c.getArgument(1))));
        when(rates.findByPricingVersionIdAndScopeType(anyLong(), any())).thenAnswer(c -> shippingRows.values().stream().filter(r -> belongs(r.getPricingVersion(), c.getArgument(0)) && r.getScopeType() == c.getArgument(1)).findFirst());
        when(pickups.findByPricingVersionIdAndBranchId(anyLong(), anyLong())).thenAnswer(c -> pickupRows.values().stream().filter(r -> belongs(r.getPricingVersion(), c.getArgument(0)) && r.getBranch().getId().equals(c.getArgument(1))).findFirst());
        when(rates.findByPricingVersionIdAndIsActiveTrue(anyLong())).thenAnswer(c -> shippingRows.values().stream().filter(r -> belongs(r.getPricingVersion(), c.getArgument(0)) && r.isActive()).toList());
        when(pickups.findByPricingVersionIdAndIsActiveTrue(anyLong())).thenAnswer(c -> pickupRows.values().stream().filter(r -> belongs(r.getPricingVersion(), c.getArgument(0)) && r.isActive()).toList());
        when(rates.findByPricingVersionId(anyLong(), any())).thenAnswer(c -> page(shippingRows.values().stream().filter(r -> belongs(r.getPricingVersion(), c.getArgument(0))).toList(), c.getArgument(1)));
        when(pickups.findByPricingVersionId(anyLong(), any())).thenAnswer(c -> page(pickupRows.values().stream().filter(r -> belongs(r.getPricingVersion(), c.getArgument(0))).toList(), c.getArgument(1)));
        when(rates.findByServiceIdAndPricingVersionIsNull(anyLong(), any())).thenAnswer(c -> page(shippingRows.values().stream().filter(r -> r.getService().getId().equals(c.getArgument(0)) && r.getPricingVersion() == null).toList(), c.getArgument(1)));
        when(pickups.findByServiceIdAndPricingVersionIsNull(anyLong(), any())).thenAnswer(c -> page(pickupRows.values().stream().filter(r -> r.getService().getId().equals(c.getArgument(0)) && r.getPricingVersion() == null).toList(), c.getArgument(1)));
        when(rates.countByPricingVersionId(anyLong())).thenAnswer(c -> shippingRows.values().stream().filter(r -> belongs(r.getPricingVersion(), c.getArgument(0))).count());
        when(pickups.countByPricingVersionId(anyLong())).thenAnswer(c -> pickupRows.values().stream().filter(r -> belongs(r.getPricingVersion(), c.getArgument(0))).count());
        when(rates.saveAndFlush(any())).thenAnswer(c -> { ShippingRateRule r = c.getArgument(0); if (r.getId() == null) { r.setId(41L + shippingRows.size()); } shippingRows.put(r.getId(), r); return r; });
        when(pickups.saveAndFlush(any())).thenAnswer(c -> { PickupFeeRule r = c.getArgument(0); if (r.getId() == null) { r.setId(61L + pickupRows.size()); } pickupRows.put(r.getId(), r); return r; });
        when(branches.findByIdAndStatus(anyLong(), any())).thenAnswer(c -> Optional.ofNullable(points.get(c.getArgument(0))).filter(b -> b.getStatus() == c.getArgument(1)));
        when(branches.findByStatusAndBranchType(any(), any(), any())).thenAnswer(c -> page(points.values().stream().filter(b -> b.getStatus() == c.getArgument(0) && b.getBranchType() == c.getArgument(1)).toList(), c.getArgument(2)));
        when(network.quoteServingBranches(anyString(), anyString(), anyString(), any())).thenReturn(List.of(new ShipmentOptions.BranchOption(11L, "Origin", "Province")));
        when(network.quoteDeliveryBranches(anyString(), anyString(), anyString())).thenReturn(List.of(new ShipmentOptions.BranchOption(12L, "Destination", "Province")));
        when(network.quoteConfiguredHub(11L)).thenReturn(101L); when(network.quoteConfiguredHub(12L)).thenReturn(102L);
        when(audits.saveAndFlush(any())).thenAnswer(c -> { AuditLog a = c.getArgument(0); a.setId(201L + auditRows.size()); auditRows.add(a); return a; });
        when(audits.findByResourceTypeAndResourceId(any(), anyString(), any())).thenAnswer(c -> page(auditRows.stream().filter(a -> a.getResourceType() == c.getArgument(0) && a.getResourceId().equals(c.getArgument(1))).toList(), c.getArgument(2)));
        when(quotes.saveAndFlush(any())).thenAnswer(c -> { ShipmentQuote q = c.getArgument(0); if (q.getId() == null) { q.setId(301L + quoteRows.size()); } quoteRows.put(q.getToken(), q); return q; });
        when(quotes.findOwnedForUpdate(anyString(), anyLong())).thenAnswer(c -> Optional.ofNullable(quoteRows.get(c.getArgument(0))).filter(q -> q.getRequester().getId().equals(c.getArgument(1))));
        when(quotes.findByTokenAndRequesterId(anyString(), anyLong())).thenAnswer(c -> Optional.ofNullable(quoteRows.get(c.getArgument(0))).filter(q -> q.getRequester().getId().equals(c.getArgument(1))));
        when(shipments.saveAndFlush(any())).thenAnswer(c -> { Shipment s = c.getArgument(0); s.setId(401L + shipmentRows.size()); shipmentRows.add(s); return s; });
        when(shipments.findByQuoteIdAndSenderId(anyLong(), anyLong())).thenAnswer(c -> shipmentRows.stream().filter(s -> s.getQuote().getId().equals(c.getArgument(0)) && s.getSender().getId().equals(c.getArgument(1))).findFirst());
        when(tracking.next()).thenAnswer(c -> "UTE" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        management = new AdminPricingService(services, versions, rates, pickups, branches, users, roles, audits, clock, entityManager);
        quoting = new QuoteService(users, roles, quotes, shipments, events, branches, services, rates, pickups, network, tracking, clock, validator.getValidator(), Duration.ofMinutes(15), entityManager, versions);
        as(admin);
    }
    private static boolean belongs(PricingVersion v, Long id) { return v != null && v.getId().equals(id); }
    private static boolean effective(boolean active, LocalDateTime from, LocalDateTime to, LocalDateTime retired, PricingVersion v, LocalDateTime time) {
        return active && (v == null || v.getStatus() == PricingVersionStatus.PUBLISHED) && !time.isBefore(from) && (to == null || time.isBefore(to)) && (retired == null || time.isBefore(retired)) && (v == null || v.getRetiredFrom() == null || time.isBefore(v.getRetiredFrom()));
    }
    private static boolean conflicts(boolean active, LocalDateTime from, LocalDateTime to, LocalDateTime retired, PricingVersion v, Long excluded, LocalDateTime start, LocalDateTime end) {
        return active && (v == null || (v.getStatus() == PricingVersionStatus.PUBLISHED && !v.getId().equals(excluded))) && (end == null || from.isBefore(end)) && (to == null || to.isAfter(start)) && (retired == null || retired.isAfter(start)) && (v == null || v.getRetiredFrom() == null || v.getRetiredFrom().isAfter(start));
    }
    private static <T> Page<T> page(List<T> rows, Pageable p) { int start = Math.min(rows.size(), Math.toIntExact(p.getOffset())); return new PageImpl<>(rows.subList(start, Math.min(rows.size(), start + p.getPageSize())), p, rows.size()); }
    public void as(User u) { SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(u.getEmail(), null, permissions.get(u.getId()).toArray(String[]::new))); }
    public PricingVersion draft(LocalDateTime from, LocalDateTime to) { as(admin); Long id = management.createDraft(new PricingCommands.Draft(21L, "VERSION_" + (books.size() + 1), from, to)); return books.get(id); }
    public void shipping(PricingVersion v, RateScopeType scope, String base) { as(admin); management.shippingRule(v.getId(), v.getVersion(), new PricingCommands.ShippingRule(null, scope, new BigDecimal("1.00"), new BigDecimal(base), new BigDecimal("0.50"), new BigDecimal("5000.00"), true)); }
    public void pickup(PricingVersion v, String amount) { as(admin); management.pickupRule(v.getId(), v.getVersion(), new PricingCommands.PickupRule(null, 11L, new BigDecimal(amount), true)); }
    public void publish(PricingVersion v) { as(admin); management.publish(v.getId(), v.getVersion(), true); }
    public GuestQuoteForm guest() {
        var source = form(); var f = new GuestQuoteForm(); f.setShipmentMethod(source.getShipmentMethod()); f.setServiceId(21L); f.setOriginBranchId(11L); f.setDestinationBranchId(12L);
        f.setSenderProvince(source.getSenderProvince()); f.setSenderDistrict(source.getSenderDistrict()); f.setSenderWard(source.getSenderWard());
        f.setReceiverProvince(source.getReceiverProvince()); f.setReceiverDistrict(source.getReceiverDistrict()); f.setReceiverWard(source.getReceiverWard()); f.setWeightKg(source.getWeightKg()); return f;
    }
    @Override public void close() { validator.close(); SecurityContextHolder.clearContext(); }
}
