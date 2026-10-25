package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.SupportTicketMessage;
import com.ute.uteexpress.enums.TicketMessageKind;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
public interface SupportTicketMessageRepository extends JpaRepository<SupportTicketMessage,Long> {
    java.util.Optional<SupportTicketMessage> findByRequestTokenAndAuthorId(String token,Long authorId);
    @Query("select m from SupportTicketEvent e join e.message m where e.ticket.id=:ticketId and m.ticket.id=:ticketId "
        +"and e.action=com.ute.uteexpress.enums.TicketAction.RESOLVED and m.kind=com.ute.uteexpress.enums.TicketMessageKind.PUBLIC "
        +"and e.ticket.user.id=:owner and e.ticket.shipment.sender.id=:owner and e.ticket.handlingBranch is not null "
        +"and e.ticket.user.status=com.ute.uteexpress.enums.UserStatus.ACTIVE and exists (select r.id from UserRole r where r.user.id=:owner and r.role.name='ROLE_USER') order by e.id desc")
    java.util.List<SupportTicketMessage> findCustomerResolution(Long ticketId,Long owner,Pageable page);
    @EntityGraph(attributePaths={"author"})
    Page<SupportTicketMessage> findByTicketId(Long ticketId,Pageable page);
    @Query("select m from SupportTicketMessage m where m.ticket.id=:ticketId and m.kind=:kind and m.ticket.user.id=:owner and m.ticket.shipment.sender.id=:owner "
        +"and m.ticket.handlingBranch is not null and m.ticket.user.status=com.ute.uteexpress.enums.UserStatus.ACTIVE and exists (select r.id from UserRole r where r.user.id=:owner and r.role.name='ROLE_USER')")
    Page<SupportTicketMessage> findOwnedMessages(Long ticketId,Long owner,TicketMessageKind kind,Pageable page);
}
