package com.ute.uteexpress.security;

import com.ute.uteexpress.pricing.PricingScenario;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** MVC/filter/Thymeleaf integration; actual business services delegate to synthetic repositories, no database. */
@SpringBootTest(classes = SecurityConfigTests.TestApplication.class, properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
    "spring.flyway.enabled=false", "spring.sql.init.mode=never", "debug=false", "logging.level.root=INFO"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class PricingFlowTests {
    @Autowired MockMvc mvc;
    @MockitoBean AdminPricingService admin;
    @MockitoBean QuoteService quote;
    @MockitoBean UserRepository users;
    @MockitoBean UserRoleRepository roles;
    @MockitoBean RoleRepository catalogRoles;
    @MockitoBean EmailOtpRepository otp;
    @MockitoBean OtpMailSender mail;
    PricingScenario f;
    @BeforeEach void setup() {
        f = new PricingScenario(); org.springframework.security.core.context.SecurityContextHolder.clearContext();
        doAnswer(c -> { f.management.authorize(); return null; }).when(admin).authorize();
        when(admin.catalog(any(), anyInt())).thenAnswer(c -> f.management.catalog(c.getArgument(0), c.getArgument(1)));
        when(admin.service(anyLong(), anyInt())).thenAnswer(c -> f.management.service(c.getArgument(0), c.getArgument(1)));
        when(admin.books(any(), anyInt())).thenAnswer(c -> f.management.books(c.getArgument(0), c.getArgument(1)));
        when(admin.detail(anyLong(), anyInt())).thenAnswer(c -> f.management.detail(c.getArgument(0), c.getArgument(1)));
        when(admin.pickupBranches(anyInt())).thenAnswer(c -> f.management.pickupBranches(c.getArgument(0)));
        when(admin.createService(any())).thenAnswer(c -> f.management.createService(c.getArgument(0)));
        doAnswer(c -> { f.management.editService(c.getArgument(0), c.getArgument(1), c.getArgument(2)); return null; }).when(admin).editService(anyLong(), anyLong(), any());
        doAnswer(c -> { f.management.serviceStatus(c.getArgument(0), c.getArgument(1), c.getArgument(2)); return null; }).when(admin).serviceStatus(anyLong(), anyLong(), anyBoolean());
        when(admin.createDraft(any())).thenAnswer(c -> f.management.createDraft(c.getArgument(0)));
        doAnswer(c -> { f.management.editPeriod(c.getArgument(0), c.getArgument(1), c.getArgument(2)); return null; }).when(admin).editPeriod(anyLong(), anyLong(), any());
        doAnswer(c -> { f.management.shippingRule(c.getArgument(0), c.getArgument(1), c.getArgument(2)); return null; }).when(admin).shippingRule(anyLong(), anyLong(), any());
        doAnswer(c -> { f.management.pickupRule(c.getArgument(0), c.getArgument(1), c.getArgument(2)); return null; }).when(admin).pickupRule(anyLong(), anyLong(), any());
        when(admin.check(anyLong())).thenAnswer(c -> f.management.check(c.getArgument(0)));
        doAnswer(c -> { f.management.publish(c.getArgument(0), c.getArgument(1), c.getArgument(2)); return null; }).when(admin).publish(anyLong(), anyLong(), anyBoolean());
        doAnswer(c -> { f.management.retireVersion(c.getArgument(0), c.getArgument(1), c.getArgument(2), c.getArgument(3)); return null; }).when(admin).retireVersion(anyLong(), anyLong(), any(), anyBoolean());
        doAnswer(c -> { f.management.retireLegacy(c.getArgument(0), c.getArgument(1), c.getArgument(2), c.getArgument(3)); return null; }).when(admin).retireLegacy(anyLong(), anyBoolean(), any(), anyBoolean());
        when(quote.estimateServices()).thenAnswer(c -> f.quoting.estimateServices());
        when(quote.estimateBranches(any())).thenAnswer(c -> f.quoting.estimateBranches(c.getArgument(0)));
        when(quote.estimate(any())).thenAnswer(c -> f.quoting.estimate(c.getArgument(0)));
        when(quote.simulateDraft(anyLong(), any())).thenAnswer(c -> f.quoting.simulateDraft(c.getArgument(0), c.getArgument(1)));
    }
    @AfterEach void close() { f.close(); }
    MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder r) { return r.with(user(f.admin.getEmail()).roles("ADMIN")); }
    MockHttpServletRequestBuilder action(String url) { return admin(post(url)).with(csrf()); }
    MockHttpServletRequestBuilder estimate(String url) {
        var g = f.guest(); return post(url).with(csrf()).param("shipmentMethod", "BRANCH_DROPOFF").param("serviceId", "21").param("originBranchId", "11").param("destinationBranchId", "12")
            .param("senderProvince", g.getSenderProvince()).param("senderDistrict", g.getSenderDistrict()).param("senderWard", g.getSenderWard())
            .param("receiverProvince", g.getReceiverProvince()).param("receiverDistrict", g.getReceiverDistrict()).param("receiverWard", g.getReceiverWard()).param("weightKg", "1.01");
    }
    void createDraft() throws Exception {
        mvc.perform(action("/admin/pricing").param("serviceId", "21").param("code", "V1").param("validFrom", f.time.plusHours(1).toString()).param("validTo", f.time.plusHours(2).toString()))
            .andExpect(flash().attributeExists("notice")); assertEquals(1, f.books.size());
    }
    String version() { return f.books.get(101L).getVersion().toString(); }
    void shipping() throws Exception {
        mvc.perform(action("/admin/pricing/101/rules").param("expectedVersion", version()).param("scope", "INTRA_PROVINCE").param("baseWeightKg", "1.00").param("basePrice", "30000.00")
            .param("extraWeightStepKg", "0.50").param("extraPricePerStep", "5000.00").param("active", "true")).andExpect(flash().attributeExists("notice"));
    }
    @Test void actualAdminCatalogPagesCreateEditAndStatusWork() throws Exception {
        mvc.perform(admin(get("/admin/services"))).andExpect(status().isOk()); mvc.perform(admin(get("/admin/services/new"))).andExpect(status().isOk());
        mvc.perform(admin(get("/admin/services/21"))).andExpect(status().isOk()).andExpect(r -> assertTrue(r.getResponse().getContentAsString().contains("name=\"_csrf\"")));
        mvc.perform(admin(get("/admin/services/21/edit"))).andExpect(status().isOk());
        mvc.perform(action("/admin/services").param("code", "NEW").param("name", "New service").param("estimatedDeliveryHours", "48")).andExpect(flash().attributeExists("notice")); assertEquals(2, f.catalogs.size());
        mvc.perform(action("/admin/services/21").param("catalogVersion", "0").param("name", "<script>name()</script>").param("estimatedDeliveryHours", "24"))
            .andExpect(flash().attributeExists("notice"));
        mvc.perform(admin(get("/admin/services/21"))).andExpect(status().isOk()).andExpect(r -> { var html = r.getResponse().getContentAsString(); assertFalse(html.contains("<script>name()")); assertTrue(html.contains("&lt;script&gt;name()")); });
        mvc.perform(action("/admin/services/21/status").param("expectedVersion", f.catalog.getVersion().toString()).param("confirmed", "true").param("active", "false"))
            .andExpect(flash().attributeExists("notice")); assertFalse(f.catalog.isActive());
    }
    @Test void draftRulesPreviewValidationPublicationAndRetirementRender() throws Exception {
        mvc.perform(admin(get("/admin/pricing"))).andExpect(status().isOk()); mvc.perform(admin(get("/admin/pricing/new").param("serviceId", "21"))).andExpect(status().isOk());
        createDraft(); shipping();
        mvc.perform(action("/admin/pricing/101/pickup-rules").param("expectedVersion", version()).param("branchId", "11").param("amount", "9000.00").param("active", "true"))
            .andExpect(flash().attributeExists("notice"));
        mvc.perform(admin(get("/admin/pricing/101"))).andExpect(status().isOk()).andExpect(r -> assertTrue(r.getResponse().getContentAsString().contains("name=\"_csrf\"")));
        mvc.perform(action("/admin/pricing/101/check")).andExpect(flash().attributeExists("error"));
        mvc.perform(action("/admin/pricing/101/publish").param("expectedVersion", version()).param("confirmed", "true")).andExpect(flash().attributeExists("error"));
        for (String kind : new String[] {"shipping/41", "pickup/61"}) {
            mvc.perform(action("/admin/services/legacy/" + kind + "/retire").param("at", f.books.get(101L).getValidFrom().toString()).param("confirmed", "true"))
                .andExpect(flash().attributeExists("notice"));
        }
        mvc.perform(action("/admin/pricing/101/check")).andExpect(flash().attributeExists("notice"));
        mvc.perform(action("/admin/pricing/101/publish").param("expectedVersion", version()).param("confirmed", "true")).andExpect(flash().attributeExists("notice"));
        mvc.perform(admin(get("/admin/pricing/101"))).andExpect(status().isOk()).andExpect(r -> assertFalse(r.getResponse().getContentAsString().contains("name=\"basePrice\"")));
        mvc.perform(action("/admin/pricing/101/retire").param("expectedVersion", version()).param("at", f.books.get(101L).getValidFrom().plusMinutes(15).toString()).param("confirmed", "true"))
            .andExpect(flash().attributeExists("notice"));
    }
    @Test void editPeriodAndExistingRuleUsePathBookAndVersion() throws Exception {
        createDraft(); shipping();
        mvc.perform(action("/admin/pricing/101").param("expectedVersion", version()).param("validFrom", f.time.plusHours(3).toString())).andExpect(flash().attributeExists("notice"));
        mvc.perform(action("/admin/pricing/101/rules").param("expectedVersion", version()).param("ruleId", "42").param("scope", "SAME_REGION").param("baseWeightKg", "1.00")
            .param("basePrice", "40000.00").param("extraWeightStepKg", "0.50").param("extraPricePerStep", "5000.00").param("active", "true"))
            .andExpect(flash().attributeExists("notice")); assertEquals(RateScopeType.SAME_REGION, f.shippingRows.get(42L).getScopeType());
    }
    @Test void guestCanEstimateThroughSameEngineWithoutInternalConfigOrPersistence() throws Exception {
        mvc.perform(get("/quote")).andExpect(status().isOk()).andExpect(r -> { var html = r.getResponse().getContentAsString(); assertTrue(html.contains("name=\"_csrf\"")); assertFalse(html.contains("Admin</a>")); });
        mvc.perform(estimate("/quote/branches")).andExpect(status().isOk());
        mvc.perform(estimate("/quote")).andExpect(status().isOk()).andExpect(model().attributeExists("estimate")).andExpect(r -> {
            String html = r.getResponse().getContentAsString(); assertTrue(html.contains("25000.00"));
            for (String internal : new String[] {"shippingRateRuleId", "pricingVersionId", "baseWeightKg", "extraPricePerStep", "receiverPhone", "quoteToken"}) { assertFalse(html.contains(internal), internal); }
        }); assertTrue(f.quoteRows.isEmpty()); assertTrue(f.shipmentRows.isEmpty());
    }
    @Test void coverageModeRequiresDistrictWardAndBranchEvenThoughNationalModeDoesNot() throws Exception {
        mvc.perform(post("/quote").with(csrf()).param("nationwide","false").param("shipmentMethod","BRANCH_DROPOFF")
                .param("serviceId","21").param("senderProvince","Hà Nội").param("receiverProvince","Cà Mau").param("weightKg","1.00"))
            .andExpect(status().isOk()).andExpect(model().attributeHasErrors("estimateForm"))
            .andExpect(r->assertTrue(r.getResponse().getContentAsString().contains("cần đủ quận/huyện")));
        assertTrue(f.quoteRows.isEmpty());assertTrue(f.shipmentRows.isEmpty());
    }
    @Test void adminPreviewUsesLiveEngineAndNeverChangesShipments() throws Exception {
        mvc.perform(admin(get("/admin/pricing/preview"))).andExpect(status().isOk());
        mvc.perform(admin(estimate("/admin/pricing/preview/branches"))).andExpect(status().isOk());
        mvc.perform(admin(estimate("/admin/pricing/preview"))).andExpect(status().isOk()).andExpect(model().attributeExists("estimate")); assertTrue(f.shipmentRows.isEmpty()); assertTrue(f.quoteRows.isEmpty());
    }
    @Test void draftSimulationUsesDraftRowsAndDoesNotPublishOrPersistQuote() throws Exception {
        createDraft(); shipping(); f.catalog.setActive(false);
        mvc.perform(admin(get("/admin/pricing/101/preview"))).andExpect(status().isOk());
        mvc.perform(admin(estimate("/admin/pricing/101/preview/branches"))).andExpect(status().isOk());
        mvc.perform(admin(estimate("/admin/pricing/101/preview"))).andExpect(status().isOk()).andExpect(model().attributeExists("estimate"))
            .andExpect(r -> assertTrue(r.getResponse().getContentAsString().contains("35000.00")));
        assertEquals(PricingVersionStatus.DRAFT, f.books.get(101L).getStatus()); assertTrue(f.shipmentRows.isEmpty()); assertTrue(f.quoteRows.isEmpty());
        mvc.perform(estimate("/admin/pricing/101/preview")).andExpect(status().isUnauthorized());
    }
    @Test void formsPreserveApplicationContextPath() throws Exception {
        mvc.perform(get("/uteexpress/quote").contextPath("/uteexpress")).andExpect(status().isOk())
            .andExpect(r -> assertTrue(r.getResponse().getContentAsString().contains("action=\"/uteexpress/quote\"")));
        mvc.perform(admin(get("/uteexpress/admin/services/new").contextPath("/uteexpress"))).andExpect(status().isOk())
            .andExpect(r -> assertTrue(r.getResponse().getContentAsString().contains("action=\"/uteexpress/admin/services\"")));
    }
    @Test void guestErrorsRemainBusinessErrorsAndDoNotShowFalsePrice() throws Exception {
        f.legacy.setActive(false); mvc.perform(estimate("/quote")).andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("estimateForm", "serviceId")).andExpect(model().attributeDoesNotExist("estimate"));
        mvc.perform(estimate("/quote").param("weightKg", "-1")).andExpect(status().isOk()).andExpect(model().attributeHasErrors("estimateForm"));
    }
    @ParameterizedTest @ValueSource(strings = {"USER", "MANAGER", "SHIPPER"})
    void nonAdminCannotReadOrWriteAnyPricingArea(String role) throws Exception {
        for (String path : new String[] {"/admin/services", "/admin/services/new", "/admin/services/21", "/admin/services/21/edit", "/admin/pricing", "/admin/pricing/new", "/admin/pricing/101", "/admin/pricing/preview"}) {
            mvc.perform(get(path).with(user("non-admin").roles(role))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/admin/pricing/101/publish").with(user("non-admin").roles(role)).with(csrf())).andExpect(status().isForbidden()); verifyNoInteractions(admin, quote);
    }
    @Test void revokedAndInactiveAdminsCannotReadOrPublish() throws Exception {
        f.permissions.put(2L, java.util.List.of("ROLE_USER")); mvc.perform(admin(get("/admin/services"))).andExpect(status().isForbidden());
        f.permissions.put(2L, java.util.List.of("ROLE_ADMIN")); f.admin.setStatus(UserStatus.LOCKED); mvc.perform(admin(get("/admin/pricing/preview"))).andExpect(status().isForbidden());
    }
    @Test void allMutationEndpointsRequireCsrfIncludingPublicEstimate() throws Exception {
        for (String path : new String[] {"/admin/services", "/admin/services/21", "/admin/services/21/status", "/admin/pricing", "/admin/pricing/101", "/admin/pricing/101/rules", "/admin/pricing/101/pickup-rules", "/admin/pricing/101/check", "/admin/pricing/101/publish", "/admin/pricing/101/retire", "/admin/services/legacy/shipping/41/retire", "/admin/services/legacy/pickup/61/retire", "/admin/pricing/preview", "/admin/pricing/preview/branches", "/admin/pricing/101/preview", "/admin/pricing/101/preview/branches"}) {
            mvc.perform(admin(post(path))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/quote")).andExpect(status().isForbidden()); mvc.perform(post("/quote/branches")).andExpect(status().isForbidden()); verifyNoInteractions(admin, quote);
    }
    @ParameterizedTest @ValueSource(strings = {"actorId", "status", "version", "publishedBy", "publishedAt", "serviceId"})
    void publishRejectsMassAssignment(String field) throws Exception {
        createDraft(); clearInvocations(admin);
        mvc.perform(action("/admin/pricing/101/publish").param("expectedVersion", version()).param("confirmed", "true").param(field, "99"))
            .andExpect(status().isBadRequest()); verifyNoInteractions(admin);
    }
    @ParameterizedTest @ValueSource(strings = {"totalFee", "shippingFee", "requesterId", "pricingVersionId", "receiverPhone"})
    void guestQuoteRejectsMoneyAndPersonalDataInjection(String field) throws Exception {
        mvc.perform(estimate("/quote").param(field, "99")).andExpect(status().isBadRequest()); verifyNoInteractions(quote);
    }
    @Test void codeIsImmutableAndBadDraftInputCannotPublish() throws Exception {
        mvc.perform(action("/admin/services/21").param("code", "CHANGED").param("name", "Name").param("estimatedDeliveryHours", "24").param("catalogVersion", "0"))
            .andExpect(status().isBadRequest());
        mvc.perform(action("/admin/pricing").param("serviceId", "21").param("code", "V1").param("validFrom", "bad-date"))
            .andExpect(flash().attributeExists("error")); assertTrue(f.books.isEmpty());
        mvc.perform(admin(get("/admin/pricing").param("page", "-1"))).andExpect(status().isBadRequest());
    }
    @Test void invalidNumbersDisplayVietnameseWithoutJavaBindingDetails() throws Exception {
        mvc.perform(estimate("/quote").param("lengthCm", "invalid-number")).andExpect(status().isOk()).andExpect(r -> {
            String html = r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(html.contains("Định dạng không hợp lệ")); assertFalse(html.contains("java.math.BigDecimal")); assertFalse(html.contains("Failed to convert"));
        });
    }
    @Test void guestCannotReachAdminAndGetCannotPublish() throws Exception {
        mvc.perform(get("/admin/pricing").accept("text/html")).andExpect(status().isUnauthorized());
        mvc.perform(admin(get("/admin/pricing/101/publish"))).andExpect(status().isMethodNotAllowed()); verifyNoInteractions(admin);
    }
}
