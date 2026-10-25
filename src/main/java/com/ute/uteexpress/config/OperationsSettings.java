package com.ute.uteexpress.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Optional internal prioritization thresholds, disabled until configured; no contractual SLA. */
@Component
public class OperationsSettings {
    private final Duration shipmentWarningAge,ticketWarningAge;
    public OperationsSettings(@Value("${uteexpress.operations.shipment-warning-age:PT0S}") Duration shipment,
            @Value("${uteexpress.operations.ticket-warning-age:PT0S}") Duration ticket) {
        if(shipment.getNano()!=0 || ticket.getNano()!=0 || shipment.isNegative() || ticket.isNegative() || shipment.compareTo(Duration.ofDays(365))>0 || ticket.compareTo(Duration.ofDays(365))>0)throw new IllegalArgumentException("Operational warning ages must be whole seconds between zero and 365 days");
        shipmentWarningAge=shipment;ticketWarningAge=ticket;
    }
    public Duration shipmentWarningAge(){return shipmentWarningAge;}
    public Duration ticketWarningAge(){return ticketWarningAge;}
}
