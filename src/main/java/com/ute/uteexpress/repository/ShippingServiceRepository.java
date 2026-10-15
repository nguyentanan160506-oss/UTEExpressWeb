package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.ShippingService;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ShippingServiceRepository extends JpaRepository<ShippingService, Long> {
    List<ShippingService> findByIsActiveTrueOrderByNameAsc();
    Optional<ShippingService> findByIdAndIsActiveTrue(Long id);
}
