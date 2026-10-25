package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.SupportTicketEvent;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
public interface SupportTicketEventRepository extends JpaRepository<SupportTicketEvent,Long> {
    @Query("select count(e) from SupportTicketEvent e where e.ticket.id=:ticketId and e.action=com.ute.uteexpress.enums.TicketAction.REOPEN_REQUESTED "
        +"and not exists (select later.id from SupportTicketEvent later where later.ticket.id=:ticketId and later.id>e.id "
        +"and later.action in (com.ute.uteexpress.enums.TicketAction.REOPENED,com.ute.uteexpress.enums.TicketAction.RESOLVED))")
    long pendingReopenRequests(Long ticketId);
    @Query("select e.message.id as messageId,e.action as action from SupportTicketEvent e where e.ticket.id=:ticketId and e.message.id in :ids "
        +"and e.action in (com.ute.uteexpress.enums.TicketAction.RESOLVED,com.ute.uteexpress.enums.TicketAction.REOPEN_REQUESTED)")
    List<PublicMessageAction> findPublicMessageActions(Long ticketId,Collection<Long> ids);
    interface PublicMessageAction {Long getMessageId();com.ute.uteexpress.enums.TicketAction getAction();}
    @EntityGraph(attributePaths={"actor"})
    Page<SupportTicketEvent> findByTicketId(Long ticketId,Pageable page);
    @Query("select e.id from SupportTicketEvent e where e.processedAt is null and (e.retryAt is null or e.retryAt<=:now) order by e.id")
    List<Long> findNotificationWork(LocalDateTime now,Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from SupportTicketEvent e where e.id=:id")
    Optional<SupportTicketEvent> lockNotificationWork(Long id);
}
