package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.ShippingService;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface ShippingServiceRepository extends JpaRepository<ShippingService, Long> {
    List<ShippingService> findByIsActiveTrueOrderByNameAsc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ShippingService> findByIdAndIsActiveTrue(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ShippingService s where s.id = :id")
    Optional<ShippingService> findForPricingUpdate(Long id);
    boolean existsByCodeIgnoreCase(String code);
    @Query(value = "select s from ShippingService s where :q is null or upper(s.code) = :q or upper(s.name) = :q",
        countQuery = "select count(s) from ShippingService s where :q is null or upper(s.code) = :q or upper(s.name) = :q")
    Page<ShippingService> findCatalog(String q, Pageable pageable);
}
