package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.UserRole;
import com.ute.uteexpress.entity.id.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
}
