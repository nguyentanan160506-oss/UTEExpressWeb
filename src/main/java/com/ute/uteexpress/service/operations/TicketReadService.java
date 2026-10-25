package com.ute.uteexpress.service.operations;

import com.ute.uteexpress.dto.operations.TicketViews.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import java.time.*;
import java.util.Set;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @Transactional(readOnly=true) @PreAuthorize("hasRole('MANAGER')")
public class TicketReadService {
    private final OperationsAccessService access;private final SupportTicketRepository tickets;
    private final SupportTicketMessageRepository messages;private final SupportTicketEventRepository events;private final EmployeeRepository employees;private final Clock clock;
    public TicketReadService(OperationsAccessService access,SupportTicketRepository tickets,SupportTicketMessageRepository messages,SupportTicketEventRepository events,EmployeeRepository employees,Clock clock){this.access=access;this.tickets=tickets;this.messages=messages;this.events=events;this.employees=employees;this.clock=clock;}
    public Page<Row> list(Long branch,TicketStatus status,TicketPriority priority,TicketCategory category,Long assignee,
            boolean unassigned,boolean overdue,int page,int size,String sort){
        if(!Set.of("createdAt","updatedAt").contains(sort))throw new OperationsRuleException("Cách sắp xếp không hợp lệ.");
        var scope=access.scope(branch);var result=tickets.search(scope.actor().getId(),scope.branch().getId(),status,priority,category,assignee,unassigned,overdue,now(),page(page,size,sort)).map(this::row);
        access.scope(scope.branch().getId());return result;
    }
    public Detail detail(Long id,int messagePage,int eventPage,int candidatePage){
        User actor=access.manager();SupportTicket t=tickets.findManaged(id,actor.getId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy ticket trong phạm vi."));access.scope(t.getHandlingBranch().getId());
        var reply=messages.findByTicketId(id,page(messagePage,20,"createdAt")).map(m->new Message(m.getId(),m.getKind(),m.getBody(),name(m.getAuthor()),m.getCreatedAt()));
        var history=events.findByTicketId(id,page(eventPage,20,"occurredAt")).map(e->new Event(e.getId(),e.getAction(),name(e.getActor()),e.getFromStatus(),e.getToStatus(),e.getDetail(),e.getOccurredAt()));
        var candidates=employees.findTicketCandidates(t.getHandlingBranch().getId(),page(candidatePage,20,"id")).map(e->new Candidate(e.getId(),e.getUser().getId(),e.getFullName()));
        Detail result=new Detail(row(t),t.getVersion(),t.getContent(),t.getShipment().getTrackingNumber(),t.getShipment().getStatus(),t.getSource(),name(t.getCreatedBy()),t.getResolvedAt(),t.getClosedAt(),reply,history,candidates,t.getStatus()!=TicketStatus.CLOSED);
        access.scope(t.getHandlingBranch().getId());return result;
    }
    public Page<Candidate> candidates(Long branch,int page){var scope=access.scope(branch);return employees.findTicketCandidates(scope.branch().getId(),page(page,20,"id")).map(e->new Candidate(e.getId(),e.getUser().getId(),e.getFullName()));}
    private Row row(SupportTicket t){return new Row(t.getId(),t.getTicketCode(),t.getSubject(),t.getStatus(),t.getPriority(),t.getCategory(),t.getHandlingBranch().getId(),t.getHandlingBranch().getName(),name(t.getAssignedManager()),t.getCreatedAt(),t.getWarningDueAt(),TicketStatePolicy.working(t.getStatus()) && t.getWarningDueAt()!=null && t.getWarningDueAt().isBefore(now()));}
    private Pageable page(int p,int size,String sort){if(p<0 || p>100000 || size<1 || size>50)throw new OperationsRuleException("Phân trang không hợp lệ.");return PageRequest.of(p,size,Sort.by(Sort.Order.desc(sort),Sort.Order.desc("id")));}
    private String name(User u){return u==null?"Chưa phân công":u.getUsername();}
    private LocalDateTime now(){return LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);}
}
