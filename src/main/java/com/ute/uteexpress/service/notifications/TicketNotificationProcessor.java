package com.ute.uteexpress.service.notifications;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class TicketNotificationProcessor {
    private final SupportTicketEventRepository events;private final NotificationRepository notices;private final UserRoleRepository roles;
    private final EmployeeRepository employees;private final ManagerBranchRepository memberships;private final Clock clock;
    public TicketNotificationProcessor(SupportTicketEventRepository events,NotificationRepository notices,UserRoleRepository roles,EmployeeRepository employees,ManagerBranchRepository memberships,Clock clock){this.events=events;this.notices=notices;this.roles=roles;this.employees=employees;this.memberships=memberships;this.clock=clock;}
    @Transactional(propagation=Propagation.REQUIRES_NEW,timeout=15)
    public void process(Long id){
        var e=events.lockNotificationWork(id).orElse(null);var now=now();if(e==null || e.getProcessedAt()!=null || (e.getRetryAt()!=null && e.getRetryAt().isAfter(now)))return;
        var t=e.getTicket();boolean currentBranch=e.getBranch().getId().equals(t.getHandlingBranch().getId());
        if(currentBranch){switch(e.getAction()){
            case ASSIGNED,REASSIGNED -> {
                manager(e,e.getAssignee(),"Bạn được phân công xử lý yêu cầu hỗ trợ");
                if(e.getPreviousAssignee()!=null)manager(e,e.getPreviousAssignee(),"Yêu cầu hỗ trợ đã được chuyển người xử lý");
            }
            case REOPENED -> manager(e,e.getAssignee(),"Yêu cầu hỗ trợ đã được mở lại");
            case PUBLIC_REPLY,RESOLVED -> {
                User owner=t.getUser();
                if(t.getShipment()!=null && owner.getId().equals(t.getShipment().getSender().getId()))add(e,owner,"USER",e.getAction()==TicketAction.PUBLIC_REPLY?"Yêu cầu hỗ trợ đã có phản hồi công khai":"Yêu cầu hỗ trợ đã được giải quyết",null);
            }
            default -> { }
        }}
        e.setProcessedAt(now);events.saveAndFlush(e);
    }
    private void manager(SupportTicketEvent e,User recipient,String title){
        if(recipient==null || e.getBranch().getStatus()!=BranchStatus.ACTIVE || !memberships.existsByManagerIdAndBranchId(recipient.getId(),e.getBranch().getId()))return;
        var employee=employees.findByUserId(recipient.getId()).orElse(null);
        if(employee==null || employee.getStatus()!=EmployeeStatus.ACTIVE || employee.getUser()==null || !recipient.getId().equals(employee.getUser().getId())
                || employee.getBranch()==null || !employee.getBranch().getId().equals(e.getBranch().getId()))return;
        add(e,recipient,"MANAGER",title,e.getBranch());
    }
    private void add(SupportTicketEvent e,User recipient,String audience,String title,Branch branch){
        if(recipient==null || recipient.getStatus()!=UserStatus.ACTIVE || !roles.findRoleNamesByUserId(recipient.getId()).contains("ROLE_"+audience)
                || notices.existsByUserIdAndSourceTicketEventIdAndAudience(recipient.getId(),e.getId(),audience))return;
        Notification n=new Notification();n.setUser(recipient);n.setSourceTicketEvent(e);n.setRelatedTicket(e.getTicket());n.setAudience(audience);n.setScopeBranch(branch);
        n.setTitle(title);n.setMessage("Yêu cầu "+e.getTicket().getTicketCode()+" đã được cập nhật. Nội dung nội bộ không được đưa vào thông báo.");n.setNotificationType(NotificationType.SUPPORT);n.setCreatedAt(now());notices.saveAndFlush(n);
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW,timeout=15)
    public void retry(Long id){events.lockNotificationWork(id).filter(e->e.getProcessedAt()==null).ifPresent(e->{e.setAttempts(Math.min(1000000,e.getAttempts()+1));e.setRetryAt(now().plusSeconds(ShipmentNotificationProcessor.backoff(e.getAttempts())));events.saveAndFlush(e);});}
    private LocalDateTime now(){var t=LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);return t.withNano(t.getNano()/100*100);}
}
