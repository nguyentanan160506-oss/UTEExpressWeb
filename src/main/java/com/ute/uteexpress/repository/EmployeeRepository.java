package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
}
