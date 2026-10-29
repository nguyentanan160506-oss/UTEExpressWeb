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
    @Import({LogisticsAccessService.class, ShipmentLifecycleService.class, BranchNetworkService.class, ManagerLogisticsService.class,
        TransportTripService.class, TransportTripReadService.class, DeliveryCustodyService.class, LastMileService.class, DeliveryReadService.class})
    static class Config { @Bean Clock clock() { return Clock.systemUTC(); } }
    @Autowired LogisticsAccessService access;
    @MockitoBean jakarta.persistence.EntityManager entityManager;
    @Autowired ShipmentLifecycleService lifecycle;
    @Autowired BranchNetworkService network;
    @Autowired ManagerLogisticsService manager;
    @MockitoBean DeliveryDispatchRepository deliveryDispatch;
    @MockitoBean DeliveryProofRepository deliveryProof;
    @MockitoBean DeliveryRecoveryRepository deliveryRecovery;
    @MockitoBean RedeliveryDecisionRepository redeliveryDecision;
    @MockitoBean ReturnRequestRepository returnRequest;
    @MockitoBean ReturnReceiptRepository returnReceipt;
    @MockitoBean DeliveryAttemptRepository deliveryAttempt;
    @Autowired TransportTripService transport;
    @Autowired LastMileService delivery;
    @Autowired DeliveryReadService deliveryRead;
    @Autowired TransportTripReadService tripRead;
    @MockitoBean TransportRouteRepository transportRoutes;
    @MockitoBean TransportTripRepository transportTrips;
    @MockitoBean TransportTripItemRepository transportItems;
    @MockitoBean TripReceiptRepository tripReceipts;
    @MockitoBean TripDiscrepancyRepository tripDiscrepancies;
    @MockitoBean TransportTripEventRepository tripEvents;
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
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> deliveryRead.managerList(null, null, 0));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> deliveryRead.shipperList(0));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> delivery.assign(new com.ute.uteexpress.dto.delivery.DeliveryCommands.Assign(31L, 0L, 21L, null, null)));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> delivery.accept(new com.ute.uteexpress.dto.delivery.DeliveryCommands.Action(41L, 0L, true, "REF")));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> tripRead.list(0));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> transport.create(new com.ute.uteexpress.dto.transport.TripCommands.Create(1L, "a".repeat(32))));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> lifecycle.transition(command()));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> access.requireAssignedShipper(31L, AssignmentType.PICKUP));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> network.configuredHub(11L));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> manager.dashboard(null));
        verifyNoInteractions(users, shipments, employees, events, branches);
    }
    @Test @WithMockUser(roles = "USER")
    void customerCannotCallOperationalServices() {
        assertNoDeliveryAuthority();
        assertNoTransportAuthority();
        assertThrows(AccessDeniedException.class, () -> lifecycle.transition(command()));
        assertThrows(AccessDeniedException.class, () -> access.requireManagerForBranch(11L));
        assertThrows(AccessDeniedException.class, () -> access.requireAssignedShipper(31L, AssignmentType.PICKUP));
        assertThrows(AccessDeniedException.class, manager::branches);
        assertThrows(AccessDeniedException.class, () -> lifecycle.assignPickup(new com.ute.uteexpress.dto.shipment.PickupAssignmentCommand(31L, 0L, 21L, null, null)));
        assertThrows(AccessDeniedException.class, () -> lifecycle.receiveDropoff(new com.ute.uteexpress.dto.shipment.BranchIntakeCommand(31L, 0L, true, null)));
        verifyNoInteractions(users, shipments, employees, events);
    }
    @Test @WithMockUser(roles = "SHIPPER")
    void shipperCannotConfirmOrCancelAsManager() {
        assertNoDeliveryManagerAuthority();
        assertNoTransportAuthority();
        assertThrows(AccessDeniedException.class, () -> lifecycle.reviewOriginHandoff(
            new com.ute.uteexpress.dto.shipment.HandoffReviewCommand(61L, 0L, true, true, null)));
        assertThrows(AccessDeniedException.class, () -> lifecycle.transition(command()));
        assertThrows(AccessDeniedException.class, () -> access.requireManagerForBranch(11L));
        assertThrows(AccessDeniedException.class, () -> manager.detail(31L, 0, 0, 0));
        assertThrows(AccessDeniedException.class, () -> lifecycle.assignPickup(new com.ute.uteexpress.dto.shipment.PickupAssignmentCommand(31L, 0L, 21L, null, null)));
        assertThrows(AccessDeniedException.class, () -> lifecycle.receiveDropoff(new com.ute.uteexpress.dto.shipment.BranchIntakeCommand(31L, 0L, true, null)));
        verifyNoInteractions(users, shipments, managers, events);
    }
    @Test @WithMockUser(roles = "MANAGER")
    void managerCannotImpersonateAssignedShipper() {
        assertNoDeliveryShipperAuthority();
        assertThrows(AccessDeniedException.class, () -> lifecycle.collectPickup(
            new com.ute.uteexpress.dto.shipment.PickupCollectionCommand(41L, 0L, PickupResult.SUCCESS, true, "REF", null)));
        assertThrows(AccessDeniedException.class, () -> lifecycle.requestOriginHandoff(
            new com.ute.uteexpress.dto.shipment.OriginHandoffCommand(41L, 0L, null)));
        assertThrows(AccessDeniedException.class, () -> access.requireAssignedShipper(31L, AssignmentType.PICKUP));
        verifyNoInteractions(users, employees, assignments);
    }
    @Test @WithMockUser(roles = "ADMIN")
    void adminDoesNotImplicitlyBecomeOperationalManagerOrShipper() {
        assertNoDeliveryAuthority();
        assertNoTransportAuthority();
        assertThrows(AccessDeniedException.class, () -> lifecycle.collectPickup(
            new com.ute.uteexpress.dto.shipment.PickupCollectionCommand(41L, 0L, PickupResult.SUCCESS, true, "REF", null)));
        assertThrows(AccessDeniedException.class, () -> lifecycle.reviewOriginHandoff(
            new com.ute.uteexpress.dto.shipment.HandoffReviewCommand(61L, 0L, true, true, null)));
        assertThrows(AccessDeniedException.class, () -> lifecycle.transition(command()));
        assertThrows(AccessDeniedException.class, () -> access.requireAssignedShipper(31L, AssignmentType.DELIVERY));
        verifyNoInteractions(users, shipments, employees, events);
    }
    private void assertNoTransportAuthority() {
        assertThrows(AccessDeniedException.class, () -> tripRead.detail(1L, 0, 0, 0, 0));
        assertThrows(AccessDeniedException.class, () -> transport.create(new com.ute.uteexpress.dto.transport.TripCommands.Create(1L, "a".repeat(32))));
        assertThrows(AccessDeniedException.class, () -> transport.addItems(new com.ute.uteexpress.dto.transport.TripCommands.Manifest(1L, 0L, java.util.List.of(31L))));
        assertThrows(AccessDeniedException.class, () -> transport.receive(new com.ute.uteexpress.dto.transport.TripCommands.Receive(1L, 0L, 101L, true, "REF", null, false)));
        assertThrows(AccessDeniedException.class, () -> transport.close(new com.ute.uteexpress.dto.transport.TripCommands.Change(1L, 0L, true, "REF", null)));
        verifyNoInteractions(transportRoutes, transportTrips, transportItems, tripReceipts, tripDiscrepancies, tripEvents);
    }
    private void assertNoDeliveryAuthority() { assertNoDeliveryManagerAuthority(); assertNoDeliveryShipperAuthority(); }
    private void assertNoDeliveryManagerAuthority() {
        assertThrows(AccessDeniedException.class, () -> deliveryRead.managerDetail(31L, 0));
        assertThrows(AccessDeniedException.class, () -> delivery.assign(new com.ute.uteexpress.dto.delivery.DeliveryCommands.Assign(31L, 0L, 21L, null, null)));
        assertThrows(AccessDeniedException.class, () -> delivery.prepare(new com.ute.uteexpress.dto.delivery.DeliveryCommands.Prepare(31L, 0L, 41L, true, "REF", null)));
        assertThrows(AccessDeniedException.class, () -> delivery.redelivery(new com.ute.uteexpress.dto.delivery.DeliveryCommands.Redelivery(31L, 0L, null, "Retry")));
        assertThrows(AccessDeniedException.class, () -> delivery.reviewRecovery(new com.ute.uteexpress.dto.delivery.DeliveryCommands.ReviewRecovery(31L, 0L, 81L, true, true, "REF", null)));
        assertThrows(AccessDeniedException.class, () -> delivery.initiateReturn(new com.ute.uteexpress.dto.delivery.DeliveryCommands.ReturnStart(31L, 0L, "Return")));
        assertThrows(AccessDeniedException.class, () -> delivery.returnToSender(new com.ute.uteexpress.dto.delivery.DeliveryCommands.SenderReceipt(31L, 0L, true, ReturnRecipientType.SENDER, "Sender", "REF", null)));
        verifyNoInteractions(deliveryDispatch, deliveryProof, deliveryAttempt, deliveryRecovery, redeliveryDecision, returnRequest, returnReceipt);
    }
    private void assertNoDeliveryShipperAuthority() {
        assertThrows(AccessDeniedException.class, () -> deliveryRead.shipperDetail(41L, 0));
        assertThrows(AccessDeniedException.class, () -> delivery.accept(new com.ute.uteexpress.dto.delivery.DeliveryCommands.Action(41L, 0L, true, "REF")));
        assertThrows(AccessDeniedException.class, () -> delivery.start(new com.ute.uteexpress.dto.delivery.DeliveryCommands.Action(41L, 0L, true, "REF")));
        assertThrows(AccessDeniedException.class, () -> delivery.complete(new com.ute.uteexpress.dto.delivery.DeliveryCommands.Complete(41L, 0L, true, DeliveryRecipientType.RECIPIENT, "Recipient", "REF", null)));
        assertThrows(AccessDeniedException.class, () -> delivery.fail(new com.ute.uteexpress.dto.delivery.DeliveryCommands.Fail(41L, 0L, true, DeliveryFailureCode.ABSENT, "Absent", null)));
        assertThrows(AccessDeniedException.class, () -> delivery.requestRecovery(new com.ute.uteexpress.dto.delivery.DeliveryCommands.Recover(41L, 0L, null)));
        verifyNoInteractions(deliveryDispatch, deliveryProof, deliveryAttempt, deliveryRecovery, redeliveryDecision, returnRequest, returnReceipt);
    }
}
