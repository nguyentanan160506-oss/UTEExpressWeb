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
    @MockitoBean private ShipmentQuoteRepository quotes;
    @MockitoBean private PickupFeeRuleRepository pickups;
    @MockitoBean private BranchServiceAreaRepository areas;
    private ShipmentQuote quoted;
    private Branch routeBranch(Long id) {
        Branch local = branch(id); Branch hub = branch(id + 100); hub.setBranchType(BranchType.CENTRAL_HUB);
        local.setParentHub(hub); return local;
    }

    @BeforeEach void setup() {
        when(users.findByEmail(owner().getEmail())).thenReturn(Optional.of(owner()));
        when(userRoles.findRoleNamesByUserId(1L)).thenReturn(List.of("ROLE_USER"));
        when(branches.findByStatusOrderByNameAsc(BranchStatus.ACTIVE)).thenReturn(List.of(branch(11L), branch(12L)));
        when(branches.findByIdAndStatus(11L, BranchStatus.ACTIVE)).thenReturn(Optional.of(routeBranch(11L)));
        when(branches.findByIdAndStatus(12L, BranchStatus.ACTIVE)).thenReturn(Optional.of(routeBranch(12L)));
        when(services.findByIsActiveTrueOrderByNameAsc()).thenReturn(List.of(catalog()));
        when(services.findByIdAndIsActiveTrue(21L)).thenReturn(Optional.of(catalog()));
        when(rates.findEffectiveRules(eq(21L), eq(RateScopeType.INTRA_PROVINCE), any(LocalDateTime.class)))
            .thenReturn(List.of(rate()));
        when(branches.findByIdAndStatus(111L, BranchStatus.ACTIVE)).thenReturn(Optional.of(routeBranch(11L).getParentHub()));
        when(branches.findByIdAndStatus(112L, BranchStatus.ACTIVE)).thenReturn(Optional.of(routeBranch(12L).getParentHub()));
        when(areas.findServingBranches(anyString(), anyString(), anyString(), any(), any(), any(), any()))
            .thenReturn(List.of(routeBranch(11L)));
        when(areas.findDeliveryBranches(anyString(), anyString(), anyString(), any(), any(), any()))
            .thenReturn(List.of(routeBranch(12L)));
        when(quotes.saveAndFlush(any())).thenAnswer(call -> {
            quoted = call.getArgument(0); quoted.setId(51L);
            when(quotes.findByTokenAndRequesterId(quoted.getToken(), 1L)).thenReturn(Optional.of(quoted));
            when(quotes.findOwnedForUpdate(quoted.getToken(), 1L)).thenReturn(Optional.of(quoted)); return quoted;
        });
    }

    @Test void createsOwnedShipmentThenRendersOnlyPersistedEventHistory() throws Exception {
        List<ShipmentEvent> recorded = new ArrayList<>();
        when(shipments.saveAndFlush(any())).thenAnswer(invocation -> {
            Shipment saved = invocation.getArgument(0); saved.setId(31L);
            when(shipments.findByIdAndSenderId(31L, 1L)).thenReturn(Optional.of(saved));
            when(shipments.findByQuoteIdAndSenderId(51L, 1L)).thenReturn(Optional.of(saved)); return saved;
        });
        when(events.saveAndFlush(any())).thenAnswer(invocation -> {
            ShipmentEvent event = invocation.getArgument(0); recorded.add(event); return event;
        });
        when(events.findByShipmentIdAndShipmentSenderId(eq(31L), eq(1L), any(Pageable.class)))
            .thenAnswer(invocation -> new PageImpl<>(List.copyOf(recorded)));
        var request = post("/user/shipments/quotes").with(csrf()); var form = new BeanWrapperImpl(form());
        for (var property : form.getPropertyDescriptors()) {
            if ("class".equals(property.getName())) { continue; }
            Object value = form.getPropertyValue(property.getName());
            if (value != null) { request.param(property.getName(), value.toString()); }
        }
        mvc.perform(request).andExpect(status().is3xxRedirection());
        assertNotNull(quoted); assertTrue(recorded.isEmpty()); verify(shipments, never()).saveAndFlush(any());
        mvc.perform(get("/user/shipments/quotes/" + quoted.getToken())).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"));
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/user/shipments").with(csrf()).param("quoteToken", quoted.getToken()))
                .andExpect(redirectedUrl("/user/shipments/31"));
        }
        verify(shipments, times(1)).saveAndFlush(any());
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

    @Test void homePickupResolvesCoveredBranchAndShowsDatabaseSurchargeBeforeConfirming() throws Exception {
        PickupFeeRule pickup = new PickupFeeRule(); pickup.setId(61L);
        pickup.setAmount(new java.math.BigDecimal("7500.00")); pickup.setEffectiveFrom(NOW.minusDays(1));
        when(pickups.findEffectiveRules(eq(11L), eq(21L), any())).thenReturn(List.of(pickup));
        when(shipments.saveAndFlush(any())).thenAnswer(call -> { Shipment saved = call.getArgument(0); saved.setId(31L); return saved; });
        ShipmentCreateForm input = form(); input.setShipmentMethod(ShipmentMethod.HOME_PICKUP); input.setOriginBranchId(null);
        input.setNote("<script>private</script>");
        var request = post("/user/shipments/quotes").with(csrf()); var wrapper = new BeanWrapperImpl(input);
        for (var property : wrapper.getPropertyDescriptors()) {
            if ("class".equals(property.getName())) { continue; }
            Object value = wrapper.getPropertyValue(property.getName());
            if (value != null) { request.param(property.getName(), value.toString()); }
        }
        mvc.perform(request).andExpect(status().is3xxRedirection());
        assertEquals(11L, quoted.getOriginBranch().getId()); assertEquals(ShipmentMethod.HOME_PICKUP, quoted.getShipmentMethod());
        mvc.perform(get("/user/shipments/quotes/" + quoted.getToken())).andExpect(status().isOk()).andExpect(result -> {
            String html = result.getResponse().getContentAsString();
            assertTrue(html.contains("7.500,00")); assertTrue(html.contains("32.500,00"));
            assertFalse(html.contains("<script>private</script>")); assertTrue(html.contains("&lt;script&gt;private&lt;/script&gt;"));
        });
        verify(shipments, never()).saveAndFlush(any()); verifyNoInteractions(events);
        mvc.perform(post("/user/shipments").with(csrf()).param("quoteToken", quoted.getToken()))
            .andExpect(redirectedUrl("/user/shipments/31"));
        verify(shipments).saveAndFlush(argThat(saved -> saved.getPickupFee().equals(new java.math.BigDecimal("7500.00"))
            && saved.getTotalFee().equals(new java.math.BigDecimal("32500.00")) && saved.getCurrentBranch() == null));
    }

    @Test void noRateProducesAFieldErrorAndNoWritesThroughTheRealService() throws Exception {
        when(rates.findEffectiveRules(anyLong(), any(), any())).thenReturn(List.of());
        ShipmentCreateForm data = form();
        var request = post("/user/shipments/quotes").with(csrf()); var form = new BeanWrapperImpl(data);
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
