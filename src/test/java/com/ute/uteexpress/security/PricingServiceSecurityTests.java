package com.ute.uteexpress.security;

import com.ute.uteexpress.dto.pricing.PricingCommands;
import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.service.AdminPricingService;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(PricingServiceSecurityTests.Config.class)
class PricingServiceSecurityTests {
    @TestConfiguration @EnableMethodSecurity @Import(AdminPricingService.class)
    static class Config { @Bean Clock clock() { return Clock.systemUTC(); } }
    @Autowired AdminPricingService service;
    @MockitoBean ShippingServiceRepository services;
    @MockitoBean PricingVersionRepository versions;
    @MockitoBean ShippingRateRuleRepository rates;
    @MockitoBean PickupFeeRuleRepository pickups;
    @MockitoBean BranchRepository branches;
    @MockitoBean UserRepository users;
    @MockitoBean UserRoleRepository roles;
    @MockitoBean AuditLogRepository audit;
    @MockitoBean jakarta.persistence.EntityManager entityManager;
    @Test void guestCannotInvokePricingAdminServices() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.publish(1L, 0L, true));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.catalog(null, 0));
        verifyNoInteractions(users, services, versions, rates, pickups, audit);
    }
    void denied() {
        assertThrows(AccessDeniedException.class, () -> service.catalog(null, 0));
        assertThrows(AccessDeniedException.class, () -> service.detail(1L, 0));
        assertThrows(AccessDeniedException.class, () -> service.createService(new PricingCommands.Catalog("NEW", "Name", null, 24, null, null, null, null)));
        assertThrows(AccessDeniedException.class, () -> service.serviceStatus(21L, 0L, false));
        assertThrows(AccessDeniedException.class, () -> service.createDraft(new PricingCommands.Draft(21L, "NEW", LocalDateTime.now().plusDays(1), null)));
        assertThrows(AccessDeniedException.class, () -> service.publish(1L, 0L, true));
        assertThrows(AccessDeniedException.class, () -> service.retireVersion(1L, 0L, LocalDateTime.now().plusDays(2), true));
        assertThrows(AccessDeniedException.class, () -> service.retireLegacy(41L, false, LocalDateTime.now().plusDays(2), true));
        verifyNoInteractions(users, services, versions, rates, pickups, audit, entityManager);
    }
    @Test @WithMockUser(roles = "USER") void userDenied() { denied(); }
    @Test @WithMockUser(roles = "MANAGER") void managerDenied() { denied(); }
    @Test @WithMockUser(roles = "SHIPPER") void shipperDenied() { denied(); }
    @Test @WithMockUser(username = "revoked@example.test", roles = "ADMIN") void cachedAdminRoleNeedsExistingDatabaseActor() {
        assertThrows(AccessDeniedException.class, service::authorize); verify(users).findByEmail("revoked@example.test"); verifyNoInteractions(services, rates, pickups, audit);
    }
}
