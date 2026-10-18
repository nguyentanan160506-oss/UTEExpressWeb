package com.ute.uteexpress.security;

import com.ute.uteexpress.dto.manager.ManagerViews.*;
import com.ute.uteexpress.dto.shipment.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.service.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static com.ute.uteexpress.manager.ManagerFixtures.*;
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
    "spring.flyway.enabled=false", "spring.sql.init.mode=never", "debug=false", "logging.level.root=INFO"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class ManagerLogisticsControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean UserRepository users;
    @MockitoBean UserRoleRepository userRoles;
    @MockitoBean RoleRepository roles;
    @MockitoBean EmailOtpRepository otps;
    @MockitoBean OtpMailSender mail;
    @MockitoBean ManagerLogisticsService manager;
    @MockitoBean ShipmentLifecycleService lifecycle;
    @Test void guestCannotAccessManagerHtmlOrApiAndCannotMutate() throws Exception {
        for (String path : List.of("/manager", "/manager/shipments", "/manager/shipments/31")) {
            mvc.perform(get(path).accept(MediaType.TEXT_HTML)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        }
        mvc.perform(post("/manager/shipments/31/confirm").with(csrf()).param("expectedVersion", "0").param("id", "999"))
            .andExpect(status().isUnauthorized()); verifyNoInteractions(manager, lifecycle);
    }
    void forbidden() throws Exception {
        for (String path : List.of("/manager", "/manager/shipments", "/manager/shipments/31")) {
            mvc.perform(get(path).accept(MediaType.APPLICATION_JSON)).andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        }
        mvc.perform(post("/manager/shipments/31/intake").with(csrf()).param("expectedVersion", "0").param("physicallyReceived", "true"))
            .andExpect(status().isForbidden()); verifyNoInteractions(manager, lifecycle);
    }
    @Test @WithMockUser(roles = "USER") void userCannotAccessManagerModule() throws Exception { forbidden(); }
    @Test @WithMockUser(roles = "SHIPPER") void shipperCannotUseManagerPrivileges() throws Exception { forbidden(); }
    @Test @WithMockUser(roles = "ADMIN") void adminDoesNotImplicitlyInheritManager() throws Exception { forbidden(); }
    @Test @WithMockUser(roles = "MANAGER")
    void dashboardRendersManagedBranchSelectorAndScopedCounts() throws Exception {
        when(manager.dashboard(11L)).thenReturn(new Dashboard(9, 2, 3, 1, 3));
        when(manager.branches()).thenReturn(List.of(new BranchOption(11L, "Chi nhánh A"), new BranchOption(12L, "Chi nhánh B")));
        mvc.perform(get("/manager").param("branchId", "11")).andExpect(status().isOk()).andExpect(view().name("manager/dashboard"))
            .andExpect(result -> {
                String html = result.getResponse().getContentAsString(); assertTrue(html.contains("Chi nhánh A")); assertTrue(html.contains("Chi nhánh B"));
                assertTrue(html.contains("Tổng quan yêu cầu gửi hàng")); assertTrue(html.contains("branchId=11"));
            });
        verify(manager).dashboard(11L);
    }
    @Test @WithMockUser(roles = "MANAGER")
    void listPreservesAllFiltersAndServerPagination() throws Exception {
        when(manager.branches()).thenReturn(List.of(new BranchOption(11L, "Chi nhánh A")));
        when(manager.list(eq(11L), eq(ShipmentStatus.CONFIRMED), eq(ShipmentMethod.HOME_PICKUP), eq("UTE"), any()))
            .thenAnswer(call -> new PageImpl<>(List.of(detail(ShipmentStatus.CONFIRMED, ShipmentMethod.HOME_PICKUP, false).shipment()), call.getArgument(4), 50));
        mvc.perform(get("/manager/shipments").param("branchId", "11").param("status", "CONFIRMED").param("method", "HOME_PICKUP")
                .param("q", "UTE").param("page", "1").param("size", "10"))
            .andExpect(status().isOk()).andExpect(result -> {
                String html = result.getResponse().getContentAsString(); assertTrue(html.contains("UTE_TEST_TRACKING"));
                for (String filter : List.of("branchId=11", "status=CONFIRMED", "method=HOME_PICKUP", "q=UTE", "size=10")) { assertTrue(html.contains(filter)); }
            });
        verify(manager).list(11L, ShipmentStatus.CONFIRMED, ShipmentMethod.HOME_PICKUP, "UTE", PageRequest.of(1, 10));
    }
    @Test @WithMockUser(roles = "MANAGER")
    void detailRendersRealActionFormsWithCsrfButNoActorBranchOrStatusFields() throws Exception {
        when(manager.detail(31L, 0, 0, 0)).thenReturn(detail(ShipmentStatus.CONFIRMED, ShipmentMethod.HOME_PICKUP, false));
        mvc.perform(get("/manager/shipments/31")).andExpect(status().isOk()).andExpect(result -> {
            String html = result.getResponse().getContentAsString(); assertTrue(html.contains("name=\"_csrf\""));
            assertTrue(html.contains("name=\"expectedVersion\"")); assertTrue(html.contains("name=\"employeeId\""));
            assertTrue(html.contains("/manager/shipments/31/pickup-assignments")); assertFalse(html.contains("/manager/shipments/31/intake"));
            for (String field : List.of("actorId", "branchId", "status", "role", "passwordHash")) { assertFalse(html.contains("name=\"" + field + "\"")); }
        });
    }
    @Test @WithMockUser(roles = "MANAGER")
    void dropoffFormRequiresSeparatePhysicalAcknowledgment() throws Exception {
        when(manager.detail(31L, 0, 0, 0)).thenReturn(detail(ShipmentStatus.CONFIRMED, ShipmentMethod.BRANCH_DROPOFF, true));
        mvc.perform(get("/manager/shipments/31")).andExpect(status().isOk()).andExpect(result -> {
            String html = result.getResponse().getContentAsString(); assertTrue(html.contains("name=\"physicallyReceived\""));
            assertTrue(html.contains("name=\"receiptReference\"")); assertFalse(html.contains("name=\"employeeId\""));
        });
    }
    @Test @WithMockUser(roles = "MANAGER")
    void postsBuildCommandsFromPathAndAllowlistedFieldsOnly() throws Exception {
        mvc.perform(post("/manager/shipments/31/confirm").with(csrf()).param("expectedVersion", "0"))
            .andExpect(redirectedUrl("/manager/shipments/31")).andExpect(flash().attributeExists("notice"));
        verify(lifecycle).transition(new ShipmentTransitionCommand(31L, 0L, ShipmentStatus.CONFIRMED));
        mvc.perform(post("/manager/shipments/31/cancel").with(csrf()).param("expectedVersion", "0").param("reason", "Customer declined"))
            .andExpect(flash().attributeExists("notice"));
        verify(lifecycle).transition(new ShipmentTransitionCommand(31L, 0L, ShipmentStatus.CANCELLED, "Customer declined"));
        mvc.perform(post("/manager/shipments/31/pickup-assignments").with(csrf()).param("expectedVersion", "0")
                .param("employeeId", "21").param("replacesAssignmentId", "41").param("reason", "Reassign"))
            .andExpect(flash().attributeExists("notice"));
        verify(lifecycle).assignPickup(new PickupAssignmentCommand(31L, 0L, 21L, 41L, "Reassign"));
        mvc.perform(post("/manager/shipments/31/intake").with(csrf()).param("expectedVersion", "0")
                .param("receiptReference", "REF").param("physicallyReceived", "true"))
            .andExpect(flash().attributeExists("notice"));
        verify(lifecycle).receiveDropoff(new BranchIntakeCommand(31L, 0L, "REF", true, null));
    }
    @Test @WithMockUser(roles = "MANAGER")
    void allWritesNeedCsrfAndRejectForgedAuthorityFields() throws Exception {
        for (String action : List.of("confirm", "cancel", "pickup-assignments", "intake")) {
            mvc.perform(post("/manager/shipments/31/" + action).param("expectedVersion", "0")).andExpect(status().isForbidden());
            mvc.perform(post("/manager/shipments/31/" + action).with(csrf().useInvalidToken())).andExpect(status().isForbidden());
            mvc.perform(get("/manager/shipments/31/" + action)).andExpect(status().isMethodNotAllowed());
            for (String field : List.of("actorId", "branchId", "shipmentId", "role", "status", "assignmentId")) {
                mvc.perform(post("/manager/shipments/31/" + action).with(csrf()).param("expectedVersion", "0").param(field, "999"))
                    .andExpect(status().isBadRequest());
            }
        }
        verifyNoInteractions(lifecycle);
    }
    @Test @WithMockUser(roles = "MANAGER")
    void invalidFormsAndUnacknowledgedReceiptCannotInvokeLifecycle() throws Exception {
        mvc.perform(post("/manager/shipments/31/confirm").with(csrf())).andExpect(flash().attributeExists("error"));
        mvc.perform(post("/manager/shipments/31/pickup-assignments").with(csrf()).param("expectedVersion", "0").param("employeeId", "-1"))
            .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/manager/shipments/31/intake").with(csrf()).param("expectedVersion", "0").param("receiptReference", "REF"))
            .andExpect(flash().attributeExists("error")); verifyNoInteractions(lifecycle);
    }
    @Test @WithMockUser(roles = "MANAGER")
    void outsideScopeIsDeniedWithoutRenderingPersonalData() throws Exception {
        when(manager.detail(99L, 0, 0, 0)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        mvc.perform(get("/manager/shipments/99")).andExpect(status().isNotFound());
        when(lifecycle.transition(any())).thenThrow(new AccessDeniedException("Forbidden"));
        mvc.perform(post("/manager/shipments/99/confirm").with(csrf()).param("expectedVersion", "0").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }
    @Test @WithMockUser(roles = "MANAGER")
    void ruleConflictAndPersistenceErrorsNeverProduceSuccessOrExposeInternals() throws Exception {
        doThrow(new LogisticsRuleException("Không thể phân công.")).when(lifecycle).assignPickup(any());
        mvc.perform(post("/manager/shipments/31/pickup-assignments").with(csrf()).param("expectedVersion", "0").param("employeeId", "21"))
            .andExpect(flash().attributeExists("error")).andExpect(result -> assertFalse(result.getFlashMap().containsKey("notice")));
        doThrow(new DataIntegrityViolationException("PRIVATE_SQL_MARKER")).when(lifecycle).assignPickup(any());
        mvc.perform(post("/manager/shipments/31/pickup-assignments").with(csrf()).param("expectedVersion", "0").param("employeeId", "21"))
            .andExpect(flash().attributeExists("error")).andExpect(result -> assertFalse(result.getFlashMap().toString().contains("PRIVATE_SQL_MARKER")));
    }
    @Test @WithMockUser(roles = "MANAGER")
    void invalidFiltersAndIdsAre400BeforeQuerying() throws Exception {
        for (String[] pair : new String[][] {{"page", "-1"}, {"size", "101"}, {"branchId", "-1"}, {"status", "UNKNOWN"}, {"method", "VENDOR"}, {"q", "a".repeat(51)}}) {
            mvc.perform(get("/manager/shipments").param(pair[0], pair[1])).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/manager/shipments/-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/manager/shipments/31").param("assignmentPage", "-1")).andExpect(status().isBadRequest());
        verifyNoInteractions(manager);
    }
}
