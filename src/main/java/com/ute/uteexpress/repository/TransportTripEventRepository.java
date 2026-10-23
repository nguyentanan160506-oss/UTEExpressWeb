package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.TransportTripEvent;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransportTripEventRepository extends JpaRepository<TransportTripEvent, Long> {
    Page<TransportTripEvent> findByTripId(Long tripId, Pageable pageable);
}
