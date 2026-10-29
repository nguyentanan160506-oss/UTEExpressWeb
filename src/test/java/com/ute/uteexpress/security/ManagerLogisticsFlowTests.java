package com.ute.uteexpress.security;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.service.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static com.ute.uteexpress.manager.ManagerFixtures.*;
import static com.ute.uteexpress.shipment.ShipmentFixtures.branch;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MVC, authorization guards, lifecycle and templates. Persistence boundaries are isolated mocks. */
@SpringBootTest(classes = SecurityConfigTests.TestApplication.class, properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
    "spring.flyway.enabled=false", "spring.sql.init.mode=never", "debug=false", "logging.level.root=INFO"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@WithMockUser(username = "manager-fixture@example.test", roles = "MANAGER")
class ManagerLogisticsFlowTests {
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
    User actor;
    Shipment shipment;
    ShipmentIntake receipt;
    final List<ShipmentAssignment> tasks = new ArrayList<>();
    final List<ShipmentEvent> recorded = new ArrayList<>();

    @BeforeEach void setup() {
        tasks.clear(); recorded.clear(); receipt = null; actor = manager();
        shipment = shipment(ShipmentStatus.CREATED, ShipmentMethod.HOME_PICKUP); shipment.setTrackingNumber("UTE" + "A".repeat(32));
        when(users.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(roles.findRoleNamesByUserId(1L)).thenReturn(List.of("ROLE_MANAGER"));
        when(members.existsByManagerIdAndBranchId(1L, 11L)).thenReturn(true);
        when(members.findManagedBranches(1L, BranchStatus.ACTIVE)).thenReturn(List.of(branch(11L)));
        when(branches.findByIdAndStatus(11L, BranchStatus.ACTIVE)).thenReturn(Optional.of(branch(11L)));
        when(branches.findByIdAndStatus(12L, BranchStatus.ACTIVE)).thenReturn(Optional.of(branch(12L)));
        when(employees.findByUserId(1L)).thenReturn(Optional.of(receiver()));
        for (long id : new long[] {21L, 23L}) {
            Employee courier = courier(id);
            when(employees.findByIdAndBranchId(id, 11L)).thenReturn(Optional.of(courier));
            when(roles.findRoleNamesByUserId(courier.getUser().getId())).thenReturn(List.of("ROLE_SHIPPER"));
        }
        when(employees.findPickupCandidates(eq(11L), any())).thenReturn(new PageImpl<>(List.of(courier(21L), courier(23L))));
        when(shipments.findForLifecycle(31L)).thenAnswer(call -> Optional.of(shipment));
        when(shipments.findOne(org.mockito.ArgumentMatchers.<Specification<Shipment>>any())).thenAnswer(call -> Optional.of(shipment));
        when(shipments.saveAndFlush(any())).thenAnswer(call -> {
            Shipment s = call.getArgument(0); ReflectionTestUtils.setField(s, "version", s.getVersion() + 1); return s;
        });
        when(assignments.findByShipmentIdAndAssignmentTypeAndStatusIn(31L, AssignmentType.PICKUP, LogisticsAccessService.ACTIVE_ASSIGNMENTS))
            .thenAnswer(call -> tasks.stream().filter(t -> LogisticsAccessService.ACTIVE_ASSIGNMENTS.contains(t.getStatus())).toList());
        when(assignments.existsByShipmentIdAndStatusIn(31L, LogisticsAccessService.ACTIVE_ASSIGNMENTS))
            .thenAnswer(call -> tasks.stream().anyMatch(t -> LogisticsAccessService.ACTIVE_ASSIGNMENTS.contains(t.getStatus())));
        when(assignments.saveAndFlush(any())).thenAnswer(call -> {
            ShipmentAssignment task = call.getArgument(0);
            if (task.getId() == null) { task.setId(41L + tasks.size()); tasks.add(task); } return task;
        });
        when(assignments.findByShipmentId(eq(31L), any())).thenAnswer(call -> new PageImpl<>(List.copyOf(tasks)));
        when(events.saveAndFlush(any())).thenAnswer(call -> { ShipmentEvent event = call.getArgument(0); recorded.add(event); return event; });
        when(events.findByShipmentId(eq(31L), any())).thenAnswer(call -> new PageImpl<>(List.copyOf(recorded)));
        when(intakes.existsByShipmentId(31L)).thenAnswer(call -> receipt != null);
        when(intakes.findByShipmentId(31L)).thenAnswer(call -> Optional.ofNullable(receipt));
        when(intakes.saveAndFlush(any())).thenAnswer(call -> { receipt = call.getArgument(0); receipt.setId(51L); return receipt; });
    }
    void confirm() throws Exception {
        mvc.perform(post("/manager/shipments/31/confirm").with(csrf()).param("expectedVersion", "0"))
            .andExpect(redirectedUrl("/manager/shipments/31")).andExpect(flash().attributeExists("notice"));
        assertEquals(ShipmentStatus.CONFIRMED, shipment.getStatus()); assertNull(receipt); assertNull(shipment.getCurrentBranch());
    }
    @Test void homeConfirmAssignReassignAndRenderActualHistoryWithoutPhysicalReceipt() throws Exception {
        var total = shipment.getTotalFee(); confirm();
        mvc.perform(post("/manager/shipments/31/pickup-assignments").with(csrf()).param("expectedVersion", "1").param("employeeId", "21"))
            .andExpect(flash().attributeExists("notice"));
        assertEquals(ShipmentStatus.PICKUP_ASSIGNED, shipment.getStatus()); assertEquals(1, tasks.size());
        mvc.perform(post("/manager/shipments/31/pickup-assignments").with(csrf()).param("expectedVersion", "2")
                .param("employeeId", "23").param("replacesAssignmentId", "41").param("reason", "MANAGER_ONLY_REASON"))
            .andExpect(flash().attributeExists("notice"));
        assertEquals(2, tasks.size()); assertEquals(AssignmentStatus.CANCELLED, tasks.getFirst().getStatus());
        assertEquals(AssignmentStatus.ASSIGNED, tasks.getLast().getStatus()); assertEquals(3, recorded.size());
        mvc.perform(get("/manager/shipments/31")).andExpect(status().isOk()).andExpect(result -> {
            String html = result.getResponse().getContentAsString(); assertTrue(html.contains("MANAGER_ONLY_REASON"));
            assertTrue(html.contains("Đã phân công lại lấy hàng")); assertTrue(html.contains("Đã hủy nhiệm vụ"));
            assertFalse(html.contains("name=\"physicallyReceived\""));
        });
        mvc.perform(post("/manager/shipments/31/intake").with(csrf()).param("expectedVersion", "3")
                .param("physicallyReceived", "true"))
            .andExpect(flash().attributeExists("error"));
        assertNull(receipt); assertNull(shipment.getCurrentBranch()); assertEquals(total, shipment.getTotalFee());
    }
    @Test void dropoffRequiresSeparateAcknowledgmentAndProducesExactlyOneReceipt() throws Exception {
        shipment.setShipmentMethod(ShipmentMethod.BRANCH_DROPOFF); confirm();
        mvc.perform(post("/manager/shipments/31/intake").with(csrf()).param("expectedVersion", "1"))
            .andExpect(flash().attributeExists("error")); assertNull(receipt);
        mvc.perform(post("/manager/shipments/31/intake").with(csrf()).param("expectedVersion", "1")
                .param("physicallyReceived", "true"))
            .andExpect(flash().attributeExists("notice"));
        assertEquals(ShipmentStatus.AT_ORIGIN_BRANCH, shipment.getStatus()); assertEquals(11L, shipment.getCurrentBranch().getId());
        assertEquals(22L, receipt.getEmployee().getId()); assertEquals(1L, receipt.getReceivedBy().getId());
        mvc.perform(post("/manager/shipments/31/intake").with(csrf()).param("expectedVersion", "1")
                .param("physicallyReceived", "true"))
            .andExpect(flash().attributeExists("notice"));
        verify(intakes, times(1)).saveAndFlush(any()); assertEquals(2, recorded.size());
        mvc.perform(get("/manager/shipments/31")).andExpect(status().isOk())
            .andExpect(result -> assertTrue(result.getResponse().getContentAsString().contains(receipt.getReceiptReference())));
    }
    @Test void foreignShipmentBranchAndForeignEmployeeCannotBeUsedThroughRealGuards() throws Exception {
        shipment.setOriginBranch(branch(12L));
        mvc.perform(post("/manager/shipments/31/confirm").with(csrf()).param("expectedVersion", "0"))
            .andExpect(status().isForbidden()); assertTrue(recorded.isEmpty());
        shipment.setOriginBranch(branch(11L)); shipment.setStatus(ShipmentStatus.CONFIRMED);
        mvc.perform(post("/manager/shipments/31/pickup-assignments").with(csrf()).param("expectedVersion", "0").param("employeeId", "999"))
            .andExpect(flash().attributeExists("error")); assertTrue(tasks.isEmpty());
    }
    @Test void managerWithoutLinkedStaffProfileCannotReceiveDropoff() throws Exception {
        shipment.setShipmentMethod(ShipmentMethod.BRANCH_DROPOFF); shipment.setStatus(ShipmentStatus.CONFIRMED);
        when(employees.findByUserId(1L)).thenReturn(Optional.empty());
        mvc.perform(post("/manager/shipments/31/intake").with(csrf()).param("expectedVersion", "0")
                .param("physicallyReceived", "true"))
            .andExpect(status().isForbidden()); assertNull(receipt); assertTrue(recorded.isEmpty());
    }
    @Test void lockedOrRevokedManagerCannotUseOldSession() throws Exception {
        actor.setStatus(UserStatus.LOCKED);
        mvc.perform(post("/manager/shipments/31/confirm").with(csrf()).param("expectedVersion", "0")).andExpect(status().isForbidden());
        actor.setStatus(UserStatus.ACTIVE); when(roles.findRoleNamesByUserId(1L)).thenReturn(List.of("ROLE_USER"));
        mvc.perform(get("/manager")).andExpect(status().isForbidden()); assertTrue(recorded.isEmpty());
    }
    @Test void managerInternalEventsAndReasonsNeverBecomePublicTrackingTimeline() throws Exception {
        confirm();
        mvc.perform(post("/manager/shipments/31/cancel").with(csrf()).param("expectedVersion", "1").param("reason", "MANAGER_ONLY_REASON"))
            .andExpect(flash().attributeExists("notice"));
        ShipmentRepository.PublicTrackingSummary summary = mock(ShipmentRepository.PublicTrackingSummary.class);
        when(summary.getStatus()).thenReturn(shipment.getStatus()); when(summary.getUpdatedAt()).thenReturn(shipment.getUpdatedAt());
        when(shipments.findPublicTrackingSummary(shipment.getTrackingNumber())).thenReturn(Optional.of(summary));
        var internal = recorded.stream().map(e -> {
            var row = mock(ShipmentEventRepository.PublicTrackingEvent.class);
            when(row.getTitle()).thenReturn(e.getEventTitle()); when(row.getFromStatus()).thenReturn(e.getFromStatus());
            when(row.getToStatus()).thenReturn(e.getToStatus()); when(row.getOccurredAt()).thenReturn(e.getOccurredAt()); return row;
        }).toList();
        when(events.findPublicTrackingEvents(any(), any(), any(), any())).thenReturn(internal);
        mvc.perform(get("/tracking").param("trackingNumber", shipment.getTrackingNumber())).andExpect(status().isOk()).andExpect(result -> {
            String html = result.getResponse().getContentAsString(); assertFalse(html.contains("MANAGER_ONLY_REASON"));
            assertFalse(html.contains("Trạng thái:")); assertFalse(html.contains(shipment.getSenderPhone()));
        });
    }
}
