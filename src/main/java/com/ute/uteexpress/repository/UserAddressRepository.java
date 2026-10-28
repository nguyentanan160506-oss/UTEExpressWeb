package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {
    java.util.List<UserAddress> findByUserIdOrderByIdAsc(Long userId);
    java.util.Optional<UserAddress> findByIdAndUserId(Long id,Long userId);
}
