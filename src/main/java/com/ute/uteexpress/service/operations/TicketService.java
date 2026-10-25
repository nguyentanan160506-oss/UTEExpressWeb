package com.ute.uteexpress.service.operations;

import com.ute.uteexpress.config.OperationsSettings;
import com.ute.uteexpress.dto.operations.TicketCommands.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

@Service @Validated @Transactional @PreAuthorize("hasRole('MANAGER')")
public class TicketService {
    private final OperationsAccessService access;
    private final SupportTicketRepository tickets;
    private final SupportTicketAssignmentRepository assignments;
    private final SupportTicketMessageRepository messages;
    private final SupportTicketEventRepository events;
    private final ShipmentRepository shipments;
    private final OperationsSettings settings;
    private final Clock clock;
    public TicketService(OperationsAccessService access,SupportTicketRepository tickets,SupportTicketAssignmentRepository assignments,
            SupportTicketMessageRepository messages,SupportTicketEventRepository events,ShipmentRepository shipments,OperationsSettings settings,Clock clock) {
        this.access=access;this.tickets=tickets;this.assignments=assignments;this.messages=messages;this.events=events;this.shipments=shipments;this.settings=settings;this.clock=clock;
    }
    public Long create(@NotNull @Valid Create c) {
        var scope=access.scope(c.branchId());
        if(c.source()==TicketSource.CUSTOMER)throw rule("Luồng tiếp nhận này phải ghi nguồn do Manager tiếp nhận.");
        var previous=tickets.findByRequestTokenAndCreatedById(c.token(),scope.actor().getId());
        if(previous.isPresent()) {
            var t=previous.get();access.scope(t.getHandlingBranch().getId());
            if(!Objects.equals(t.getRequestFingerprint(),fingerprint(c)))throw rule("Mã yêu cầu đã được sử dụng với nội dung khác. Vui lòng tải lại form.");
            return t.getId();
        }
        Shipment s=shipments.findForTicket(c.trackingNumber().strip().toUpperCase(Locale.ROOT),scope.branch().getId())
            .orElseThrow(()->rule("Không tìm thấy vận đơn thuộc vai trò nguồn, đích hoặc đang giữ của chi nhánh này."));
        SupportTicket t=new SupportTicket();t.setTicketCode("TK"+UUID.randomUUID().toString().replace("-","").substring(0,24));t.setRequestToken(c.token());t.setRequestFingerprint(fingerprint(c));
        t.setUser(s.getSender());t.setShipment(s);t.setHandlingBranch(scope.branch());t.setCreatedBy(scope.actor());t.setSource(c.source());
        t.setCategory(c.category());t.setPriority(c.priority());t.setSubject(c.subject().strip());t.setContent(c.content().strip());t.setStatus(TicketStatus.OPEN);
        var now=now();t.setCreatedAt(now);t.setUpdatedAt(now);if(!settings.ticketWarningAge().isZero())t.setWarningDueAt(now.plus(settings.ticketWarningAge()));
        tickets.saveAndFlush(t);record(t,scope.actor(),TicketAction.CREATED,null,"Manager tiếp nhận yêu cầu qua "+c.source().name(),null,null,null);return t.getId();
    }
    public void assign(@NotNull @Positive Long id,@NotNull @Valid Assign c) {
        User actor=access.manager();SupportTicket t=locked(id,actor,c.version());
        if(!TicketStatePolicy.working(t.getStatus()))throw rule("Chỉ phân công ticket đang mở xử lý.");
        Employee employee=access.assignee(c.employeeId(),t.getHandlingBranch().getId());User old=t.getAssignedManager();
        if(old!=null && old.getId().equals(employee.getUser().getId()))throw rule("Người xử lý hiện tại không thay đổi.");
        String reason=text(c.reason(),500,old!=null);var active=assignments.findByTicketIdAndEndedAtIsNull(id);
        if(active.size()>1 || (old!=null && active.size()!=1) || (old==null && !active.isEmpty()))throw rule("Lịch sử phân công chưa nhất quán; cần kiểm tra dữ liệu.");
        for(var a:active){a.setEndedAt(now());a.setEndedBy(actor);assignments.saveAndFlush(a);}
        SupportTicketAssignment a=new SupportTicketAssignment();a.setTicket(t);a.setManager(employee.getUser());a.setEmployee(employee);a.setAssignedBy(actor);a.setAssignedAt(now());a.setReason(reason);assignments.saveAndFlush(a);
        TicketStatus from=t.getStatus();t.setAssignedManager(employee.getUser());if(from==TicketStatus.OPEN || from==TicketStatus.REOPENED)t.setStatus(TicketStatus.ASSIGNED);
        touch(t);record(t,actor,old==null?TicketAction.ASSIGNED:TicketAction.REASSIGNED,from,reason,employee.getUser(),old,null);
    }
    public void changeStatus(@NotNull @Positive Long id,@NotNull @Valid ChangeStatus c) {
        User actor=access.manager();SupportTicket t=locked(id,actor,c.version());
        if(!TicketStatePolicy.ordinary(t.getStatus(),c.status()))throw rule("Chuyển trạng thái không hợp lệ. Giải quyết, đóng hoặc mở lại dùng thao tác riêng.");
        access.currentAssignee(t);TicketStatus from=t.getStatus();t.setStatus(c.status());touch(t);record(t,actor,TicketAction.STATUS_CHANGED,from,text(c.reason(),500,false),null,null,null);
    }
    public void note(@NotNull @Positive Long id,@NotNull @Valid Message c) { message(id,c,TicketMessageKind.INTERNAL); }
    public void reply(@NotNull @Positive Long id,@NotNull @Valid Message c) { message(id,c,TicketMessageKind.PUBLIC); }
    private void message(Long id,Message c,TicketMessageKind kind) {
        User actor=access.manager();SupportTicket t=locked(id,actor,c.version());if(t.getStatus()==TicketStatus.CLOSED)throw rule("Ticket đã đóng; cần mở lại trước khi bổ sung nội dung.");
        SupportTicketMessage m=message(t,actor,kind,text(c.body(),2000,true));touch(t);
        record(t,actor,kind==TicketMessageKind.PUBLIC?TicketAction.PUBLIC_REPLY:TicketAction.INTERNAL_NOTE,t.getStatus(),null,null,null,m);
    }
    public void classify(@NotNull @Positive Long id,@NotNull @Valid Classify c) {
        User actor=access.manager();SupportTicket t=locked(id,actor,c.version());if(t.getStatus()==TicketStatus.CLOSED)throw rule("Ticket đã đóng.");
        String reason=text(c.reason(),500,true);boolean priority=t.getPriority()!=c.priority(),category=t.getCategory()!=c.category();if(!priority && !category)return;
        if(priority){TicketPriority old=t.getPriority();t.setPriority(c.priority());record(t,actor,TicketAction.PRIORITY_CHANGED,t.getStatus(),old+" → "+c.priority()+"; "+reason,null,null,null);}
        if(category){TicketCategory old=t.getCategory();t.setCategory(c.category());record(t,actor,TicketAction.CATEGORY_CHANGED,t.getStatus(),old+" → "+c.category()+"; "+reason,null,null,null);}touch(t);
    }
    public void resolve(@NotNull @Positive Long id,@NotNull @Valid Decision c) {
        User actor=access.manager();SupportTicket t=locked(id,actor,c.version());if(!TicketStatePolicy.resolvable(t.getStatus()))throw rule("Cần ticket đang xử lý/chờ phản hồi để giải quyết.");
        access.currentAssignee(t);String resolution=text(c.reason(),2000,true);TicketStatus from=t.getStatus();t.setStatus(TicketStatus.RESOLVED);t.setResolutionNotes(resolution);t.setResolvedAt(now());
        var m=message(t,actor,TicketMessageKind.PUBLIC,resolution);touch(t);record(t,actor,TicketAction.RESOLVED,from,null,t.getAssignedManager(),null,m);
    }
    public void close(@NotNull @Positive Long id,@NotNull @Valid Decision c) {
        User actor=access.manager();SupportTicket t=locked(id,actor,c.version());if(t.getStatus()!=TicketStatus.RESOLVED)throw rule("Chỉ đóng ticket đã được giải quyết.");
        String reason=text(c.reason(),500,true);TicketStatus from=t.getStatus();t.setStatus(TicketStatus.CLOSED);t.setClosedAt(now());touch(t);record(t,actor,TicketAction.CLOSED,from,reason,null,null,null);
    }
    public void reopen(@NotNull @Positive Long id,@NotNull @Valid Decision c) {
        User actor=access.manager();SupportTicket t=locked(id,actor,c.version());if(t.getStatus()!=TicketStatus.RESOLVED && t.getStatus()!=TicketStatus.CLOSED)throw rule("Chỉ mở lại ticket đã giải quyết hoặc đã đóng.");
        String reason=text(c.reason(),500,true);TicketStatus from=t.getStatus();t.setStatus(TicketStatus.REOPENED);t.setResolvedAt(null);t.setClosedAt(null);touch(t);record(t,actor,TicketAction.REOPENED,from,reason,t.getAssignedManager(),null,null);
    }
    private SupportTicket locked(Long id,User actor,Long version) {
        SupportTicket t=tickets.lockManaged(id,actor.getId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy ticket trong phạm vi."));
        access.scope(t.getHandlingBranch().getId());if(version==null || !Objects.equals(t.getVersion(),version))throw new ObjectOptimisticLockingFailureException(SupportTicket.class,id);return t;
    }
    private SupportTicketMessage message(SupportTicket t,User actor,TicketMessageKind kind,String body) {
        SupportTicketMessage m=new SupportTicketMessage();m.setTicket(t);m.setAuthor(actor);m.setKind(kind);m.setBody(body);m.setCreatedAt(now());return messages.saveAndFlush(m);
    }
    private void touch(SupportTicket t) {var now=now();t.setUpdatedAt(t.getUpdatedAt()!=null && !now.isAfter(t.getUpdatedAt())?t.getUpdatedAt().plusNanos(100):now);tickets.saveAndFlush(t);}
    private void record(SupportTicket t,User actor,TicketAction action,TicketStatus from,String detail,User assignee,User previous,SupportTicketMessage message) {
        SupportTicketEvent e=new SupportTicketEvent();e.setTicket(t);e.setBranch(t.getHandlingBranch());e.setActor(actor);e.setAction(action);e.setFromStatus(from);e.setToStatus(t.getStatus());e.setDetail(detail);e.setAssignee(assignee);e.setPreviousAssignee(previous);e.setMessage(message);e.setOccurredAt(now());events.saveAndFlush(e);
    }
    private String text(String value,int max,boolean required) {if((required && (value==null || value.isBlank())) || (value!=null && value.length()>max))throw rule("Nội dung/lý do bắt buộc và không được vượt giới hạn.");return value==null || value.isBlank()?null:value.strip();}
    private String fingerprint(Create c) {
        // Length-delimited canonical fields avoid separator ambiguity; hash is never accepted from the client.
        String[] fields={c.branchId().toString(),c.trackingNumber().strip().toUpperCase(Locale.ROOT),c.subject().strip(),c.content().strip(),c.category().name(),c.priority().name(),c.source().name()};
        var input=new StringBuilder();for(String field:fields)input.append(field.length()).append(':').append(field);
        try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(input.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException("SHA-256 unavailable",impossible);}
    }
    private LocalDateTime now(){var t=LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);return t.withNano(t.getNano()/100*100);}
    private OperationsRuleException rule(String message){return new OperationsRuleException(message);}
}
