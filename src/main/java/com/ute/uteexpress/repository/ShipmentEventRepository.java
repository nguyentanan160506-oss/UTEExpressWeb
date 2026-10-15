package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.ShipmentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ShipmentEventRepository extends JpaRepository<ShipmentEvent, Long> {
    Page<ShipmentEvent> findByShipmentIdAndShipmentSenderId(Long shipmentId, Long senderId, Pageable pageable);
}
