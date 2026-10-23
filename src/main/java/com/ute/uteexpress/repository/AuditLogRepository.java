package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import com.ute.uteexpress.enums.AuditResourceType;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findByResourceTypeAndResourceId(AuditResourceType resourceType, String resourceId, Pageable pageable);
}
