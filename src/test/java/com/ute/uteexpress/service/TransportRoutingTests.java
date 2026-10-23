package com.ute.uteexpress.service;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import org.junit.jupiter.api.*;
import static com.ute.uteexpress.transport.TransportFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class TransportRoutingTests {
    Branch origin = point(11, BranchType.LOCAL_BRANCH), destination = point(12, BranchType.LOCAL_BRANCH);
    Branch h1 = point(101, BranchType.CENTRAL_HUB), h2 = point(102, BranchType.CENTRAL_HUB);
    @BeforeEach void setup() { origin.setParentHub(h1); destination.setParentHub(h2); }
    @Test void canonicalPathUsesBothShipmentSnapshotsInOrder() {
        Shipment s = parcel(31, origin, destination); assertEquals(java.util.List.of(origin, h1, h2, destination), TransportRouting.path(s));
    }
    @Test void sameHubSkipsLinehaulAndReceiptBecomesDestinationHubMilestone() {
        destination.setParentHub(h1); Shipment s = parcel(31, origin, destination);
        assertEquals(java.util.List.of(origin, h1, destination), TransportRouting.path(s));
        assertEquals(ShipmentStatus.AT_DESTINATION_HUB, TransportRouting.statusAt(s, h1));
    }
    @Test void sameLocalPointHasNoTransportLeg() {
        Shipment s = parcel(31, origin, origin); assertEquals(java.util.List.of(origin), TransportRouting.path(s));
        assertThrows(LogisticsRuleException.class, () -> TransportRouting.validateRoute(origin, origin, TransportLeg.COLLECTION));
    }
    @Test void snapshotMismatchAndMissingOrInactiveHubFailWithoutGuessing() {
        Shipment s = parcel(31, origin, destination); s.setOriginHub(null);
        assertThrows(LogisticsRuleException.class, () -> TransportRouting.path(s)); s.setOriginHub(h1);
        origin.setParentHub(h2); assertThrows(LogisticsRuleException.class, () -> TransportRouting.validateLinks(s));
        origin.setParentHub(h1); h2.setStatus(BranchStatus.INACTIVE); assertThrows(LogisticsRuleException.class, () -> TransportRouting.path(s));
    }
    @Test void wrongHubOrLocalToLocalRouteCannotBypassNetwork() {
        assertThrows(LogisticsRuleException.class, () -> TransportRouting.validateRoute(origin, h2, TransportLeg.COLLECTION));
        assertThrows(LogisticsRuleException.class, () -> TransportRouting.validateRoute(h1, destination, TransportLeg.DISTRIBUTION));
        assertThrows(LogisticsRuleException.class, () -> TransportRouting.validateRoute(origin, destination, TransportLeg.LINEHAUL));
    }
    @Test void shipmentCannotSkipOriginHubOrMoveBeforePreviousReceiptState() {
        Shipment s = parcel(31, origin, destination); TransportTrip t = new TransportTrip(); t.setOrigin(h1); t.setDestination(h2);
        assertThrows(LogisticsRuleException.class, () -> TransportRouting.requireNext(s, t));
        s.setCurrentBranch(h1); s.setStatus(ShipmentStatus.IN_TRANSIT); assertThrows(LogisticsRuleException.class, () -> TransportRouting.requireNext(s, t));
        s.setStatus(ShipmentStatus.AT_ORIGIN_HUB); assertDoesNotThrow(() -> TransportRouting.requireNext(s, t));
    }
}
