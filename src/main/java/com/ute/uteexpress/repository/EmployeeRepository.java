package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    @EntityGraph(attributePaths = {"user", "branch"})
    Optional<Employee> findByUserId(Long userId);
}
