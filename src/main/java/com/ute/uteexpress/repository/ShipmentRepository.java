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

public interface ShipmentRepository extends JpaRepository<Shipment, Long>, JpaSpecificationExecutor<Shipment> {
    @EntityGraph(attributePaths = {"originBranch", "destinationBranch", "currentBranch", "service"})
    Optional<Shipment> findByIdAndSenderId(Long id, Long senderId);

    // Scalar projection: public lookups never load contacts, addresses or monetary fields.
    @Query("select s.status as status, s.updatedAt as updatedAt from Shipment s where s.trackingNumber = :trackingNumber")
    Optional<PublicTrackingSummary> findPublicTrackingSummary(@Param("trackingNumber") String trackingNumber);

    interface PublicTrackingSummary {
        ShipmentStatus getStatus();
        LocalDateTime getUpdatedAt();
    }
}
