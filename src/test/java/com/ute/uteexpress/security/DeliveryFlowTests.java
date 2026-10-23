package com.ute.uteexpress.security;

import com.ute.uteexpress.delivery.DeliveryScenario;
import com.ute.uteexpress.dto.delivery.DeliveryCommands.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.service.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MVC/filter chain/templates, delegates to actual services with synthetic repositories. Not JDBC. */
@SpringBootTest(classes = SecurityConfigTests.TestApplication.class, properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
    "spring.flyway.enabled=false", "spring.sql.init.mode=never", "debug=false", "logging.level.root=INFO"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class DeliveryFlowTests {
    @Autowired MockMvc mvc;
    @MockitoBean LastMileService delivery;
    @MockitoBean DeliveryReadService read;
    @MockitoBean OtpMailSender mail;
    @MockitoBean UserRepository users;
    @MockitoBean UserRoleRepository roles;
    @MockitoBean RoleRepository roleCatalog;
    @MockitoBean EmailOtpRepository otps;
    DeliveryScenario f;
    @BeforeEach void setup() {
        f = new DeliveryScenario(); SecurityContextHolder.clearContext();
        doAnswer(c -> { f.service.assign(c.getArgument(0)); return null; }).when(delivery).assign(any());
        doAnswer(c -> { f.service.prepare(c.getArgument(0)); return null; }).when(delivery).prepare(any());
        doAnswer(c -> { f.service.accept(c.getArgument(0)); return null; }).when(delivery).accept(any());
        doAnswer(c -> { f.service.start(c.getArgument(0)); return null; }).when(delivery).start(any());
        doAnswer(c -> { f.service.complete(c.getArgument(0)); return null; }).when(delivery).complete(any());
        doAnswer(c -> { f.service.fail(c.getArgument(0)); return null; }).when(delivery).fail(any());
        doAnswer(c -> { f.service.redelivery(c.getArgument(0)); return null; }).when(delivery).redelivery(any());
        doAnswer(c -> { f.service.requestRecovery(c.getArgument(0)); return null; }).when(delivery).requestRecovery(any());
        doAnswer(c -> { f.service.reviewRecovery(c.getArgument(0)); return null; }).when(delivery).reviewRecovery(any());
        doAnswer(c -> { f.service.initiateReturn(c.getArgument(0)); return null; }).when(delivery).initiateReturn(any());
        doAnswer(c -> { f.service.returnToSender(c.getArgument(0)); return null; }).when(delivery).returnToSender(any());
        when(read.managerList(any(), any(), anyInt())).thenAnswer(c -> f.read.managerList(c.getArgument(0), c.getArgument(1), c.getArgument(2)));
        when(read.managerDetail(anyLong(), anyInt())).thenAnswer(c -> f.read.managerDetail(c.getArgument(0), c.getArgument(1)));
        when(read.shipperList(anyInt())).thenAnswer(c -> f.read.shipperList(c.getArgument(0)));
        when(read.shipperDetail(anyLong(), anyInt())).thenAnswer(c -> f.read.shipperDetail(c.getArgument(0), c.getArgument(1)));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    MockHttpServletRequestBuilder manager(MockHttpServletRequestBuilder r) { return r.with(user(f.manager.getEmail()).roles("MANAGER")); }
    MockHttpServletRequestBuilder courier(MockHttpServletRequestBuilder r) { return r.with(user(f.courier.getUser().getEmail()).roles("SHIPPER")); }
    MockHttpServletRequestBuilder postManager(String suffix) { return manager(post("/manager/deliveries/31/" + suffix)).with(csrf()).param("expectedVersion", f.s.getVersion().toString()); }
    MockHttpServletRequestBuilder postCourier(String suffix) {
        var request = courier(post("/shipper/deliveries/41/" + suffix)).with(csrf()).param("expectedVersion", f.s.getVersion().toString());
        if (!suffix.equals("return-to-branch")) { request.param("confirmed", "true"); }
        if (!suffix.equals("return-to-branch") && !suffix.equals("fail")) { request.param("reference", "ACTUAL-REFERENCE"); }
        return request;
    }
    void assign() throws Exception { mvc.perform(postManager("assign").param("employeeId", "21")).andExpect(flash().attributeExists("notice")); }
    void prepare() throws Exception { assign(); mvc.perform(postManager("handoff").param("assignmentId", "41").param("confirmed", "true").param("reference", "OUTBOUND"))
        .andExpect(flash().attributeExists("notice")); }
    void start() throws Exception { prepare(); mvc.perform(postCourier("accept")).andExpect(flash().attributeExists("notice")); mvc.perform(postCourier("start")).andExpect(flash().attributeExists("notice")); }
    void failAttempt() throws Exception { mvc.perform(postCourier("fail").param("code", "ABSENT").param("reason", "Recipient absent"))
        .andExpect(flash().attributeExists("notice")); }

    @Test void managerPagesRenderAndIncludeCsrf() throws Exception {
        mvc.perform(manager(get("/manager/deliveries"))).andExpect(status().isOk());
        mvc.perform(manager(get("/manager/deliveries/31"))).andExpect(status().isOk()).andExpect(r -> {
            String html = r.getResponse().getContentAsString(); assertTrue(html.contains(f.s.getTrackingNumber())); assertTrue(html.contains("name=\"_csrf\""));
        });
        assign(); mvc.perform(manager(get("/manager/deliveries/31"))).andExpect(status().isOk());
    }
    @Test void shipperPagesAndStructuredSuccessFormWork() throws Exception {
        start(); mvc.perform(courier(get("/shipper/deliveries"))).andExpect(status().isOk());
        mvc.perform(courier(get("/shipper/deliveries/41"))).andExpect(status().isOk()).andExpect(r -> {
            String html = r.getResponse().getContentAsString(); assertTrue(html.contains(f.s.getReceiverPhone())); assertTrue(html.contains("name=\"_csrf\""));
        });
        mvc.perform(postCourier("complete").param("recipientType", "RECIPIENT").param("recipientName", "Actual recipient")).andExpect(flash().attributeExists("notice"));
        assertEquals(ShipmentStatus.DELIVERED, f.s.getStatus()); assertEquals(1, f.proofRows.size());
        mvc.perform(manager(get("/manager/deliveries/31"))).andExpect(status().isOk());
        mvc.perform(courier(get("/shipper/deliveries/41"))).andExpect(status().isOk()).andExpect(r -> assertFalse(r.getResponse().getContentAsString().contains(f.s.getReceiverPhone())));
    }
    @Test void retryAndRecoveryPagesRenderWithExplicitReceipts() throws Exception {
        start(); failAttempt(); mvc.perform(manager(get("/manager/deliveries/31"))).andExpect(status().isOk());
        mvc.perform(postManager("redelivery").param("reason", "Retry approved")).andExpect(flash().attributeExists("notice"));
        mvc.perform(postCourier("start")).andExpect(flash().attributeExists("notice")); failAttempt();
        mvc.perform(postCourier("return-to-branch")).andExpect(flash().attributeExists("notice")); assertNull(f.s.getCurrentBranch());
        mvc.perform(manager(get("/manager/deliveries/31"))).andExpect(status().isOk());
        mvc.perform(courier(get("/shipper/deliveries/41"))).andExpect(status().isOk());
        mvc.perform(postManager("recoveries/reject").param("recoveryId", "81").param("reason", "Parcel not presented"))
            .andExpect(flash().attributeExists("notice")); assertNull(f.s.getCurrentBranch());
        mvc.perform(postCourier("return-to-branch")).andExpect(flash().attributeExists("notice"));
        mvc.perform(postManager("recoveries/accept").param("recoveryId", "82").param("confirmed", "true").param("reference", "RECEIPT"))
            .andExpect(flash().attributeExists("notice")); assertSame(f.destination, f.s.getCurrentBranch());
        mvc.perform(manager(get("/manager/deliveries/31"))).andExpect(status().isOk());
    }
    @Test void returnAndFinalSenderReceiptUseActualForms() throws Exception {
        f.s.setOriginBranch(f.destination); mvc.perform(postManager("return").param("reason", "Return approved"))
            .andExpect(flash().attributeExists("notice")); assertEquals(ShipmentStatus.RETURNING, f.s.getStatus());
        mvc.perform(manager(get("/manager/deliveries/31"))).andExpect(status().isOk());
        mvc.perform(postManager("return-to-sender").param("confirmed", "true").param("recipientType", "SENDER")
            .param("recipientName", "Actual sender").param("reference", "FINAL-RECEIPT")).andExpect(flash().attributeExists("notice"));
        assertEquals(ShipmentStatus.RETURNED, f.s.getStatus()); mvc.perform(manager(get("/manager/deliveries/31"))).andExpect(status().isOk());
    }
    @Test void reassignmentCancelsOldTaskAndDeniesOldCourier() throws Exception {
        prepare(); mvc.perform(postManager("reassign").param("employeeId", "23").param("replacesAssignmentId", "41").param("reason", "Unavailable"))
            .andExpect(flash().attributeExists("notice"));
        mvc.perform(postCourier("accept")).andExpect(status().isForbidden()); assertEquals(AssignmentStatus.CANCELLED, f.tasks.get(41L).getStatus());
        mvc.perform(courier(get("/shipper/deliveries/41"))).andExpect(status().isOk()).andExpect(r -> assertFalse(r.getResponse().getContentAsString().contains(f.s.getReceiverPhone())));
    }
    @Test void foreignCourierCannotReadOrWriteByGuessingId() throws Exception {
        prepare(); var foreign = user(f.otherCourier.getUser().getEmail()).roles("SHIPPER");
        mvc.perform(get("/shipper/deliveries/41").with(foreign)).andExpect(status().isNotFound());
        mvc.perform(post("/shipper/deliveries/41/accept").with(foreign).with(csrf()).param("expectedVersion", f.s.getVersion().toString())
            .param("confirmed", "true").param("reference", "ILLEGAL")).andExpect(status().isNotFound());
        assertSame(f.destination, f.s.getCurrentBranch());
    }
    @Test void foreignManagerCannotViewOrModifyDelivery() throws Exception {
        mvc.perform(get("/manager/deliveries/31").with(user(f.sourceManager.getEmail()).roles("MANAGER"))).andExpect(status().isNotFound());
        mvc.perform(post("/manager/deliveries/31/assign").with(user(f.sourceManager.getEmail()).roles("MANAGER")).with(csrf())
            .param("expectedVersion", "0").param("employeeId", "21")).andExpect(status().isForbidden()); assertTrue(f.tasks.isEmpty());
    }
    @Test void revokedOrInactiveSessionCannotAct() throws Exception {
        prepare(); f.roleRows.put(f.courier.getUser().getId(), List.of("ROLE_USER"));
        mvc.perform(postCourier("accept")).andExpect(status().isForbidden());
        f.roleRows.put(f.courier.getUser().getId(), List.of("ROLE_SHIPPER")); f.courier.setStatus(EmployeeStatus.INACTIVE);
        mvc.perform(courier(get("/shipper/deliveries/41"))).andExpect(status().isForbidden()); assertSame(f.destination, f.s.getCurrentBranch());
    }
    @Test void everyMutatingEndpointRequiresCsrf() throws Exception {
        for (String suffix : List.of("assign", "reassign", "handoff", "redelivery", "return", "recoveries/accept", "recoveries/reject", "return-to-sender")) {
            mvc.perform(manager(post("/manager/deliveries/31/" + suffix))).andExpect(status().isForbidden());
        }
        for (String suffix : List.of("accept", "start", "complete", "fail", "return-to-branch")) {
            mvc.perform(courier(post("/shipper/deliveries/41/" + suffix))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(delivery);
    }
    @ParameterizedTest @ValueSource(strings = {"USER", "ADMIN", "SHIPPER"})
    void nonManagerCannotReachManagerDelivery(String role) throws Exception {
        mvc.perform(get("/manager/deliveries").with(user("wrong-role").roles(role))).andExpect(status().isForbidden());
        mvc.perform(post("/manager/deliveries/31/assign").with(user("wrong-role").roles(role)).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(read, delivery);
    }
    @ParameterizedTest @ValueSource(strings = {"USER", "ADMIN", "MANAGER"})
    void nonShipperCannotReachShipperDelivery(String role) throws Exception {
        mvc.perform(get("/shipper/deliveries").with(user("wrong-role").roles(role))).andExpect(status().isForbidden());
        mvc.perform(post("/shipper/deliveries/41/complete").with(user("wrong-role").roles(role)).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(read, delivery);
    }
    @Test void guestGetsExistingUnauthorizedResponseAndCannotUseGetToMutate() throws Exception {
        mvc.perform(get("/manager/deliveries").accept(org.springframework.http.MediaType.TEXT_HTML)).andExpect(status().isUnauthorized());
        mvc.perform(get("/shipper/deliveries").accept(org.springframework.http.MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        mvc.perform(courier(get("/shipper/deliveries/41/complete"))).andExpect(status().isMethodNotAllowed()); verifyNoInteractions(delivery);
    }
    @ParameterizedTest @ValueSource(strings = {"actorId", "employeeId", "status", "deliveredAt", "totalFee"})
    void shipperCannotBindServerOwnedFields(String field) throws Exception {
        start(); clearInvocations(delivery);
        mvc.perform(postCourier("complete").param("recipientType", "RECIPIENT").param("recipientName", "Actual recipient").param(field, "99"))
            .andExpect(status().isBadRequest()); verifyNoInteractions(delivery); assertTrue(f.proofRows.isEmpty());
    }
    @Test void invalidProofAndInvalidEnumCannotProduceSuccess() throws Exception {
        start(); mvc.perform(postCourier("complete").param("recipientType", "RECIPIENT").param("recipientName", " "))
            .andExpect(flash().attributeExists("error"));
        mvc.perform(postCourier("complete").param("recipientType", "FAKE").param("recipientName", "Actual recipient"))
            .andExpect(flash().attributeExists("error")); assertTrue(f.proofRows.isEmpty());
    }
    @Test void historyEscapesUntrustedReasonAndNotes() throws Exception {
        start(); mvc.perform(postCourier("fail").param("code", "ABSENT").param("reason", "<script>private()</script>"))
            .andExpect(flash().attributeExists("notice"));
        mvc.perform(manager(get("/manager/deliveries/31"))).andExpect(status().isOk()).andExpect(r -> {
            String html = r.getResponse().getContentAsString(); assertFalse(html.contains("<script>private()")); assertTrue(html.contains("&lt;script&gt;private()"));
        });
    }
    @Test void invalidPageIsRejectedBeforeRead() throws Exception {
        mvc.perform(manager(get("/manager/deliveries").param("page", "-1"))).andExpect(status().isBadRequest());
        mvc.perform(courier(get("/shipper/deliveries").param("page", "1000001"))).andExpect(status().isBadRequest()); verifyNoInteractions(read);
    }
}
