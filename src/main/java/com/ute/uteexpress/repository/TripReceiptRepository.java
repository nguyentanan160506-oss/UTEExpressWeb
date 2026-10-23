package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.TripReceipt;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface TripReceiptRepository extends JpaRepository<TripReceipt, Long> {
    boolean existsByItemId(Long itemId);
    Optional<TripReceipt> findByItemId(Long itemId);
    @Query("""
        select count(r) from TripReceipt r where r.item.shipment.id = :shipmentId and r.branch.id = :branchId
        and r.item.trip.destination.id = :branchId and r.item.status = com.ute.uteexpress.enums.TripItemStatus.RECEIVED
        """)
    long countProofAt(Long shipmentId, Long branchId);
}
