package com.ute.uteexpress.service.reports;

import com.ute.uteexpress.entity.User;
import com.ute.uteexpress.enums.UserStatus;
import com.ute.uteexpress.repository.*;
import jakarta.persistence.EntityManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true) @PreAuthorize("hasRole('ADMIN')")
public class AdminReportingAccess {
    private final UserRepository users;private final UserRoleRepository roles;private final EntityManager em;
    public AdminReportingAccess(UserRepository users,UserRoleRepository roles,EntityManager em){this.users=users;this.roles=roles;this.em=em;}
    public User requireAdmin(){
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !auth.isAuthenticated() || auth.getAuthorities().stream().noneMatch(a->"ROLE_ADMIN".equals(a.getAuthority())))throw denied();
        User u=users.findByEmail(auth.getName()).orElseThrow(this::denied);em.refresh(u);
        if(u.getStatus()!=UserStatus.ACTIVE || !roles.findRoleNamesByUserId(u.getId()).contains("ROLE_ADMIN"))throw denied();
        return u;
    }
    private AccessDeniedException denied(){return new AccessDeniedException("Forbidden");}
}
