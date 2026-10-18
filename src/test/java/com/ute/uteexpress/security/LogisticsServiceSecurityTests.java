package com.ute.uteexpress.security;

import com.ute.uteexpress.dto.shipment.ShipmentTransitionCommand;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.service.*;
import java.time.Clock;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(LogisticsServiceSecurityTests.Config.class)
class LogisticsServiceSecurityTests {
    @TestConfiguration @EnableMethodSecurity
    @Import({LogisticsAccessService.class, ShipmentLifecycleService.class, BranchNetworkService.class})
    static class Config { @Bean Clock clock() { return Clock.systemUTC(); } }
    @Autowired LogisticsAccessService access;
    @Autowired ShipmentLifecycleService lifecycle;
    @Autowired BranchNetworkService network;
    @MockitoBean UserRepository users;
    @MockitoBean UserRoleRepository roles;
    @MockitoBean EmployeeRepository employees;
    @MockitoBean ManagerBranchRepository managers;
    @MockitoBean BranchRepository branches;
    @MockitoBean ShipmentAssignmentRepository assignments;
    @MockitoBean ShipmentRepository shipments;
    @MockitoBean ShipmentEventRepository events;
    @MockitoBean BranchServiceAreaRepository areas;
    private ShipmentTransitionCommand command() { return new ShipmentTransitionCommand(31L, 0L, ShipmentStatus.CONFIRMED); }

    @Test void guestCannotCallLogisticsServicesDirectly() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> lifecycle.transition(command()));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> access.requireAssignedShipper(31L, AssignmentType.PICKUP));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> network.configuredHub(11L));
        verifyNoInteractions(users, shipments, employees, events, branches);
    }
    @Test @WithMockUser(roles = "USER")
    void customerCannotCallOperationalServices() {
        assertThrows(AccessDeniedException.class, () -> lifecycle.transition(command()));
        assertThrows(AccessDeniedException.class, () -> access.requireManagerForBranch(11L));
        assertThrows(AccessDeniedException.class, () -> access.requireAssignedShipper(31L, AssignmentType.PICKUP));
        verifyNoInteractions(users, shipments, employees, events);
    }
    @Test @WithMockUser(roles = "SHIPPER")
    void shipperCannotConfirmOrCancelAsManager() {
        assertThrows(AccessDeniedException.class, () -> lifecycle.transition(command()));
        assertThrows(AccessDeniedException.class, () -> access.requireManagerForBranch(11L));
        verifyNoInteractions(users, shipments, managers, events);
    }
    @Test @WithMockUser(roles = "MANAGER")
    void managerCannotImpersonateAssignedShipper() {
        assertThrows(AccessDeniedException.class, () -> access.requireAssignedShipper(31L, AssignmentType.PICKUP));
        verifyNoInteractions(users, employees, assignments);
    }
    @Test @WithMockUser(roles = "ADMIN")
    void adminDoesNotImplicitlyBecomeOperationalManagerOrShipper() {
        assertThrows(AccessDeniedException.class, () -> lifecycle.transition(command()));
        assertThrows(AccessDeniedException.class, () -> access.requireAssignedShipper(31L, AssignmentType.DELIVERY));
        verifyNoInteractions(users, shipments, employees, events);
    }
}
