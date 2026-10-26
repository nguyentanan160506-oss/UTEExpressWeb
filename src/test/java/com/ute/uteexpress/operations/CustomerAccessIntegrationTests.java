package com.ute.uteexpress.operations;

import com.ute.uteexpress.config.SecurityConfig;
import com.ute.uteexpress.controller.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.security.*;
import com.ute.uteexpress.service.TrackingRateLimiter;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;
import static com.ute.uteexpress.operations.OperationsJdbcFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/** Real BCrypt authentication, session/security filters, MVC/Thymeleaf and JDBC. No Mockito beans. */
@SpringJUnitWebConfig(CustomerAccessIntegrationTests.Config.class)
@TestPropertySource(properties={"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration", "spring.flyway.enabled=false", "spring.sql.init.mode=never", "debug=false"})
class CustomerAccessIntegrationTests extends CustomerJourneySupport {
    @TestConfiguration(proxyBeanMethods=false) @EnableAutoConfiguration
    @Import({CustomerJourneyConfig.class, SecurityConfig.class, SecurityErrorHandlers.class,
        AuthenticationResponseHandlers.class, DatabaseUserDetailsService.class, AuthController.class,
        UserShipmentController.class, PublicQuoteController.class, PublicTrackingController.class,
        CustomerSupportController.class, CustomerSupportErrorAdvice.class, NotificationController.class})
    static class Config {
        @Bean TrackingRateLimiter trackingLimiter(OperationsJdbcConfig.MutableClock clock) { return new TrackingRateLimiter(clock,10000,10000); }
    }
    @Autowired WebApplicationContext context;
    @Autowired PasswordEncoder encoder;
    MockMvc mvc;
    @BeforeEach void web() {
        SecurityContextHolder.clearContext();
        mvc = webAppContextSetup(context).apply(springSecurity()).defaultResponseCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8).build();
    }
    MockHttpSession login(User account) throws Exception {
        String generated = UUID.randomUUID().toString(); String hash = encoder.encode(generated);
        db(() -> { em.find(User.class, account.getId()).setPasswordHash(hash); return null; });
        var session = new MockHttpSession(); String previous = session.getId();
        mvc.perform(post("/login").session(session).with(anonymous()).with(csrf())
            .param("email",account.getEmail()).param("password",generated)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/account"));
        assertNotEquals(previous, session.getId()); return session;
    }
    @Test void realDatabaseLoginRetainsSessionAndLogoutInvalidatesAuthentication() throws Exception {
        var session = login(f.owner);
        mvc.perform(get("/user/shipments").session(session)).andExpect(status().isOk());
        mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden()); assertFalse(session.isInvalid());
        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?logout"));
        assertTrue(session.isInvalid());
        mvc.perform(get("/user/shipments").with(anonymous())).andExpect(status().isUnauthorized());
    }
    @ParameterizedTest @EnumSource(value=UserStatus.class,names={"LOCKED","UNVERIFIED"})
    void lockedAndUnverifiedLoginsHaveSameGenericFailureAsWrongPassword(UserStatus state) throws Exception {
        String generated=UUID.randomUUID().toString(); String hash=encoder.encode(generated);
        db(() -> { var u=em.find(User.class,f.owner.getId());u.setPasswordHash(hash);u.setStatus(state);return null; });
        mvc.perform(post("/login").with(csrf()).param("email",f.owner.getEmail()).param("password",generated))
            .andExpect(redirectedUrl("/login?error"));
        mvc.perform(post("/login").with(csrf()).accept(MediaType.APPLICATION_JSON).param("email",f.owner.getEmail()).param("password",generated))
            .andExpect(status().isUnauthorized()).andExpect(content().json("{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401}"));
    }
    @ParameterizedTest @ValueSource(strings={"USER","MANAGER","SHIPPER","ADMIN"})
    void realDatabaseRolesDoNotImplyUserOrOtherRoles(String role) throws Exception {
        User account=switch(role){case "USER" -> f.owner;case "MANAGER" -> f.manager;case "SHIPPER" -> f.shipper;case "ADMIN" -> f.admin;default -> throw new AssertionError(role);};
        var session=login(account);
        mvc.perform(get("/account").session(session)).andExpect(status().isOk());
        mvc.perform(get("/user/shipments").session(session)).andExpect(status().is(role.equals("USER")?200:403));
        mvc.perform(get("/account/support").session(session)).andExpect(status().is(role.equals("USER")?200:403));
        if(role.equals("USER")) {
            mvc.perform(get("/admin/reports").session(session)).andExpect(status().isForbidden());
            mvc.perform(get("/manager/operations").session(session)).andExpect(status().isForbidden());
            mvc.perform(get("/shipper/pickups").session(session)).andExpect(status().isForbidden());
        }
    }
    @ParameterizedTest @ValueSource(strings={"/user/shipments","/account/support","/api/notifications"})
    void anonymousAndUnknownSessionGet401WithoutPrivateResponseOrRedirectLoop(String path) throws Exception {
        mvc.perform(get(path).with(anonymous()).with(request -> { request.setRequestedSessionId("unknown-session"); request.setRequestedSessionIdValid(false); return request; }).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Location"))
            .andExpect(content().json("{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401}"));
    }
    @Test void databaseRevocationBlocksExistingAuthenticatedSession() throws Exception {
        var session=login(f.owner);
        db(() -> { em.find(User.class,f.owner.getId()).setStatus(UserStatus.LOCKED);return null; });
        mvc.perform(get("/user/shipments").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/account/support").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/notifications").session(session)).andExpect(status().isForbidden());
    }
    MockHttpServletRequestBuilder quotePost() {
        var f=form(); return post("/user/shipments/quotes").with(user(this.f.owner.getEmail()).roles("USER")).with(csrf())
            .param("shipmentMethod",f.getShipmentMethod().name()).param("serviceId",f.getServiceId().toString())
            .param("originBranchId",f.getOriginBranchId().toString()).param("destinationBranchId",f.getDestinationBranchId().toString())
            .param("senderName",f.getSenderName()).param("senderPhone",f.getSenderPhone()).param("senderProvince",f.getSenderProvince())
            .param("senderDistrict",f.getSenderDistrict()).param("senderWard",f.getSenderWard()).param("senderAddress",f.getSenderAddress())
            .param("receiverName",f.getReceiverName()).param("receiverPhone",f.getReceiverPhone()).param("receiverProvince",f.getReceiverProvince())
            .param("receiverDistrict",f.getReceiverDistrict()).param("receiverWard",f.getReceiverWard()).param("receiverAddress",f.getReceiverAddress())
            .param("packageDescription",f.getPackageDescription()).param("weightKg",f.getWeightKg().toPlainString()).param("paymentPayer",f.getPaymentPayer().name())
            .param("codAmount","0").param("declaredValue","0");
    }
    @Test void actualMvcQuoteReviewConfirmPersistsShipmentAndDoubleSubmitReturnsSameResource() throws Exception {
        var result=mvc.perform(quotePost()).andExpect(status().is3xxRedirection()).andReturn();
        String path=result.getResponse().getRedirectedUrl(), token=path.substring(path.lastIndexOf('/')+1);
        mvc.perform(get(path).with(user(f.owner.getEmail()).roles("USER"))).andExpect(status().isOk()).andExpect(view().name("shipments/quote"));
        var first=mvc.perform(post("/user/shipments").with(user(f.owner.getEmail()).roles("USER")).with(csrf()).param("quoteToken",token))
            .andExpect(status().is3xxRedirection()).andReturn();
        mvc.perform(post("/user/shipments").with(user(f.owner.getEmail()).roles("USER")).with(csrf()).param("quoteToken",token))
            .andExpect(redirectedUrl(first.getResponse().getRedirectedUrl()));
        assertEquals(1,count(Shipment.class)); assertEquals(1,count(ShipmentEvent.class));
        mvc.perform(get(first.getResponse().getRedirectedUrl()).with(user(f.other.getEmail()).roles("USER"))).andExpect(status().isNotFound());
    }
    @ParameterizedTest @ValueSource(strings={"senderId","status","shippingFee","eventTitle","actorId"})
    void forgedShipmentFieldsAreRejectedByActualBinderWithoutPersistence(String field) throws Exception {
        mvc.perform(quotePost().param(field,"forged")).andExpect(status().isOk()).andExpect(view().name("shipments/create"))
            .andExpect(model().attributeHasErrors("shipmentForm"));
        assertEquals(0,count(ShipmentQuote.class)); assertEquals(0,count(Shipment.class));
    }
    @Test void csrfMissingOrInvalidPreventsGuestQuoteAndAuthenticatedShipmentWrites() throws Exception {
        mvc.perform(post("/quote")).andExpect(status().isForbidden());
        mvc.perform(post("/user/shipments").with(user(f.owner.getEmail()).roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/user/shipments").with(user(f.owner.getEmail()).roles("USER")).with(csrf().useInvalidToken())).andExpect(status().isForbidden());
        assertEquals(0,count(Shipment.class));
    }
    @Test void guestTrackingAndQuoteRenderWithoutExposingShipmentOrTicketInternals() throws Exception {
        as(f.owner,"ROLE_USER"); Long id=create(); String code=userShipments.detail(id,0).trackingNumber(); SecurityContextHolder.clearContext();
        mvc.perform(get("/tracking").with(anonymous()).param("trackingNumber",code)).andExpect(status().isOk())
            .andExpect(result -> {var html=result.getResponse().getContentAsString(); assertTrue(html.contains(code));
                for(String value:List.of(f.owner.getEmail(),"0901234567","Địa chỉ gửi thử nghiệm","45000.00","/account/support/")) assertFalse(html.contains(value)); });
        mvc.perform(get("/quote").with(anonymous())).andExpect(status().isOk()).andExpect(view().name("pricing/estimate"));
        mvc.perform(get("/user/shipments/"+id).with(anonymous()).param("trackingNumber",code)).andExpect(status().isUnauthorized());
        assertEquals(1,count(ShipmentEvent.class)); assertEquals(0,notices.count());
    }
    MockHttpServletRequestBuilder publicQuotePost() {
        var f=form();return post("/quote").with(anonymous()).with(csrf()).param("shipmentMethod","BRANCH_DROPOFF")
            .param("serviceId",f.getServiceId().toString()).param("originBranchId",f.getOriginBranchId().toString()).param("destinationBranchId",f.getDestinationBranchId().toString())
            .param("senderProvince",f.getSenderProvince()).param("senderDistrict",f.getSenderDistrict()).param("senderWard",f.getSenderWard())
            .param("receiverProvince",f.getReceiverProvince()).param("receiverDistrict",f.getReceiverDistrict()).param("receiverWard",f.getReceiverWard()).param("weightKg","1.01");
    }
    @Test void anonymousPostUsesPublishedDatabasePriceAndHasNoBusinessWrites() throws Exception {
        mvc.perform(publicQuotePost()).andExpect(status().isOk()).andExpect(model().attributeExists("estimate"))
            .andExpect(result->{var estimate=(com.ute.uteexpress.dto.pricing.PublicQuoteView)result.getModelAndView().getModel().get("estimate");
                assertEquals(new java.math.BigDecimal("45000.00"),estimate.totalFee());});
        assertEquals(0,count(ShipmentQuote.class));assertEquals(0,count(Shipment.class));assertEquals(0,count(ShipmentEvent.class));assertEquals(0,notices.count());
    }
    @Test void anonymousMissingPriceShowsBusinessErrorWithoutSqlOrPartialWrites() throws Exception {
        db(()->{em.find(ShippingRateRule.class,rateId).setActive(false);return null;});
        mvc.perform(publicQuotePost()).andExpect(status().isOk()).andExpect(model().attributeDoesNotExist("estimate"))
            .andExpect(model().attributeHasErrors("estimateForm")).andExpect(result->{String html=result.getResponse().getContentAsString();
                assertFalse(html.contains("org.hibernate"));assertFalse(html.contains("SQL statement"));assertFalse(html.contains("Exception:"));});
        assertEquals(0,count(Shipment.class));assertEquals(0,count(ShipmentQuote.class));
    }
}
