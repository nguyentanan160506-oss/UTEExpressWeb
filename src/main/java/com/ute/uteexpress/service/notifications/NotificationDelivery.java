package com.ute.uteexpress.service.notifications;

import com.ute.uteexpress.repository.NotificationRepository;
import java.time.*;
import java.util.Map;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class NotificationDelivery {
    private final NotificationRepository notices;
    private final SimpMessagingTemplate broker;
    private final Clock clock;
    public NotificationDelivery(NotificationRepository notices, SimpMessagingTemplate broker, Clock clock) {
        this.notices = notices; this.broker = broker; this.clock = clock;
    }
    /** This transaction only reads notifications committed by the event worker's earlier transaction. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 15)
    public void deliver(Long id) {
        var n = notices.lockDelivery(id).orElse(null); var now = now();
        if (n == null || n.getDeliveredAt() != null || (n.getDeliveryRetryAt() != null && n.getDeliveryRetryAt().isAfter(now))) { return; }
        // Recheck current DB scope, including employee/membership/role changes since creation.
        if (notices.findVisibleById(id, n.getUser().getId()).isPresent()) {
            broker.convertAndSendToUser(n.getUser().getEmail(), "/queue/notifications", Map.of("type", "REFRESH"));
        }
        // Means accepted by local channel, not client acknowledgement. DB reconciliation handles missed frames.
        n.setDeliveredAt(now); notices.saveAndFlush(n);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 15)
    public void retry(Long id) {
        notices.lockDelivery(id).filter(n -> n.getDeliveredAt() == null).ifPresent(n -> {
            n.setDeliveryAttempts(Math.min(1000000, n.getDeliveryAttempts() + 1));
            n.setDeliveryRetryAt(now().plusSeconds(ShipmentNotificationProcessor.backoff(n.getDeliveryAttempts()))); notices.saveAndFlush(n);
        });
    }
    private LocalDateTime now() { var t = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); return t.withNano(t.getNano() / 100 * 100); }
}
