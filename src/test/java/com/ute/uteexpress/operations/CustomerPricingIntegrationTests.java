package com.ute.uteexpress.operations;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.ShipmentCreationException;
import jakarta.validation.ConstraintViolationException;
import java.math.BigDecimal;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.server.ResponseStatusException;
import static com.ute.uteexpress.operations.OperationsJdbcFixture.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(CustomerJourneyConfig.class)
class CustomerPricingIntegrationTests extends CustomerJourneySupport {
    @ParameterizedTest @EnumSource(ShipmentMethod.class)
    void guestAndUserUseSamePublishedPricesWithoutGuestPersistence(ShipmentMethod method) {
        var input = form(); input.setShipmentMethod(method);
        SecurityContextHolder.clearContext();
        var guest = quoteService.estimate(guest(input));
        assertEquals(0, count(ShipmentQuote.class)); assertEquals(0, count(Shipment.class));
        assertEquals(0, count(ShipmentEvent.class)); assertEquals(0, notices.count());
        as(f.owner, "ROLE_USER"); var token = quoteService.quote(input); var user = quoteService.review(token);
        assertEquals(guest.totalFee(), user.totalFee()); assertEquals(guest.shippingFee(), user.shippingFee());
        assertEquals(new BigDecimal(method == ShipmentMethod.HOME_PICKUP ? "7500.00" : "0.00"), user.pickupFee());
        assertEquals(PricingVersionStatus.PUBLISHED, db(() -> em.find(PricingVersion.class, pricingId).getStatus()));
    }
    @Test void surroundingProvinceWhitespaceDoesNotChangeUserPriceRelativeToGuest() {
        var input = form(); input.setDestinationBranchId(f.source.getId());
        input.setReceiverProvince("  " + input.getSenderProvince() + "  ");
        var guest = quoteService.estimate(guest(input));
        assertEquals(new BigDecimal("25000.00"), guest.shippingFee());
        assertEquals(guest.shippingFee(), quoteService.review(quoteService.quote(input)).shippingFee());
    }
    @ParameterizedTest @ValueSource(strings={"DRAFT", "FUTURE", "EXPIRED", "RETIRED", "INACTIVE", "MISSING"})
    void unavailablePublishedPriceFailsWithoutPartialWrites(String condition) {
        db(() -> {
            var r = em.find(ShippingRateRule.class, rateId);
            switch (condition) {
                case "DRAFT" -> { var v = em.find(PricingVersion.class, pricingId); v.setStatus(PricingVersionStatus.DRAFT); v.setPublishedBy(null); v.setPublishedAt(null); }
                case "FUTURE" -> r.setEffectiveFrom(NOW.plusHours(1));
                case "EXPIRED" -> r.setEffectiveTo(NOW);
                case "RETIRED" -> em.find(PricingVersion.class, pricingId).setRetiredFrom(NOW);
                case "INACTIVE" -> r.setActive(false);
                case "MISSING" -> em.remove(r);
                default -> throw new AssertionError(condition);
            } return null;
        });
        assertThrows(ShipmentCreationException.class, () -> quoteService.estimate(guest(form())));
        assertThrows(ShipmentCreationException.class, () -> quoteService.quote(form()));
        assertEquals(0, count(ShipmentQuote.class)); assertEquals(0, count(Shipment.class));
    }
    @ParameterizedTest @ValueSource(strings={"senderAddress", "receiverAddress", "length", "weight", "name", "service", "branch"})
    void invalidInputDoesNotPersistQuoteOrShipment(String field) {
        var input = form();
        switch (field) {
            case "senderAddress" -> input.setSenderAddress(" ");
            case "receiverAddress" -> input.setReceiverAddress(null);
            case "length" -> input.setLengthCm(BigDecimal.ZERO);
            case "weight" -> input.setWeightKg(new BigDecimal("-1"));
            case "name" -> input.setSenderName("x".repeat(101));
            case "service" -> input.setServiceId(999999L);
            case "branch" -> input.setDestinationBranchId(f.foreign.getId());
            default -> throw new AssertionError(field);
        }
        if (field.equals("service") || field.equals("branch")) assertThrows(ShipmentCreationException.class, () -> quoteService.quote(input));
        else assertThrows(ConstraintViolationException.class, () -> quoteService.quote(input));
        assertEquals(0, count(ShipmentQuote.class)); assertEquals(0, count(Shipment.class));
    }
    @Test void disabledServiceAndMissingPickupRuleFailClosed() {
        db(() -> { em.find(ShippingService.class, f.catalog.getId()).setActive(false); return null; });
        assertThrows(ShipmentCreationException.class, () -> quoteService.quote(form()));
        db(() -> { em.find(ShippingService.class, f.catalog.getId()).setActive(true); em.remove(em.find(PickupFeeRule.class, pickupId)); return null; });
        var input = form(); input.setShipmentMethod(ShipmentMethod.HOME_PICKUP);
        assertThrows(ShipmentCreationException.class, () -> quoteService.quote(input));
        assertEquals(0, count(ShipmentQuote.class));
    }
    @Test void consumedQuoteReplayPreservesOwnerActorStatusEventAndFeeSnapshotAfterRateChanges() {
        String token = quoteService.quote(form()); Long id = quoteService.confirm(token);
        db(() -> { em.find(ShippingRateRule.class, rateId).setBasePrice(new BigDecimal("90000.00")); return null; });
        assertEquals(id, quoteService.confirm(token)); assertEquals(1, count(Shipment.class)); assertEquals(1, count(ShipmentEvent.class));
        var view = userShipments.detail(id, 0); assertEquals(new BigDecimal("45000.00"), view.totalFee());
        assertEquals(ShipmentStatus.CREATED, view.status());
        db(() -> { var s = em.find(Shipment.class, id); assertEquals(f.owner.getId(), s.getSender().getId());
            var e = em.createQuery("select e from ShipmentEvent e", ShipmentEvent.class).getSingleResult();
            assertEquals(f.owner.getId(), e.getActor().getId()); assertNull(s.getCurrentBranch()); return null; });
        assertEquals(new BigDecimal("95000.00"), quoteService.estimate(guest(form())).totalFee());
    }
    @Test void otherUserCannotReviewOrConsumeQuoteAndOwnerCanStillUseIt() {
        String token = quoteService.quote(form()); as(f.other, "ROLE_USER");
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> quoteService.review(token)).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> quoteService.confirm(token)).getStatusCode().value());
        assertEquals(0, count(Shipment.class)); as(f.owner, "ROLE_USER"); assertNotNull(quoteService.confirm(token));
    }
    @Test void exactExpiryAndDatabaseRoleRevocationRejectConfirmation() {
        String token = quoteService.quote(form()); clock.set(quoteService.review(token).expiresAt());
        assertThrows(ShipmentCreationException.class, () -> quoteService.confirm(token)); clock.set(NOW);
        db(() -> { em.createQuery("delete from UserRole r where r.user.id=:id").setParameter("id", f.owner.getId()).executeUpdate(); return null; });
        assertThrows(AccessDeniedException.class, () -> quoteService.confirm(token)); assertEquals(0, count(Shipment.class));
    }
    @Test void eventDatabaseFailureRollsBackShipmentAndConsumptionAndSameQuoteCanRetry() {
        String token = quoteService.quote(form());
        constraint("ALTER TABLE dbo.shipment_events ADD CONSTRAINT TEST_EVENT_FAILURE CHECK (1=0)");
        try { assertThrows(org.springframework.dao.DataAccessException.class, () -> quoteService.confirm(token));
            assertEquals(0, count(Shipment.class)); assertEquals(0, count(ShipmentEvent.class));
            assertTrue(quoteService.review(token).confirmable());
        } finally { constraint("ALTER TABLE dbo.shipment_events DROP CONSTRAINT TEST_EVENT_FAILURE"); }
        assertNotNull(quoteService.confirm(token)); assertEquals(1, count(Shipment.class));
    }
    @Test void simultaneousQuoteConfirmationsCommitExactlyOneShipmentAndCreationEvent() throws Exception {
        String token = quoteService.quote(form()); var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        Callable<Long> confirm = () -> {
            as(f.owner,"ROLE_USER");ready.countDown();
            try { assertTrue(start.await(5,TimeUnit.SECONDS));return quoteService.confirm(token); }
            finally { SecurityContextHolder.clearContext(); }
        };
        try {
            var a=pool.submit(confirm);var b=pool.submit(confirm);assertTrue(ready.await(5,TimeUnit.SECONDS));start.countDown();
            assertEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
            assertEquals(1,count(Shipment.class));assertEquals(1,count(ShipmentEvent.class));assertTrue(quoteService.review(token).consumed());
        } finally { start.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS)); }
    }
    @Test void routeDeactivationAfterQuoteRejectsConfirmationWithoutConsumingSnapshot() {
        String token=quoteService.quote(form());
        db(()->{em.find(Branch.class,f.destination.getId()).setStatus(BranchStatus.INACTIVE);return null;});
        assertThrows(ShipmentCreationException.class,()->quoteService.confirm(token));assertEquals(0,count(Shipment.class));
        assertFalse(quoteService.review(token).consumed());
    }
}
