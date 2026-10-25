package com.ute.uteexpress.service.operations;

import com.ute.uteexpress.entity.User;
import com.ute.uteexpress.enums.UserStatus;
import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.service.notifications.NotificationIdentity;
import jakarta.persistence.EntityManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true) @PreAuthorize("hasRole('USER')")
public class CustomerSupportAccess {
    private final NotificationIdentity identity;
    private final UserRepository users;
    private final UserRoleRepository roles;
    private final EntityManager em;
    public CustomerSupportAccess(NotificationIdentity identity, UserRepository users, UserRoleRepository roles, EntityManager em) {
        this.identity=identity; this.users=users; this.roles=roles; this.em=em;
    }
    public User current() {
        User u=identity.current(); check(u); return u;
    }
    /** Account lock serializes create retries and prevents a concurrent account-status change during a write. */
    @Transactional
    public User lockCurrent() {
        User u=current();
        u=users.findByIdForUpdate(u.getId()).orElseThrow(()->new AccessDeniedException("Forbidden"));
        // Identity may already have populated the persistence context before waiting for a root lock.
        em.refresh(u); check(u); return u;
    }
    private void check(User u) {
        if(u.getStatus()!=UserStatus.ACTIVE || !roles.findRoleNamesByUserId(u.getId()).contains("ROLE_USER"))
            throw new AccessDeniedException("Forbidden");
    }
}
