package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.SupportTicketMessage;
import com.ute.uteexpress.enums.TicketMessageKind;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
public interface SupportTicketMessageRepository extends JpaRepository<SupportTicketMessage,Long> {
    @EntityGraph(attributePaths={"author"})
    Page<SupportTicketMessage> findByTicketId(Long ticketId,Pageable page);
    @Query("select m from SupportTicketMessage m where m.ticket.id=:ticketId and m.kind=:kind and m.ticket.user.id=:owner and m.ticket.shipment.sender.id=:owner")
    Page<SupportTicketMessage> findOwnedMessages(Long ticketId,Long owner,TicketMessageKind kind,Pageable page);
}
