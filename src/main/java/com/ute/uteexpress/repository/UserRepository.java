package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
