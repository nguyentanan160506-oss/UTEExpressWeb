package com.ute.uteexpress.service.operations;

import com.ute.uteexpress.dto.operations.TicketViews.*;
import com.ute.uteexpress.enums.TicketMessageKind;
import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.service.notifications.NotificationIdentity;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Commit17 extension: no customer HTTP routes are added in Commit16. */
@Service @Transactional(readOnly=true) @PreAuthorize("hasRole('USER')")
public class TicketCustomerReadService {
    private final NotificationIdentity identity;private final UserRoleRepository roles;private final SupportTicketRepository tickets;private final SupportTicketMessageRepository messages;
    public TicketCustomerReadService(NotificationIdentity identity,UserRoleRepository roles,SupportTicketRepository tickets,SupportTicketMessageRepository messages){this.identity=identity;this.roles=roles;this.tickets=tickets;this.messages=messages;}
    public CustomerDetail detail(Long id,int page){
        if(page<0 || page>100000)throw new OperationsRuleException("Phân trang không hợp lệ.");
        var u=identity.current();if(!roles.findRoleNamesByUserId(u.getId()).contains("ROLE_USER"))throw new AccessDeniedException("Forbidden");
        var t=tickets.findOwnedForCustomer(id,u.getId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy yêu cầu."));
        var visible=messages.findOwnedMessages(id,u.getId(),TicketMessageKind.PUBLIC,PageRequest.of(page,20,Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id")))).map(m->new PublicMessage(m.getBody(),m.getCreatedAt()));
        return new CustomerDetail(t.getTicketCode(),t.getSubject(),t.getStatus(),visible);
    }
}
