package com.ute.uteexpress.security;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.service.OtpMailSender;
import com.ute.uteexpress.notifications.NotificationScenario;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = SecurityConfigTests.TestApplication.class, properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
    "spring.flyway.enabled=false", "spring.sql.init.mode=never", "uteexpress.notifications.worker.enabled=false", "debug=false", "logging.level.root=INFO"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class NotificationControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean UserRepository users;
    @MockitoBean UserRoleRepository roles;
    @MockitoBean RoleRepository roleRepository;
    @MockitoBean EmailOtpRepository otps;
    @MockitoBean OtpMailSender mail;
    @MockitoBean NotificationRepository notices;
    NotificationScenario f;
    @BeforeEach void setup() {
        f = new NotificationScenario(); f.processor.process(f.event("Đã tạo vận đơn", null, ShipmentStatus.CREATED).getId());
        when(users.findByEmail(anyString())).thenAnswer(c->f.users.findByEmail(c.getArgument(0)));
        when(roles.findRoleNamesByUserId(anyLong())).thenAnswer(c->f.roles.findRoleNamesByUserId(c.getArgument(0)));
        when(notices.findVisible(anyLong(),anyBoolean(),any())).thenAnswer(c->f.notices.findVisible(c.getArgument(0),c.getArgument(1),c.getArgument(2)));
        when(notices.unreadCount(anyLong())).thenAnswer(c->f.notices.unreadCount(c.getArgument(0)));
        when(notices.findVisibleById(anyLong(),anyLong())).thenAnswer(c->f.notices.findVisibleById(c.getArgument(0),c.getArgument(1)));
        when(notices.markRead(anyLong(),anyLong(),any())).thenAnswer(c->f.notices.markRead(c.getArgument(0),c.getArgument(1),c.getArgument(2)));
        when(notices.markAllRead(anyLong(),any())).thenAnswer(c->f.notices.markAllRead(c.getArgument(0),c.getArgument(1)));
    }
    @AfterEach void close() { f.close(); }
    @Test void guestCannotReadApiOrHandshake() throws Exception {
        mvc.perform(get("/api/notifications").accept("application/json")).andExpect(status().isUnauthorized());
        mvc.perform(get("/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/ws/notifications")).andExpect(status().isUnauthorized());
    }
    @Test void ownerGetsSafeStableDtoAndNoStoreHeader() throws Exception {
        mvc.perform(get("/api/notifications").with(user(f.owner.getEmail()).roles("USER")))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.unreadCount").value(1))
            .andExpect(jsonPath("$.items[0].sourceEvent").doesNotExist()).andExpect(jsonPath("$.items[0].user").doesNotExist())
            .andExpect(jsonPath("$.items[0].relatedShipment").doesNotExist()).andExpect(jsonPath("$.items[0].id").value(1));
    }
    @Test void pageRendersAndEscapesStoredMarkup() throws Exception {
        f.rows.get(1L).setTitle("<script>alert(1)</script>"); f.rows.get(1L).setMessage("<img src=x onerror=alert(1)>");
        mvc.perform(get("/notifications").with(user(f.owner.getEmail()).roles("USER"))).andExpect(status().isOk()).andExpect(result->{
            String html=result.getResponse().getContentAsString();assertTrue(html.contains("&lt;script&gt;"));assertFalse(html.contains("<script>alert(1)</script>"));assertTrue(html.contains("/js/notifications.js"));assertTrue(html.contains("name=\"_csrf\""));
        });
    }
    @Test void emptyAndUnreadViewsRender() throws Exception {
        mvc.perform(get("/notifications").param("unread","true").with(user(f.other.getEmail()).roles("USER"))).andExpect(status().isOk()).andExpect(result->assertTrue(result.getResponse().getContentAsString().contains("Chưa có thông báo")));
    }
    @Test void postRequiresCsrfAndIgnoresClientRecipientFields() throws Exception {
        mvc.perform(post("/api/notifications/1/read").with(user(f.owner.getEmail()).roles("USER"))).andExpect(status().isForbidden()); assertFalse(f.rows.get(1L).isRead());
        mvc.perform(post("/api/notifications/1/read").with(user(f.other.getEmail()).roles("USER")).with(csrf()).param("userId","1")).andExpect(status().isNotFound()); assertFalse(f.rows.get(1L).isRead());
        mvc.perform(post("/api/notifications/1/read").with(user(f.owner.getEmail()).roles("USER")).with(csrf()).param("userId","2")).andExpect(status().isOk());assertTrue(f.rows.get(1L).isRead());
        mvc.perform(post("/api/notifications/1/read").with(user(f.owner.getEmail()).roles("USER")).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/notifications/unread-count").with(user(f.owner.getEmail()).roles("USER"))).andExpect(jsonPath("$.count").value(0));
    }
    @Test void markAllAffectsOnlyOwnAndSafeHtmlFallbackWorks() throws Exception {
        mvc.perform(post("/notifications/read-all").with(user(f.owner.getEmail()).roles("USER")).with(csrf())).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/notifications"));
        assertTrue(f.rows.get(1L).isRead());assertFalse(f.rows.get(2L).isRead());
    }
    @Test void navigationChecksOwnerAndCurrentManagerScope() throws Exception {
        mvc.perform(get("/notifications/1/open").with(user(f.owner.getEmail()).roles("USER"))).andExpect(redirectedUrl("/user/shipments/31"));
        mvc.perform(get("/notifications/1/open").with(user(f.other.getEmail()).roles("USER"))).andExpect(status().isNotFound());
        f.memberships.remove(3L);mvc.perform(get("/notifications/2/open").with(user(f.manager.getEmail()).roles("MANAGER"))).andExpect(status().isNotFound());
    }
    @ParameterizedTest @ValueSource(strings={"0","51","2147483647","abc"})
    void abnormalSizeRejected(String size) throws Exception { mvc.perform(get("/api/notifications").param("size",size).with(user(f.owner.getEmail()).roles("USER"))).andExpect(status().isBadRequest()); }
    @ParameterizedTest @ValueSource(strings={"ROLE_USER","ROLE_SHIPPER","ROLE_MANAGER","ROLE_ADMIN"})
    void allSupportedRolesHaveNotificationCenterButAdminHasNoImplicitShipmentNotices(String role) throws Exception {
        User account=f.user(100,role);mvc.perform(get("/notifications").with(user(account.getEmail()).authorities(()->role))).andExpect(status().isOk());
        mvc.perform(get("/api/notifications/unread-count").with(user(account.getEmail()).authorities(()->role))).andExpect(jsonPath("$.count").value(0));
    }
    @Test void disabledAccountCannotUseStaleSessionAuthorities() throws Exception { f.owner.setStatus(UserStatus.LOCKED);mvc.perform(get("/api/notifications").with(user(f.owner.getEmail()).roles("USER"))).andExpect(status().isForbidden()); }
    @Test void noNewPublicOrUserShipmentPermissions() throws Exception {
        mvc.perform(get("/api/anything-else").with(user(f.owner.getEmail()).roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/user/shipments/31")).andExpect(status().isUnauthorized());
        mvc.perform(get("/tracking")).andExpect(status().isOk());mvc.perform(get("/quote")).andExpect(status().isOk());
    }
}
