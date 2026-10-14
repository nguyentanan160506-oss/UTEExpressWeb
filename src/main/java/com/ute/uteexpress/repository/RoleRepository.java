package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.Role;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Role r where r.name = :name")
    Optional<Role> findByNameForUpdate(@Param("name") String name);

    List<Role> findByNameInOrderByNameAsc(Collection<String> names);
}
