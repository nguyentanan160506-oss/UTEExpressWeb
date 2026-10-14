package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.UserRole;
import com.ute.uteexpress.entity.id.UserRoleId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    @Query("select membership.role.name from UserRole membership where membership.user.id = :userId")
    List<String> findRoleNamesByUserId(@Param("userId") Long userId);
}
