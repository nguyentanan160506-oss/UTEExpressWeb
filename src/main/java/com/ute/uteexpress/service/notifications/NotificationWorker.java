package com.ute.uteexpress.service.notifications;

import com.ute.uteexpress.repository.*;
import java.time.*;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @ConditionalOnProperty(name = "uteexpress.notifications.worker.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationWorker {
    private final ShipmentEventRepository events;
    private final NotificationRepository notices;
    private final ShipmentNotificationProcessor processor;
    private final NotificationDelivery delivery;
    private final Clock clock;
    public NotificationWorker(ShipmentEventRepository events, NotificationRepository notices, ShipmentNotificationProcessor processor,
            NotificationDelivery delivery, Clock clock) {
        this.events = events; this.notices = notices; this.processor = processor; this.delivery = delivery; this.clock = clock;
    }
    @Scheduled(fixedDelayString = "${uteexpress.notifications.worker.delay-ms:3000}")
    public void poll() {
        try {
            var now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
            for (Long id : events.findNotificationWork(now, PageRequest.of(0, 30))) {
                try { processor.process(id); } catch (RuntimeException failure) { processor.retry(id); warn(); }
            }
            for (Long id : notices.findDeliveryWork(now, PageRequest.of(0, 30))) {
                try { delivery.deliver(id); } catch (RuntimeException failure) { delivery.retry(id); warn(); }
            }
        } catch (RuntimeException unavailable) { warn(); }
    }
    private void warn() { LoggerFactory.getLogger(getClass()).warn("Notification worker deferred work; pending database rows will be retried."); }
}
