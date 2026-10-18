package com.ute.uteexpress.security;

import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.service.*;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.convert.ConversionService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.server.ResponseStatusException;

import static com.ute.uteexpress.shipment.ShipmentFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(QuoteServiceSecurityTests.Config.class)
class QuoteServiceSecurityTests {
    private static final String TOKEN = "a".repeat(32);
    @TestConfiguration @EnableMethodSecurity
    @Import({QuoteService.class, TrackingNumberGenerator.class})
    static class Config {
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean LocalValidatorFactoryBean validator() { return new LocalValidatorFactoryBean(); }
        @Bean static ConversionService conversionService() { return ApplicationConversionService.getSharedInstance(); }
    }
    @Autowired QuoteService service;
    @MockitoBean UserRepository users;
    @MockitoBean UserRoleRepository roles;
    @MockitoBean ShipmentQuoteRepository quotes;
    @MockitoBean ShipmentRepository shipments;
    @MockitoBean ShipmentEventRepository events;
    @MockitoBean BranchRepository branches;
    @MockitoBean ShippingServiceRepository services;
    @MockitoBean ShippingRateRuleRepository rates;
    @MockitoBean PickupFeeRuleRepository pickups;
    @MockitoBean BranchNetworkService network;

    @Test void guestCannotCallAnyQuoteOperation() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.quote(form()));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.confirm(TOKEN));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.review(TOKEN));
        verifyNoInteractions(users, quotes, shipments, rates);
    }
    private void denied() {
        assertThrows(AccessDeniedException.class, () -> service.quote(form()));
        assertThrows(AccessDeniedException.class, () -> service.confirm(TOKEN));
        assertThrows(AccessDeniedException.class, () -> service.branchChoices(form()));
        verifyNoInteractions(users, quotes, shipments, rates);
    }
    @Test @WithMockUser(roles = "ADMIN") void adminHasNoImplicitCustomerPermission() { denied(); }
    @Test @WithMockUser(roles = "MANAGER") void managerHasNoImplicitCustomerPermission() { denied(); }
    @Test @WithMockUser(roles = "SHIPPER") void shipperHasNoImplicitCustomerPermission() { denied(); }
    @Test @WithMockUser(username = "shipment-fixture@example.test", roles = {"USER", "ADMIN"})
    void explicitUserRoleUsesCurrentDatabaseOwnerForLookup() {
        when(users.findByEmail(owner().getEmail())).thenReturn(Optional.of(owner()));
        when(roles.findRoleNamesByUserId(1L)).thenReturn(List.of("ROLE_USER", "ROLE_ADMIN"));
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.confirm(TOKEN)).getStatusCode().value());
        verify(quotes).findOwnedForUpdate(TOKEN, 1L);
    }
    @Test @WithMockUser(username = "shipment-fixture@example.test", roles = "USER")
    void revokedDatabaseRoleOverridesOldAuthenticatedSession() {
        when(users.findByEmail(owner().getEmail())).thenReturn(Optional.of(owner()));
        when(roles.findRoleNamesByUserId(1L)).thenReturn(List.of("ROLE_ADMIN"));
        assertThrows(AccessDeniedException.class, () -> service.confirm(TOKEN)); verifyNoInteractions(quotes, shipments);
    }
}
