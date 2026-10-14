package com.ute.uteexpress.service;

import com.ute.uteexpress.dto.admin.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.entity.id.UserRoleId;
import com.ute.uteexpress.enums.UserStatus;
import com.ute.uteexpress.repository.*;
import jakarta.persistence.criteria.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminUserServiceTests {
    UserRepository users;
    RoleRepository roles;
    UserRoleRepository memberships;
    AdminUserService service;
    User actor;
    User target;
    Role userRole;
    Role managerRole;
    Role adminRole;

    @BeforeEach void setup() {
        users = mock(UserRepository.class);
        roles = mock(RoleRepository.class);
        memberships = mock(UserRoleRepository.class);
        service = new AdminUserService(users, roles, memberships);
        actor = user(1L, "admin-fixture@example.test", UserStatus.ACTIVE);
        target = user(2L, "target-fixture@example.test", UserStatus.ACTIVE);
        authenticate(actor.getEmail(), "ROLE_ADMIN");
        when(users.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(users.findByEmailForUpdate(actor.getEmail())).thenReturn(Optional.of(actor));
        when(users.findById(2L)).thenReturn(Optional.of(target));
        when(users.findByIdForUpdate(2L)).thenReturn(Optional.of(target));
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(actor));
        when(memberships.findRoleNamesByUserId(1L)).thenReturn(List.of("ROLE_ADMIN"));
        when(memberships.findRoleNamesByUserId(2L)).thenReturn(List.of("ROLE_USER"));
        userRole = role(11L, "ROLE_USER");
        managerRole = role(12L, "ROLE_MANAGER");
        adminRole = role(13L, "ROLE_ADMIN");
        when(roles.findByNameForUpdate("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        when(roles.findByNameInOrderByNameAsc(anyCollection()))
            .thenReturn(List.of(adminRole, managerRole, userRole));
        when(memberships.findRoleSummaries(List.of(2L)))
            .thenReturn(List.of(summary(2L, 11L, "ROLE_USER")));
        when(memberships.countUsersWithRoleAndStatus("ROLE_ADMIN", UserStatus.ACTIVE)).thenReturn(2L);
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void dashboardUsesActualGroupedCountsAndCorrectTotal() {
        when(users.countByStatus()).thenReturn(List.of(count(UserStatus.ACTIVE, 7),
            count(UserStatus.LOCKED, 2), count(UserStatus.UNVERIFIED, 3)));
        assertEquals(new AdminDashboard(12, 7, 3, 2), service.dashboard());
        verify(users).countByStatus();
    }

    @Test void listUsesPageableAndDetachedViewsWithRolesWithoutSecrets() {
        String secretMarker = UUID.randomUUID().toString();
        target.setPasswordHash(secretMarker);
        when(users.findAll(org.mockito.ArgumentMatchers.<Specification<User>>any(), any(Pageable.class)))
            .thenAnswer(invocation -> new PageImpl<>(List.of(target), invocation.getArgument(1), 41));
        var result = service.list("target", UserStatus.ACTIVE,
            PageRequest.of(2, 20, Sort.by("passwordHash")));
        assertEquals(41, result.getTotalElements());
        assertEquals(2, result.getNumber());
        assertEquals(List.of(new AdminRoleView(11L, "ROLE_USER")), result.getContent().getFirst().roles());
        assertFalse(result.getContent().getFirst().toString().contains(secretMarker));
        var page = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(users).findAll(org.mockito.ArgumentMatchers.<Specification<User>>any(), page.capture());
        assertEquals(Sort.by("id").descending(), page.getValue().getSort());
    }

    @Test void detailContainsOnlySafeFieldsAndMissingUserIs404() {
        var detail = service.detail(2L);
        assertEquals(target.getEmail(), detail.email());
        assertFalse(detail.self());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.detail(999L))
            .getStatusCode().value());
        assertTrue(Arrays.stream(AdminUserView.class.getRecordComponents())
            .noneMatch(field -> field.getName().toLowerCase(Locale.ROOT).contains("password")
                || field.getName().toLowerCase(Locale.ROOT).contains("otp")));
    }

    @Test void lockAndUnlockOnlyAllowActiveLockedTransitions() {
        service.lock(2L);
        assertEquals(UserStatus.LOCKED, target.getStatus());
        service.unlock(2L);
        assertEquals(UserStatus.ACTIVE, target.getStatus());
        verify(users, times(2)).save(target);
    }

    @Test void selfLockAndSelfAdminRemovalAreDenied() {
        assertThrows(AdminRuleException.class, () -> service.lock(1L));
        RoleUpdateForm form = form(11L);
        assertThrows(AdminRuleException.class, () -> service.updateRoles(1L, form));
        verify(users, never()).save(actor);
        verify(memberships, never()).deleteById(any());
    }

    @Test void lastActiveAdminCannotBeLockedOrLoseAdminRole() {
        when(memberships.findRoleNamesByUserId(2L)).thenReturn(List.of("ROLE_ADMIN"));
        when(memberships.findRoleSummaries(List.of(2L))).thenReturn(List.of(summary(2L, 13L, "ROLE_ADMIN")));
        when(memberships.countUsersWithRoleAndStatus("ROLE_ADMIN", UserStatus.ACTIVE)).thenReturn(1L);
        assertThrows(AdminRuleException.class, () -> service.lock(2L));
        assertThrows(AdminRuleException.class, () -> service.updateRoles(2L, form(11L)));
        assertEquals(UserStatus.ACTIVE, target.getStatus());
        verify(memberships, never()).deleteById(any());
    }

    @Test void unverifiedCannotBeLockedOrActivatedThroughUnlock() {
        target.setStatus(UserStatus.UNVERIFIED);
        assertThrows(AdminRuleException.class, () -> service.lock(2L));
        assertThrows(AdminRuleException.class, () -> service.unlock(2L));
        assertEquals(UserStatus.UNVERIFIED, target.getStatus());
        verify(users, never()).save(any());
    }

    @Test void repeatedOrInvalidStatusTransitionsAreRejected() {
        assertThrows(AdminRuleException.class, () -> service.unlock(2L));
        target.setStatus(UserStatus.LOCKED);
        assertThrows(AdminRuleException.class, () -> service.lock(2L));
    }

    @Test void validRoleChangeDeletesOnlyRemovedRolesAndAddsSelectedExistingRoles() {
        service.updateRoles(2L, form(12L));
        verify(memberships).deleteById(new UserRoleId(2L, 11L));
        var added = org.mockito.ArgumentCaptor.forClass(UserRole.class);
        verify(memberships).save(added.capture());
        assertEquals(new UserRoleId(2L, 12L), added.getValue().getId());
        assertSame(managerRole, added.getValue().getRole());
        verify(roles, never()).save(any());
    }

    @Test void adminCanGrantExistingAdminRoleToAnotherAccountExplicitly() {
        service.updateRoles(2L, form(11L, 13L));
        var added = org.mockito.ArgumentCaptor.forClass(UserRole.class);
        verify(memberships).save(added.capture());
        assertEquals(new UserRoleId(2L, 13L), added.getValue().getId());
        verify(memberships, never()).deleteById(any());
    }

    @Test void unsupportedOrNonexistentRoleIdsAreRejectedBeforeWriting() {
        assertThrows(AdminRuleException.class, () -> service.updateRoles(2L, form(999L)));
        assertThrows(AdminRuleException.class, () -> service.updateRoles(2L, form(11L, 12L, 13L, 999L)));
        verify(memberships, never()).save(any());
        verify(memberships, never()).deleteById(any());
    }

    @Test void rolesOutsideAllowlistArePreservedAndRetainedAssignmentsAreNotRewritten() {
        when(memberships.findRoleSummaries(List.of(2L))).thenReturn(List.of(
            summary(2L, 11L, "ROLE_USER"), summary(2L, 99L, "ROLE_EXTERNAL")));
        service.updateRoles(2L, form(11L));
        verify(memberships, never()).deleteById(any());
        verify(memberships, never()).save(any());
    }

    @Test void staleSessionCannotAccessAdminAfterRoleRevocationOrLock() {
        when(memberships.findRoleNamesByUserId(1L)).thenReturn(List.of("ROLE_USER"));
        assertThrows(AccessDeniedException.class, () -> service.dashboard());
        assertThrows(AccessDeniedException.class, () -> service.lock(2L));
        when(memberships.findRoleNamesByUserId(1L)).thenReturn(List.of("ROLE_ADMIN"));
        actor.setStatus(UserStatus.LOCKED);
        assertThrows(AccessDeniedException.class, () -> service.detail(2L));
        assertThrows(AccessDeniedException.class, () -> service.updateRoles(2L, form(12L)));
        verify(users, never()).save(any());
    }

    @Test void guestOrUserAuthoritiesCannotInvokeServiceDirectly() {
        authenticate(actor.getEmail(), "ROLE_USER");
        assertThrows(AccessDeniedException.class, () -> service.dashboard());
        SecurityContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, () -> service.lock(2L));
        verify(users, never()).save(any());
    }

    @Test void mutationAcquiresSharedDatabaseMutexBeforeActorAndTarget() {
        service.lock(2L);
        var order = inOrder(roles, users);
        order.verify(roles).findByNameForUpdate("ROLE_ADMIN");
        order.verify(users).findByEmailForUpdate(actor.getEmail());
        order.verify(users).findByIdForUpdate(2L);
    }

    @Test void missingAdminRoleConfigurationPreventsMutation() {
        when(roles.findByNameForUpdate("ROLE_ADMIN")).thenReturn(Optional.empty());
        assertThrows(AdminRuleException.class, () -> service.lock(2L));
        verify(users, never()).findByIdForUpdate(any());
    }

    @SuppressWarnings("unchecked")
    @Test void searchCombinesStatusAndEscapedCaseInsensitivePredicates() {
        Root<User> root = mock(Root.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        jakarta.persistence.criteria.Path<String> email = mock(jakarta.persistence.criteria.Path.class);
        jakarta.persistence.criteria.Path<String> username = mock(jakarta.persistence.criteria.Path.class);
        jakarta.persistence.criteria.Path<String> fullName = mock(jakarta.persistence.criteria.Path.class);
        jakarta.persistence.criteria.Path<String> phone = mock(jakarta.persistence.criteria.Path.class);
        jakarta.persistence.criteria.Path<Object> status = mock(jakarta.persistence.criteria.Path.class);
        Expression<String> lowerEmail = mock(Expression.class);
        Expression<String> lowerUsername = mock(Expression.class);
        Expression<String> lowerName = mock(Expression.class);
        when(root.<String>get("email")).thenReturn(email);
        when(root.<String>get("username")).thenReturn(username);
        when(root.<String>get("fullName")).thenReturn(fullName);
        when(root.<String>get("phoneNumber")).thenReturn(phone);
        when(root.get("status")).thenReturn(status);
        when(builder.lower(email)).thenReturn(lowerEmail);
        when(builder.lower(username)).thenReturn(lowerUsername);
        when(builder.lower(fullName)).thenReturn(lowerName);
        AdminUserService.searchSpecification("  AB%_[  ", UserStatus.LOCKED).toPredicate(root, query, builder);
        String escaped = "%ab\\%\\_\\[%";
        verify(builder).equal(status, UserStatus.LOCKED);
        verify(builder).like(lowerEmail, escaped, '\\');
        verify(builder).like(lowerUsername, escaped, '\\');
        verify(builder).like(lowerName, escaped, '\\');
        verify(builder).like(phone, escaped, '\\');
    }

    static void authenticate(String email, String authority) {
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(email, null, List.of(new SimpleGrantedAuthority(authority))));
    }

    static User user(Long id, String email, UserStatus status) {
        User user = new User();
        user.setId(id); user.setEmail(email); user.setUsername("fixture" + id);
        user.setFullName("Tài khoản thử nghiệm");
        user.setPhoneNumber("0901234567"); user.setStatus(status);
        user.setCreatedAt(LocalDateTime.of(2026, 10, 15, 0, 0));
        user.setUpdatedAt(user.getCreatedAt());
        return user;
    }

    static Role role(Long id, String name) {
        Role role = new Role(); role.setId(id); role.setName(name); return role;
    }

    static RoleUpdateForm form(Long... ids) {
        RoleUpdateForm form = new RoleUpdateForm(); form.setRoleIds(Arrays.asList(ids)); return form;
    }

    static UserRoleRepository.RoleSummary summary(Long userId, Long roleId, String name) {
        return new UserRoleRepository.RoleSummary() {
            public Long getUserId() { return userId; }
            public Long getRoleId() { return roleId; }
            public String getRoleName() { return name; }
        };
    }

    static UserRepository.StatusCount count(UserStatus status, long total) {
        return new UserRepository.StatusCount() {
            public UserStatus getStatus() { return status; }
            public long getTotal() { return total; }
        };
    }
}
