package com.ute.uteexpress.security;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.service.OtpMailSender;
import com.ute.uteexpress.dto.shipment.ShipmentCreateForm;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.data.domain.*;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.ute.uteexpress.shipment.ShipmentFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real Controller + service + Spring Security + templates; only persistence and mail are mocked. */
@SpringBootTest(classes = SecurityConfigTests.TestApplication.class, properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
    "spring.flyway.enabled=false", "spring.sql.init.mode=never",
    "spring.thymeleaf.check-template-location=false", "debug=false", "logging.level.root=INFO"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@WithMockUser(username = "shipment-fixture@example.test", roles = "USER")
class UserShipmentFlowTests {
    @Autowired private MockMvc mvc;
    @MockitoBean private UserRepository users;
    @MockitoBean private UserRoleRepository userRoles;
    @MockitoBean private RoleRepository roles;
    @MockitoBean private EmailOtpRepository otps;
    @MockitoBean private OtpMailSender otpMailSender;
    @MockitoBean private ShipmentRepository shipments;
    @MockitoBean private ShipmentEventRepository events;
    @MockitoBean private BranchRepository branches;
    @MockitoBean private ShippingServiceRepository services;
    @MockitoBean private ShippingRateRuleRepository rates;

    @BeforeEach void setup() {
        when(users.findByEmail(owner().getEmail())).thenReturn(Optional.of(owner()));
        when(userRoles.findRoleNamesByUserId(1L)).thenReturn(List.of("ROLE_USER"));
        when(branches.findByStatusOrderByNameAsc(BranchStatus.ACTIVE)).thenReturn(List.of(branch(11L), branch(12L)));
        when(branches.findByIdAndStatus(11L, BranchStatus.ACTIVE)).thenReturn(Optional.of(branch(11L)));
        when(branches.findByIdAndStatus(12L, BranchStatus.ACTIVE)).thenReturn(Optional.of(branch(12L)));
        when(services.findByIsActiveTrueOrderByNameAsc()).thenReturn(List.of(catalog()));
        when(services.findByIdAndIsActiveTrue(21L)).thenReturn(Optional.of(catalog()));
        when(rates.findEffectiveRules(eq(21L), eq(RateScopeType.INTRA_PROVINCE), any(LocalDateTime.class)))
            .thenReturn(List.of(rate()));
    }

    @Test void createsOwnedShipmentThenRendersOnlyPersistedEventHistory() throws Exception {
        List<ShipmentEvent> recorded = new ArrayList<>();
        when(shipments.saveAndFlush(any())).thenAnswer(invocation -> {
            Shipment saved = invocation.getArgument(0); saved.setId(31L);
            when(shipments.findByIdAndSenderId(31L, 1L)).thenReturn(Optional.of(saved)); return saved;
        });
        when(events.saveAndFlush(any())).thenAnswer(invocation -> {
            ShipmentEvent event = invocation.getArgument(0); recorded.add(event); return event;
        });
        when(events.findByShipmentIdAndShipmentSenderId(eq(31L), eq(1L), any(Pageable.class)))
            .thenAnswer(invocation -> new PageImpl<>(List.copyOf(recorded)));
        var request = post("/user/shipments").with(csrf()); var form = new BeanWrapperImpl(form());
        for (var property : form.getPropertyDescriptors()) {
            if ("class".equals(property.getName())) { continue; }
            Object value = form.getPropertyValue(property.getName());
            if (value != null) { request.param(property.getName(), value.toString()); }
        }
        mvc.perform(request).andExpect(redirectedUrl("/user/shipments/31"));
        mvc.perform(get("/user/shipments/31")).andExpect(status().isOk()).andExpect(result -> {
            String html = result.getResponse().getContentAsString();
            assertTrue(html.contains("Đã tạo vận đơn")); assertTrue(html.contains("25.000,00"));
        });
        assertEquals(1, recorded.size());
        assertEquals(1L, recorded.getFirst().getShipment().getSender().getId());
    }

    @Test void foreignShipmentReturns404AndNeverReadsOrLeaksItsTimeline() throws Exception {
        when(shipments.findByIdAndSenderId(99L, 1L)).thenReturn(Optional.empty());
        mvc.perform(get("/user/shipments/99")).andExpect(status().isNotFound());
        verify(shipments).findByIdAndSenderId(99L, 1L);
        verify(shipments, never()).findById(anyLong()); verifyNoInteractions(events);
    }

    @Test void noRateProducesAFieldErrorAndNoWritesThroughTheRealService() throws Exception {
        when(rates.findEffectiveRules(anyLong(), any(), any())).thenReturn(List.of());
        ShipmentCreateForm data = form();
        var request = post("/user/shipments").with(csrf()); var form = new BeanWrapperImpl(data);
        for (var property : form.getPropertyDescriptors()) {
            if ("class".equals(property.getName())) { continue; }
            Object value = form.getPropertyValue(property.getName());
            if (value != null) { request.param(property.getName(), value.toString()); }
        }
        mvc.perform(request).andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("shipmentForm", "serviceId"));
        verifyNoInteractions(shipments, events);
    }
}
