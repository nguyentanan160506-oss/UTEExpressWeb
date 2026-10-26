package com.ute.uteexpress.operations;

import com.ute.uteexpress.service.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/** Reuse existing JDBC/pricing/report fixtures; every logistics service and repository is real. */
@TestConfiguration(proxyBeanMethods=false)
@Import({CustomerJourneyConfig.class, AdminReportingJdbcConfig.class, ManagerLogisticsService.class,
    ShipperPickupService.class, ManagerHandoffService.class, TransportTripReadService.class, DeliveryReadService.class})
class LogisticsJourneyConfig { }
