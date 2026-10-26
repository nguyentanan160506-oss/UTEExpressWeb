package com.ute.uteexpress.service;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import jakarta.validation.constraints.*;
import jakarta.persistence.EntityManager;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
// Callers may probe origin/destination or physical-staff capabilities and catch a denial.
// These read-only checks must not poison the caller's transaction; uncaught denial still
// rolls back the transactional business service that owns the write.
@Transactional(readOnly = true, noRollbackFor = AccessDeniedException.class)
public class LogisticsAccessService {
    public static final Set<AssignmentStatus> ACTIVE_ASSIGNMENTS = Set.of(AssignmentStatus.ASSIGNED, AssignmentStatus.IN_PROGRESS);
    private final UserRepository users;
    private final UserRoleRepository roles;
    private final EmployeeRepository employees;
    private final ManagerBranchRepository managerBranches;
    private final BranchRepository branches;
    private final ShipmentAssignmentRepository assignments;
    private final EntityManager entityManager;

    public LogisticsAccessService(UserRepository users, UserRoleRepository roles, EmployeeRepository employees,
            ManagerBranchRepository managerBranches, BranchRepository branches, ShipmentAssignmentRepository assignments, EntityManager entityManager) {
        this.users = users; this.roles = roles; this.employees = employees;
        this.managerBranches = managerBranches; this.branches = branches; this.assignments = assignments;
        this.entityManager = entityManager;
    }

    @PreAuthorize("hasRole('MANAGER')")
    public User requireManager() { return requireActor("ROLE_MANAGER"); }

    @PreAuthorize("hasRole('MANAGER')")
    public User requireManagerForBranch(@NotNull @Positive Long branchId) {
        User actor = requireActor("ROLE_MANAGER");
        if (branches.findByIdAndStatus(branchId, BranchStatus.ACTIVE).isEmpty()
                || !managerBranches.existsByManagerIdAndBranchId(actor.getId(), branchId)) {
            throw forbidden();
        }
        return actor;
    }

    @PreAuthorize("hasRole('MANAGER')")
    public Employee requireAssignableShipper(@NotNull @Positive Long employeeId, @NotNull @Positive Long branchId) {
        requireManagerForBranch(branchId);
        Employee employee = employees.findByIdAndBranchId(employeeId, branchId)
            .orElseThrow(() -> new LogisticsRuleException("Nhân viên không đủ điều kiện nhận nhiệm vụ lấy hàng."));
        refreshEmployee(employee);
        if (employee.getStatus() != EmployeeStatus.ACTIVE || employee.getPosition() != EmployeePosition.COURIER_DRIVER
                || employee.getBranch() == null || !branchId.equals(employee.getBranch().getId())
                || employee.getBranch().getStatus() != BranchStatus.ACTIVE || employee.getUser() == null
                || employee.getUser().getStatus() != UserStatus.ACTIVE
                || !roles.findRoleNamesByUserId(employee.getUser().getId()).contains("ROLE_SHIPPER")) {
            throw new LogisticsRuleException("Nhân viên không đủ điều kiện nhận nhiệm vụ lấy hàng.");
        }
        return employee;
    }

    @PreAuthorize("hasRole('MANAGER')")
    public Employee requireIntakeEmployee(@NotNull @Positive Long branchId) {
        User actor = requireManagerForBranch(branchId);
        Employee employee = employees.findByUserId(actor.getId()).orElseThrow(this::forbidden);
        refreshEmployee(employee);
        if (employee.getUser() == null || !actor.getId().equals(employee.getUser().getId())
                || employee.getStatus() != EmployeeStatus.ACTIVE || employee.getBranch() == null
                || !branchId.equals(employee.getBranch().getId()) || employee.getBranch().getStatus() != BranchStatus.ACTIVE
                || (employee.getPosition() != EmployeePosition.DISPATCHER && employee.getPosition() != EmployeePosition.WAREHOUSE_STAFF)) {
            throw forbidden();
        }
        return employee;
    }

    @PreAuthorize("hasRole('SHIPPER')")
    public ShipperProfile requireShipper() {
        User actor = requireActor("ROLE_SHIPPER");
        Employee employee = employees.findByUserId(actor.getId()).orElseThrow(this::forbidden);
        refreshEmployee(employee);
        if (employee.getUser() == null || !actor.getId().equals(employee.getUser().getId())
                || employee.getStatus() != EmployeeStatus.ACTIVE || employee.getPosition() != EmployeePosition.COURIER_DRIVER
                || employee.getBranch() == null || employee.getBranch().getStatus() != BranchStatus.ACTIVE) {
            throw forbidden();
        }
        return new ShipperProfile(actor, employee);
    }

    @PreAuthorize("hasRole('SHIPPER')")
    public AssignedShipper requireAssignedShipper(@NotNull @Positive Long shipmentId, @NotNull AssignmentType type) {
        ShipperProfile profile = requireShipper();
        User actor = profile.actor();
        Employee employee = profile.employee();
        // TRANSFER needs the trip/custody model of Commit 12, not just an employee role.
        if (type != AssignmentType.PICKUP && type != AssignmentType.DELIVERY) {
            throw new LogisticsRuleException("Nhiệm vụ trung chuyển cần dữ liệu chuyến và bàn giao của commit sau.");
        }
        var found = assignments.findByShipmentIdAndEmployeeIdAndAssignmentTypeAndStatusIn(
            shipmentId, employee.getId(), type, ACTIVE_ASSIGNMENTS);
        if (found.size() != 1) { throw forbidden(); }
        ShipmentAssignment assignment = found.getFirst();
        if (assignment.getEmployee() == null || !employee.getId().equals(assignment.getEmployee().getId())
                || assignment.getShipment() == null || !shipmentId.equals(assignment.getShipment().getId())
                || assignment.getAssignmentType() != type || !ACTIVE_ASSIGNMENTS.contains(assignment.getStatus())
                || assignment.getCompletedAt() != null) {
            throw forbidden();
        }
        Branch responsible = type == AssignmentType.PICKUP
            ? assignment.getShipment().getOriginBranch() : assignment.getShipment().getDestinationBranch();
        if (responsible == null || responsible.getStatus() != BranchStatus.ACTIVE
                || !employee.getBranch().getId().equals(responsible.getId())) { throw forbidden(); }
        if (type == AssignmentType.PICKUP && assignment.getShipment().getShipmentMethod() != ShipmentMethod.HOME_PICKUP) {
            throw forbidden(); // Legacy/BRANCH_DROPOFF orders cannot be treated as home pickup.
        }
        return new AssignedShipper(actor, employee, assignment);
    }

    private User requireActor(String requiredRole) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getAuthorities().stream()
                .noneMatch(authority -> requiredRole.equals(authority.getAuthority()))) { throw forbidden(); }
        User actor = users.findByEmail(authentication.getName()).orElseThrow(this::forbidden);
        // Identity may have been loaded before waiting for the Shipment lock.
        // Another entity query alone would reuse stale first-level-cache state.
        entityManager.refresh(actor);
        if (actor.getStatus() != UserStatus.ACTIVE
                || !roles.findRoleNamesByUserId(actor.getId()).contains(requiredRole)) { throw forbidden(); }
        return actor;
    }

    private void refreshEmployee(Employee employee) {
        entityManager.refresh(employee);
        if (employee.getUser() != null) { entityManager.refresh(employee.getUser()); }
        if (employee.getBranch() != null) { entityManager.refresh(employee.getBranch()); }
    }

    private AccessDeniedException forbidden() { return new AccessDeniedException("Forbidden"); }
    /** Internal context for future operation services, never a web response DTO. */
    public record AssignedShipper(User actor, Employee employee, ShipmentAssignment assignment) { }
    public record ShipperProfile(User actor, Employee employee) { }
}
