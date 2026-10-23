package com.ute.uteexpress.pricing;

import com.ute.uteexpress.dto.pricing.*;
import com.ute.uteexpress.dto.pricing.PricingCommands.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static com.ute.uteexpress.shipment.ShipmentFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Business behavior against mocked persistence; not SQL Server integration. */
class PricingManagementTests {
    PricingScenario f;
    @BeforeEach void setup() { f = new PricingScenario(); }
    @AfterEach void close() { f.close(); }
    Catalog catalog(String code, String name) { return new Catalog(code, name, "Description", 24, null, null, null, null); }
    PricingVersion draft() { return f.draft(NOW.plusHours(1), NOW.plusHours(2)); }
    PricingVersion configured() { var v = draft(); f.shipping(v, RateScopeType.INTRA_PROVINCE, "30000.00"); return v; }
    ShippingRule shipping(Long id, String amount) { return new ShippingRule(id, RateScopeType.INTRA_PROVINCE, new BigDecimal("1.00"), new BigDecimal(amount), new BigDecimal("0.50"), new BigDecimal("5000.00"), true); }
    void guest() { SecurityContextHolder.clearContext(); }
    @Test void createServiceIsDisabledUntilExplicitActivationAndIsAudited() {
        Long id = f.management.createService(catalog("NEW_SERVICE", "New service")); var s = f.catalogs.get(id);
        assertFalse(s.isActive()); assertSame(f.admin, s.getCreatedBy()); assertSame(f.admin, s.getUpdatedBy());
        assertEquals(AuditResourceType.SHIPPING_SERVICE, f.auditRows.getFirst().getResourceType()); assertEquals("SERVICE_CREATED", f.auditRows.getFirst().getAction());
    }
    @Test void duplicateCodeIncludingCaseIsRejected() { assertThrows(AdminRuleException.class, () -> f.management.createService(catalog("STANDARD", "Another"))); assertTrue(f.auditRows.isEmpty()); }
    @ParameterizedTest @ValueSource(strings = {"", " ", "lowercase", "BAD CODE", "<script>"})
    void invalidCodeRejected(String code) { assertThrows(AdminRuleException.class, () -> f.management.createService(catalog(code, "Service"))); assertEquals(1, f.catalogs.size()); }
    @ParameterizedTest @ValueSource(strings = {"", " "})
    void blankNameRejected(String name) { assertThrows(AdminRuleException.class, () -> f.management.createService(catalog("NEW", name))); }
    @Test void catalogDescriptionAndConditionsCanChangeButCodeCannot() {
        f.management.editService(21L, 0L, catalog(null, "Updated service")); assertEquals("STANDARD", f.catalog.getCode()); assertEquals("Updated service", f.catalog.getName());
        assertThrows(AdminRuleException.class, () -> f.management.editService(21L, f.catalog.getVersion(), catalog("CHANGED", "Name")));
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> f.management.editService(21L, 0L, catalog(null, "Stale")));
    }
    @Test void disablingServiceBlocksNewEstimateAndConfirmationWithoutChangingOldShipment() {
        f.as(f.customer); String token = f.quoting.quote(form()); Long id = f.quoting.confirm(token); var old = f.shipmentRows.getFirst(); var total = old.getTotalFee();
        String unconsumed = f.quoting.quote(form()); f.as(f.admin); f.management.serviceStatus(21L, f.catalog.getVersion(), false);
        guest(); assertThrows(ShipmentCreationException.class, () -> f.quoting.estimate(f.guest()));
        f.as(f.customer); assertThrows(ShipmentCreationException.class, () -> f.quoting.confirm(unconsumed)); assertEquals(id, f.quoting.confirm(token));
        assertSame(f.catalog, old.getService()); assertEquals(total, old.getTotalFee()); assertEquals(1, f.shipmentRows.size());
        f.as(f.admin); f.management.serviceStatus(21L, f.catalog.getVersion(), true); assertTrue(f.catalog.isActive());
    }
    @Test void catalogLimitsApplyToGuestUserAndConfirm() {
        f.as(f.customer); String token = f.quoting.quote(form()); f.as(f.admin);
        f.management.editService(21L, f.catalog.getVersion(), new Catalog(null, "Limited", null, 24, new BigDecimal("1.00"), null, null, null));
        guest(); assertThrows(ShipmentCreationException.class, () -> f.quoting.estimate(f.guest()));
        f.as(f.customer); assertThrows(ShipmentCreationException.class, () -> f.quoting.quote(form())); assertThrows(ShipmentCreationException.class, () -> f.quoting.confirm(token));
    }
    @Test void dimensionLimitRequiresMeasurementInsteadOfGuessingVolume() {
        f.catalog.setMaxLengthCm(new BigDecimal("50.00")); guest(); var input = f.guest();
        assertThrows(ShipmentCreationException.class, () -> f.quoting.estimate(input)); input.setLengthCm(new BigDecimal("50.00"));
        assertEquals(new BigDecimal("25000.00"), f.quoting.estimate(input).totalFee()); input.setLengthCm(new BigDecimal("50.01")); assertThrows(ShipmentCreationException.class, () -> f.quoting.estimate(input));
    }
    @Test void draftDoesNotAffectLivePricesOrImplyLegacyPublication() {
        var v = configured(); assertEquals(PricingVersionStatus.DRAFT, v.getStatus()); assertNull(v.getPublishedAt()); assertNull(f.legacy.getPricingVersion());
        guest(); assertEquals(new BigDecimal("25000.00"), f.quoting.estimate(f.guest()).totalFee());
    }
    @Test void duplicateVersionCodeIsRejectedPerService() {
        var v = draft(); assertThrows(AdminRuleException.class, () -> f.management.createDraft(new Draft(21L, v.getCode(), v.getValidFrom(), v.getValidTo())));
    }
    @Test void draftCanEditRuleAndPeriodWithVersionAudit() {
        var v = configured(); var r = f.shippingRows.values().stream().filter(row -> row.getPricingVersion() == v).findFirst().orElseThrow();
        Long previous = v.getVersion(); f.management.shippingRule(v.getId(), previous, shipping(r.getId(), "31000.00"));
        assertEquals(new BigDecimal("31000.00"), r.getBasePrice()); assertTrue(v.getVersion() > previous);
        f.management.editPeriod(v.getId(), v.getVersion(), new PricingCommands.Period(NOW.plusHours(3), NOW.plusHours(4)));
        assertEquals(v.getValidFrom(), r.getEffectiveFrom()); assertEquals(v.getValidTo(), r.getEffectiveTo()); assertTrue(f.auditRows.size() >= 4);
    }
    @Test void staleDraftUpdateIsRejected() {
        var v = configured(); assertThrows(ObjectOptimisticLockingFailureException.class, () -> f.management.editPeriod(v.getId(), 0L, new PricingCommands.Period(NOW.plusDays(1), null)));
    }
    @Test void invalidIntervalIncludingEqualEndAndUnsupportedPrecisionRejected() {
        assertThrows(AdminRuleException.class, () -> f.draft(NOW, NOW)); assertThrows(AdminRuleException.class, () -> f.draft(NOW, NOW.minusSeconds(1)));
        assertThrows(AdminRuleException.class, () -> f.draft(NOW.withNano(1), null)); assertTrue(f.books.isEmpty());
    }
    @Test void noRuleOrNoConfirmationCannotPublish() {
        var v = draft(); assertThrows(AdminRuleException.class, () -> f.publish(v)); f.shipping(v, RateScopeType.INTER_PROVINCE, "30000.00");
        assertThrows(AdminRuleException.class, () -> f.management.publish(v.getId(), v.getVersion(), false)); assertEquals(PricingVersionStatus.DRAFT, v.getStatus());
    }
    @Test void cannotBackdatePublication() {
        var v = configured(); f.legacy.setActive(false); f.time = v.getValidFrom().plusSeconds(1); assertThrows(AdminRuleException.class, () -> f.publish(v));
    }
    @Test void publicationRechecksTimeAfterConflictScan() {
        var v = configured(); f.legacy.setActive(false);
        when(f.rates.findConflicts(anyLong(), any(), anyLong(), any(), any())).thenAnswer(c -> { f.time = v.getValidFrom().plusNanos(100); return List.of(); });
        assertThrows(AdminRuleException.class, () -> f.publish(v)); assertNull(v.getPublishedAt()); assertEquals(PricingVersionStatus.DRAFT, v.getStatus());
    }
    @ParameterizedTest @ValueSource(strings = {"-0.01", "10000000000000000.00", "1.001"})
    void invalidMoneyCannotEnterDraft(String value) {
        var v = draft(); assertThrows(AdminRuleException.class, () -> f.management.shippingRule(v.getId(), v.getVersion(), shipping(null, value)));
        assertEquals(1, f.shippingRows.size());
    }
    @Test void nonpositiveStepAndUnsupportedRegionRejected() {
        var v = draft(); assertThrows(AdminRuleException.class, () -> f.management.shippingRule(v.getId(), v.getVersion(), new ShippingRule(null, RateScopeType.INTRA_PROVINCE, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, true)));
        assertThrows(AdminRuleException.class, () -> f.shipping(v, RateScopeType.INTER_REGION, "30000.00"));
    }
    @Test void overlappingWeightFormulasForSameScopeRejectedEvenIfFirstDisabled() {
        var v = configured(); var r = f.shippingRows.values().stream().filter(row -> row.getPricingVersion() == v).findFirst().orElseThrow(); r.setActive(false);
        assertThrows(AdminRuleException.class, () -> f.shipping(v, RateScopeType.INTRA_PROVINCE, "32000.00"));
    }
    @Test void ruleIdFromAnotherBookCannotBeEdited() {
        var first = configured(); var second = draft(); var r = f.shippingRows.values().stream().filter(row -> row.getPricingVersion() == first).findFirst().orElseThrow();
        assertThrows(ResponseStatusException.class, () -> f.management.shippingRule(second.getId(), second.getVersion(), shipping(r.getId(), "99000.00")));
        assertEquals(new BigDecimal("30000.00"), r.getBasePrice());
    }
    @Test void legacyConflictRequiresExplicitRetirementAndPreservesOriginalPriceAndEnd() {
        var v = configured(); assertThrows(AdminRuleException.class, () -> f.publish(v));
        var price = f.legacy.getBasePrice(); var end = f.legacy.getEffectiveTo();
        f.management.retireLegacy(41L, false, v.getValidFrom(), true); f.publish(v);
        assertEquals(price, f.legacy.getBasePrice()); assertEquals(end, f.legacy.getEffectiveTo()); assertNull(f.legacy.getPricingVersion());
        assertEquals(PricingVersionStatus.PUBLISHED, v.getStatus()); assertSame(f.admin, v.getPublishedBy()); assertEquals(NOW, v.getPublishedAt());
    }
    @Test void publishedPricesCannotBeEditedOrPublishedTwice() {
        var v = configured(); f.legacy.setActive(false); f.publish(v);
        assertThrows(AdminRuleException.class, () -> f.shipping(v, RateScopeType.INTER_PROVINCE, "50000.00"));
        assertThrows(AdminRuleException.class, () -> f.management.editPeriod(v.getId(), v.getVersion(), new PricingCommands.Period(NOW.plusDays(2), null)));
        assertThrows(AdminRuleException.class, () -> f.publish(v));
        assertEquals(1, f.auditRows.stream().filter(a -> a.getAction().equals("PRICE_PUBLISHED")).count());
    }
    @Test void publishedConflictAndHalfOpenAdjacentPeriods() {
        f.legacy.setActive(false); var a = configured(); f.publish(a);
        var b = f.draft(a.getValidFrom(), a.getValidTo()); f.shipping(b, RateScopeType.INTRA_PROVINCE, "40000.00"); assertThrows(AdminRuleException.class, () -> f.publish(b));
        f.management.editPeriod(b.getId(), b.getVersion(), new PricingCommands.Period(a.getValidTo(), a.getValidTo().plusHours(1))); f.publish(b);
        f.time = a.getValidTo().minusNanos(100); guest(); assertEquals(new BigDecimal("35000.00"), f.quoting.estimate(f.guest()).totalFee());
        f.time = a.getValidTo(); assertEquals(new BigDecimal("45000.00"), f.quoting.estimate(f.guest()).totalFee());
        f.time = b.getValidTo(); assertThrows(ShipmentCreationException.class, () -> f.quoting.estimate(f.guest()));
    }
    @Test void exactStartUsesNewPriceAndBeforeStartUsesLegacy() {
        var v = configured(); f.management.retireLegacy(41L, false, v.getValidFrom(), true); f.publish(v);
        guest(); f.time = v.getValidFrom().minusNanos(100); assertEquals(new BigDecimal("25000.00"), f.quoting.estimate(f.guest()).totalFee());
        f.time = v.getValidFrom(); assertEquals(new BigDecimal("35000.00"), f.quoting.estimate(f.guest()).totalFee());
    }
    @Test void versionRetirementIsFutureOnlyOneTimeAndAffectsNewQuotesOnly() {
        f.legacy.setActive(false); var v = configured(); f.publish(v);
        assertThrows(AdminRuleException.class, () -> f.management.retireVersion(v.getId(), v.getVersion(), NOW, true));
        f.management.retireVersion(v.getId(), v.getVersion(), v.getValidFrom().plusMinutes(5), true);
        assertThrows(AdminRuleException.class, () -> f.management.retireVersion(v.getId(), v.getVersion(), v.getValidFrom().plusMinutes(6), true));
        f.time = v.getRetiredFrom(); guest(); assertThrows(ShipmentCreationException.class, () -> f.quoting.estimate(f.guest()));
    }
    @Test void managedRulesCannotBeRetiredThroughLegacyEndpoint() {
        var v = configured(); var r = f.shippingRows.values().stream().filter(row -> row.getPricingVersion() == v).findFirst().orElseThrow();
        assertThrows(AdminRuleException.class, () -> f.management.retireLegacy(r.getId(), false, NOW.plusHours(2), true));
    }
    @Test void quoteCreatedBeforeCutoverHonorsLockedPriceUntilOriginalExpiry() {
        f.as(f.customer); String token = f.quoting.quote(form()); var oldQuote = f.quoteRows.get(token); var oldPrice = oldQuote.getTotalFee();
        var v = f.draft(NOW.plusMinutes(2), null); f.shipping(v, RateScopeType.INTRA_PROVINCE, "30000.00"); f.management.retireLegacy(41L, false, v.getValidFrom(), true); f.publish(v);
        f.time = NOW.plusMinutes(3); f.as(f.customer); f.quoting.confirm(token); assertEquals(oldPrice, f.shipmentRows.getFirst().getTotalFee()); assertSame(f.legacy, oldQuote.getShippingRateRule());
        String next = f.quoting.quote(form()); assertEquals(new BigDecimal("35000.00"), f.quoteRows.get(next).getTotalFee()); assertSame(v, f.quoteRows.get(next).getShippingRateRule().getPricingVersion());
        assertEquals(oldPrice, f.shipmentRows.getFirst().getTotalFee());
    }
    @Test void quoteIssuedAfterRetirementScheduledIsCappedAtCutover() {
        f.management.retireLegacy(41L, false, NOW.plusMinutes(2), true); f.as(f.customer); String token = f.quoting.quote(form());
        assertEquals(NOW.plusMinutes(2), f.quoteRows.get(token).getExpiresAt()); f.time = NOW.plusMinutes(2);
        assertThrows(ShipmentCreationException.class, () -> f.quoting.confirm(token)); assertTrue(f.shipmentRows.isEmpty());
    }
    @Test void pickupVersionConflictAndNewHomeSurchargeUseExistingFormula() {
        var v = draft(); f.pickup(v, "9000.00"); assertThrows(AdminRuleException.class, () -> f.publish(v));
        f.management.retireLegacy(61L, true, v.getValidFrom(), true); f.publish(v); f.time = v.getValidFrom(); guest();
        var input = f.guest(); input.setShipmentMethod(ShipmentMethod.HOME_PICKUP); assertEquals(new BigDecimal("34000.00"), f.quoting.estimate(input).totalFee());
        input.setShipmentMethod(ShipmentMethod.BRANCH_DROPOFF); assertEquals(BigDecimal.ZERO.setScale(2), f.quoting.estimate(input).pickupFee());
    }
    @Test void pickupRequiresActiveLocalBranchPositivePriceAndNoDuplicateBranch() {
        var v = draft(); assertThrows(AdminRuleException.class, () -> f.management.pickupRule(v.getId(), v.getVersion(), new PickupRule(null, 101L, BigDecimal.ONE, true)));
        assertThrows(AdminRuleException.class, () -> f.management.pickupRule(v.getId(), v.getVersion(), new PickupRule(null, 11L, BigDecimal.ZERO, true)));
        f.pickup(v, "9000.00"); assertThrows(AdminRuleException.class, () -> f.pickup(v, "10000.00"));
    }
    @Test void guestAndUserUseIdenticalPricesWithoutPublicQuotePersistence() {
        guest(); var estimate = f.quoting.estimate(f.guest()); assertTrue(f.quoteRows.isEmpty()); assertTrue(f.shipmentRows.isEmpty());
        f.as(f.customer); String token = f.quoting.quote(form()); assertEquals(estimate.totalFee(), f.quoteRows.get(token).getTotalFee());
        assertEquals(Set.of("service", "method", "shippingFee", "pickupFee", "insuranceFee", "totalFee", "currency", "estimatedAt", "expiresAt"), Arrays.stream(PublicQuoteView.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).collect(java.util.stream.Collectors.toSet()));
    }
    @Test void draftSimulationUsesSameFormulaWithoutLeakingDraftToLiveSelection() {
        var v = configured(); f.catalog.setActive(false);
        var estimate = f.quoting.simulateDraft(v.getId(), f.guest()); assertEquals(new BigDecimal("35000.00"), estimate.totalFee()); assertEquals(v.getValidFrom(), estimate.estimatedAt());
        assertTrue(f.quoteRows.isEmpty()); assertTrue(f.shipmentRows.isEmpty()); assertEquals(PricingVersionStatus.DRAFT, v.getStatus());
        f.catalog.setActive(true); guest(); assertEquals(new BigDecimal("25000.00"), f.quoting.estimate(f.guest()).totalFee());
        assertThrows(AccessDeniedException.class, () -> f.quoting.simulateDraft(v.getId(), f.guest()));
    }
    @Test void draftSimulationRequiresCorrectServiceAndRealDatabaseAdmin() {
        var v = configured(); var input = f.guest(); input.setServiceId(999L);
        assertThrows(ShipmentCreationException.class, () -> f.quoting.simulateDraft(v.getId(), input));
        f.permissions.put(2L, List.of("ROLE_USER")); assertThrows(AccessDeniedException.class, () -> f.quoting.simulateDraft(v.getId(), f.guest()));
    }
    @Test void missingAndAmbiguousRatesFailClosedWithoutPersisting() {
        f.legacy.setActive(false); guest(); assertThrows(ShipmentCreationException.class, () -> f.quoting.estimate(f.guest()));
        f.legacy.setActive(true); ShippingRateRule duplicate = rate(); duplicate.setId(99L); f.shippingRows.put(99L, duplicate);
        assertThrows(ShipmentCreationException.class, () -> f.quoting.estimate(f.guest())); assertTrue(f.quoteRows.isEmpty());
    }
    @Test void stepRoundingRemainsCeilingAndMoneyRemainsExact() {
        guest(); var input = f.guest(); input.setWeightKg(new BigDecimal("1.50")); assertEquals(new BigDecimal("25000.00"), f.quoting.estimate(input).shippingFee());
        input.setWeightKg(new BigDecimal("1.51")); assertEquals(new BigDecimal("30000.00"), f.quoting.estimate(input).shippingFee());
        f.legacy.setBasePrice(new BigDecimal("9999999999999999.99")); assertThrows(ShipmentCreationException.class, () -> f.quoting.estimate(input));
    }
    @Test void onlyDatabaseActiveAdminCanManage() {
        f.permissions.put(2L, List.of("ROLE_USER")); assertThrows(AccessDeniedException.class, () -> f.management.catalog(null, 0));
        f.permissions.put(2L, List.of("ROLE_ADMIN")); f.admin.setStatus(UserStatus.LOCKED); assertThrows(AccessDeniedException.class, () -> f.management.createService(catalog("NEW", "Name")));
        f.admin.setStatus(UserStatus.UNVERIFIED); assertThrows(AccessDeniedException.class, f.management::authorize);
        assertTrue(f.auditRows.isEmpty());
    }
    @Test void noPhysicalDeleteOrShipmentRewriteOccursInManagement() {
        f.management.createService(catalog("NEW", "Name")); var v = configured(); f.legacy.setActive(false); f.publish(v);
        verifyNoInteractions(f.shipments, f.events); verify(f.services, never()).delete(any()); verify(f.versions, never()).delete(any()); verify(f.rates, never()).delete(any()); verify(f.pickups, never()).delete(any());
    }
}
