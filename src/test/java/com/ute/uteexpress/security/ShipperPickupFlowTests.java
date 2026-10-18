package com.ute.uteexpress.security;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.service.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.data.domain.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static com.ute.uteexpress.manager.ManagerFixtures.*;
import static com.ute.uteexpress.shipment.ShipmentFixtures.branch;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual MVC, method security, access guards, lifecycle and Thymeleaf. No JDBC or real database locks. */
@SpringBootTest(classes = SecurityConfigTests.TestApplication.class, properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
    "spring.flyway.enabled=false", "spring.sql.init.mode=never", "debug=false", "logging.level.root=INFO"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class ShipperPickupFlowTests {
    @Autowired MockMvc mvc;
    @MockitoBean UserRepository users;
    @MockitoBean UserRoleRepository roles;
    @MockitoBean RoleRepository catalogRoles;
    @MockitoBean EmailOtpRepository otps;
    @MockitoBean OtpMailSender mail;
    @MockitoBean ManagerBranchRepository members;
    @MockitoBean BranchRepository branches;
    @MockitoBean EmployeeRepository employees;
    @MockitoBean ShipmentRepository shipments;
    @MockitoBean ShipmentAssignmentRepository assignments;
    @MockitoBean ShipmentEventRepository events;
    @MockitoBean ShipmentIntakeRepository intakes;
    @MockitoBean PickupEvidenceRepository evidence;
    @MockitoBean OriginHandoffRepository handoffs;
    Shipment shipment;
    ShipmentAssignment task;
    Employee shipper, receiver;
    final List<PickupEvidence> proofs = new ArrayList<>();
    final List<OriginHandoff> requests = new ArrayList<>();
    final List<ShipmentEvent> timeline = new ArrayList<>();
    ShipmentIntake intake;

    @BeforeEach void setup() {
        proofs.clear(); requests.clear(); timeline.clear(); intake = null;
        shipment = shipment(ShipmentStatus.PICKUP_ASSIGNED, ShipmentMethod.HOME_PICKUP);
        shipment.setTrackingNumber("UTE" + "A".repeat(32));
        shipper = courier(21L); receiver = receiver();
        task = new ShipmentAssignment(); task.setId(41L); task.setShipment(shipment); task.setEmployee(shipper);
        task.setAssignmentType(AssignmentType.PICKUP); task.setStatus(AssignmentStatus.ASSIGNED);
        task.setAssignedBy(manager()); task.setAssignedAt(LocalDateTime.of(2026, 10, 15, 12, 0));
        for (User u : List.of(manager(), shipper.getUser())) {
            when(users.findByEmail(u.getEmail())).thenReturn(Optional.of(u));
            when(roles.findRoleNamesByUserId(u.getId())).thenReturn(List.of(u.getId() == 1L ? "ROLE_MANAGER" : "ROLE_SHIPPER"));
        }
        when(employees.findByUserId(121L)).thenReturn(Optional.of(shipper));
        when(employees.findByUserId(1L)).thenReturn(Optional.of(receiver));
        when(members.existsByManagerIdAndBranchId(1L, 11L)).thenReturn(true);
        when(branches.findByIdAndStatus(11L, BranchStatus.ACTIVE)).thenReturn(Optional.of(branch(11L)));
        when(shipments.findForLifecycle(31L)).thenAnswer(c -> Optional.of(shipment));
        when(shipments.saveAndFlush(any())).thenAnswer(c -> {
            ReflectionTestUtils.setField(shipment, "version", shipment.getVersion() + 1); return shipment;
        });
        when(assignments.findPickupShipmentIdForOwner(41L, 21L)).thenReturn(Optional.of(31L));
        when(assignments.findByIdAndEmployeeIdAndAssignmentType(41L, 21L, AssignmentType.PICKUP)).thenAnswer(c -> Optional.of(task));
        when(assignments.findByEmployeeIdAndAssignmentType(eq(21L), eq(AssignmentType.PICKUP), any()))
            .thenAnswer(c -> new PageImpl<>(List.of(task)));
        when(assignments.findByShipmentIdAndEmployeeIdAndAssignmentTypeAndStatusIn(31L, 21L, AssignmentType.PICKUP, LogisticsAccessService.ACTIVE_ASSIGNMENTS))
            .thenAnswer(c -> LogisticsAccessService.ACTIVE_ASSIGNMENTS.contains(task.getStatus()) ? List.of(task) : List.of());
        when(assignments.findByShipmentIdAndAssignmentTypeAndStatusIn(31L, AssignmentType.PICKUP, LogisticsAccessService.ACTIVE_ASSIGNMENTS))
            .thenAnswer(c -> LogisticsAccessService.ACTIVE_ASSIGNMENTS.contains(task.getStatus()) ? List.of(task) : List.of());
        when(assignments.saveAndFlush(any())).thenAnswer(c -> c.getArgument(0));
        when(evidence.findByAssignmentIdAndResult(41L, PickupResult.SUCCESS))
            .thenAnswer(c -> proofs.stream().filter(p -> p.getResult() == PickupResult.SUCCESS).findFirst());
        when(evidence.findByAssignmentId(eq(41L), any())).thenAnswer(c -> new PageImpl<>(List.copyOf(proofs)));
        when(evidence.saveAndFlush(any())).thenAnswer(c -> {
            PickupEvidence p = c.getArgument(0); p.setId(51L + proofs.size()); proofs.add(p); return p;
        });
        when(handoffs.existsByShipmentIdAndStatus(31L, HandoffStatus.PENDING))
            .thenAnswer(c -> requests.stream().anyMatch(h -> h.getStatus() == HandoffStatus.PENDING));
        when(handoffs.findByAssignmentId(eq(41L), any())).thenAnswer(c -> new PageImpl<>(List.copyOf(requests)));
        when(handoffs.findManaged(eq(1L), eq(HandoffStatus.PENDING), any())).thenAnswer(c -> new PageImpl<>(requests.stream()
            .filter(h -> h.getStatus() == HandoffStatus.PENDING).toList()));
        when(handoffs.saveAndFlush(any())).thenAnswer(c -> {
            OriginHandoff h = c.getArgument(0); if (h.getId() == null) { h.setId(61L + requests.size()); requests.add(h); } return h;
        });
        when(handoffs.findShipmentId(anyLong())).thenAnswer(c -> requests.stream().filter(h -> h.getId().equals(c.getArgument(0)))
            .map(h -> h.getShipment().getId()).findFirst());
        when(handoffs.findById(anyLong())).thenAnswer(c -> requests.stream().filter(h -> h.getId().equals(c.getArgument(0))).findFirst());
        when(intakes.existsByShipmentId(31L)).thenAnswer(c -> intake != null);
        when(intakes.saveAndFlush(any())).thenAnswer(c -> { intake = c.getArgument(0); return intake; });
        when(events.saveAndFlush(any())).thenAnswer(c -> { ShipmentEvent e = c.getArgument(0); timeline.add(e); return e; });
    }
    MockHttpServletRequestBuilder asShipper(MockHttpServletRequestBuilder r) { return r.with(user("courier21@example.test").roles("SHIPPER")); }
    MockHttpServletRequestBuilder asManager(MockHttpServletRequestBuilder r) { return r.with(user("manager-fixture@example.test").roles("MANAGER")); }
    MockHttpServletRequestBuilder collect() {
        return asShipper(post("/shipper/pickups/41/collect")).with(csrf()).param("expectedVersion", shipment.getVersion().toString())
            .param("result", "SUCCESS").param("reference", "PICKUP_PRIVATE_REFERENCE").param("physicallyCollected", "true");
    }
    void pickedUp() throws Exception { mvc.perform(collect()).andExpect(flash().attributeExists("notice")); }
    void requested() throws Exception {
        pickedUp(); mvc.perform(asShipper(post("/shipper/pickups/41/handoffs")).with(csrf())
            .param("expectedVersion", shipment.getVersion().toString()).param("notes", "PRIVATE_HANDOFF_NOTE"))
            .andExpect(flash().attributeExists("notice"));
    }
    MockHttpServletRequestBuilder accept() {
        return asManager(post("/manager/handoffs/61/accept")).with(csrf())
            .param("expectedVersion", shipment.getVersion().toString()).param("receiptReference", "ACTUAL_RECEIPT")
            .param("physicallyReceived", "true");
    }
    @Test void ownDashboardTasksAndDetailRenderWithCsrfAndOnlyNecessaryPickupContact() throws Exception {
        mvc.perform(asShipper(get("/shipper"))).andExpect(status().isOk());
        mvc.perform(asShipper(get("/shipper/pickups"))).andExpect(status().isOk());
        mvc.perform(asShipper(get("/shipper/pickups/41"))).andExpect(status().isOk()).andExpect(r -> {
            String html = r.getResponse().getContentAsString(); assertTrue(html.contains(shipment.getSenderPhone()));
            assertTrue(html.contains("name=\"_csrf\"")); assertTrue(html.contains("/shipper/pickups/41/collect"));
        });
        verify(assignments).findByEmployeeIdAndAssignmentType(eq(21L), eq(AssignmentType.PICKUP), argThat(p -> p.getPageSize() == 20));
    }
    @Test void foreignAssignmentReadAndWriteAreNotFound() throws Exception {
        mvc.perform(asShipper(get("/shipper/pickups/42"))).andExpect(status().isNotFound());
        mvc.perform(asShipper(post("/shipper/pickups/42/collect")).with(csrf()).param("expectedVersion", "0")
            .param("result", "SUCCESS").param("reference", "REF").param("physicallyCollected", "true")).andExpect(status().isNotFound());
        verify(shipments, never()).findForLifecycle(any()); verify(evidence, never()).saveAndFlush(any());
    }
    @ParameterizedTest @ValueSource(strings = {"USER", "MANAGER", "ADMIN"})
    void otherRolesHaveNoShipperAccess(String role) throws Exception {
        mvc.perform(get("/shipper").with(user("other@example.test").roles(role))).andExpect(status().isForbidden());
        mvc.perform(post("/shipper/pickups/41/collect").with(user("other@example.test").roles(role)).with(csrf()))
            .andExpect(status().isForbidden()); verifyNoInteractions(evidence);
    }
    @Test void anonymousHtmlAndApiReceiveExisting401Contract() throws Exception {
        mvc.perform(get("/shipper").accept("text/html")).andExpect(status().isUnauthorized());
        mvc.perform(get("/shipper/pickups").accept("application/json")).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401));
    }
    @Test void lockedUserCannotReadOrCollectUsingOldSession() throws Exception {
        shipper.getUser().setStatus(UserStatus.LOCKED);
        mvc.perform(asShipper(get("/shipper/pickups"))).andExpect(status().isForbidden());
        mvc.perform(collect()).andExpect(status().isForbidden()); assertTrue(proofs.isEmpty());
    }
    @Test void inactiveEmployeeCannotReadOrCollect() throws Exception {
        shipper.setStatus(EmployeeStatus.INACTIVE);
        mvc.perform(asShipper(get("/shipper/pickups/41"))).andExpect(status().isForbidden());
        mvc.perform(collect()).andExpect(status().isForbidden()); assertTrue(proofs.isEmpty());
    }
    @Test void revokedDatabaseRoleOrMissingLinkDeniesOldSession() throws Exception {
        when(roles.findRoleNamesByUserId(121L)).thenReturn(List.of());
        mvc.perform(collect()).andExpect(status().isForbidden());
        when(roles.findRoleNamesByUserId(121L)).thenReturn(List.of("ROLE_SHIPPER"));
        when(employees.findByUserId(121L)).thenReturn(Optional.empty());
        mvc.perform(asShipper(get("/shipper"))).andExpect(status().isForbidden());
    }
    @Test void collectionRecordsActualActorEvidenceEventWithoutOriginReceipt() throws Exception {
        var total = shipment.getTotalFee(); pickedUp();
        assertEquals(ShipmentStatus.PICKED_UP, shipment.getStatus()); assertEquals(AssignmentStatus.IN_PROGRESS, task.getStatus());
        assertNull(task.getCompletedAt()); assertNull(intake); assertNull(shipment.getCurrentBranch()); assertEquals(total, shipment.getTotalFee());
        PickupEvidence proof = proofs.getFirst(); assertEquals(121L, proof.getActor().getId()); assertEquals(21L, proof.getEmployee().getId());
        assertSame(task, proof.getAssignment()); assertEquals(PickupResult.SUCCESS, proof.getResult()); assertNotNull(proof.getRecordedAt());
        assertEquals(0, proof.getRecordedAt().getNano() % 100); assertEquals(ShipmentStatus.PICKED_UP, timeline.getFirst().getToStatus());
        mvc.perform(asShipper(get("/shipper/pickups/41"))).andExpect(status().isOk());
    }
    @Test void duplicateCollectionRejectedWithStaleAndFreshVersions() throws Exception {
        pickedUp(); mvc.perform(collect()).andExpect(flash().attributeExists("error"));
        mvc.perform(collect().param("expectedVersion", "0")).andExpect(flash().attributeExists("error"));
        assertEquals(1, proofs.size()); assertEquals(1, timeline.size());
    }
    @Test void failedAttemptRequiresReasonStaysPendingAndCanRetryWithFullAudit() throws Exception {
        mvc.perform(asShipper(post("/shipper/pickups/41/collect")).with(csrf()).param("expectedVersion", "0").param("result", "FAILED"))
            .andExpect(flash().attributeExists("error")); assertTrue(proofs.isEmpty());
        mvc.perform(asShipper(post("/shipper/pickups/41/collect")).with(csrf()).param("expectedVersion", "0")
            .param("result", "FAILED").param("notes", "Sender unavailable"))
            .andExpect(flash().attributeExists("notice"));
        assertEquals(AssignmentStatus.ASSIGNED, task.getStatus()); assertEquals(ShipmentStatus.PICKUP_ASSIGNED, shipment.getStatus());
        assertEquals(PickupResult.FAILED, proofs.getFirst().getResult()); assertNull(intake); assertNull(shipment.getCurrentBranch());
        pickedUp(); assertEquals(2, proofs.size()); assertEquals(ShipmentStatus.PICKED_UP, shipment.getStatus());
    }
    @Test void staleReassignedCancelledTaskCannotCollectButRemainsReadableHistory() throws Exception {
        task.setStatus(AssignmentStatus.CANCELLED); task.setCompletedAt(LocalDateTime.now());
        mvc.perform(collect()).andExpect(status().isForbidden());
        mvc.perform(asShipper(get("/shipper/pickups/41"))).andExpect(status().isOk()); assertTrue(proofs.isEmpty());
    }
    @Test void invalidStateLegacyOrDropoffCannotBeCollected() throws Exception {
        shipment.setStatus(ShipmentStatus.CONFIRMED); mvc.perform(collect()).andExpect(flash().attributeExists("error"));
        shipment.setStatus(ShipmentStatus.PICKUP_ASSIGNED); shipment.setShipmentMethod(ShipmentMethod.BRANCH_DROPOFF);
        mvc.perform(collect()).andExpect(status().isForbidden()); shipment.setShipmentMethod(null);
        mvc.perform(collect()).andExpect(status().isForbidden()); assertTrue(proofs.isEmpty());
    }
    @Test void collectionNeedsPhysicalAcknowledgmentAndReference() throws Exception {
        mvc.perform(asShipper(post("/shipper/pickups/41/collect")).with(csrf()).param("expectedVersion", "0")
            .param("result", "SUCCESS").param("reference", "REF")).andExpect(flash().attributeExists("error"));
        mvc.perform(asShipper(post("/shipper/pickups/41/collect")).with(csrf()).param("expectedVersion", "0")
            .param("result", "SUCCESS").param("physicallyCollected", "true")).andExpect(flash().attributeExists("error")); assertTrue(proofs.isEmpty());
    }
    @Test void cannotHandoffWithoutSuccessfulEvidenceEvenForLegacyPickedUpState() throws Exception {
        shipment.setStatus(ShipmentStatus.PICKED_UP); task.setStatus(AssignmentStatus.IN_PROGRESS);
        mvc.perform(asShipper(post("/shipper/pickups/41/handoffs")).with(csrf()).param("expectedVersion", "0"))
            .andExpect(flash().attributeExists("error")); assertTrue(requests.isEmpty());
    }
    @Test void handoffRequestDoesNotReceivePackageAndCannotBeDuplicated() throws Exception {
        requested(); assertEquals(ShipmentStatus.PICKED_UP, shipment.getStatus()); assertNull(intake); assertNull(shipment.getCurrentBranch());
        assertEquals(121L, requests.getFirst().getRequestedBy().getId()); assertEquals(11L, requests.getFirst().getBranch().getId());
        mvc.perform(asShipper(post("/shipper/pickups/41/handoffs")).with(csrf()).param("expectedVersion", shipment.getVersion().toString()))
            .andExpect(flash().attributeExists("error")); assertEquals(1, requests.size());
        mvc.perform(asManager(get("/manager/handoffs"))).andExpect(status().isOk());
        mvc.perform(asManager(get("/manager/handoffs/61"))).andExpect(status().isOk()).andExpect(r ->
            assertTrue(r.getResponse().getContentAsString().contains("PICKUP_PRIVATE_REFERENCE")));
    }
    @Test void shipperCannotUseManagerIntakeOrAcceptEndpoints() throws Exception {
        requested(); mvc.perform(asShipper(post("/manager/handoffs/61/accept")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(asShipper(post("/manager/shipments/31/intake")).with(csrf())).andExpect(status().isForbidden()); assertNull(intake);
    }
    @Test void otherBranchManagerCannotReadOrAccept() throws Exception {
        requested(); when(members.existsByManagerIdAndBranchId(1L, 11L)).thenReturn(false);
        mvc.perform(asManager(get("/manager/handoffs/61"))).andExpect(status().isForbidden());
        mvc.perform(accept()).andExpect(status().isForbidden()); assertNull(intake);
    }
    @Test void dualRoleDelivererCannotReceiveHisOwnPackageEvenIfPositionChanged() throws Exception {
        requested(); User actor = shipper.getUser();
        when(roles.findRoleNamesByUserId(121L)).thenReturn(List.of("ROLE_SHIPPER", "ROLE_MANAGER"));
        when(members.existsByManagerIdAndBranchId(121L, 11L)).thenReturn(true);
        shipper.setPosition(EmployeePosition.DISPATCHER);
        mvc.perform(post("/manager/handoffs/61/accept").with(user(actor.getEmail()).roles("SHIPPER", "MANAGER")).with(csrf())
            .param("expectedVersion", shipment.getVersion().toString()).param("receiptReference", "SELF")
            .param("physicallyReceived", "true")).andExpect(status().isForbidden()); assertNull(intake);
    }
    @Test void managerWithoutQualifiedEmployeeCannotReceive() throws Exception {
        requested(); receiver.setStatus(EmployeeStatus.INACTIVE);
        mvc.perform(accept()).andExpect(status().isForbidden()); assertNull(intake);
    }
    @Test void noValidHandoffAndNoDirectHomeIntake() throws Exception {
        pickedUp(); mvc.perform(accept()).andExpect(status().isNotFound());
        mvc.perform(asManager(post("/manager/shipments/31/intake")).with(csrf()).param("expectedVersion", "1")
            .param("receiptReference", "REF").param("physicallyReceived", "true")).andExpect(flash().attributeExists("error")); assertNull(intake);
    }
    @Test void acceptanceNeedsPhysicalReceiptThenClosesTaskAndMovesWithEventExactlyOnce() throws Exception {
        requested(); mvc.perform(asManager(post("/manager/handoffs/61/accept")).with(csrf()).param("expectedVersion", "2")
            .param("receiptReference", "REF")).andExpect(flash().attributeExists("error")); assertNull(intake);
        mvc.perform(accept()).andExpect(flash().attributeExists("notice"));
        assertEquals(ShipmentStatus.AT_ORIGIN_BRANCH, shipment.getStatus()); assertEquals(11L, shipment.getCurrentBranch().getId());
        assertEquals(ShipmentMethod.HOME_PICKUP, intake.getShipmentMethod()); assertSame(task, intake.getPickupAssignment());
        assertEquals(1L, intake.getReceivedBy().getId()); assertEquals(22L, intake.getEmployee().getId());
        assertEquals("ACTUAL_RECEIPT", intake.getReceiptReference()); assertEquals(AssignmentStatus.COMPLETED, task.getStatus());
        assertNotNull(task.getCompletedAt()); assertEquals(HandoffStatus.ACCEPTED, requests.getFirst().getStatus()); assertEquals(3, timeline.size());
        assertEquals(ShipmentStatus.AT_ORIGIN_BRANCH, timeline.getLast().getToStatus());
        mvc.perform(accept()).andExpect(flash().attributeExists("error")); verify(intakes, times(1)).saveAndFlush(any());
        mvc.perform(asManager(get("/manager/handoffs/61"))).andExpect(status().isOk());
    }
    @Test void rejectionRequiresReasonKeepsCustodyAndAllowsNewRequestWithHistory() throws Exception {
        requested(); mvc.perform(asManager(post("/manager/handoffs/61/reject")).with(csrf()).param("expectedVersion", "2"))
            .andExpect(flash().attributeExists("error"));
        mvc.perform(asManager(post("/manager/handoffs/61/reject")).with(csrf()).param("expectedVersion", "2").param("reason", "Seal damaged"))
            .andExpect(flash().attributeExists("notice"));
        assertEquals(ShipmentStatus.PICKED_UP, shipment.getStatus()); assertNull(intake); assertNull(shipment.getCurrentBranch());
        assertEquals(AssignmentStatus.IN_PROGRESS, task.getStatus()); assertEquals(HandoffStatus.REJECTED, requests.getFirst().getStatus());
        assertEquals("Seal damaged", requests.getFirst().getReason());
        mvc.perform(asShipper(post("/shipper/pickups/41/handoffs")).with(csrf()).param("expectedVersion", "3"))
            .andExpect(flash().attributeExists("notice")); assertEquals(2, requests.size());
        mvc.perform(asShipper(get("/shipper/pickups/41"))).andExpect(status().isOk());
    }
    @Test void csrfRequiredForCollectionRequestAcceptanceAndRejection() throws Exception {
        mvc.perform(asShipper(post("/shipper/pickups/41/collect"))).andExpect(status().isForbidden());
        mvc.perform(asShipper(post("/shipper/pickups/41/handoffs"))).andExpect(status().isForbidden());
        mvc.perform(asManager(post("/manager/handoffs/61/accept"))).andExpect(status().isForbidden());
        mvc.perform(asManager(post("/manager/handoffs/61/reject"))).andExpect(status().isForbidden()); assertTrue(proofs.isEmpty());
    }
    @ParameterizedTest @ValueSource(strings = {"actorId", "employeeId", "shipmentId", "branchId", "status"})
    void clientCannotInjectIdentityBranchOrState(String field) throws Exception {
        mvc.perform(collect().param(field, "999")).andExpect(status().isBadRequest()); assertTrue(proofs.isEmpty());
    }
    @Test void uriAssignmentIdAlwaysControlsTargetEvenIfBodyTriesOverride() throws Exception {
        mvc.perform(collect().param("assignmentId", "999")).andExpect(flash().attributeExists("notice"));
        assertEquals(41L, proofs.getFirst().getAssignment().getId()); verify(assignments, never()).findPickupShipmentIdForOwner(eq(999L), any());
    }
    @Test void internalEventsRemainOutsidePublicTrackingWhitelist() throws Exception {
        requested(); mvc.perform(accept()).andExpect(flash().attributeExists("notice"));
        for (ShipmentEvent e : timeline) {
            var projection = mock(ShipmentEventRepository.PublicTrackingEvent.class);
            when(projection.getFromStatus()).thenReturn(e.getFromStatus());
            when(projection.getToStatus()).thenReturn(e.getToStatus());
            when(projection.getTitle()).thenReturn(e.getEventTitle());
            when(projection.getOccurredAt()).thenReturn(e.getOccurredAt());
            assertFalse(PublicTrackingEventPolicy.allowsEvent(projection));
        }
    }
    @Test void publicTrackingPageNeverContainsEvidenceContactsReceiptOrHandoffNotes() throws Exception {
        requested(); mvc.perform(accept()).andExpect(flash().attributeExists("notice"));
        var summary = mock(ShipmentRepository.PublicTrackingSummary.class);
        when(summary.getStatus()).thenReturn(shipment.getStatus()); when(summary.getUpdatedAt()).thenReturn(shipment.getUpdatedAt());
        when(shipments.findPublicTrackingSummary(shipment.getTrackingNumber())).thenReturn(Optional.of(summary));
        when(events.findPublicTrackingEvents(eq(shipment.getTrackingNumber()), any(), anyString(), any())).thenReturn(List.of());
        mvc.perform(get("/tracking").param("trackingNumber", shipment.getTrackingNumber())).andExpect(status().isOk()).andExpect(r -> {
            String html = r.getResponse().getContentAsString();
            for (String secret : List.of("PICKUP_PRIVATE_REFERENCE", "PRIVATE_HANDOFF_NOTE", "ACTUAL_RECEIPT",
                    shipment.getSenderPhone(), shipment.getSenderAddress(), shipper.getFullName())) {
                assertFalse(html.contains(secret), secret);
            }
        });
    }
    @Test void fullLengthFailureReasonIsAuditedAndRenderedAsText() throws Exception {
        String prefix = "<script>private()</script>";
        String notes = prefix + "X".repeat(500 - prefix.length());
        mvc.perform(asShipper(post("/shipper/pickups/41/collect")).with(csrf()).param("expectedVersion", "0")
            .param("result", "FAILED").param("notes", notes)).andExpect(flash().attributeExists("notice"));
        assertEquals(500, proofs.getFirst().getNotes().length()); assertTrue(timeline.getFirst().getDescription().length() <= 500);
        mvc.perform(asShipper(get("/shipper/pickups/41"))).andExpect(status().isOk()).andExpect(r -> {
            String html = r.getResponse().getContentAsString(); assertFalse(html.contains("<script>private()"));
            assertTrue(html.contains("&lt;script&gt;private()"));
        });
    }
    @Test void managerCannotInjectActorOrRetargetHandoffViaBody() throws Exception {
        requested(); mvc.perform(accept().param("actorId", "999")).andExpect(status().isBadRequest()); assertNull(intake);
        mvc.perform(accept().param("handoffId", "999")).andExpect(flash().attributeExists("notice"));
        assertEquals(HandoffStatus.ACCEPTED, requests.getFirst().getStatus());
    }
}
