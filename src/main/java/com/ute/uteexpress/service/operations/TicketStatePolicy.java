package com.ute.uteexpress.service.operations;

import com.ute.uteexpress.enums.TicketStatus;
import java.util.Set;
public final class TicketStatePolicy {
    private TicketStatePolicy() { }
    public static boolean working(TicketStatus s) { return Set.of(TicketStatus.OPEN,TicketStatus.ASSIGNED,TicketStatus.PROCESSING,TicketStatus.WAITING_CUSTOMER,TicketStatus.WAITING_INTERNAL,TicketStatus.REOPENED).contains(s); }
    public static boolean ordinary(TicketStatus from,TicketStatus to) {
        return switch(from) {
            case ASSIGNED,REOPENED -> to==TicketStatus.PROCESSING;
            case PROCESSING -> to==TicketStatus.WAITING_CUSTOMER || to==TicketStatus.WAITING_INTERNAL;
            case WAITING_CUSTOMER,WAITING_INTERNAL -> to==TicketStatus.PROCESSING;
            default -> false;
        };
    }
    public static boolean resolvable(TicketStatus s) { return Set.of(TicketStatus.PROCESSING,TicketStatus.WAITING_CUSTOMER,TicketStatus.WAITING_INTERNAL).contains(s); }
}
