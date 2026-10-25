package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Optional;
import java.time.LocalDateTime;
import com.ute.uteexpress.enums.ShipmentStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface ShipmentRepository extends JpaRepository<Shipment, Long>, JpaSpecificationExecutor<Shipment> {
    @Query("select s.id as id,s.trackingNumber as trackingNumber from Shipment s where s.sender.id=:owner")
    org.springframework.data.domain.Page<SupportShipmentOption> findSupportOptions(Long owner,org.springframework.data.domain.Pageable page);
    interface SupportShipmentOption {Long getId();String getTrackingNumber();}
    @Override
    @EntityGraph(attributePaths = {"originBranch"})
    org.springframework.data.domain.Page<Shipment> findAll(org.springframework.data.jpa.domain.Specification<Shipment> spec,
        org.springframework.data.domain.Pageable pageable);
    Optional<Shipment> findByQuoteIdAndSenderId(Long quoteId, Long senderId);
    @EntityGraph(attributePaths = {"originBranch", "destinationBranch", "currentBranch", "service"})
    Optional<Shipment> findByIdAndSenderId(Long id, Long senderId);

    @Query("select s from Shipment s where s.trackingNumber=:tracking and (s.originBranch.id=:branchId or s.destinationBranch.id=:branchId or s.currentBranch.id=:branchId)")
    Optional<Shipment> findForTicket(String tracking,Long branchId);

    // All operational writers share this parent lock, then verify the submitted @Version.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"originBranch"})
    @Query("select s from Shipment s where s.id = :id")
    Optional<Shipment> findForLifecycle(@Param("id") Long id);

    // Scalar projection: public lookups never load contacts, addresses or monetary fields.
    @Query("select s.status as status, s.updatedAt as updatedAt from Shipment s where s.trackingNumber = :trackingNumber")
    Optional<PublicTrackingSummary> findPublicTrackingSummary(@Param("trackingNumber") String trackingNumber);

    interface PublicTrackingSummary {
        ShipmentStatus getStatus();
        LocalDateTime getUpdatedAt();
    }
}
