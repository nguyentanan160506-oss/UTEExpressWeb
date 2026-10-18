package com.ute.uteexpress.security;

import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.service.*;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static com.ute.uteexpress.shipment.ShipmentFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(UserShipmentServiceSecurityTests.Config.class)
class UserShipmentServiceSecurityTests {
    @TestConfiguration @EnableMethodSecurity
    @Import({UserShipmentService.class, TrackingNumberGenerator.class})
    static class Config {
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean LocalValidatorFactoryBean validator() { return new LocalValidatorFactoryBean(); }
    }
    @Autowired private UserShipmentService service;
    @MockitoBean private UserRepository users;
    @MockitoBean private UserRoleRepository roles;
    @MockitoBean private ShipmentRepository shipments;
    @MockitoBean private ShipmentEventRepository events;
    @MockitoBean private BranchRepository branches;
    @MockitoBean private ShippingServiceRepository services;
    @MockitoBean private ShippingRateRuleRepository rates;

    @Test void guestCannotInvokeServiceDirectly() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, service::options);
        verifyNoInteractions(users, roles, shipments, events, branches, services, rates);
    }

    @Test @WithMockUser(roles = "MANAGER")
    void managerCannotInvokeServiceDirectly() {
        assertThrows(AccessDeniedException.class, service::options);
        assertThrows(AccessDeniedException.class, () -> service.detail(31L, 0));
        verifyNoInteractions(users, roles, shipments, events);
    }

    @Test @WithMockUser(roles = "ADMIN")
    void adminWithoutUserRoleCannotInvokeServiceDirectly() {
        assertThrows(AccessDeniedException.class, service::options);
        verifyNoInteractions(users, roles, shipments, events);
    }

    @Test @WithMockUser(username = "shipment-fixture@example.test", roles = {"USER", "ADMIN"})
    void activeMultiRoleUserCanInvokeService() {
        when(users.findByEmail(owner().getEmail())).thenReturn(Optional.of(owner()));
        when(roles.findRoleNamesByUserId(1L)).thenReturn(List.of("ROLE_USER", "ROLE_ADMIN"));
        assertTrue(service.options().branches().isEmpty());
    }

    @Test @WithMockUser(username = "shipment-fixture@example.test", roles = "USER")
    void staleUserRoleIsRejectedUsingCurrentDatabaseMembership() {
        when(users.findByEmail(owner().getEmail())).thenReturn(Optional.of(owner()));
        when(roles.findRoleNamesByUserId(1L)).thenReturn(List.of("ROLE_ADMIN"));
        assertThrows(AccessDeniedException.class, service::options);
        verifyNoInteractions(branches, services);
    }
}
