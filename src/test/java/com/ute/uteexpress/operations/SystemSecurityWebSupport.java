package com.ute.uteexpress.operations;

import com.ute.uteexpress.config.SecurityConfig;
import com.ute.uteexpress.controller.*;
import com.ute.uteexpress.entity.User;
import com.ute.uteexpress.security.*;
import com.ute.uteexpress.service.TrackingRateLimiter;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/** MVC fixture uses real BCrypt/DAO authentication and the actual filter chain over H2. */
abstract class SystemSecurityWebSupport extends SystemSecuritySupport {
    static final String EXCLUDE="spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration";
    @TestConfiguration(proxyBeanMethods=false) @EnableAutoConfiguration
    @Import({SystemSecurityIntegrationConfig.class,SecurityConfig.class,SecurityErrorHandlers.class,AuthenticationResponseHandlers.class,DatabaseUserDetailsService.class,
        AuthController.class,AdminController.class,AdminReportsController.class,AdminAuditController.class,AdminReportingErrorAdvice.class,
        UserShipmentController.class,PublicQuoteController.class,PublicTrackingController.class,CustomerSupportController.class,CustomerSupportErrorAdvice.class,
        NotificationController.class,ManagerLogisticsController.class,ShipperPickupController.class,ManagerHandoffController.class,TransportTripController.class,
        ManagerDeliveryController.class,ShipperDeliveryController.class,OperationsDashboardController.class,ManagerTicketController.class,OperationsErrorAdvice.class})
    static class Config {
        @Bean TrackingRateLimiter trackingLimiter(OperationsJdbcConfig.MutableClock clock) {return new TrackingRateLimiter(clock,10000,10000);}
    }
    @Autowired WebApplicationContext context;
    @Autowired PasswordEncoder encoder;
    MockMvc mvc;
    final Map<Long,String> generated=new HashMap<>(),hashes=new HashMap<>();
    @BeforeEach void mvc() {SecurityContextHolder.clearContext();mvc=webAppContextSetup(context).apply(springSecurity()).defaultResponseCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8).build();}
    String credential(User account) {
        return generated.computeIfAbsent(account.getId(),id->{String value=UUID.randomUUID().toString(),hash=encoder.encode(value);hashes.put(id,hash);db(()->{em.find(User.class,id).setPasswordHash(hash);return null;});return value;});
    }
    MockHttpSession login(User account) throws Exception {
        String secret=credential(account);var session=new MockHttpSession();String previous=session.getId();
        releaseFixturePrincipal();
        mvc.perform(post("/login").session(session).with(anonymous()).with(csrf()).param("email",account.getEmail()).param("password",secret).param("redirect","https://untrusted.example"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/account"));assertNotEquals(previous,session.getId());releaseFixturePrincipal();return session;
    }
    void releaseFixturePrincipal() {org.springframework.security.test.context.TestSecurityContextHolder.clearContext();SecurityContextHolder.clearContext();}
    void safe(String text) {for(String value:hashes.values())assertFalse(text.contains(value),"Credential hash must not be rendered");for(String value:generated.values())assertFalse(text.contains(value),"Credential must not be rendered");assertFalse(text.contains("org.hibernate.exception"));}
}
