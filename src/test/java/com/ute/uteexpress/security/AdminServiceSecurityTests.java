package com.ute.uteexpress.security;

import com.ute.uteexpress.entity.User;
import com.ute.uteexpress.enums.UserStatus;
import com.ute.uteexpress.repository.RoleRepository;
import com.ute.uteexpress.repository.UserRepository;
import com.ute.uteexpress.repository.UserRoleRepository;
import com.ute.uteexpress.service.AdminUserService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(AdminServiceSecurityTests.Config.class)
class AdminServiceSecurityTests {
    @TestConfiguration
    @EnableMethodSecurity
    @Import({AdminUserService.class,com.ute.uteexpress.config.OtpConfig.class})
    static class Config { }

    @Autowired private AdminUserService service;
    @MockitoBean private UserRepository users;
    @MockitoBean private RoleRepository roles;
    @MockitoBean private UserRoleRepository memberships;
    @MockitoBean private com.ute.uteexpress.repository.AuditLogRepository audits;

    @Test void guestCannotInvokeServiceDirectly() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, service::dashboard);
        verifyNoInteractions(users, roles, memberships);
    }

    @Test @WithMockUser(roles = "USER")
    void userCannotInvokeServiceDirectly() {
        assertThrows(AccessDeniedException.class, service::dashboard);
        assertThrows(AccessDeniedException.class, () -> service.lock(2L));
        verifyNoInteractions(users, roles, memberships);
    }

    @Test @WithMockUser(roles = "MANAGER")
    void managerCannotInvokeServiceDirectly() {
        assertThrows(AccessDeniedException.class, service::dashboard);
        verifyNoInteractions(users, roles, memberships);
    }

    @Test @WithMockUser(username = "admin-fixture@example.test", roles = "ADMIN")
    void activeDatabaseAdminCanInvokeService() {
        stubActor();
        when(memberships.findRoleNamesByUserId(1L)).thenReturn(List.of("ROLE_ADMIN"));
        when(users.countByStatus()).thenReturn(List.of());
        assertEquals(0, service.dashboard().total());
        verify(users).countByStatus();
    }

    @Test @WithMockUser(username = "admin-fixture@example.test", roles = "ADMIN")
    void staleAdminAuthorityWithoutDatabaseRoleIsRejected() {
        stubActor();
        when(memberships.findRoleNamesByUserId(1L)).thenReturn(List.of("ROLE_USER"));
        assertThrows(AccessDeniedException.class, service::dashboard);
        verify(users, never()).countByStatus();
    }

    private void stubActor() {
        User actor = new User();
        actor.setId(1L);
        actor.setEmail("admin-fixture@example.test");
        actor.setStatus(UserStatus.ACTIVE);
        when(users.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
    }
}
