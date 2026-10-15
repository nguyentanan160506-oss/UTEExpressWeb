package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.ShipmentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ute.uteexpress.enums.ShipmentStatus;
import java.time.LocalDateTime;
import java.util.List;

public interface ShipmentEventRepository extends JpaRepository<ShipmentEvent, Long> {
    Page<ShipmentEvent> findByShipmentIdAndShipmentSenderId(Long shipmentId, Long senderId, Pageable pageable);

    // Current schema has no event visibility flag. Restrict in SQL and verify the exact tuple again in the service.
    @Query("select e.eventTitle as title, e.fromStatus as fromStatus, e.toStatus as toStatus, e.occurredAt as occurredAt "
        + "from ShipmentEvent e where e.shipment.trackingNumber = :trackingNumber and e.fromStatus is null "
        + "and e.toStatus = :status and e.eventTitle = :title order by e.occurredAt desc, e.id desc")
    List<PublicTrackingEvent> findPublicTrackingEvents(@Param("trackingNumber") String trackingNumber,
        @Param("status") ShipmentStatus status, @Param("title") String title, Pageable pageable);

    interface PublicTrackingEvent {
        String getTitle();
        ShipmentStatus getFromStatus();
        ShipmentStatus getToStatus();
        LocalDateTime getOccurredAt();
    }
}
