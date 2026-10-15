package com.ute.uteexpress.security;

import com.ute.uteexpress.entity.User;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.service.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.ute.uteexpress.tracking.TrackingFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real public service, MVC templates and security chain; all persistence stays mocked. */
@SpringBootTest(classes = SecurityConfigTests.TestApplication.class, properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
    "spring.flyway.enabled=false", "spring.sql.init.mode=never",
    "spring.thymeleaf.check-template-location=false", "debug=false", "logging.level.root=INFO"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class PublicTrackingControllerTests {
    @Autowired private MockMvc mvc;
    @MockitoBean private UserRepository users;
    @MockitoBean private UserRoleRepository userRoles;
    @MockitoBean private RoleRepository roles;
    @MockitoBean private EmailOtpRepository otps;
    @MockitoBean private OtpMailSender otpMailSender;
    @MockitoBean private ShipmentRepository shipments;
    @MockitoBean private ShipmentEventRepository events;
    @MockitoBean private TrackingRateLimiter limiter;

    @BeforeEach void setup() {
        when(limiter.check(anyString())).thenReturn(new TrackingRateLimiter.Decision(true, 0));
        when(shipments.findPublicTrackingSummary(CODE)).thenReturn(Optional.of(summary(ShipmentStatus.IN_TRANSIT)));
        when(events.findPublicTrackingEvents(eq(CODE), eq(ShipmentStatus.CREATED),
                eq(PublicTrackingEventPolicy.CREATED_TITLE), any(Pageable.class)))
            .thenReturn(List.of(event(PublicTrackingEventPolicy.CREATED_TITLE, null, ShipmentStatus.CREATED)));
    }

    @Test void guestCanOpenTheSearchFormWithoutAnyDatabaseLookup() throws Exception {
        mvc.perform(get("/tracking")).andExpect(status().isOk()).andExpect(view().name("tracking/index"))
            .andExpect(result -> {
                String html = result.getResponse().getContentAsString();
                assertTrue(html.contains("Tra cứu vận đơn")); assertTrue(html.contains("method=\"get\""));
                assertTrue(html.contains("name=\"trackingNumber\"")); assertTrue(html.contains("maxlength=\"35\""));
                assertFalse(html.contains("name=\"id\"")); assertFalse(html.contains("Hành trình vận đơn</h3>"));
            });
        verifyNoInteractions(shipments, events, limiter);
    }

    @Test void guestLookupRendersPublicSummaryAndRealAllowedTimeline() throws Exception {
        mvc.perform(get("/tracking").param("trackingNumber", CODE.toLowerCase(Locale.ROOT)))
            .andExpect(status().isOk()).andExpect(result -> {
                String html = result.getResponse().getContentAsString();
                assertTrue(html.contains(CODE)); assertTrue(html.contains("Đang vận chuyển"));
                assertTrue(html.contains("Đã tạo vận đơn")); assertTrue(html.contains("16/10/2026 00:00"));
                assertTrue(html.contains("UTC")); assertFalse(html.contains("/user/shipments/"));
            });
        verify(shipments).findPublicTrackingSummary(CODE);
        verifyNoMoreInteractions(shipments);
    }

    @Test void absentAndUnpublishableSummaryHaveIdenticalPublicResponses() throws Exception {
        when(shipments.findPublicTrackingSummary(CODE)).thenReturn(Optional.empty());
        var absent = mvc.perform(get("/tracking").param("trackingNumber", CODE))
            .andExpect(status().isOk()).andExpect(model().attributeDoesNotExist("result")).andReturn();
        when(shipments.findPublicTrackingSummary(CODE)).thenReturn(Optional.of(summary(null)));
        var hidden = mvc.perform(get("/tracking").param("trackingNumber", CODE))
            .andExpect(status().isOk()).andExpect(model().attributeDoesNotExist("result")).andReturn();
        assertEquals(absent.getResponse().getContentAsString(), hidden.getResponse().getContentAsString());
        assertTrue(hidden.getResponse().getContentAsString().contains("Không tìm thấy thông tin tra cứu công khai"));
        verifyNoInteractions(events);
    }

    @Test void invalidBlankOversizedAndInjectedCodesAreRejectedWithoutQuerying() throws Exception {
        for (String input : List.of("", "31", "UTE123", "A".repeat(500), "<script>PRIVATE_MARKER</script>", "' OR 1=1 --")) {
            mvc.perform(get("/tracking").param("trackingNumber", input)).andExpect(status().isBadRequest())
                .andExpect(model().attributeDoesNotExist("result")).andExpect(result -> {
                    String html = result.getResponse().getContentAsString();
                    assertTrue(html.contains("Mã vận đơn không hợp lệ"));
                    assertFalse(html.contains("PRIVATE_MARKER")); assertFalse(html.contains("' OR 1=1 --"));
                });
        }
        verifyNoInteractions(shipments, events);
        verify(limiter, times(6)).check(anyString());
    }

    @Test void numericIdParameterCannotBeUsedForLookup() throws Exception {
        mvc.perform(get("/tracking").param("id", "31")).andExpect(status().isOk())
            .andExpect(model().attributeDoesNotExist("result"));
        mvc.perform(get("/tracking/31").accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        verifyNoInteractions(shipments, events);
    }

    @Test void privateAndUnapprovedEventContentNeverAppearsInHtmlOrViewModel() throws Exception {
        when(events.findPublicTrackingEvents(anyString(), any(), anyString(), any())).thenReturn(List.of(
            event("SENDER_NAME_MARKER RECEIVER_PHONE_MARKER ADDRESS_MARKER COD_MARKER", null, ShipmentStatus.CREATED),
            event("INTERNAL_LOCATION_MARKER", ShipmentStatus.IN_TRANSIT, ShipmentStatus.IN_TRANSIT),
            event("PASSWORD_HASH_MARKER", null, ShipmentStatus.DELIVERED),
            event(PublicTrackingEventPolicy.CREATED_TITLE, null, ShipmentStatus.CREATED)));
        mvc.perform(get("/tracking").param("trackingNumber", CODE)).andExpect(status().isOk()).andExpect(result -> {
            String html = result.getResponse().getContentAsString();
            String model = result.getModelAndView().getModel().get("result").toString();
            for (String marker : List.of("SENDER_NAME_MARKER", "RECEIVER_PHONE_MARKER", "ADDRESS_MARKER",
                    "COD_MARKER", "INTERNAL_LOCATION_MARKER", "PASSWORD_HASH_MARKER", "senderId",
                    "receiverName", "shippingFee", "declaredValue", "actorId", "phoneNumber", "description")) {
                assertFalse(html.contains(marker), marker); assertFalse(model.contains(marker), marker);
            }
            assertTrue(html.contains("Đã tạo vận đơn"));
        });
    }

    @Test void privateEventsDoNotProduceASyntheticPublicTimeline() throws Exception {
        when(events.findPublicTrackingEvents(anyString(), any(), anyString(), any())).thenReturn(List.of(
            event("INTERNAL_ONLY", ShipmentStatus.IN_TRANSIT, ShipmentStatus.IN_TRANSIT)));
        mvc.perform(get("/tracking").param("trackingNumber", CODE)).andExpect(status().isOk())
            .andExpect(result -> {
                String html = result.getResponse().getContentAsString();
                assertTrue(html.contains("Chưa có sự kiện công khai")); assertFalse(html.contains("INTERNAL_ONLY"));
                assertFalse(html.contains("Đã tạo vận đơn")); assertTrue(html.contains("Đang vận chuyển"));
            });
    }

    @Test void transportPeerAddressIsUsedAndForwardedHeadersCannotChooseTheBucket() throws Exception {
        mvc.perform(get("/tracking").param("trackingNumber", CODE)
                .header("X-Forwarded-For", "192.0.2.250").header("Forwarded", "for=192.0.2.251")
                .with(request -> { request.setRemoteAddr("192.0.2.1"); return request; }))
            .andExpect(status().isOk());
        verify(limiter).check("192.0.2.1");
        verify(limiter, never()).check("192.0.2.250"); verify(limiter, never()).check("192.0.2.251");
    }

    @Test void rateLimitReturns429AndRetryAfterBeforeAnyQuery() throws Exception {
        when(limiter.check(anyString())).thenReturn(new TrackingRateLimiter.Decision(false, 60));
        mvc.perform(get("/tracking").param("trackingNumber", CODE)).andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "60")).andExpect(model().attributeDoesNotExist("result"))
            .andExpect(result -> assertTrue(result.getResponse().getContentAsString().contains("tra cứu quá nhiều lần")));
        verifyNoInteractions(shipments, events);
    }

    @Test void publicResponsesAreNotCachedAndDoNotSendTrackingCodesAsReferrers() throws Exception {
        mvc.perform(get("/tracking").param("trackingNumber", CODE)).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("Referrer-Policy", "no-referrer"))
            .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"));
    }

    @Test void databaseFailuresUseAGenericUnavailableMessage() throws Exception {
        when(shipments.findPublicTrackingSummary(CODE))
            .thenThrow(new DataAccessResourceFailureException("SQL_INTERNAL_MARKER"));
        mvc.perform(get("/tracking").param("trackingNumber", CODE)).andExpect(status().isServiceUnavailable())
            .andExpect(result -> {
                String html = result.getResponse().getContentAsString();
                assertTrue(html.contains("Chưa thể tra cứu")); assertFalse(html.contains("SQL_INTERNAL_MARKER"));
            });
    }

    @Test void guestStillCannotReadUserShipmentByIdOrAdminPages() throws Exception {
        for (String path : List.of("/user/shipments/31", "/user/shipments", "/admin", "/api/unmapped")) {
            mvc.perform(get(path).accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(shipments, events);
    }

    @Test @WithMockUser(username = "public-fixture@example.test", roles = "USER")
    void userRetainsAccountAndOwnedHistoryAccessAndCanUsePublicTracking() throws Exception {
        stubActor("ROLE_USER");
        when(shipments.findAll(org.mockito.ArgumentMatchers.<Specification<com.ute.uteexpress.entity.Shipment>>any(),
            any(Pageable.class))).thenReturn(Page.empty());
        mvc.perform(get("/account")).andExpect(status().isOk());
        mvc.perform(get("/user/shipments")).andExpect(status().isOk());
        mvc.perform(get("/tracking").param("trackingNumber", CODE)).andExpect(status().isOk());
        mvc.perform(get("/admin").accept(MediaType.APPLICATION_JSON)).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(username = "public-fixture@example.test", roles = "ADMIN")
    void adminRetainsDashboardAccessAndCanUsePublicTrackingWithoutGainingUserAccess() throws Exception {
        stubActor("ROLE_ADMIN");
        mvc.perform(get("/admin")).andExpect(status().isOk());
        mvc.perform(get("/tracking").param("trackingNumber", CODE)).andExpect(status().isOk());
        mvc.perform(get("/user/shipments/31").accept(MediaType.APPLICATION_JSON)).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(roles = "USER")
    void onlyTheExactGetRouteWasMadePublic() throws Exception {
        mvc.perform(post("/tracking").with(csrf()).param("trackingNumber", CODE).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
        mvc.perform(get("/tracking/private").accept(MediaType.APPLICATION_JSON)).andExpect(status().isForbidden());
        mvc.perform(get("/api/tracking").accept(MediaType.APPLICATION_JSON)).andExpect(status().isForbidden());
        verifyNoInteractions(shipments, events);
    }

    @Test void publicAuthPagesLinkToTrackingAndItsLocalStylesAreAccessible() throws Exception {
        for (String path : List.of("/login", "/register")) {
            mvc.perform(get(path)).andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString().contains("href=\"/tracking\"")));
        }
        mvc.perform(get("/css/tracking.css")).andExpect(status().isOk());
    }

    private void stubActor(String role) {
        User actor = new User(); actor.setId(1L); actor.setEmail("public-fixture@example.test");
        actor.setStatus(UserStatus.ACTIVE);
        when(users.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(userRoles.findRoleNamesByUserId(1L)).thenReturn(List.of(role));
    }
}
