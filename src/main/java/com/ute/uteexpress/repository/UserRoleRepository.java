package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.UserRole;
import com.ute.uteexpress.entity.id.UserRoleId;
import java.util.List;
import java.util.Collection;
import com.ute.uteexpress.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    @Query("select membership.role.name from UserRole membership where membership.user.id = :userId")
    List<String> findRoleNamesByUserId(@Param("userId") Long userId);

    @Query("select m.user.id as userId, m.role.id as roleId, m.role.name as roleName "
        + "from UserRole m where m.user.id in :userIds order by m.role.name")
    List<RoleSummary> findRoleSummaries(@Param("userIds") Collection<Long> userIds);

    @Query("select count(m) from UserRole m where m.role.name = :roleName and m.user.status = :status")
    long countUsersWithRoleAndStatus(@Param("roleName") String roleName, @Param("status") UserStatus status);

    interface RoleSummary {
        Long getUserId();
        Long getRoleId();
        String getRoleName();
    }
}
