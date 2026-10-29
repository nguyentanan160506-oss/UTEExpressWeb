package com.ute.uteexpress.operations;

import com.ute.uteexpress.dto.delivery.DeliveryCommands.*;
import com.ute.uteexpress.dto.shipment.*;
import com.ute.uteexpress.dto.transport.TripCommands.LocalReady;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.LogisticsRuleException;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.server.ResponseStatusException;
import static com.ute.uteexpress.operations.OperationsJdbcFixture.*;
import static org.junit.jupiter.api.Assertions.*;

/** Cross-module flows call the real services; no repository, pricing or lifecycle mocks. */
@SpringJUnitConfig(CustomerJourneyConfig.class)
class CustomerShipmentJourneyTests extends CustomerJourneySupport {
    @Test void quoteCreateConfirmIntakeLocalSortDeliverAndCustomerNoticeUseRealBusinessServices() {
        Long id = localReady();
        Long courier = db(() -> em.createQuery("select e.id from Employee e where e.user.id=:user", Long.class).setParameter("user", f.shipper.getId()).getSingleResult());
        lastMile.assign(new Assign(id, shipmentVersion(id), courier, null, null));
        Long assignment = db(() -> em.createQuery("select a.id from ShipmentAssignment a where a.shipment.id=:id", Long.class).setParameter("id", id).getSingleResult());
        lastMile.prepare(new Prepare(id, shipmentVersion(id), assignment, true, "LOCAL-PREPARE", null));
        as(f.shipper, "ROLE_SHIPPER");
        lastMile.accept(new Action(assignment, shipmentVersion(id), true, "LOCAL-ACCEPT"));
        lastMile.start(new Action(assignment, shipmentVersion(id), true, "LOCAL-START"));
        // Proof is required; rejected completion must not mutate attempts, status or notices.
        long eventsBefore = count(ShipmentEvent.class);
        assertThrows(LogisticsRuleException.class, () -> lastMile.complete(new Complete(assignment, shipmentVersion(id), false,
            DeliveryRecipientType.RECIPIENT, "Synthetic recipient", "PROOF-TEST", null)));
        assertEquals(0, count(DeliveryProof.class)); assertEquals(0, count(DeliveryAttempt.class));
        assertEquals(eventsBefore, count(ShipmentEvent.class));
        lastMile.complete(new Complete(assignment, shipmentVersion(id), true, DeliveryRecipientType.RECIPIENT,
            "Synthetic recipient", "PROOF-PRIVATE", "PRIVATE-DELIVERY-NOTE"));
        assertEquals(1, count(DeliveryProof.class)); assertEquals(1, count(DeliveryAttempt.class));
        Long event = db(() -> em.createQuery("select e.id from ShipmentEvent e where e.shipment.id=:id and e.toStatus=:state", Long.class)
            .setParameter("id", id).setParameter("state", ShipmentStatus.DELIVERED).getSingleResult());
        shipmentNotifications.process(event); long noticesBefore = notices.count(); shipmentNotifications.process(event);
        assertEquals(noticesBefore, notices.count());
        as(f.owner, "ROLE_USER"); var view = userShipments.detail(id, 0);
        assertEquals(ShipmentStatus.DELIVERED, view.status()); assertEquals(new BigDecimal("25000.00"), view.totalFee());
        var notice = inbox.list(0, 20, false).items().getFirst();
        assertEquals("/user/shipments/" + id, inbox.target(notice.id()));
        assertFalse(notice.toString().contains("PROOF-PRIVATE")); assertFalse(notice.toString().contains("PRIVATE-DELIVERY-NOTE"));
        assertFalse(trackingService.lookup(view.trackingNumber()).orElseThrow().toString().contains("PROOF-PRIVATE"));
        as(f.other, "ROLE_USER"); assertEquals(0, inbox.count());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> inbox.read(notice.id())).getStatusCode().value());
        as(f.owner, "ROLE_USER"); inbox.read(notice.id()); assertEquals(0, inbox.count());
    }
    Long localReady() {
        // Source = destination is a supported physical path. No fake status, transport trip or receipt.
        db(() -> {
            var employee = em.createQuery("select e from Employee e where e.user.id=:user", Employee.class).setParameter("user", f.shipper.getId()).getSingleResult();
            employee.setBranch(em.find(Branch.class, f.source.getId())); return null;
        });
        var form = form(); form.setDestinationBranchId(f.source.getId()); form.setReceiverProvince(f.source.getProvince());
        Long id = quoteService.confirm(quoteService.quote(form));
        as(f.manager, "ROLE_MANAGER"); lifecycle.transition(new ShipmentTransitionCommand(id, shipmentVersion(id), ShipmentStatus.CONFIRMED));
        as(f.owner, "ROLE_USER"); assertEquals(ShipmentStatus.CONFIRMED, userShipments.detail(id, 0).status());
        as(f.manager, "ROLE_MANAGER"); lifecycle.receiveDropoff(new BranchIntakeCommand(id, shipmentVersion(id), true, null));
        transport.readyAtSameBranch(new LocalReady(id, shipmentVersion(id), true, "LOCAL-SORT", null));
        assertEquals(0, count(TransportTrip.class)); assertEquals(1, count(ShipmentIntake.class)); return id;
    }
    @Test void managerEventFailureRollsBackStatusAndLeavesUserViewAndNotificationsConsistent() {
        Long id = create(); as(f.manager, "ROLE_MANAGER");
        long before = count(ShipmentEvent.class);
        constraint("ALTER TABLE dbo.shipment_events ADD CONSTRAINT TEST_TRANSITION_FAILURE CHECK (to_status <> 'CONFIRMED')");
        try { assertThrows(org.springframework.dao.DataAccessException.class, () -> lifecycle.transition(new ShipmentTransitionCommand(id, shipmentVersion(id), ShipmentStatus.CONFIRMED))); }
        finally { constraint("ALTER TABLE dbo.shipment_events DROP CONSTRAINT TEST_TRANSITION_FAILURE"); }
        as(f.owner, "ROLE_USER"); assertEquals(ShipmentStatus.CREATED, userShipments.detail(id, 0).status());
        assertEquals(before, count(ShipmentEvent.class)); assertEquals(0, notices.count());
    }
    @Test void bidirectionalOwnershipSearchPagingAndLiteralWildcardsRemainScopedAtService() {
        Long a = create(); as(f.other, "ROLE_USER"); Long b = create();
        assertEquals(b, userShipments.list("", null, PageRequest.of(0, 1)).getContent().getFirst().id());
        String codeA = db(() -> em.find(Shipment.class, a).getTrackingNumber());
        assertTrue(userShipments.list(codeA, null, PageRequest.of(0, 20)).isEmpty());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> userShipments.detail(a, 0)).getStatusCode().value());
        as(f.owner, "ROLE_USER"); assertEquals(1, userShipments.list("UTE", ShipmentStatus.CREATED, PageRequest.of(0, 20)).getTotalElements());
        for (String literal : List.of("%", "_", "[", "\\")) assertTrue(userShipments.list(literal, null, PageRequest.of(0, 20)).isEmpty());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> userShipments.detail(b, 0)).getStatusCode().value());
        assertTrue(userShipments.list("", null, PageRequest.of(1, 1)).isEmpty());
    }
    @ParameterizedTest @EnumSource(value = UserStatus.class, names={"LOCKED", "UNVERIFIED"})
    void disabledDatabaseUserWithStaleSessionLosesShipmentAccess(UserStatus status) {
        Long id = create(); db(() -> { em.find(User.class, f.owner.getId()).setStatus(status); return null; });
        assertThrows(AccessDeniedException.class, () -> userShipments.detail(id, 0));
        assertThrows(AccessDeniedException.class, () -> quoteService.quote(form()));
    }
    @ParameterizedTest @EnumSource(ShipmentStatus.class)
    void guestSummaryAcrossEveryCurrentStatusNeverExposesPrivateEventsOrWrites(ShipmentStatus status) {
        // A read-model fixture, not a claim of exercising lifecycle transitions for all physical routes.
        Long id = create();
        db(() -> { var s = em.find(Shipment.class, id); s.setStatus(status); s.setNote("PRIVATE-SHIPMENT-NOTE");
            var event = f.event(s, "PRIVATE-ASSIGNMENT-DISCREPANCY", status, status, NOW); event.setDescription("PRIVATE-INTERNAL-CONTENT");
            event.setLocationText("PRIVATE-ADDRESS"); return null; });
        String code = userShipments.detail(id, 0).trackingNumber();
        long eventCount = count(ShipmentEvent.class);
        var result = trackingService.lookup("  " + code.toLowerCase(Locale.ROOT) + "  ").orElseThrow();
        String serialized = result.toString();
        for (String secret : List.of("PRIVATE-", f.owner.getEmail(), "0901234567", "25000.00", "45000.00")) assertFalse(serialized.contains(secret));
        assertEquals(1, result.events().size()); assertEquals(eventCount, count(ShipmentEvent.class)); assertEquals(0, notices.count());
    }
    @ParameterizedTest @ValueSource(strings={"UTE", "UTE%", "UTE<script>", "UTE0000000000000000000000000000000 ", "UTE0000000000000000000000000000000-", "UTE 00000000000000000000000000000000"})
    void malformedTrackingCannotTriggerPersistence(String code) {
        assertThrows(com.ute.uteexpress.service.PublicTrackingService.InvalidTrackingNumberException.class, () -> trackingService.lookup(code));
        assertEquals(0, count(ShipmentEvent.class)); assertEquals(0, notices.count());
    }
    @Test void legacyShipmentWithoutMethodOrQuoteKeepsOriginalStoredFees() {
        Long id = db(() -> { var s = f.shipment(f.source, f.destination, ShipmentStatus.RETURNED, NOW.minusDays(20));
            s.setShipmentMethod(null); s.setPickupFee(null); return s.getId(); });
        var before = userShipments.detail(id, 0);
        db(() -> { em.find(ShippingRateRule.class, rateId).setBasePrice(new BigDecimal("99000.00")); return null; });
        var after = userShipments.detail(id, 0); assertNull(after.shipmentMethod()); assertNull(after.pickupFee());
        assertEquals(before.totalFee(), after.totalFee()); assertEquals(ShipmentStatus.RETURNED, after.status());
    }
}
