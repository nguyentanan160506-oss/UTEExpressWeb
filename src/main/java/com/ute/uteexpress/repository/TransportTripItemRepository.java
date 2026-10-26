package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.TransportTripItem;
import com.ute.uteexpress.enums.TripItemStatus;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface TransportTripItemRepository extends JpaRepository<TransportTripItem, Long> {
    // The trip's configured route also supplies the mapped VARCHAR type for :leg.
    // Bare parameter-to-enum-literal comparisons alone have no JDBC mapping in Hibernate 7.
    String RETURN_CANDIDATES = """
         exists (select route.id from TransportRoute route where route.origin.id = :originId and route.destination.id = :destinationId and route.leg = :leg)
         and s.status = com.ute.uteexpress.enums.ShipmentStatus.RETURNING and s.currentBranch.id = :originId
         and exists (select r.id from ReturnRequest r where r.shipment.id = s.id and r.stage = com.ute.uteexpress.enums.ReturnStage.READY_FOR_TRANSPORT)
         and s.originBranch.parentHub.id = s.originHub.id and s.destinationBranch.parentHub.id = s.destinationHub.id
         and s.originBranch.status = com.ute.uteexpress.enums.BranchStatus.ACTIVE and s.destinationBranch.status = com.ute.uteexpress.enums.BranchStatus.ACTIVE
         and s.originHub.status = com.ute.uteexpress.enums.BranchStatus.ACTIVE and s.destinationHub.status = com.ute.uteexpress.enums.BranchStatus.ACTIVE
         and not exists (select i.id from TransportTripItem i where i.shipment.id = s.id and i.active = true)
         and not exists (select a.id from ShipmentAssignment a where a.shipment.id = s.id and a.status in (com.ute.uteexpress.enums.AssignmentStatus.ASSIGNED, com.ute.uteexpress.enums.AssignmentStatus.IN_PROGRESS))
         and ((:leg = com.ute.uteexpress.enums.TransportLeg.COLLECTION and s.destinationBranch.id = :originId and s.destinationHub.id = :destinationId)
         or (:leg = com.ute.uteexpress.enums.TransportLeg.LINEHAUL and s.destinationHub.id = :originId and s.originHub.id = :destinationId and s.originHub.id <> s.destinationHub.id)
         or (:leg = com.ute.uteexpress.enums.TransportLeg.DISTRIBUTION and s.originHub.id = :originId and s.originBranch.id = :destinationId))
        """;
    @Query(value = "select s from Shipment s where " + RETURN_CANDIDATES, countQuery = "select count(s) from Shipment s where " + RETURN_CANDIDATES)
    Page<com.ute.uteexpress.entity.Shipment> findReturnCandidates(Long originId, Long destinationId, com.ute.uteexpress.enums.TransportLeg leg, Pageable pageable);
    String CANDIDATES = """
         exists (select route.id from TransportRoute route where route.origin.id = :originId and route.destination.id = :destinationId and route.leg = :leg)
         and s.currentBranch.id = :originId and s.shipmentMethod is not null and s.originBranch.id <> s.destinationBranch.id
         and s.originBranch.parentHub.id = s.originHub.id and s.destinationBranch.parentHub.id = s.destinationHub.id
         and s.originBranch.status = com.ute.uteexpress.enums.BranchStatus.ACTIVE
         and s.destinationBranch.status = com.ute.uteexpress.enums.BranchStatus.ACTIVE
         and s.originHub.status = com.ute.uteexpress.enums.BranchStatus.ACTIVE
         and s.destinationHub.status = com.ute.uteexpress.enums.BranchStatus.ACTIVE
         and exists (select r.id from ShipmentIntake r where r.shipment.id = s.id and r.branch.id = s.originBranch.id and r.shipmentMethod = s.shipmentMethod)
         and not exists (select i.id from TransportTripItem i where i.shipment.id = s.id and i.active = true)
         and not exists (select a.id from ShipmentAssignment a where a.shipment.id = s.id and a.status in (com.ute.uteexpress.enums.AssignmentStatus.ASSIGNED, com.ute.uteexpress.enums.AssignmentStatus.IN_PROGRESS))
         and ((:leg = com.ute.uteexpress.enums.TransportLeg.COLLECTION and s.originBranch.id = :originId and s.originHub.id = :destinationId and s.status = com.ute.uteexpress.enums.ShipmentStatus.AT_ORIGIN_BRANCH)
         or (:leg = com.ute.uteexpress.enums.TransportLeg.LINEHAUL and s.originHub.id = :originId and s.destinationHub.id = :destinationId and s.originHub.id <> s.destinationHub.id and s.status = com.ute.uteexpress.enums.ShipmentStatus.AT_ORIGIN_HUB)
         or (:leg = com.ute.uteexpress.enums.TransportLeg.DISTRIBUTION and s.destinationHub.id = :originId and s.destinationBranch.id = :destinationId and s.status = com.ute.uteexpress.enums.ShipmentStatus.AT_DESTINATION_HUB))
         and (s.currentBranch.id = s.originBranch.id or exists (select r.id from TripReceipt r where r.item.shipment.id = s.id and r.branch.id = :originId and r.item.trip.destination.id = :originId and r.item.status = com.ute.uteexpress.enums.TripItemStatus.RECEIVED))
        """;
    @Query(value = "select s from Shipment s where " + CANDIDATES, countQuery = "select count(s) from Shipment s where " + CANDIDATES)
    Page<com.ute.uteexpress.entity.Shipment> findCandidates(Long originId, Long destinationId, com.ute.uteexpress.enums.TransportLeg leg, Pageable pageable);
    String LOCAL_READY = """
         s.originBranch.id = s.destinationBranch.id and s.currentBranch.id = s.originBranch.id
         and s.shipmentMethod is not null and s.status = com.ute.uteexpress.enums.ShipmentStatus.AT_ORIGIN_BRANCH
         and s.originHub.id = s.destinationHub.id and s.originBranch.parentHub.id = s.originHub.id
         and s.originHub.status = com.ute.uteexpress.enums.BranchStatus.ACTIVE
         and exists (select m.id from ManagerBranch m where m.manager.id = :managerId and m.branch.id = s.originBranch.id and m.branch.status = com.ute.uteexpress.enums.BranchStatus.ACTIVE)
         and exists (select r.id from ShipmentIntake r where r.shipment.id = s.id and r.branch.id = s.originBranch.id and r.shipmentMethod = s.shipmentMethod)
         and not exists (select i.id from TransportTripItem i where i.shipment.id = s.id and i.active = true)
         and not exists (select a.id from ShipmentAssignment a where a.shipment.id = s.id and a.status in (com.ute.uteexpress.enums.AssignmentStatus.ASSIGNED, com.ute.uteexpress.enums.AssignmentStatus.IN_PROGRESS))
        """;
    @Query(value = "select s from Shipment s where " + LOCAL_READY, countQuery = "select count(s) from Shipment s where " + LOCAL_READY)
    Page<com.ute.uteexpress.entity.Shipment> findLocalReady(Long managerId, Pageable pageable);
    @Query("select i.shipment.id from TransportTripItem i where i.trip.id = :tripId order by i.shipment.id")
    List<Long> findShipmentIds(Long tripId);
    @Query("select i.shipment.id from TransportTripItem i where i.id = :itemId and i.trip.id = :tripId")
    Optional<Long> findShipmentId(Long tripId, Long itemId);
    Optional<TransportTripItem> findByIdAndTripId(Long itemId, Long tripId);
    Optional<TransportTripItem> findByTripIdAndShipmentId(Long tripId, Long shipmentId);
    Optional<TransportTripItem> findByShipmentIdAndActiveTrue(Long shipmentId);
    Page<TransportTripItem> findByTripId(Long tripId, Pageable pageable);
    long countByTripId(Long tripId);
    long countByTripIdAndStatus(Long tripId, TripItemStatus status);
    @Query(value = """
        select i from TransportTripItem i where i.shipment.id = :shipmentId and exists (
        select m.id from ManagerBranch m where m.manager.id = :managerId and m.branch.status = com.ute.uteexpress.enums.BranchStatus.ACTIVE
        and (m.branch.id = i.trip.origin.id or m.branch.id = i.trip.destination.id))
        """, countQuery = """
        select count(i) from TransportTripItem i where i.shipment.id = :shipmentId and exists (
        select m.id from ManagerBranch m where m.manager.id = :managerId and m.branch.status = com.ute.uteexpress.enums.BranchStatus.ACTIVE
        and (m.branch.id = i.trip.origin.id or m.branch.id = i.trip.destination.id))
        """)
    Page<TransportTripItem> findManagedHistory(Long shipmentId, Long managerId, Pageable pageable);
}
