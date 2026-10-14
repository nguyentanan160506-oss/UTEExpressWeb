package com.ute.uteexpress.security;

import com.ute.uteexpress.entity.User;
import com.ute.uteexpress.enums.UserStatus;
import com.ute.uteexpress.repository.UserRepository;
import com.ute.uteexpress.repository.UserRoleRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises the real form-login/logout filters, templates and session repository. */
@SpringBootTest(classes = SecurityConfigTests.TestApplication.class, properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
    "spring.flyway.enabled=false",
    "spring.sql.init.mode=never",
    "spring.thymeleaf.check-template-location=false",
    "debug=false",
    "logging.level.root=INFO"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class AuthenticationTests {

    private static final String EMAIL = "auth-fixture@example.test";
    private static final String FAILURE_MESSAGE =
        "Không thể đăng nhập. Vui lòng kiểm tra thông tin hoặc liên hệ quản trị viên.";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PasswordEncoder encoder;

    @MockitoBean
    private UserRepository users;

    @MockitoBean
    private UserRoleRepository userRoles;

    @Test
    void loginTemplateRendersEmailFormWithRealCsrfTokenAndLocalStyles() throws Exception {
        MvcResult page = mvc.perform(get("/login"))
            .andExpect(status().isOk())
            .andExpect(view().name("auth/login"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(result -> {
                String html = result.getResponse().getContentAsString();
                assertTrue(html.contains("<form action=\"/login\" method=\"post\">"));
                assertTrue(Pattern.compile("<input[^>]*name=\"email\"[^>]*type=\"email\"").matcher(html).find());
                assertTrue(Pattern.compile("<input[^>]*name=\"password\"[^>]*type=\"password\"").matcher(html).find());
                assertTrue(Pattern.compile("<input[^>]*type=\"hidden\"[^>]*name=\"_csrf\"").matcher(html).find());
                assertFalse(Pattern.compile("<input[^>]*name=\"password\"[^>]*value=").matcher(html).find());
            })
            .andReturn();
        mvc.perform(get("/css/vendor/bootstrap.min.css")).andExpect(status().isOk());
        mvc.perform(get("/css/auth.css")).andExpect(status().isOk());
        // Ignored build artifact for visual inspection; it contains no real credentials.
        Files.writeString(Path.of("target", "auth-login-preview.html"),
            page.getResponse().getContentAsString(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    @Test
    void renderedCsrfTokenLogsInByEmailAndChangesExistingSessionId() throws Exception {
        String password = prepare(UserStatus.ACTIVE, List.of("ROLE_USER"));
        MvcResult page = mvc.perform(get("/login")).andReturn();
        MockHttpSession session = session(page);
        String previousId = session.getId();
        var token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"")
            .matcher(page.getResponse().getContentAsString());
        assertTrue(token.find());

        mvc.perform(post("/login").session(session)
                .with(request -> {
                    request.setRequestedSessionId(previousId);
                    request.setRequestedSessionIdValid(true);
                    return request;
                })
                .param("_csrf", token.group(1)).param("email", EMAIL).param("password", password))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/account"));

        assertNotEquals(previousId, session.getId());
        Authentication authentication = authentication(session);
        assertTrue(authentication.isAuthenticated());
        assertEquals(EMAIL, authentication.getName());
        assertNull(authentication.getCredentials());
        assertNull(((org.springframework.security.core.userdetails.UserDetails)
            authentication.getPrincipal()).getPassword());
        mvc.perform(get("/account").session(session))
            .andExpect(status().isOk()).andExpect(view().name("auth/account"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(EMAIL)))
            .andExpect(result -> assertTrue(Pattern.compile(
                "<form action=\"/logout\" method=\"post\">\\s*<input[^>]*name=\"_csrf\"")
                .matcher(result.getResponse().getContentAsString()).find()));
        mvc.perform(get("/login").session(session))
            .andExpect(redirectedUrl("/account"));
        verify(users).findByEmail(EMAIL);
        verify(users, never()).findByUsername(anyString());
    }

    @Test
    void incorrectPasswordUnknownEmailAndBlockedAccountsHaveSameHtmlFailure() throws Exception {
        String password = prepare(UserStatus.ACTIVE, List.of("ROLE_USER"));
        assertHtmlFailure(EMAIL, UUID.randomUUID().toString());
        assertHtmlFailure("unknown@example.test", password);
        password = prepare(UserStatus.LOCKED, List.of("ROLE_USER"));
        assertHtmlFailure(EMAIL, password);
        password = prepare(UserStatus.UNVERIFIED, List.of("ROLE_USER"));
        assertHtmlFailure(EMAIL, password);
        password = prepare(null, List.of("ROLE_USER"));
        assertHtmlFailure(EMAIL, password);

        mvc.perform(get("/login").param("error", ""))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString(FAILURE_MESSAGE)))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("LOCKED"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("UNVERIFIED"))));
    }

    @Test
    void loginRejectsMissingAndInvalidCsrfBeforeQueryingCredentials() throws Exception {
        mvc.perform(post("/login").param("email", EMAIL).param("password", UUID.randomUUID().toString())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        mvc.perform(post("/login").with(csrf().useInvalidToken())
                .param("email", EMAIL).param("password", UUID.randomUUID().toString())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
        verifyNoInteractions(users, userRoles);
    }

    @Test
    void loginDoesNotAcceptOldUsernameParameter() throws Exception {
        String password = prepare(UserStatus.ACTIVE, List.of("ROLE_USER"));
        mvc.perform(post("/login").with(csrf()).param("username", EMAIL).param("password", password))
            .andExpect(redirectedUrl("/login?error"));
        verifyNoInteractions(users, userRoles);
    }

    @Test
    void malformedCredentialsProduceGenericApi401() throws Exception {
        String password = prepare(UserStatus.ACTIVE, List.of("ROLE_USER"));
        for (String email : List.of("", " ", "a".repeat(151))) {
            mvc.perform(post("/login").with(csrf()).param("email", email).param("password", password)
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.title").value("Unauthorized"));
        }
        mvc.perform(post("/login").with(csrf()).param("email", EMAIL)
                .param("password", "a".repeat(1000)).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void apiFormLoginReturns204AndPersistsSession() throws Exception {
        String password = prepare(UserStatus.ACTIVE, List.of("ROLE_USER"));
        MvcResult result = mvc.perform(post("/login").with(csrf())
                .param("email", EMAIL).param("password", password).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent()).andExpect(content().string("")).andReturn();
        mvc.perform(get("/account").session(session(result))).andExpect(status().isOk());
    }

    @Test
    void apiLoginFailureDoesNotExposeWhetherAccountExistsOrIsLocked() throws Exception {
        String password = prepare(UserStatus.LOCKED, List.of("ROLE_ADMIN"));
        String locked = apiFailure(EMAIL, password);
        String missing = apiFailure("unknown@example.test", password);
        prepare(UserStatus.ACTIVE, List.of("ROLE_USER"));
        String wrongPassword = apiFailure(EMAIL, UUID.randomUUID().toString());
        assertEquals(locked, missing);
        assertEquals(locked, wrongPassword);
        assertFalse(locked.contains(EMAIL));
    }

    @Test
    void userRoleFromLoginSessionCannotInvokeAdminFunction() throws Exception {
        MockHttpSession session = login(UserStatus.ACTIVE, List.of("ROLE_USER"));
        assertEquals(Set.of("ROLE_USER"), roleNames(session));
        mvc.perform(get("/__security-fixture/account").session(session)).andExpect(status().isOk());
        mvc.perform(get("/__security-fixture/admin").session(session).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void adminAndManagerRolesFromDatabaseArePreservedWithoutHierarchy() throws Exception {
        MockHttpSession admin = login(UserStatus.ACTIVE, List.of("ROLE_ADMIN"));
        assertEquals(Set.of("ROLE_ADMIN"), roleNames(admin));
        mvc.perform(get("/__security-fixture/admin").session(admin)).andExpect(status().isOk());
        mvc.perform(get("/__security-fixture/account").session(admin).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
        MockHttpSession manager = login(UserStatus.ACTIVE, List.of("ROLE_MANAGER"));
        assertEquals(Set.of("ROLE_MANAGER"), roleNames(manager));
        mvc.perform(get("/__security-fixture/manager").session(manager)).andExpect(status().isOk());
        mvc.perform(get("/__security-fixture/admin").session(manager).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    void accountWithNoRolesIsNotGrantedAnyBusinessAuthority() throws Exception {
        MockHttpSession session = login(UserStatus.ACTIVE, List.of());
        assertTrue(roleNames(session).isEmpty());
        mvc.perform(get("/account").session(session)).andExpect(status().isOk());
        mvc.perform(get("/__security-fixture/admin").session(session).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    void logoutInvalidatesSessionClearsContextAndExpiresSessionCookie() throws Exception {
        MockHttpSession session = login(UserStatus.ACTIVE, List.of("ROLE_USER"));
        MvcResult result = mvc.perform(post("/logout").session(session).with(csrf()))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?logout"))
            .andReturn();
        assertTrue(session.isInvalid());
        assertNull(result.getRequest().getUserPrincipal());
        assertEquals(0, result.getResponse().getCookie("JSESSIONID").getMaxAge());
        mvc.perform(get("/account").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/login").param("logout", ""))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Bạn đã đăng xuất an toàn.")));
    }

    @Test
    void logoutRejectsGetMissingTokenAndInvalidTokenWithoutEndingSession() throws Exception {
        MockHttpSession session = login(UserStatus.ACTIVE, List.of("ROLE_USER"));
        mvc.perform(get("/logout").session(session).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
        mvc.perform(post("/logout").session(session).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
        mvc.perform(post("/logout").session(session).with(csrf().useInvalidToken())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
        assertFalse(session.isInvalid());
        mvc.perform(get("/account").session(session)).andExpect(status().isOk());
    }

    @Test
    void apiLogoutReturns204AndEndsSession() throws Exception {
        MockHttpSession session = login(UserStatus.ACTIVE, List.of("ROLE_USER"));
        mvc.perform(post("/logout").session(session).with(csrf()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertTrue(session.isInvalid());
    }

    @Test
    void guestCannotAccessAccountAndHtmlDoesNotReceiveFakeJson() throws Exception {
        mvc.perform(get("/account").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/account").accept(MediaType.TEXT_HTML))
            .andExpect(status().isUnauthorized())
            .andExpect(result -> assertEquals("Unauthorized", result.getResponse().getErrorMessage()))
            .andExpect(result -> assertNull(result.getResponse().getContentType()));
    }

    @Test
    void successRedirectIgnoresClientSuppliedExternalTarget() throws Exception {
        String password = prepare(UserStatus.ACTIVE, List.of("ROLE_USER"));
        mvc.perform(post("/login").with(csrf()).param("email", EMAIL).param("password", password)
                .param("redirect", "https://example.test/external"))
            .andExpect(redirectedUrl("/account"));
    }

    private void assertHtmlFailure(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/login").with(csrf())
                .param("email", email).param("password", password))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?error"))
            .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        if (session != null) {
            assertNull(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
            assertNull(session.getAttribute("SPRING_SECURITY_LAST_EXCEPTION"));
        }
    }

    private String apiFailure(String email, String password) throws Exception {
        return mvc.perform(post("/login").with(csrf()).param("email", email).param("password", password)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(401))
            .andReturn().getResponse().getContentAsString();
    }

    private MockHttpSession login(UserStatus status, List<String> roles) throws Exception {
        String password = prepare(status, roles);
        return session(mvc.perform(post("/login").with(csrf())
                .param("email", EMAIL).param("password", password))
            .andExpect(redirectedUrl("/account")).andReturn());
    }

    private String prepare(UserStatus status, List<String> roles) {
        String password = UUID.randomUUID().toString();
        User user = new User();
        user.setId(71L);
        user.setUsername("auth-fixture");
        user.setEmail(EMAIL);
        user.setStatus(status);
        user.setPasswordHash(encoder.encode(password));
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRoles.findRoleNamesByUserId(71L)).thenReturn(roles);
        return password;
    }

    private MockHttpSession session(MvcResult result) {
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        return session;
    }

    private Authentication authentication(MockHttpSession session) {
        SecurityContext context = (SecurityContext) session.getAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertNotNull(context);
        return context.getAuthentication();
    }

    private Set<String> roleNames(MockHttpSession session) {
        return authentication(session).getAuthorities().stream()
            .map(authority -> authority.getAuthority()).filter(role -> role.startsWith("ROLE_"))
            .collect(Collectors.toSet());
    }
}
