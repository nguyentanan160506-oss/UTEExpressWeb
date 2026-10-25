package com.ute.uteexpress.service.notifications;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import java.time.*;
import org.springframework.data.domain.PageRequest;
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
            case CREATED -> {
                if(t.getSource()==TicketSource.CUSTOMER && t.getCreatedBy()!=null && e.getActor().getId().equals(t.getUser().getId())
                        && t.getCreatedBy().getId().equals(t.getUser().getId()))branchManagers(e,"Khách hàng gửi yêu cầu hỗ trợ mới");
            }
            case CUSTOMER_MESSAGE,REOPEN_REQUESTED -> {
                if(e.getActor().getId().equals(t.getUser().getId()) && t.getShipment()!=null
                        && t.getUser().getId().equals(t.getShipment().getSender().getId()) && e.getMessage()!=null
                        && e.getMessage().getTicket().getId().equals(t.getId()) && e.getMessage().getKind()==TicketMessageKind.PUBLIC) {
                    String title=e.getAction()==TicketAction.CUSTOMER_MESSAGE?"Khách hàng bổ sung yêu cầu hỗ trợ":"Khách hàng yêu cầu xem xét mở lại ticket";
                    if(!manager(e,t.getAssignedManager(),title))branchManagers(e,title);
                }
            }
            case ASSIGNED,REASSIGNED -> {
                manager(e,e.getAssignee(),"Bạn được phân công xử lý yêu cầu hỗ trợ");
                if(e.getPreviousAssignee()!=null)manager(e,e.getPreviousAssignee(),"Yêu cầu hỗ trợ đã được chuyển người xử lý");
            }
            case REOPENED -> {manager(e,e.getAssignee(),"Yêu cầu hỗ trợ đã được mở lại");owner(e,"Yêu cầu hỗ trợ đã được mở lại");}
            case CLOSED -> owner(e,"Yêu cầu hỗ trợ đã được đóng");
            case STATUS_CHANGED -> {
                if(e.getToStatus()==TicketStatus.WAITING_CUSTOMER)owner(e,"Yêu cầu hỗ trợ đang chờ bạn phản hồi");
                else if(e.getToStatus()==TicketStatus.PROCESSING && e.getFromStatus()==TicketStatus.WAITING_CUSTOMER)owner(e,"Yêu cầu hỗ trợ tiếp tục được xử lý");
            }
            case PUBLIC_REPLY,RESOLVED -> {
                User owner=t.getUser();
                if(t.getShipment()!=null && owner.getId().equals(t.getShipment().getSender().getId()))add(e,owner,"USER",e.getAction()==TicketAction.PUBLIC_REPLY?"Yêu cầu hỗ trợ đã có phản hồi công khai":"Yêu cầu hỗ trợ đã được giải quyết",null);
            }
            default -> { }
        }}
        e.setProcessedAt(now);events.saveAndFlush(e);
    }
    private void owner(SupportTicketEvent e,String title){
        var t=e.getTicket();if(t.getShipment()!=null && t.getUser().getId().equals(t.getShipment().getSender().getId()))add(e,t.getUser(),"USER",title,null);
    }
    private void branchManagers(SupportTicketEvent e,String title){
        if(e.getBranch().getStatus()!=BranchStatus.ACTIVE)return;
        // Same bounded, fail-atomic fan-out policy as shipment notifications; no global ROLE_MANAGER broadcast.
        for(int page=0;;page++){
            var batch=employees.findTicketCandidates(e.getBranch().getId(),PageRequest.of(page,100,org.springframework.data.domain.Sort.by("id")));
            if(batch.isEmpty())break;
            if(page>=10)throw new IllegalStateException("Ticket notification recipient limit exceeded");
            for(var employee:batch)manager(e,employee.getUser(),title);
            if(!batch.hasNext())break;
        }
    }
    private boolean manager(SupportTicketEvent e,User recipient,String title){
        if(recipient==null || recipient.getStatus()!=UserStatus.ACTIVE || !roles.findRoleNamesByUserId(recipient.getId()).contains("ROLE_MANAGER")
                || e.getBranch().getStatus()!=BranchStatus.ACTIVE || !memberships.existsByManagerIdAndBranchId(recipient.getId(),e.getBranch().getId()))return false;
        var employee=employees.findByUserId(recipient.getId()).orElse(null);
        if(employee==null || employee.getStatus()!=EmployeeStatus.ACTIVE || employee.getUser()==null || !recipient.getId().equals(employee.getUser().getId())
                || employee.getBranch()==null || !employee.getBranch().getId().equals(e.getBranch().getId()))return false;
        add(e,recipient,"MANAGER",title,e.getBranch());
        return true;
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
