package com.ute.uteexpress.operations;

import com.ute.uteexpress.dto.operations.CustomerTicketCommands;
import com.ute.uteexpress.dto.operations.TicketCommands;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.operations.CustomerSupportThrottle;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.server.ResponseStatusException;
import static com.ute.uteexpress.operations.OperationsJdbcFixture.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercise the throttle inside the real TicketService transaction, not just its limiter class. */
@SpringJUnitConfig(CustomerThrottleIntegrationTests.Config.class)
class CustomerThrottleIntegrationTests extends CustomerSupportJdbcSupport {
    @TestConfiguration(proxyBeanMethods=false) @Import(OperationsJdbcConfig.class)
    static class Config {
        @Bean @Primary CustomerSupportThrottle boundedThrottle(OperationsJdbcConfig.MutableClock clock) {
            return new CustomerSupportThrottle(clock,2,100);
        }
    }
    @Test void quotaRollbackReplayOtherUserManagerAndClockResetWorkTogether() {
        Long id=createCustomer();var message=message(id,"Second write");tickets.customerMessage(id,message);
        var limit=assertThrows(ResponseStatusException.class,()->tickets.customerMessage(id,message(id,"Over quota")));
        assertEquals(429,limit.getStatusCode().value());assertEquals("60",limit.getHeaders().getFirst("Retry-After"));
        assertEquals(1,messageRows.count());assertEquals(2,eventRows.count());
        assertDoesNotThrow(()->tickets.customerMessage(id,message)); // replay does not consume another quota
        assertNotNull(customer.portalDetail(id,0));
        var own=db(()->{var s=f.shipment(f.source,f.destination,ShipmentStatus.CREATED,NOW);s.setSender(f.other);return s;});
        as(f.other,"ROLE_USER");assertNotNull(tickets.createCustomer(new CustomerTicketCommands.Create(own.getId(),TicketCategory.DELAY,"Other customer","Independent quota",token())));
        as(f.manager,"ROLE_MANAGER");tickets.reply(id,new TicketCommands.Message(version(id),"Manager is not throttled by customer quota"));
        as(f.owner,"ROLE_USER");assertEquals(2,customer.portalDetail(id,0).messages().getTotalElements());
        clock.set(NOW.plusMinutes(1));tickets.customerMessage(id,message(id,"After reset"));
        assertEquals(TicketStatus.OPEN,ticketRows.findById(id).orElseThrow().getStatus());
        assertEquals(3,customer.portalDetail(id,0).messages().getTotalElements());
    }
}
