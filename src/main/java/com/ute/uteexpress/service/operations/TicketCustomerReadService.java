package com.ute.uteexpress.service.operations;

import com.ute.uteexpress.dto.operations.TicketViews.*;
import com.ute.uteexpress.enums.TicketMessageKind;
import com.ute.uteexpress.dto.operations.CustomerTicketViews;
import com.ute.uteexpress.dto.operations.CustomerTicketViews.Filter;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.entity.SupportTicket;
import java.util.*;
import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.service.notifications.NotificationIdentity;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Customer-only projections over the existing ticket root. Never expose the Manager detail DTO. */
@Service @Transactional(readOnly=true) @PreAuthorize("hasRole('USER')")
public class TicketCustomerReadService {
    private final NotificationIdentity identity;private final UserRoleRepository roles;private final SupportTicketRepository tickets;private final SupportTicketMessageRepository messages;
    private final CustomerSupportAccess access;private final ShipmentRepository shipments;private final SupportTicketEventRepository events;
    public TicketCustomerReadService(NotificationIdentity identity,UserRoleRepository roles,SupportTicketRepository tickets,SupportTicketMessageRepository messages,
            CustomerSupportAccess access,ShipmentRepository shipments,SupportTicketEventRepository events){this.identity=identity;this.roles=roles;this.tickets=tickets;this.messages=messages;this.access=access;this.shipments=shipments;this.events=events;}
    public Page<CustomerTicketViews.Row> list(Filter filter,int page,int size) {
        page(page,size);var u=access.current();
        Set<TicketStatus> statuses=switch(filter==null?Filter.ALL:filter) {
            case ALL -> EnumSet.allOf(TicketStatus.class);
            case WORKING -> EnumSet.of(TicketStatus.OPEN,TicketStatus.ASSIGNED,TicketStatus.PROCESSING,TicketStatus.WAITING_INTERNAL,TicketStatus.REOPENED);
            case WAITING_CUSTOMER -> EnumSet.of(TicketStatus.WAITING_CUSTOMER);
            case RESOLVED -> EnumSet.of(TicketStatus.RESOLVED);
            case CLOSED -> EnumSet.of(TicketStatus.CLOSED);
        };
        return tickets.searchCustomer(u.getId(),statuses,PageRequest.of(page,size,Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id")))).map(this::row);
    }
    public Page<CustomerTicketViews.ShipmentOption> shipmentOptions(int page) {
        page(page,20);var u=access.current();
        return shipments.findSupportOptions(u.getId(),PageRequest.of(page,20,Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id"))))
            .map(s->new CustomerTicketViews.ShipmentOption(s.getId(),s.getTrackingNumber()));
    }
    public CustomerTicketViews.ShipmentOption shipment(Long id) {
        var u=access.current();var s=shipments.findByIdAndSenderId(id,u.getId()).orElseThrow(this::missing);
        return new CustomerTicketViews.ShipmentOption(s.getId(),s.getTrackingNumber());
    }
    public CustomerTicketViews.Detail portalDetail(Long id,int page) {
        page(page,20);var u=access.current();
        var t=tickets.findOwnedForCustomer(id,u.getId()).orElseThrow(this::missing);
        var publicRows=messages.findOwnedMessages(id,u.getId(),TicketMessageKind.PUBLIC,PageRequest.of(page,20,Sort.by(Sort.Order.asc("createdAt"),Sort.Order.asc("id"))));
        Map<Long,TicketAction> actions=new HashMap<>();
        if(!publicRows.isEmpty())for(var action:events.findPublicMessageActions(id,publicRows.map(m->m.getId()).getContent()))actions.put(action.getMessageId(),action.getAction());
        var visible=publicRows.map(m->new CustomerTicketViews.Message(m.getBody(),m.getCreatedAt(),m.getAuthor().getId().equals(u.getId()),
            actions.get(m.getId())==TicketAction.RESOLVED,actions.get(m.getId())==TicketAction.REOPEN_REQUESTED));
        boolean pending=events.pendingReopenRequests(id)>0;
        // Manual Manager intake content is internal. Only the authenticated customer's original text is published.
        String content=t.getSource()==TicketSource.CUSTOMER && t.getCreatedBy()!=null && t.getCreatedBy().getId().equals(u.getId())?t.getContent():null;
        CustomerTicketViews.Message resolution=null;
        if(t.getStatus()==TicketStatus.RESOLVED || t.getStatus()==TicketStatus.CLOSED) {
            var result=messages.findCustomerResolution(id,u.getId(),PageRequest.of(0,1));
            if(!result.isEmpty())resolution=new CustomerTicketViews.Message(result.getFirst().getBody(),result.getFirst().getCreatedAt(),false,true,false);
        }
        return new CustomerTicketViews.Detail(row(t),t.getVersion(),content,visible,resolution,TicketStatePolicy.customerMessage(t.getStatus()),TicketStatePolicy.customerReopenRequest(t.getStatus())&&!pending,pending);
    }
    private CustomerTicketViews.Row row(SupportTicket t){return new CustomerTicketViews.Row(t.getId(),t.getTicketCode(),t.getSubject(),t.getShipment().getTrackingNumber(),t.getCategory(),t.getStatus(),t.getCreatedAt(),t.getUpdatedAt());}
    private void page(int page,int size){if(page<0 || page>100000 || size<1 || size>50)throw new OperationsRuleException("Phân trang không hợp lệ (tối đa 50 yêu cầu mỗi trang).");}
    private ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy yêu cầu hoặc vận đơn được phép sử dụng.");}
    public CustomerDetail detail(Long id,int page){
        if(page<0 || page>100000)throw new OperationsRuleException("Phân trang không hợp lệ.");
        var u=identity.current();if(!roles.findRoleNamesByUserId(u.getId()).contains("ROLE_USER"))throw new AccessDeniedException("Forbidden");
        var t=tickets.findOwnedForCustomer(id,u.getId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy yêu cầu."));
        var visible=messages.findOwnedMessages(id,u.getId(),TicketMessageKind.PUBLIC,PageRequest.of(page,20,Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id")))).map(m->new PublicMessage(m.getBody(),m.getCreatedAt()));
        return new CustomerDetail(t.getTicketCode(),t.getSubject(),t.getStatus(),visible);
    }
}
