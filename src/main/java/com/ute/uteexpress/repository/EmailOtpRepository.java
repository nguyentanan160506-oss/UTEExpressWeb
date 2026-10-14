package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.EmailOtp;
import com.ute.uteexpress.enums.OtpType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailOtpRepository extends JpaRepository<EmailOtp, Long> {

    Optional<EmailOtp> findFirstByEmailAndOtpTypeOrderByCreatedAtDescIdDesc(String email, OtpType otpType);

    long countByEmailAndOtpTypeAndCreatedAtGreaterThanEqual(String email, OtpType otpType,
        LocalDateTime createdAt);
}
