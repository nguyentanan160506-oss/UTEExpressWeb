package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.ShipmentIntake;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentIntakeRepository extends JpaRepository<ShipmentIntake, Long> {
    boolean existsByShipmentId(Long shipmentId);
    Optional<ShipmentIntake> findByShipmentId(Long shipmentId);
}
