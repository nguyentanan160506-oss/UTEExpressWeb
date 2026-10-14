package com.ute.uteexpress.security;

import com.ute.uteexpress.dto.admin.*;
import com.ute.uteexpress.enums.UserStatus;
import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.service.*;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = SecurityConfigTests.TestApplication.class, properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
    "spring.flyway.enabled=false", "spring.sql.init.mode=never",
    "spring.thymeleaf.check-template-location=false", "debug=false", "logging.level.root=INFO"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class AdminControllerTests {
    @Autowired private MockMvc mvc;
    @MockitoBean private UserRepository users;
    @MockitoBean private UserRoleRepository userRoles;
    @MockitoBean private RoleRepository roles;
    @MockitoBean private EmailOtpRepository otps;
    @MockitoBean private OtpMailSender otpMailSender;
    @MockitoBean private AdminUserService admin;

    @Test @WithMockUser(roles = "ADMIN")
    void dashboardShowsDatabaseServiceCountsAndAdminNavigation() throws Exception {
        when(admin.dashboard()).thenReturn(new AdminDashboard(12, 7, 3, 2));
        mvc.perform(get("/admin")).andExpect(status().isOk()).andExpect(view().name("admin/dashboard"))
            .andExpect(model().attribute("stats", new AdminDashboard(12, 7, 3, 2)))
            .andExpect(result -> assertTrue(result.getResponse().getContentAsString().contains("UTEExpress · Admin")));
    }

    @Test void guestIsRejectedWith401ForAdminAndNestedRoutes() throws Exception {
        for (String path : List.of("/admin", "/admin/users", "/admin/users/2", "/admin/unknown")) {
            mvc.perform(get(path).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        }
        verifyNoInteractions(admin);
    }

    @Test @WithMockUser(roles = "USER")
    void userCannotReadOrWriteAdmin() throws Exception {
        mvc.perform(get("/admin").accept(MediaType.APPLICATION_JSON)).andExpect(status().isForbidden());
        mvc.perform(get("/admin").accept(MediaType.TEXT_HTML)).andExpect(status().isForbidden())
            .andExpect(result -> assertEquals("Forbidden", result.getResponse().getErrorMessage()));
        mvc.perform(post("/admin/users/2/lock").with(csrf()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
        verifyNoInteractions(admin);
    }

    @Test @WithMockUser(roles = "MANAGER")
    void managerCannotReadAdmin() throws Exception {
        mvc.perform(get("/admin/users").accept(MediaType.APPLICATION_JSON)).andExpect(status().isForbidden());
        verifyNoInteractions(admin);
    }

    @Test void guestHtmlUsesExistingUnauthorizedErrorHandler() throws Exception {
        mvc.perform(get("/admin").accept(MediaType.TEXT_HTML)).andExpect(status().isUnauthorized())
            .andExpect(result -> assertEquals("Unauthorized", result.getResponse().getErrorMessage()));
        verifyNoInteractions(admin);
    }

    @Test @WithMockUser(roles = "ADMIN")
    void listingPassesSearchStatusAndPageableAndRendersPagination() throws Exception {
        when(admin.list(eq("fixture"), eq(UserStatus.LOCKED), any(Pageable.class)))
            .thenAnswer(invocation -> new PageImpl<>(List.of(userView(UserStatus.LOCKED)), invocation.getArgument(2), 31));
        mvc.perform(get("/admin/users").param("q", "fixture").param("status", "LOCKED")
                .param("page", "1").param("size", "10"))
            .andExpect(status().isOk()).andExpect(view().name("admin/users"))
            .andExpect(model().attribute("q", "fixture"))
            .andExpect(result -> {
                String html = result.getResponse().getContentAsString();
                assertTrue(html.contains("target@example.test"));
                assertTrue(html.contains("Trang trước"));
                assertTrue(html.contains("Trang sau"));
            });
        var page = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(admin).list(eq("fixture"), eq(UserStatus.LOCKED), page.capture());
        assertEquals(1, page.getValue().getPageNumber());
        assertEquals(10, page.getValue().getPageSize());
    }

    @Test @WithMockUser(roles = "ADMIN")
    void detailRendersSafeFieldsRoleChoicesAndPostFormsWithCsrf() throws Exception {
        stubDetail(UserStatus.ACTIVE);
        mvc.perform(get("/admin/users/2")).andExpect(status().isOk()).andExpect(view().name("admin/user-detail"))
            .andExpect(result -> {
                String html = result.getResponse().getContentAsString();
                assertTrue(html.contains("target@example.test"));
                assertTrue(html.contains("action=\"/admin/users/2/lock\""));
                assertTrue(html.contains("action=\"/admin/users/2/roles\""));
                assertTrue(html.contains("name=\"_csrf\""));
                assertTrue(html.contains("name=\"roleIds\""));
                assertFalse(html.contains("passwordHash"));
                assertFalse(html.contains("otpHash"));
            });
    }

    @Test @WithMockUser(roles = "ADMIN")
    void unverifiedDetailDoesNotOfferLockOrUnlock() throws Exception {
        stubDetail(UserStatus.UNVERIFIED);
        mvc.perform(get("/admin/users/2")).andExpect(status().isOk())
            .andExpect(result -> {
                String html = result.getResponse().getContentAsString();
                assertFalse(html.contains("action=\"/admin/users/2/lock\""));
                assertFalse(html.contains("action=\"/admin/users/2/unlock\""));
            });
    }

    @Test @WithMockUser(roles = "ADMIN")
    void missingUserIs404() throws Exception {
        when(admin.detail(999L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản."));
        mvc.perform(get("/admin/users/999")).andExpect(status().isNotFound());
    }

    @Test @WithMockUser(roles = "ADMIN")
    void invalidQueryOrPageParametersAre400() throws Exception {
        mvc.perform(get("/admin/users").param("status", "INVALID")).andExpect(status().isBadRequest());
        mvc.perform(get("/admin/users").param("size", "1000")).andExpect(status().isBadRequest());
        mvc.perform(get("/admin/users").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/admin/users").param("q", "a".repeat(151))).andExpect(status().isBadRequest());
        mvc.perform(get("/admin/users/-2")).andExpect(status().isBadRequest());
        verifyNoInteractions(admin);
    }

    @Test @WithMockUser(roles = "ADMIN")
    void validLockAndUnlockShowSuccess() throws Exception {
        mvc.perform(post("/admin/users/2/lock").with(csrf()))
            .andExpect(redirectedUrl("/admin/users/2")).andExpect(flash().attributeExists("notice"));
        mvc.perform(post("/admin/users/2/unlock").with(csrf()))
            .andExpect(redirectedUrl("/admin/users/2")).andExpect(flash().attributeExists("notice"));
        verify(admin).lock(2L);
        verify(admin).unlock(2L);
    }

    @Test @WithMockUser(roles = "ADMIN")
    void businessRuleAndDatabaseFailuresAreNotReportedAsSuccess() throws Exception {
        doThrow(new AdminRuleException("Không thể tự khóa.")).when(admin).lock(2L);
        mvc.perform(post("/admin/users/2/lock").with(csrf()))
            .andExpect(redirectedUrl("/admin/users/2")).andExpect(flash().attributeExists("error"))
            .andExpect(result -> assertFalse(result.getFlashMap().containsKey("notice")));
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("test-only"))
            .when(admin).unlock(2L);
        mvc.perform(post("/admin/users/2/unlock").with(csrf()))
            .andExpect(flash().attributeExists("error"))
            .andExpect(result -> assertFalse(result.getFlashMap().containsKey("notice")));
    }

    @Test @WithMockUser(roles = "ADMIN")
    void validRoleFormUsesOnlySelectedIdsAndAcceptsCsrfFromHtmlForm() throws Exception {
        mvc.perform(post("/admin/users/2/roles").with(csrf()).param("roleIds", "11", "12").param("id", "999"))
            .andExpect(redirectedUrl("/admin/users/2")).andExpect(flash().attributeExists("notice"));
        var form = org.mockito.ArgumentCaptor.forClass(RoleUpdateForm.class);
        verify(admin).updateRoles(eq(2L), form.capture());
        assertEquals(List.of(11L, 12L), form.getValue().getRoleIds());
    }

    @Test @WithMockUser(roles = "ADMIN")
    void tamperedFieldsAndInvalidRoleValuesCannotChangeCredentialsOrRoles() throws Exception {
        mvc.perform(post("/admin/users/2/roles").with(csrf()).param("roleIds", "11")
                .param("passwordHash", "untrusted").param("status", "ACTIVE"))
            .andExpect(flash().attributeExists("error"))
            .andExpect(result -> assertFalse(result.getFlashMap().containsKey("notice")));
        mvc.perform(post("/admin/users/2/roles").with(csrf()).param("roleIds", "-1"))
            .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/admin/users/2/roles").with(csrf()).param("roleIds", "not-a-number"))
            .andExpect(flash().attributeExists("error"));
        verify(admin, never()).updateRoles(anyLong(), any());
    }

    @Test @WithMockUser(roles = "ADMIN")
    void allAdminMutationsRequireCsrfAndNeverAcceptGet() throws Exception {
        for (String suffix : List.of("lock", "unlock", "roles")) {
            mvc.perform(post("/admin/users/2/" + suffix).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
            mvc.perform(get("/admin/users/2/" + suffix)).andExpect(status().isMethodNotAllowed());
        }
        verifyNoInteractions(admin);
    }

    private void stubDetail(UserStatus status) {
        when(admin.detail(2L)).thenReturn(userView(status));
        when(admin.editableRoles()).thenReturn(List.of(
            new AdminRoleView(11L, "ROLE_USER"), new AdminRoleView(12L, "ROLE_MANAGER"), new AdminRoleView(13L, "ROLE_ADMIN")));
    }

    private AdminUserView userView(UserStatus status) {
        LocalDateTime time = LocalDateTime.of(2026, 10, 15, 0, 0);
        return new AdminUserView(2L, "fixture", "target@example.test", "Tài khoản thử nghiệm",
            "0901234567", status, time, time, List.of(new AdminRoleView(11L, "ROLE_USER")), false);
    }
}
