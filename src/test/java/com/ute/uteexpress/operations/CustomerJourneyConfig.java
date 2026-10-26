package com.ute.uteexpress.operations;

import com.ute.uteexpress.service.*;
import com.ute.uteexpress.service.notifications.ShipmentNotificationProcessor;
import com.ute.uteexpress.service.notifications.NotificationWorker;
import com.ute.uteexpress.service.notifications.TicketNotificationWorker;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;

/** Real application services/repositories over isolated H2; no worker scheduler or SQL Server. */
@TestConfiguration(proxyBeanMethods = false)
@Import({OperationsJdbcConfig.class, QuoteService.class, BranchNetworkService.class,
    TrackingNumberGenerator.class, UserShipmentService.class, PublicTrackingService.class,
    ShipmentLifecycleService.class, LastMileService.class, DeliveryCustodyService.class,
    TransportTripService.class, ShipmentNotificationProcessor.class, AdminPricingService.class,
    NotificationWorker.class, TicketNotificationWorker.class})
public class CustomerJourneyConfig {
    @Bean org.springframework.validation.beanvalidation.LocalValidatorFactoryBean validator() {
        return new org.springframework.validation.beanvalidation.LocalValidatorFactoryBean();
    }
    @Bean static org.springframework.core.convert.ConversionService conversionService() {
        return org.springframework.boot.convert.ApplicationConversionService.getSharedInstance();
    }
}
