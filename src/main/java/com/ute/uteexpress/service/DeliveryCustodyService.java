package com.ute.uteexpress.service;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import org.springframework.stereotype.Component;

/** Internal proof checker. Caller must authorize and lock Shipment before any write. */
@Component
public class DeliveryCustodyService {
    private final TripReceiptRepository receipts;
    private final ShipmentIntakeRepository intakes;
    private final DeliveryDispatchRepository dispatches;
    private final DeliveryRecoveryRepository recoveries;
    private final TransportTripItemRepository tripItems;
    private final ShipmentEventRepository events;
    public DeliveryCustodyService(TripReceiptRepository receipts, ShipmentIntakeRepository intakes, DeliveryDispatchRepository dispatches,
            DeliveryRecoveryRepository recoveries, TransportTripItemRepository tripItems, ShipmentEventRepository events) {
        this.receipts = receipts; this.intakes = intakes; this.dispatches = dispatches;
        this.recoveries = recoveries; this.tripItems = tripItems; this.events = events;
    }
    void requireAtDestination(Shipment s) {
        requireAtDestination(s, null);
    }
    // A return trip may recheck its own reservation while sealing/departing, never another trip's.
    void requireAtDestination(Shipment s, Long reservingTripId) {
        var reservation = tripItems.findByShipmentIdAndActiveTrue(s.getId());
        boolean conflict = reservation.isPresent() && (reservingTripId == null
            || !reservingTripId.equals(reservation.get().getTrip().getId())
            || reservation.get().getStatus() != TripItemStatus.MANIFESTED);
        if (!TransportRouting.same(s.getCurrentBranch(), s.getDestinationBranch()) || s.getShipmentMethod() == null
                || s.getDestinationBranch().getBranchType() != BranchType.LOCAL_BRANCH
                || s.getDestinationBranch().getStatus() != BranchStatus.ACTIVE || conflict) { throw invalid(); }
        var held = dispatches.findFirstByShipmentIdAndAcceptedAtIsNotNullOrderByAcceptedAtDescIdDesc(s.getId());
        if (held.isPresent()) {
            if (held.get().getStatus() != DeliveryDispatchStatus.BRANCH_RETURNED
                    || !recoveries.existsByAssignmentIdAndStatus(held.get().getAssignment().getId(), DeliveryRecoveryStatus.RECEIVED)) { throw invalid(); }
            return;
        }
        if (receipts.countForwardProofAt(s.getId(), s.getDestinationBranch().getId()) > 0) { return; }
        if (TransportRouting.same(s.getOriginBranch(), s.getDestinationBranch())
                && events.existsByShipmentIdAndFromStatusAndToStatusAndEventTitle(s.getId(), ShipmentStatus.AT_ORIGIN_BRANCH,
                    ShipmentStatus.AT_DESTINATION_BRANCH, "Phân loại chờ giao tại cùng chi nhánh")) {
            ShipmentIntake r = intakes.findByShipmentId(s.getId()).orElseThrow(this::invalid);
            if (TransportRouting.same(r.getBranch(), s.getDestinationBranch()) && r.getShipmentMethod() == s.getShipmentMethod()) { return; }
        }
        throw invalid();
    }
    private LogisticsRuleException invalid() { return new LogisticsRuleException("Chưa có bằng chứng kiện đang thực sự tại chi nhánh đích, hoặc kiện còn thuộc chuyến/Shipper."); }
}
