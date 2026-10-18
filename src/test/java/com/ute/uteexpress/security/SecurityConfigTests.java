package com.ute.uteexpress.security;

import com.ute.uteexpress.config.SecurityConfig;
import com.ute.uteexpress.entity.User;
import com.ute.uteexpress.enums.UserStatus;
import com.ute.uteexpress.repository.UserRepository;
import com.ute.uteexpress.repository.RoleRepository;
import com.ute.uteexpress.repository.EmailOtpRepository;
import com.ute.uteexpress.service.OtpMailSender;
import com.ute.uteexpress.repository.UserRoleRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

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
    "spring.flyway.enabled=false",
    "spring.sql.init.mode=never",
    "spring.thymeleaf.check-template-location=false",
    "debug=false",
    "logging.level.root=INFO"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class SecurityConfigTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private UserRepository users;

    @MockitoBean
    private UserRoleRepository userRoles;

    @MockitoBean
    private RoleRepository roles;

    @MockitoBean
    private EmailOtpRepository otps;

    @MockitoBean
    private OtpMailSender otpMailSender;

    @Test
    void bcryptBeanHashesAndVerifiesWithoutStoringPlaintext() {
        String raw = UUID.randomUUID().toString();
        String first = encoder.encode(raw);
        String second = encoder.encode(raw);
        assertInstanceOf(BCryptPasswordEncoder.class, encoder);
        assertNotEquals(raw, first);
        assertNotEquals(first, second);
        assertTrue(encoder.matches(raw, first));
        assertFalse(encoder.matches(UUID.randomUUID().toString(), first));
    }

    @Test
    void guestCanReadStaticResourcesWithSecurityHeaders() throws Exception {
        mvc.perform(get("/css/security-fixture.css"))
            .andExpect(status().isOk())
            .andExpect(result -> assertEquals("/* Security test fixture only. */",
                result.getResponse().getContentAsString().strip()))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(head("/css/security-fixture.css")).andExpect(status().isOk());
    }

    @Test
    void guestReceives401ProblemJsonForProtectedApiRequest() throws Exception {
        mvc.perform(get("/__security-fixture/account").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void userCannotInvokeAdminFunction() throws Exception {
        mvc.perform(get("/__security-fixture/admin").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @WithMockUser(roles = "SHIPPER")
    void shipperCannotAccessAdminOrUserShipments() throws Exception {
        for (String path : List.of("/__security-fixture/admin", "/admin/users", "/user/shipments/31")) {
            mvc.perform(get(path).accept(MediaType.APPLICATION_JSON)).andExpect(status().isForbidden());
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanInvokeExplicitlyAdminSecuredFixture() throws Exception {
        mvc.perform(get("/__security-fixture/admin")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void managerCanInvokeManagerFunctionButNotAdminFunction() throws Exception {
        mvc.perform(get("/__security-fixture/manager")).andExpect(status().isOk());
        mvc.perform(get("/__security-fixture/admin").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void userCanInvokeExplicitlyUserSecuredFixture() throws Exception {
        mvc.perform(get("/__security-fixture/account")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminDoesNotImplicitlyInheritOtherRoles() throws Exception {
        mvc.perform(get("/__security-fixture/account").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    void productionChainDeniesEveryUndeclaredRouteForGuest() throws Exception {
        for (String path : List.of("/", "/unmapped", "/manager", "/api/unmapped")) {
            mvc.perform(get(path).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void productionChainDeniesUndeclaredRoutesEvenForAdmin() throws Exception {
        mvc.perform(get("/api/unmapped").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
        mvc.perform(get("/unmapped").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    void htmlRequestUsesServletErrorResponseInsteadOfFakeJsonOrLoginRedirect() throws Exception {
        mvc.perform(get("/private").accept(MediaType.TEXT_HTML))
            .andExpect(status().isUnauthorized())
            .andExpect(result -> assertNull(result.getResponse().getContentType()))
            .andExpect(result -> assertEquals("Unauthorized", result.getResponse().getErrorMessage()))
            .andExpect(result -> assertNull(result.getResponse().getRedirectedUrl()));
    }

    @Test
    @WithMockUser(roles = "USER")
    void forbiddenHtmlRequestUsesServletErrorResponse() throws Exception {
        mvc.perform(get("/__security-fixture/admin").accept(MediaType.TEXT_HTML))
            .andExpect(status().isForbidden())
            .andExpect(result -> assertNull(result.getResponse().getContentType()))
            .andExpect(result -> assertEquals("Forbidden", result.getResponse().getErrorMessage()));
    }

    @Test
    void responseNegotiationHonorsHtmlQualityAndHandlesInvalidAccept() throws Exception {
        mvc.perform(get("/private").header("Accept", "text/html;q=1,application/json;q=0.5"))
            .andExpect(status().isUnauthorized())
            .andExpect(result -> assertNull(result.getResponse().getContentType()));
        mvc.perform(get("/private").accept(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/private").header("Accept", "invalid"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void csrfIsRequiredForSessionAuthenticatedWrites() throws Exception {
        mvc.perform(post("/__security-fixture/account").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));
        mvc.perform(post("/__security-fixture/account").with(csrf()))
            .andExpect(status().isOk());
        mvc.perform(post("/__security-fixture/account").with(csrf().useInvalidToken())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void staticPathsAreNotGrantedWriteAccess() throws Exception {
        mvc.perform(post("/css/security-fixture.css").with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    void authenticationManagerUsesDatabaseAuthoritiesAndBcrypt() {
        String raw = prepareAccount(UserStatus.ACTIVE);
        var result = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated("fixture@example.test", raw));
        assertTrue(result.isAuthenticated());
        assertEquals(List.of("ROLE_USER"), result.getAuthorities().stream()
            .map(authority -> authority.getAuthority())
            .filter(authority -> authority.startsWith("ROLE_")).toList());
        // Spring Security 7 records the successful password factor as an authority.
        assertTrue(result.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("FACTOR_PASSWORD")));
        assertNull(result.getCredentials());
        assertFalse(result.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void authenticationManagerRejectsIncorrectCredentials() {
        prepareAccount(UserStatus.ACTIVE);
        assertThrows(BadCredentialsException.class, () -> authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated("fixture@example.test", UUID.randomUUID().toString())));
    }

    @Test
    void authenticationManagerRejectsLockedAndUnverifiedAccounts() {
        String locked = prepareAccount(UserStatus.LOCKED);
        assertThrows(LockedException.class, () -> authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated("fixture@example.test", locked)));
        String unverified = prepareAccount(UserStatus.UNVERIFIED);
        assertThrows(DisabledException.class, () -> authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated("fixture@example.test", unverified)));
    }

    private String prepareAccount(UserStatus status) {
        String raw = UUID.randomUUID().toString();
        User user = new User();
        user.setId(41L);
        user.setUsername("fixture-user");
        user.setEmail("fixture@example.test");
        user.setPasswordHash(encoder.encode(raw));
        user.setStatus(status);
        when(users.findByEmail("fixture@example.test")).thenReturn(Optional.of(user));
        when(userRoles.findRoleNamesByUserId(41L)).thenReturn(List.of("ROLE_USER"));
        return raw;
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({SecurityConfig.class, SecurityErrorHandlers.class,
        DatabaseUserDetailsService.class, AuthenticationResponseHandlers.class, FixtureConfiguration.class})
    static class TestApplication {
        // Shared MVC contexts disable JPA. Supply the new module's persistence boundaries
        // so the existing Auth/Admin tests keep exercising their real controllers and filters.
        @Bean com.ute.uteexpress.repository.EmployeeRepository employeeRepository() {
            return mock(com.ute.uteexpress.repository.EmployeeRepository.class);
        }
        @Bean com.ute.uteexpress.repository.ManagerBranchRepository managerBranchRepository() {
            return mock(com.ute.uteexpress.repository.ManagerBranchRepository.class);
        }
        @Bean com.ute.uteexpress.repository.ShipmentAssignmentRepository shipmentAssignmentRepository() {
            return mock(com.ute.uteexpress.repository.ShipmentAssignmentRepository.class);
        }
        @Bean com.ute.uteexpress.repository.BranchServiceAreaRepository branchServiceAreaRepository() {
            return mock(com.ute.uteexpress.repository.BranchServiceAreaRepository.class);
        }
        @Bean com.ute.uteexpress.repository.ShipmentRepository shipmentRepository() {
            return mock(com.ute.uteexpress.repository.ShipmentRepository.class);
        }
        @Bean com.ute.uteexpress.repository.ShipmentEventRepository shipmentEventRepository() {
            return mock(com.ute.uteexpress.repository.ShipmentEventRepository.class);
        }
        @Bean com.ute.uteexpress.repository.BranchRepository branchRepository() {
            return mock(com.ute.uteexpress.repository.BranchRepository.class);
        }
        @Bean com.ute.uteexpress.repository.ShippingServiceRepository shippingServiceRepository() {
            return mock(com.ute.uteexpress.repository.ShippingServiceRepository.class);
        }
        @Bean com.ute.uteexpress.repository.ShippingRateRuleRepository shippingRateRuleRepository() {
            return mock(com.ute.uteexpress.repository.ShippingRateRuleRepository.class);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import(FixtureController.class)
    static class FixtureConfiguration {

        // Test-only endpoints exercise production method authorization and error handlers.
        // Production has no business routes, so its catch-all chain remains denyAll.
        @Bean
        @Order(0)
        SecurityFilterChain fixtureFilterChain(HttpSecurity http,
                SecurityErrorHandlers errors) throws Exception {
            return http.securityMatcher("/__security-fixture/**")
                .csrf(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .build();
        }
    }

    @RestController
    @TestComponent
    @RequestMapping("/__security-fixture")
    static class FixtureController {

        @GetMapping("/admin")
        @PreAuthorize("hasRole('ADMIN')")
        public String admin() {
            return "admin";
        }

        @GetMapping("/manager")
        @PreAuthorize("hasRole('MANAGER')")
        public String manager() {
            return "manager";
        }

        @GetMapping("/account")
        @PreAuthorize("hasRole('USER')")
        public String account() {
            return "account";
        }

        @PostMapping("/account")
        @PreAuthorize("hasRole('USER')")
        public String updateAccount() {
            return "updated";
        }
    }
}
