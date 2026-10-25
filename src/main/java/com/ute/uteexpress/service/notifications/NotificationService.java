package com.ute.uteexpress.service.notifications;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.NotificationRepository;
import java.time.*;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @Transactional(readOnly = true) @PreAuthorize("isAuthenticated()")
public class NotificationService {
    private final NotificationRepository notices;
    private final NotificationIdentity identity;
    private final Clock clock;
    public NotificationService(NotificationRepository notices, NotificationIdentity identity, Clock clock) {
        this.notices = notices; this.identity = identity; this.clock = clock;
    }
    public record Item(Long id, String title, String message, String type, boolean read, LocalDateTime createdAt) { }
    public record Inbox(List<Item> items, int page, int size, long totalElements, int totalPages, long unreadCount) { }
    public Inbox list(int page, int size, boolean unread) {
        if (page < 0 || page > 1000000 || size < 1 || size > 50) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phân trang không hợp lệ."); }
        Long userId = identity.current().getId();
        var result = notices.findVisible(userId, unread, PageRequest.of(page, size));
        return new Inbox(result.stream().map(n -> new Item(n.getId(), n.getTitle(), n.getMessage(), n.getNotificationType().name(), n.isRead(), n.getCreatedAt())).toList(),
            page, size, result.getTotalElements(), result.getTotalPages(), notices.unreadCount(userId));
    }
    public long count() { return notices.unreadCount(identity.current().getId()); }
    @Transactional
    public void read(Long id) {
        Long userId = identity.current().getId(); visible(id, userId);
        notices.markRead(id, userId, now());
    }
    @Transactional
    public void readAll() { notices.markAllRead(identity.current().getId(), now()); }
    public String target(Long id) {
        Notification n = visible(id, identity.current().getId());
        if (n.getRelatedTicket() != null) {
            return "MANAGER".equals(n.getAudience()) ? "/manager/tickets/" + n.getRelatedTicket().getId() : "/account/support/" + n.getRelatedTicket().getId();
        }
        Shipment s = n.getRelatedShipment();
        if ("USER".equals(n.getAudience()) && s != null) { return "/user/shipments/" + s.getId(); }
        if ("MANAGER".equals(n.getAudience())) { return "/manager"; }
        if ("SHIPPER".equals(n.getAudience()) && n.getAssignment() != null) {
            var a = n.getAssignment();
            String path = a.getAssignmentType() == AssignmentType.PICKUP ? "/shipper/pickups" : "/shipper/deliveries";
            return path + (a.getStatus() == AssignmentStatus.CANCELLED ? "" : "/" + a.getId());
        }
        return "/account";
    }
    private Notification visible(Long id, Long userId) {
        if (id == null || id <= 0) { throw missing(); }
        return notices.findVisibleById(id, userId).orElseThrow(this::missing);
    }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thông báo."); }
    private LocalDateTime now() { var t = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); return t.withNano(t.getNano() / 100 * 100); }
}
