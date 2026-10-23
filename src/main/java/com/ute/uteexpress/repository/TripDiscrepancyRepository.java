package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.TripDiscrepancy;
import com.ute.uteexpress.enums.TripDiscrepancyStatus;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripDiscrepancyRepository extends JpaRepository<TripDiscrepancy, Long> {
    long countByTripIdAndStatus(Long tripId, TripDiscrepancyStatus status);
    boolean existsByItemIdAndStatus(Long itemId, TripDiscrepancyStatus status);
    List<TripDiscrepancy> findByItemIdAndStatus(Long itemId, TripDiscrepancyStatus status);
    Optional<TripDiscrepancy> findByIdAndTripId(Long id, Long tripId);
    Page<TripDiscrepancy> findByTripId(Long tripId, Pageable pageable);
}
