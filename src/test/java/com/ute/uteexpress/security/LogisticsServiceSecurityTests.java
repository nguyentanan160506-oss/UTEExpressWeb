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
    @Import({LogisticsAccessService.class, ShipmentLifecycleService.class, BranchNetworkService.class, ManagerLogisticsService.class})
    static class Config { @Bean Clock clock() { return Clock.systemUTC(); } }
    @Autowired LogisticsAccessService access;
    @Autowired ShipmentLifecycleService lifecycle;
    @Autowired BranchNetworkService network;
    @Autowired ManagerLogisticsService manager;
    @MockitoBean UserRepository users;
    @MockitoBean UserRoleRepository roles;
    @MockitoBean EmployeeRepository employees;
    @MockitoBean ManagerBranchRepository managers;
    @MockitoBean BranchRepository branches;
    @MockitoBean ShipmentAssignmentRepository assignments;
    @MockitoBean ShipmentRepository shipments;
    @MockitoBean ShipmentEventRepository events;
    @MockitoBean ShipmentIntakeRepository intakes;
    @MockitoBean PickupEvidenceRepository evidence;
    @MockitoBean OriginHandoffRepository handoffs;
    @MockitoBean BranchServiceAreaRepository areas;
    private ShipmentTransitionCommand command() { return new ShipmentTransitionCommand(31L, 0L, ShipmentStatus.CONFIRMED); }

    @Test void guestCannotCallLogisticsServicesDirectly() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> lifecycle.transition(command()));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> access.requireAssignedShipper(31L, AssignmentType.PICKUP));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> network.configuredHub(11L));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> manager.dashboard(null));
        verifyNoInteractions(users, shipments, employees, events, branches);
    }
    @Test @WithMockUser(roles = "USER")
    void customerCannotCallOperationalServices() {
        assertThrows(AccessDeniedException.class, () -> lifecycle.transition(command()));
        assertThrows(AccessDeniedException.class, () -> access.requireManagerForBranch(11L));
        assertThrows(AccessDeniedException.class, () -> access.requireAssignedShipper(31L, AssignmentType.PICKUP));
        assertThrows(AccessDeniedException.class, manager::branches);
        assertThrows(AccessDeniedException.class, () -> lifecycle.assignPickup(new com.ute.uteexpress.dto.shipment.PickupAssignmentCommand(31L, 0L, 21L, null, null)));
        assertThrows(AccessDeniedException.class, () -> lifecycle.receiveDropoff(new com.ute.uteexpress.dto.shipment.BranchIntakeCommand(31L, 0L, "REF", true, null)));
        verifyNoInteractions(users, shipments, employees, events);
    }
    @Test @WithMockUser(roles = "SHIPPER")
    void shipperCannotConfirmOrCancelAsManager() {
        assertThrows(AccessDeniedException.class, () -> lifecycle.reviewOriginHandoff(
            new com.ute.uteexpress.dto.shipment.HandoffReviewCommand(61L, 0L, true, true, "REF", null)));
        assertThrows(AccessDeniedException.class, () -> lifecycle.transition(command()));
        assertThrows(AccessDeniedException.class, () -> access.requireManagerForBranch(11L));
        assertThrows(AccessDeniedException.class, () -> manager.detail(31L, 0, 0, 0));
        assertThrows(AccessDeniedException.class, () -> lifecycle.assignPickup(new com.ute.uteexpress.dto.shipment.PickupAssignmentCommand(31L, 0L, 21L, null, null)));
        assertThrows(AccessDeniedException.class, () -> lifecycle.receiveDropoff(new com.ute.uteexpress.dto.shipment.BranchIntakeCommand(31L, 0L, "REF", true, null)));
        verifyNoInteractions(users, shipments, managers, events);
    }
    @Test @WithMockUser(roles = "MANAGER")
    void managerCannotImpersonateAssignedShipper() {
        assertThrows(AccessDeniedException.class, () -> lifecycle.collectPickup(
            new com.ute.uteexpress.dto.shipment.PickupCollectionCommand(41L, 0L, PickupResult.SUCCESS, true, "REF", null)));
        assertThrows(AccessDeniedException.class, () -> lifecycle.requestOriginHandoff(
            new com.ute.uteexpress.dto.shipment.OriginHandoffCommand(41L, 0L, null)));
        assertThrows(AccessDeniedException.class, () -> access.requireAssignedShipper(31L, AssignmentType.PICKUP));
        verifyNoInteractions(users, employees, assignments);
    }
    @Test @WithMockUser(roles = "ADMIN")
    void adminDoesNotImplicitlyBecomeOperationalManagerOrShipper() {
        assertThrows(AccessDeniedException.class, () -> lifecycle.collectPickup(
            new com.ute.uteexpress.dto.shipment.PickupCollectionCommand(41L, 0L, PickupResult.SUCCESS, true, "REF", null)));
        assertThrows(AccessDeniedException.class, () -> lifecycle.reviewOriginHandoff(
            new com.ute.uteexpress.dto.shipment.HandoffReviewCommand(61L, 0L, true, true, "REF", null)));
        assertThrows(AccessDeniedException.class, () -> lifecycle.transition(command()));
        assertThrows(AccessDeniedException.class, () -> access.requireAssignedShipper(31L, AssignmentType.DELIVERY));
        verifyNoInteractions(users, shipments, employees, events);
    }
}
