package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ute.uteexpress.enums.BranchStatus;
import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long> {
    List<Branch> findByStatusOrderByNameAsc(BranchStatus status);
    Optional<Branch> findByIdAndStatus(Long id, BranchStatus status);
}
