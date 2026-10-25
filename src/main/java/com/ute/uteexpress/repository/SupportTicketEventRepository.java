package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.SupportTicketEvent;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
public interface SupportTicketEventRepository extends JpaRepository<SupportTicketEvent,Long> {
    @EntityGraph(attributePaths={"actor"})
    Page<SupportTicketEvent> findByTicketId(Long ticketId,Pageable page);
    @Query("select e.id from SupportTicketEvent e where e.processedAt is null and (e.retryAt is null or e.retryAt<=:now) order by e.id")
    List<Long> findNotificationWork(LocalDateTime now,Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from SupportTicketEvent e where e.id=:id")
    Optional<SupportTicketEvent> lockNotificationWork(Long id);
}
