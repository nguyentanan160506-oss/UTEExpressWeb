package com.ute.uteexpress.security;

import com.ute.uteexpress.entity.User;
import com.ute.uteexpress.enums.UserStatus;
import com.ute.uteexpress.repository.UserRepository;
import com.ute.uteexpress.repository.UserRoleRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository users;
    private final UserRoleRepository userRoles;

    public DatabaseUserDetailsService(UserRepository users, UserRoleRepository userRoles) {
        this.users = users;
        this.userRoles = userRoles;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = users.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // Scalar query avoids dereferencing lazy UserRole.role outside the transaction.
        // Preserve database authorities exactly; never infer or grant a default role.
        var authorities = userRoles.findRoleNamesByUserId(user.getId()).stream()
            .distinct()
            .map(SimpleGrantedAuthority::new)
            .toList();

        return org.springframework.security.core.userdetails.User
            .withUsername(user.getUsername())
            .password(user.getPasswordHash())
            .authorities(authorities)
            .disabled(user.getStatus() != UserStatus.ACTIVE)
            .accountLocked(user.getStatus() == UserStatus.LOCKED)
            .build();
    }
}
