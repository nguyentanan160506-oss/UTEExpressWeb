package com.ute.uteexpress.operations;

import com.ute.uteexpress.config.OperationsSettings;
import com.ute.uteexpress.dto.operations.OperationsDashboardViews.Period;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.operations.*;
import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

class OperationsPolicyTests {
    OperationsDashboardReadService read=new OperationsDashboardReadService(null,null,Clock.fixed(Instant.parse("2026-10-24T00:00:00Z"),ZoneOffset.UTC),new OperationsSettings(Duration.ZERO,Duration.ZERO));
    @ParameterizedTest @CsvSource({"TODAY,2026-10-24","LAST_7_DAYS,2026-10-18","LAST_30_DAYS,2026-09-25"})
    void presetsUseOneUtcClockAndHalfOpenDayBounds(Period period,LocalDate expected){var r=read.range(period,null,null);assertEquals(expected,r.fromDate());assertEquals(LocalDateTime.of(2026,10,25,0,0),r.to());assertEquals(expected.atStartOfDay(),r.from());}
    @ParameterizedTest @CsvSource({"2026-10-25,2026-10-24","2026-07-01,2026-10-24","2026-10-24,2026-10-25","0000-01-01,0000-01-01"})
    void invalidOrUnboundedRangesAreRejected(LocalDate from,LocalDate to){assertThrows(OperationsRuleException.class,()->read.range(Period.CUSTOM,from,to));}
    @Test void missingCustomDateRejected(){assertThrows(OperationsRuleException.class,()->read.range(Period.CUSTOM,null,LocalDate.of(2026,10,24)));}
    @Test void inclusive93DaysAllowedBut94Rejected(){var end=LocalDate.of(2026,10,24);assertEquals(93,Duration.between(read.range(Period.CUSTOM,end.minusDays(92),end).from(),read.range(Period.CUSTOM,end.minusDays(92),end).to()).toDays());assertThrows(OperationsRuleException.class,()->read.range(Period.CUSTOM,end.minusDays(93),end));}
    @Test void zeroDenominatorsAreNotFalseZeroPercent(){assertNull(OperationsDashboardReadService.ratio(0,0));assertEquals(new BigDecimal("25.00"),OperationsDashboardReadService.ratio(1,4));assertEquals(new BigDecimal("0.00"),OperationsDashboardReadService.ratio(0,4));}
    @Test void warningsAreDisabledByDefaultAndNotInventedSlas(){var s=new OperationsSettings(Duration.ZERO,Duration.ZERO);assertTrue(s.shipmentWarningAge().isZero());assertTrue(s.ticketWarningAge().isZero());assertThrows(IllegalArgumentException.class,()->new OperationsSettings(Duration.ofSeconds(-1),Duration.ZERO));assertThrows(IllegalArgumentException.class,()->new OperationsSettings(Duration.ZERO,Duration.ofDays(366)));}
    @ParameterizedTest @CsvSource({"OPEN,PROCESSING,false","OPEN,RESOLVED,false","ASSIGNED,PROCESSING,true","PROCESSING,WAITING_CUSTOMER,true","PROCESSING,WAITING_INTERNAL,true","WAITING_CUSTOMER,PROCESSING,true","WAITING_INTERNAL,PROCESSING,true","REOPENED,PROCESSING,true","RESOLVED,CLOSED,false","CLOSED,PROCESSING,false","PROCESSING,CLOSED,false","ASSIGNED,RESOLVED,false"})
    void ordinaryTransitionsDoNotBypassDedicatedDecisions(TicketStatus from,TicketStatus to,boolean expected){assertEquals(expected,TicketStatePolicy.ordinary(from,to));}
    @ParameterizedTest @EnumSource(TicketStatus.class)
    void onlyActualWorkingStatesCanBeResolved(TicketStatus status){assertEquals(status==TicketStatus.PROCESSING || status==TicketStatus.WAITING_CUSTOMER || status==TicketStatus.WAITING_INTERNAL,TicketStatePolicy.resolvable(status));}
}
