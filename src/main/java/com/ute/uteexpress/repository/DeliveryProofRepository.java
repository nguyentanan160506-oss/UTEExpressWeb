package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.DeliveryProof;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryProofRepository extends JpaRepository<DeliveryProof, Long> {
    Optional<DeliveryProof> findByShipmentId(Long shipmentId);
    boolean existsByShipmentId(Long shipmentId);
}
