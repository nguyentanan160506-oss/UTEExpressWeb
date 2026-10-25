package com.ute.uteexpress.service.notifications;

import com.ute.uteexpress.repository.SupportTicketEventRepository;
import java.time.*;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @ConditionalOnProperty(name="uteexpress.notifications.worker.enabled",havingValue="true",matchIfMissing=true)
public class TicketNotificationWorker {
    private final SupportTicketEventRepository events;private final TicketNotificationProcessor processor;private final Clock clock;
    public TicketNotificationWorker(SupportTicketEventRepository events,TicketNotificationProcessor processor,Clock clock){this.events=events;this.processor=processor;this.clock=clock;}
    @Scheduled(fixedDelayString="${uteexpress.notifications.worker.delay-ms:3000}") public void poll(){
        try{for(Long id:events.findNotificationWork(LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC),PageRequest.of(0,30))){
            try{processor.process(id);}catch(RuntimeException failure){try{processor.retry(id);}catch(RuntimeException unavailable){warn();}warn();}
        }}catch(RuntimeException unavailable){warn();}
    }
    private void warn(){LoggerFactory.getLogger(getClass()).warn("Ticket notification work deferred; pending rows remain in database.");}
}
