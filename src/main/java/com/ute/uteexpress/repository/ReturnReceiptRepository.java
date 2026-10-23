package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.ReturnReceipt;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReturnReceiptRepository extends JpaRepository<ReturnReceipt, Long> {
    Optional<ReturnReceipt> findByReturnRequestId(Long requestId);
    boolean existsByReturnRequestId(Long requestId);
}
