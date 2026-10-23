package com.ute.uteexpress.service.notifications;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import java.time.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Event row is the durable inbox. Its lock serializes concurrent workers; all notices + acknowledgement commit together. */
@Service
public class ShipmentNotificationProcessor {
    private final ShipmentEventRepository events;
    private final NotificationRepository notices;
    private final UserRoleRepository roles;
    private final ManagerBranchRepository managers;
    private final Clock clock;
    public ShipmentNotificationProcessor(ShipmentEventRepository events, NotificationRepository notices,
            UserRoleRepository roles, ManagerBranchRepository managers, Clock clock) {
        this.events = events; this.notices = notices; this.roles = roles; this.managers = managers; this.clock = clock;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 15)
    public void process(Long id) {
        ShipmentEvent e = events.lockNotificationWork(id).orElse(null);
        var now = now();
        if (e == null || !e.isNotificationEnabled() || e.getNotificationProcessedAt() != null
                || (e.getNotificationRetryAt() != null && e.getNotificationRetryAt().isAfter(now))) { return; }
        String userTitle = ShipmentNotificationPolicy.userTitle(e);
        if (userTitle != null) { add(e, e.getShipment().getSender(), "USER", userTitle, null, null); }
        String managerTitle = ShipmentNotificationPolicy.managerTitle(e);
        Branch point = e.getBranch() == null ? e.getShipment().getOriginBranch() : e.getBranch();
        if (managerTitle != null && point != null && point.getStatus() == BranchStatus.ACTIVE) {
            // Bounded pages; refuse abnormal fan-out atomically instead of silently dropping recipients.
            for (int page = 0; ; page++) {
                var batch = managers.findNotificationManagers(point.getId(), PageRequest.of(page, 100));
                if (batch.isEmpty()) { break; }
                if (page >= 10) { throw new IllegalStateException("Notification recipient limit exceeded"); }
                for (User u : batch) { add(e, u, "MANAGER", managerTitle, point, null); }
                if (batch.size() < 100) { break; }
            }
        }
        if (ShipmentNotificationPolicy.assignment(e)) {
            ShipmentAssignment a = e.getNotificationAssignment(), previous = e.getNotificationPreviousAssignment();
            if (a != null) {
                boolean recall = "Manager khởi tạo hoàn, chờ thu hồi/vận chuyển thực tế".equals(e.getEventTitle());
                String title = recall ? (e.getNotificationAssignmentStatus() == AssignmentStatus.CANCELLED ? "Nhiệm vụ đã bị hủy do hoàn hàng" : "Nhiệm vụ cần xử lý thu hồi kiện")
                    : "Manager cho phép giao lại".equals(e.getEventTitle()) ? "Bạn có nhiệm vụ giao lại"
                    : "Chi nhánh từ chối bàn giao".equals(e.getEventTitle()) || "Chi nhánh từ chối nhận lại kiện".equals(e.getEventTitle()) ? "Bàn giao chưa được chấp nhận, cần kiểm tra nhiệm vụ"
                    : "Chi nhánh nhận bàn giao HOME_PICKUP".equals(e.getEventTitle()) || "Chi nhánh đã thực nhận lại kiện".equals(e.getEventTitle()) ? "Chi nhánh đã thực nhận bàn giao nhiệm vụ"
                    : a.getAssignmentType() == AssignmentType.PICKUP ? "Bạn được phân công lấy hàng" : "Bạn được phân công giao hàng";
                shipper(e, a, e.getNotificationAssigneeUser(), title);
            }
            if (previous != null) { shipper(e, previous, e.getNotificationPreviousUser(), "Nhiệm vụ đã được phân công lại cho người khác"); }
        }
        e.setNotificationProcessedAt(now); events.saveAndFlush(e);
    }
    private void shipper(ShipmentEvent e, ShipmentAssignment a, User recipient, String title) {
        if (!a.getShipment().getId().equals(e.getShipment().getId())) { throw new IllegalStateException("Invalid assignment event context"); }
        Employee employee = a.getEmployee(); Branch branch = a.getAssignmentType() == AssignmentType.PICKUP
            ? e.getShipment().getOriginBranch() : e.getShipment().getDestinationBranch();
        if (recipient != null && employee.getUser() != null && recipient.getId().equals(employee.getUser().getId())
                && employee.getStatus() == EmployeeStatus.ACTIVE && employee.getPosition() == EmployeePosition.COURIER_DRIVER
                && employee.getBranch() != null && branch != null && branch.getStatus() == BranchStatus.ACTIVE
                && branch.getId().equals(employee.getBranch().getId())) {
            add(e, recipient, "SHIPPER", title, branch, a);
        }
    }
    private void add(ShipmentEvent e, User user, String audience, String title, Branch branch, ShipmentAssignment assignment) {
        if (user == null || user.getStatus() != UserStatus.ACTIVE || !roles.findRoleNamesByUserId(user.getId()).contains("ROLE_" + audience)
                || notices.existsByUserIdAndSourceEventIdAndAudience(user.getId(), e.getId(), audience)) { return; }
        Notification n = new Notification(); n.setUser(user); n.setSourceEvent(e); n.setAudience(audience);
        n.setRelatedShipment(e.getShipment()); n.setScopeBranch(branch); n.setAssignment(assignment);
        n.setTitle(title); n.setMessage("Vận đơn " + e.getShipment().getTrackingNumber() + ". Mở mục liên quan để xem tình trạng và thao tác được phép.");
        n.setNotificationType(NotificationType.SHIPMENT_STATUS); n.setCreatedAt(now()); notices.saveAndFlush(n);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 15)
    public void retry(Long id) {
        events.lockNotificationWork(id).filter(e -> e.getNotificationProcessedAt() == null).ifPresent(e -> {
            e.setNotificationAttempts(Math.min(1000000, e.getNotificationAttempts() + 1));
            e.setNotificationRetryAt(now().plusSeconds(backoff(e.getNotificationAttempts()))); events.saveAndFlush(e);
        });
    }
    public static long backoff(int attempts) { return Math.min(300, 1L << Math.min(9, Math.max(1, attempts))); }
    private LocalDateTime now() { var t = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); return t.withNano(t.getNano() / 100 * 100); }
}
