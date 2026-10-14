package com.ute.uteexpress.security;

import com.ute.uteexpress.entity.User;
import com.ute.uteexpress.enums.UserStatus;
import com.ute.uteexpress.repository.UserRepository;
import com.ute.uteexpress.repository.UserRoleRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseUserDetailsServiceTests {

    private UserRepository users;
    private UserRoleRepository roles;
    private DatabaseUserDetailsService service;

    @BeforeEach
    void createService() {
        users = mock(UserRepository.class);
        roles = mock(UserRoleRepository.class);
        service = new DatabaseUserDetailsService(users, roles);
    }

    @Test
    void authoritiesAreExactlyTheDistinctPersistedRoleNames() {
        prepareUser(UserStatus.ACTIVE);
        when(roles.findRoleNamesByUserId(41L)).thenReturn(
            List.of("ROLE_USER", "ROLE_MANAGER", "ROLE_USER"));
        var details = service.loadUserByUsername("fixture@example.test");
        assertEquals(Set.of("ROLE_USER", "ROLE_MANAGER"), details.getAuthorities().stream()
            .map(authority -> authority.getAuthority()).collect(Collectors.toSet()));
        assertEquals(2, details.getAuthorities().size());
        assertTrue(details.isEnabled());
        assertTrue(details.isAccountNonLocked());
    }

    @Test
    void userWithoutRolesReceivesNoDefaultAuthority() {
        prepareUser(UserStatus.ACTIVE);
        when(roles.findRoleNamesByUserId(41L)).thenReturn(List.of());
        assertTrue(service.loadUserByUsername("fixture@example.test").getAuthorities().isEmpty());
    }

    @Test
    void lockedAndUnverifiedStatesAreRepresentedWithoutGrantingAccess() {
        prepareUser(UserStatus.LOCKED);
        var locked = service.loadUserByUsername("fixture@example.test");
        assertFalse(locked.isAccountNonLocked());
        assertFalse(locked.isEnabled());
        prepareUser(UserStatus.UNVERIFIED);
        assertFalse(service.loadUserByUsername("fixture@example.test").isEnabled());
        prepareUser(null);
        assertFalse(service.loadUserByUsername("fixture@example.test").isEnabled());
    }

    @Test
    void nonexistentAccountFailsWithoutLookingUpRoles() {
        when(users.findByEmail("absent")).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("absent"));
        verifyNoInteractions(roles);
    }

    @Test
    void emailLookupTrimsWhitespaceAndDoesNotFallBackToUsername() {
        prepareUser(UserStatus.ACTIVE);
        var details = service.loadUserByUsername("  fixture@example.test  ");
        assertEquals("fixture@example.test", details.getUsername());
        verify(users).findByEmail("fixture@example.test");
        verify(users, never()).findByUsername(anyString());
    }

    @Test
    void missingOrOverlongEmailFailsBeforeQueryingDatabase() {
        for (String email : new String[] {null, "", " ", "a".repeat(151)}) {
            assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername(email));
        }
        verifyNoInteractions(users, roles);
    }

    @Test
    void userLoadingHasReadOnlyTransactionBoundary() throws Exception {
        var transaction = DatabaseUserDetailsService.class
            .getMethod("loadUserByUsername", String.class).getAnnotation(Transactional.class);
        assertNotNull(transaction);
        assertTrue(transaction.readOnly());
    }

    private void prepareUser(UserStatus status) {
        User user = new User();
        user.setId(41L);
        user.setUsername("fixture-user");
        user.setEmail("fixture@example.test");
        // Not a real credential. Authentication is tested with BCrypt in SecurityConfigTests.
        user.setPasswordHash("test-only-placeholder");
        user.setStatus(status);
        when(users.findByEmail("fixture@example.test")).thenReturn(Optional.of(user));
        when(roles.findRoleNamesByUserId(41L)).thenReturn(List.of());
    }
}
