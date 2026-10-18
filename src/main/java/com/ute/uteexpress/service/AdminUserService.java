package com.ute.uteexpress.service;

import com.ute.uteexpress.dto.admin.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.entity.id.UserRoleId;
import com.ute.uteexpress.enums.UserStatus;
import com.ute.uteexpress.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

@Service
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserService {
    private static final String ADMIN = "ROLE_ADMIN";
    private static final Set<String> EDITABLE_ROLES = Set.of("ROLE_USER", "ROLE_MANAGER", "ROLE_SHIPPER", ADMIN);
    private final UserRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository memberships;

    public AdminUserService(UserRepository users, RoleRepository roles, UserRoleRepository memberships) {
        this.users = users;
        this.roles = roles;
        this.memberships = memberships;
    }

    @Transactional(readOnly = true)
    public AdminDashboard dashboard() {
        requireActor(false);
        Map<UserStatus, Long> counts = new EnumMap<>(UserStatus.class);
        users.countByStatus().forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        long active = counts.getOrDefault(UserStatus.ACTIVE, 0L);
        long unverified = counts.getOrDefault(UserStatus.UNVERIFIED, 0L);
        long locked = counts.getOrDefault(UserStatus.LOCKED, 0L);
        return new AdminDashboard(active + unverified + locked, active, unverified, locked);
    }

    @Transactional(readOnly = true)
    public Page<AdminUserView> list(@Size(max = 150) String search, UserStatus status, Pageable requested) {
        User actor = requireActor(false);
        Pageable page = PageRequest.of(Math.max(0, requested.getPageNumber()),
            Math.max(1, Math.min(100, requested.getPageSize())), Sort.by("id").descending());
        Page<User> result = users.findAll(searchSpecification(search, status), page);
        Map<Long, List<AdminRoleView>> userRoles = roleViews(result.getContent().stream().map(User::getId).toList());
        return result.map(user -> view(user, userRoles.getOrDefault(user.getId(), List.of()), actor.getId()));
    }

    @Transactional(readOnly = true)
    public AdminUserView detail(@Positive Long id) {
        User actor = requireActor(false);
        User user = users.findById(id).orElseThrow(this::notFound);
        return view(user, roleViews(List.of(id)).getOrDefault(id, List.of()), actor.getId());
    }

    @Transactional(readOnly = true)
    public List<AdminRoleView> editableRoles() {
        requireActor(false);
        return availableRoles().stream().map(role -> new AdminRoleView(role.getId(), role.getName())).toList();
    }

    @Transactional
    public void lock(@Positive Long id) {
        User actor = beginMutation();
        User target = users.findByIdForUpdate(id).orElseThrow(this::notFound);
        if (target.getId().equals(actor.getId())) {
            throw new AdminRuleException("Bạn không thể tự khóa tài khoản của mình.");
        }
        if (target.getStatus() != UserStatus.ACTIVE) {
            throw new AdminRuleException("Chỉ có thể khóa tài khoản ACTIVE. Không khóa tài khoản chưa xác minh.");
        }
        protectLastAdmin(target);
        target.setStatus(UserStatus.LOCKED);
        users.save(target);
    }

    @Transactional
    public void unlock(@Positive Long id) {
        beginMutation();
        User target = users.findByIdForUpdate(id).orElseThrow(this::notFound);
        if (target.getStatus() != UserStatus.LOCKED) {
            throw new AdminRuleException("Chỉ có thể mở khóa tài khoản LOCKED. Tài khoản UNVERIFIED phải xác minh email.");
        }
        target.setStatus(UserStatus.ACTIVE);
        users.save(target);
    }

    @Transactional
    public void updateRoles(@Positive Long id, @Valid RoleUpdateForm form) {
        User actor = beginMutation();
        User target = users.findByIdForUpdate(id).orElseThrow(this::notFound);
        List<Role> allowed = availableRoles();
        Set<Long> allowedIds = allowed.stream().map(Role::getId).collect(Collectors.toSet());
        if (form.getRoleIds() == null || form.getRoleIds().size() > EDITABLE_ROLES.size()
                || !allowedIds.containsAll(form.getRoleIds())) {
            throw new AdminRuleException("Yêu cầu chứa quyền không được phép hoặc không tồn tại.");
        }
        Set<Long> selected = new HashSet<>(form.getRoleIds());
        List<UserRoleRepository.RoleSummary> current = memberships.findRoleSummaries(List.of(id));
        Set<Long> currentIds = current.stream().map(UserRoleRepository.RoleSummary::getRoleId)
            .collect(Collectors.toSet());
        Role adminRole = allowed.stream().filter(role -> ADMIN.equals(role.getName())).findFirst()
            .orElseThrow(() -> new AdminRuleException("Quyền ADMIN chưa được cấu hình."));
        if (target.getId().equals(actor.getId()) && !selected.contains(adminRole.getId())) {
            throw new AdminRuleException("Bạn không thể tự loại bỏ quyền ADMIN của mình.");
        }
        if (currentIds.contains(adminRole.getId()) && !selected.contains(adminRole.getId())) {
            protectLastAdmin(target);
        }
        // Preserve any pre-existing role outside this module's explicit allowlist.
        for (Long currentId : currentIds) {
            if (allowedIds.contains(currentId) && !selected.contains(currentId)) {
                memberships.deleteById(new UserRoleId(id, currentId));
            }
        }
        for (Role role : allowed) {
            if (selected.contains(role.getId()) && !currentIds.contains(role.getId())) {
                UserRole membership = new UserRole();
                membership.setId(new UserRoleId(id, role.getId()));
                membership.setUser(target);
                membership.setRole(role);
                memberships.save(membership);
            }
        }
    }

    private User beginMutation() {
        // Shared database mutex, not a JVM lock: every Admin mutation uses this exact order.
        roles.findByNameForUpdate(ADMIN)
            .orElseThrow(() -> new AdminRuleException("Quyền ADMIN chưa được cấu hình."));
        return requireActor(true);
    }

    private User requireActor(boolean forUpdate) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream().noneMatch(authority -> ADMIN.equals(authority.getAuthority()))) {
            throw new AccessDeniedException("Forbidden");
        }
        User actor = (forUpdate ? users.findByEmailForUpdate(authentication.getName())
            : users.findByEmail(authentication.getName())).orElseThrow(() -> new AccessDeniedException("Forbidden"));
        // Re-check database state/roles: a stale session cannot retain Admin access after revocation.
        if (actor.getStatus() != UserStatus.ACTIVE
                || !memberships.findRoleNamesByUserId(actor.getId()).contains(ADMIN)) {
            throw new AccessDeniedException("Forbidden");
        }
        return actor;
    }

    private void protectLastAdmin(User target) {
        if (target.getStatus() == UserStatus.ACTIVE
                && memberships.findRoleNamesByUserId(target.getId()).contains(ADMIN)
                && memberships.countUsersWithRoleAndStatus(ADMIN, UserStatus.ACTIVE) <= 1) {
            throw new AdminRuleException("Không thể vô hiệu hóa tài khoản ADMIN hoạt động cuối cùng.");
        }
    }

    private List<Role> availableRoles() { return roles.findByNameInOrderByNameAsc(EDITABLE_ROLES); }

    private Map<Long, List<AdminRoleView>> roleViews(List<Long> ids) {
        if (ids.isEmpty()) { return Map.of(); }
        Map<Long, List<AdminRoleView>> result = new HashMap<>();
        for (var row : memberships.findRoleSummaries(ids)) {
            result.computeIfAbsent(row.getUserId(), key -> new ArrayList<>())
                .add(new AdminRoleView(row.getRoleId(), row.getRoleName()));
        }
        return result;
    }

    private AdminUserView view(User user, List<AdminRoleView> roleViews, Long actorId) {
        return new AdminUserView(user.getId(), user.getUsername(), user.getEmail(), user.getFullName(),
            user.getPhoneNumber(), user.getStatus(), user.getCreatedAt(), user.getUpdatedAt(),
            roleViews, user.getId().equals(actorId));
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản.");
    }

    static Specification<User> searchSpecification(String search, UserStatus status) {
        return (root, query, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (status != null) { predicates.add(builder.equal(root.get("status"), status)); }
            if (search != null && !search.isBlank()) {
                String text = search.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\")
                    .replace("%", "\\%").replace("_", "\\_").replace("[", "\\[");
                String pattern = "%" + text + "%";
                predicates.add(builder.or(
                    builder.like(builder.lower(root.get("email")), pattern, '\\'),
                    builder.like(builder.lower(root.get("username")), pattern, '\\'),
                    builder.like(builder.lower(root.get("fullName")), pattern, '\\'),
                    builder.like(root.get("phoneNumber"), pattern, '\\')));
            }
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
