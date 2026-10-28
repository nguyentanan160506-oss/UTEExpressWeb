package com.ute.uteexpress.security;

import com.ute.uteexpress.dto.shipment.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.service.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

import static com.ute.uteexpress.shipment.ShipmentFixtures.*;
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
    "spring.flyway.enabled=false", "spring.sql.init.mode=never",
    "spring.thymeleaf.check-template-location=false", "debug=false", "logging.level.root=INFO"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class UserShipmentControllerTests {
    @Autowired private MockMvc mvc;
    @MockitoBean private UserRepository users;
    @MockitoBean private UserRoleRepository userRoles;
    @MockitoBean private RoleRepository roles;
    @MockitoBean private EmailOtpRepository otps;
    @MockitoBean private OtpMailSender otpMailSender;
    @MockitoBean private UserShipmentService shipments;
    @MockitoBean private QuoteService quotes;
    private static final String TOKEN = "a".repeat(32);

    @BeforeEach void setup() {
        when(shipments.options()).thenReturn(options());
        when(quotes.branchChoices(any())).thenReturn(new QuoteService.BranchChoices(options().branches(), options().branches()));
    }

    @Test @WithMockUser(roles = "USER")
    void createFormRendersVietnameseFieldsCsrfAndRealCatalogChoices() throws Exception {
        mvc.perform(get("/user/shipments/create")).andExpect(status().isOk())
            .andExpect(view().name("shipments/create")).andExpect(result -> {
                String html = result.getResponse().getContentAsString();
                assertTrue(html.contains("Tạo vận đơn")); assertTrue(html.contains("name=\"_csrf\""));
                assertTrue(html.contains("name=\"senderName\"")); assertTrue(html.contains("name=\"receiverName\""));
                assertTrue(html.contains("name=\"serviceId\"")); assertTrue(html.contains("Chi nhánh thử nghiệm"));
                assertFalse(html.contains("name=\"status\"")); assertFalse(html.contains("name=\"senderId\""));
                assertFalse(html.contains("name=\"shippingFee\""));
            });
    }

    @Test @WithMockUser(roles = "USER")
    void emptyCatalogExplainsMissingDataAndDisablesCreateButton() throws Exception {
        when(shipments.options()).thenReturn(new ShipmentOptions(List.of(), List.of()));
        mvc.perform(get("/user/shipments/create")).andExpect(status().isOk())
            .andExpect(result -> assertTrue(result.getResponse().getContentAsString().contains("Chưa có dịch vụ hoạt động")));
    }

    @Test @WithMockUser(roles = "USER")
    void createsAndRedirectsToDetailWithSuccessNotice() throws Exception {
        when(quotes.quote(any())).thenReturn(TOKEN);
        mvc.perform(post("/user/shipments/quotes").with(csrf()).params(params()))
            .andExpect(redirectedUrl("/user/shipments/quotes/" + TOKEN)).andExpect(flash().attributeCount(0));
        var form = org.mockito.ArgumentCaptor.forClass(ShipmentCreateForm.class);
        verify(quotes).quote(form.capture()); assertEquals(21L, form.getValue().getServiceId());
        verify(shipments, never()).options();
    }

    @Test @WithMockUser(roles = "USER")
    void browserTransportHeadersDoNotBecomeSuppressedFormFieldsOrSupplyBusinessInputs() throws Exception {
        when(quotes.quote(any())).thenReturn(TOKEN);
        mvc.perform(post("/user/shipments/quotes").with(csrf()).params(params())
                .header("Sec-Fetch-Site", "same-origin").header("Sec-Fetch-Mode", "navigate")
                .header("Sec-CH-UA", "Browser QA").header("Origin", "http://127.0.0.1:18082")
                .header("ServiceId", "999").header("TotalFee", "0"))
            .andExpect(redirectedUrl("/user/shipments/quotes/" + TOKEN));
        var captured=org.mockito.ArgumentCaptor.forClass(ShipmentCreateForm.class);
        verify(quotes).quote(captured.capture());assertEquals(21L,captured.getValue().getServiceId());
    }

    @Test @WithMockUser(roles = {"ADMIN", "USER"})
    void explicitUserRoleAllowsMultiRoleAccount() throws Exception {
        mvc.perform(get("/user/shipments/create")).andExpect(status().isOk());
    }

    @Test void guestsCannotReadOrCreateEvenWithValidCsrf() throws Exception {
        for (String path : List.of("/user/shipments", "/user/shipments/create", "/user/shipments/31")) {
            mvc.perform(get(path).accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        }
        mvc.perform(get("/user/shipments/create").accept(MediaType.TEXT_HTML))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/user/shipments/quotes").with(csrf()).params(params()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
        verify(quotes, never()).quote(any());
    }

    @Test @WithMockUser(roles = "MANAGER")
    void managerWithoutUserRoleIsForbidden() throws Exception {
        mvc.perform(get("/user/shipments").accept(MediaType.TEXT_HTML)).andExpect(status().isForbidden());
        mvc.perform(post("/user/shipments/quotes").with(csrf()).params(params()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        verify(quotes, never()).quote(any());
    }

    @Test @WithMockUser(roles = "ADMIN")
    void adminWithoutUserRoleIsForbidden() throws Exception {
        mvc.perform(get("/user/shipments/create")).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(roles = "USER")
    void missingAndInvalidCsrfCannotCreate() throws Exception {
        mvc.perform(post("/user/shipments/quotes").params(params()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
        mvc.perform(post("/user/shipments/quotes").with(csrf().useInvalidToken()).params(params()))
            .andExpect(status().isForbidden());
        verify(quotes, never()).quote(any());
    }

    @Test @WithMockUser(roles = "USER")
    void validationErrorsAppearUnderFieldsAndRetainInputWithoutCreating() throws Exception {
        var params = params(); params.set("weightKg", "0"); params.set("receiverPhone", "abc");
        mvc.perform(post("/user/shipments/quotes").with(csrf()).params(params))
            .andExpect(result -> assertEquals("0", result.getRequest().getParameter("weightKg")))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("shipmentForm", "weightKg", "receiverPhone"))
            .andExpect(view().name("shipments/create")).andExpect(result -> {
                String html = result.getResponse().getContentAsString();
                assertTrue(html.contains("Giá trị phải lớn hơn 0."));
                assertTrue(html.contains("Người gửi thử nghiệm"));
                assertTrue(html.contains("receiverPhone-error"));
            });
        verify(quotes, never()).quote(any());
    }

    @Test @WithMockUser(roles = "USER")
    void malformedNumberAndEnumAreFieldErrors() throws Exception {
        var params = params(); params.set("weightKg", "bad"); params.set("paymentPayer", "ADMIN");
        mvc.perform(post("/user/shipments/quotes").with(csrf()).params(params))
            .andExpect(model().attributeHasFieldErrors("shipmentForm", "weightKg", "paymentPayer"))
            .andExpect(result -> assertTrue(result.getResponse().getContentAsString()
                .contains("Vui lòng nhập trọng lượng bằng số hợp lệ.")));
        verify(quotes, never()).quote(any());
    }

    @Test @WithMockUser(roles = "USER")
    void tamperedOwnerStatusFeesRolesAndTrackingAreRejected() throws Exception {
        for (String field : List.of("ownerId", "userId", "senderId", "sender.id", "status", "paymentStatus",
                "shippingFee", "insuranceFee", "totalFee", "currentBranchId", "role", "trackingNumber", "id")) {
            mvc.perform(post("/user/shipments/quotes").with(csrf()).params(params()).param(field, "999"))
                .andExpect(view().name("shipments/create")).andExpect(model().attributeHasErrors("shipmentForm"))
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString().contains("trường không được phép")));
        }
        verify(quotes, never()).quote(any());
    }

    @Test @WithMockUser(roles = "USER")
    void missingRateAndInvalidBranchErrorsStayOnRelevantFields() throws Exception {
        doThrow(new ShipmentCreationException("serviceId", "Chưa có bảng giá phù hợp.")).when(quotes).quote(any());
        mvc.perform(post("/user/shipments/quotes").with(csrf()).params(params()))
            .andExpect(model().attributeHasFieldErrors("shipmentForm", "serviceId"))
            .andExpect(result -> assertTrue(result.getResponse().getContentAsString().contains("Chưa có bảng giá phù hợp.")));
        doThrow(new ShipmentCreationException("originBranchId", "Chi nhánh không hợp lệ.")).when(quotes).quote(any());
        mvc.perform(post("/user/shipments/quotes").with(csrf()).params(params()))
            .andExpect(model().attributeHasFieldErrors("shipmentForm", "originBranchId"));
    }

    @Test @WithMockUser(roles = "USER")
    void databaseCollisionCannotShowSuccessOrExposeExceptionDetails() throws Exception {
        doThrow(new DataIntegrityViolationException("internal-detail-marker")).when(quotes).quote(any());
        mvc.perform(post("/user/shipments/quotes").with(csrf()).params(params())).andExpect(view().name("shipments/create"))
            .andExpect(model().attributeHasErrors("shipmentForm")).andExpect(result -> {
                assertFalse(result.getFlashMap().containsKey("notice"));
                String html = result.getResponse().getContentAsString();
                assertFalse(html.contains("internal-detail-marker")); assertTrue(html.contains("Không thể lưu báo giá"));
            });
    }

    @Test @WithMockUser(roles = "USER")
    void historyRendersFiltersPaginationAndOnlyServiceResults() throws Exception {
        when(shipments.list(eq("UTE"), eq(ShipmentStatus.CREATED), any(Pageable.class))).thenAnswer(invocation ->
            new PageImpl<>(List.of(new ShipmentSummary(31L, "UTE_TEST_TRACKING", "Người nhận thử nghiệm",
                NOW, ShipmentStatus.CREATED)), invocation.getArgument(2), 31));
        mvc.perform(get("/user/shipments").param("q", "UTE").param("status", "CREATED").param("page", "1").param("size", "10"))
            .andExpect(status().isOk()).andExpect(view().name("shipments/history")).andExpect(result -> {
                String html = result.getResponse().getContentAsString();
                assertTrue(html.contains("UTE_TEST_TRACKING")); assertTrue(html.contains("Trang trước"));
                assertTrue(html.contains("Trang sau")); assertTrue(html.contains("q=UTE"));
                assertTrue(html.contains("status=CREATED")); assertTrue(html.contains("size=10"));
            });
        var page = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(shipments).list(eq("UTE"), eq(ShipmentStatus.CREATED), page.capture());
        assertEquals(1, page.getValue().getPageNumber()); assertEquals(10, page.getValue().getPageSize());
    }

    @Test @WithMockUser(roles = "USER")
    void emptyHistoryShowsHelpfulEmptyState() throws Exception {
        when(shipments.list(eq(""), isNull(), any())).thenReturn(Page.empty());
        mvc.perform(get("/user/shipments")).andExpect(status().isOk())
            .andExpect(result -> assertTrue(result.getResponse().getContentAsString().contains("Chưa có vận đơn phù hợp")));
    }

    @Test @WithMockUser(roles = "USER")
    void detailShowsActualEventFeesAndNoStatusMutationForm() throws Exception {
        when(shipments.detail(31L, 0)).thenReturn(detail());
        mvc.perform(get("/user/shipments/31")).andExpect(status().isOk())
            .andExpect(view().name("shipments/detail")).andExpect(result -> {
                String html = result.getResponse().getContentAsString();
                assertTrue(html.contains("UTE_TEST_TRACKING")); assertTrue(html.contains("Đã tạo vận đơn"));
                assertTrue(html.contains("25.000,00")); assertTrue(html.contains("Hành trình vận đơn"));
                assertFalse(html.contains("passwordHash")); assertFalse(html.contains("name=\"status\""));
                assertTrue(html.contains("Chưa ghi nhận"));
            });
    }

    @Test @WithMockUser(roles = "USER")
    void missingOrForeignShipmentIs404() throws Exception {
        when(shipments.detail(99L, 0)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        mvc.perform(get("/user/shipments/99")).andExpect(status().isNotFound());
    }

    @Test @WithMockUser(roles = "USER")
    void invalidFiltersPagesAndIdsAreBadRequests() throws Exception {
        for (String[] param : new String[][] {{"status", "BOGUS"}, {"size", "101"}, {"page", "-1"}, {"q", "a".repeat(51)}}) {
            mvc.perform(get("/user/shipments").param(param[0], param[1])).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/user/shipments/-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/user/shipments/31").param("eventPage", "-1")).andExpect(status().isBadRequest());
        verify(shipments, never()).list(any(), any(), any());
        verify(shipments, never()).detail(any(), anyInt());
    }

    @Test @WithMockUser(roles = "USER")
    void usersHaveNoStatusUpdateEndpoint() throws Exception {
        mvc.perform(put("/user/shipments/31").with(csrf()).param("status", "DELIVERED"))
            .andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/user/shipments/31/status").with(csrf()).param("status", "DELIVERED"))
            .andExpect(status().isNotFound());
        verify(quotes, never()).quote(any());
    }

    @Test void guestCannotReviewQuoteOrRequestBranchesOrConfirm() throws Exception {
        mvc.perform(get("/user/shipments/quotes/" + TOKEN).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
        for (String path : List.of("/user/shipments", "/user/shipments/quotes", "/user/shipments/branches")) {
            mvc.perform(post(path).with(csrf()).param("quoteToken", TOKEN).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        }
        verify(quotes, never()).confirm(any()); verify(quotes, never()).review(any());
    }

    @Test @WithMockUser(roles = "USER")
    void confirmationAcceptsOnlyQuoteTokenAndRedirectsToCreatedShipment() throws Exception {
        when(quotes.confirm(TOKEN)).thenReturn(31L);
        mvc.perform(post("/user/shipments").with(csrf()).param("quoteToken", TOKEN))
            .andExpect(redirectedUrl("/user/shipments/31")).andExpect(flash().attributeExists("notice"));
        verify(quotes).confirm(TOKEN);
    }

    @Test @WithMockUser(roles = "USER")
    void priceInputAndRawLegacyFormCannotBypassQuoteConfirmation() throws Exception {
        for (String field : List.of("shippingFee", "pickupFee", "insuranceFee", "totalFee", "originBranchId", "requesterId", "expiresAt")) {
            mvc.perform(post("/user/shipments").with(csrf()).param("quoteToken", TOKEN).param(field, "0"))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/user/shipments").with(csrf()).params(params())).andExpect(status().isBadRequest());
        verify(quotes, never()).confirm(any());
    }

    @Test @WithMockUser(roles = "USER")
    void confirmationRequiresValidTokenAndCsrf() throws Exception {
        mvc.perform(post("/user/shipments").param("quoteToken", TOKEN)).andExpect(status().isForbidden());
        mvc.perform(post("/user/shipments").with(csrf().useInvalidToken()).param("quoteToken", TOKEN))
            .andExpect(status().isForbidden());
        for (String token : List.of("51", "x".repeat(32), "a".repeat(33))) {
            mvc.perform(post("/user/shipments").with(csrf()).param("quoteToken", token)).andExpect(status().isBadRequest());
        }
        verify(quotes, never()).confirm(any());
    }

    @Test @WithMockUser(roles = "USER")
    void reviewShowsSavedBreakdownAndOnlyTokenInConfirmationForm() throws Exception {
        when(quotes.review(TOKEN)).thenReturn(quoteView());
        mvc.perform(get("/user/shipments/quotes/" + TOKEN)).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store")).andExpect(view().name("shipments/quote"))
            .andExpect(result -> {
                String html = result.getResponse().getContentAsString();
                assertTrue(html.contains("32.500,00")); assertTrue(html.contains("7.500,00"));
                assertTrue(html.contains("Xác nhận tạo vận đơn")); assertTrue(html.contains("name=\"_csrf\""));
                assertTrue(html.contains("name=\"quoteToken\"")); assertFalse(html.contains("name=\"totalFee\""));
                assertTrue(html.contains("Chưa áp dụng sản phẩm bảo hiểm"));
            });
    }

    @Test @WithMockUser(roles = "USER")
    void quoteBusinessFailureAndPersistenceFailureNeverShowSuccessOrDatabaseDetails() throws Exception {
        when(quotes.confirm(TOKEN)).thenThrow(new ShipmentCreationException("shipmentMethod", "Báo giá đã hết hạn."));
        mvc.perform(post("/user/shipments").with(csrf()).param("quoteToken", TOKEN))
            .andExpect(redirectedUrl("/user/shipments/quotes/" + TOKEN)).andExpect(flash().attributeExists("error"))
            .andExpect(result -> assertFalse(result.getFlashMap().containsKey("notice")));
        doThrow(new DataIntegrityViolationException("private-database-marker")).when(quotes).confirm(TOKEN);
        mvc.perform(post("/user/shipments").with(csrf()).param("quoteToken", TOKEN))
            .andExpect(flash().attributeExists("error"))
            .andExpect(result -> assertFalse(result.getFlashMap().toString().contains("private-database-marker")));
    }

    @Test @WithMockUser(roles = "USER")
    void unknownAndForeignQuotesAre404AndMalformedTokensAre400() throws Exception {
        when(quotes.review(TOKEN)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        when(quotes.confirm(TOKEN)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        mvc.perform(get("/user/shipments/quotes/" + TOKEN)).andExpect(status().isNotFound());
        mvc.perform(post("/user/shipments").with(csrf()).param("quoteToken", TOKEN)).andExpect(status().isNotFound());
        mvc.perform(get("/user/shipments/quotes/51")).andExpect(status().isBadRequest());
    }

    @Test @WithMockUser(roles = "USER")
    void branchLookupRetainsInputAndCannotCreateQuoteOrShipment() throws Exception {
        mvc.perform(post("/user/shipments/branches").with(csrf()).params(params()))
            .andExpect(status().isOk()).andExpect(view().name("shipments/create"));
        verify(quotes, atLeastOnce()).branchChoices(any()); verify(quotes, never()).quote(any()); verify(quotes, never()).confirm(any());
    }

    private MultiValueMap<String, String> params() {
        var result = new LinkedMultiValueMap<String, String>(); var form = new BeanWrapperImpl(form());
        for (var property : form.getPropertyDescriptors()) {
            if ("class".equals(property.getName())) { continue; }
            Object value = form.getPropertyValue(property.getName());
            if (value != null) { result.add(property.getName(), value.toString()); }
        }
        return result;
    }
}
