package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.ShipmentQuote;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ShipmentQuoteRepository extends JpaRepository<ShipmentQuote, Long> {
    Optional<ShipmentQuote> findByTokenAndRequesterId(String token, Long requesterId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from ShipmentQuote q where q.token = :token and q.requester.id = :requesterId")
    Optional<ShipmentQuote> findOwnedForUpdate(@Param("token") String token, @Param("requesterId") Long requesterId);
}
