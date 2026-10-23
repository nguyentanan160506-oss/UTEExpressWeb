package com.ute.uteexpress.service.notifications;

import com.ute.uteexpress.entity.User;
import com.ute.uteexpress.enums.UserStatus;
import com.ute.uteexpress.repository.*;
import java.security.Principal;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly = true)
public class NotificationIdentity {
    private static final Set<String> ROLES = Set.of("ROLE_USER", "ROLE_SHIPPER", "ROLE_MANAGER", "ROLE_ADMIN");
    private final UserRepository users;
    private final UserRoleRepository roles;
    public NotificationIdentity(UserRepository users, UserRoleRepository roles) { this.users = users; this.roles = roles; }
    public User current() { return require(SecurityContextHolder.getContext().getAuthentication()); }
    public User require(Principal principal) {
        if (!(principal instanceof Authentication a) || !a.isAuthenticated() || a instanceof AnonymousAuthenticationToken) { throw denied(); }
        User user = users.findByEmail(a.getName()).orElseThrow(this::denied);
        if (user.getStatus() != UserStatus.ACTIVE || roles.findRoleNamesByUserId(user.getId()).stream()
                .noneMatch(r -> ROLES.contains(r) && a.getAuthorities().stream().anyMatch(g -> r.equals(g.getAuthority())))) { throw denied(); }
        return user;
    }
    private AccessDeniedException denied() { return new AccessDeniedException("Forbidden"); }
}
