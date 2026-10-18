package com.ute.uteexpress.service;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import jakarta.validation.constraints.*;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional(readOnly = true)
public class LogisticsAccessService {
    public static final Set<AssignmentStatus> ACTIVE_ASSIGNMENTS = Set.of(AssignmentStatus.ASSIGNED, AssignmentStatus.IN_PROGRESS);
    private final UserRepository users;
    private final UserRoleRepository roles;
    private final EmployeeRepository employees;
    private final ManagerBranchRepository managerBranches;
    private final BranchRepository branches;
    private final ShipmentAssignmentRepository assignments;

    public LogisticsAccessService(UserRepository users, UserRoleRepository roles, EmployeeRepository employees,
            ManagerBranchRepository managerBranches, BranchRepository branches, ShipmentAssignmentRepository assignments) {
        this.users = users; this.roles = roles; this.employees = employees;
        this.managerBranches = managerBranches; this.branches = branches; this.assignments = assignments;
    }

    @PreAuthorize("hasRole('MANAGER')")
    public User requireManagerForBranch(@NotNull @Positive Long branchId) {
        User actor = requireActor("ROLE_MANAGER");
        if (branches.findByIdAndStatus(branchId, BranchStatus.ACTIVE).isEmpty()
                || !managerBranches.existsByManagerIdAndBranchId(actor.getId(), branchId)) {
            throw forbidden();
        }
        return actor;
    }

    @PreAuthorize("hasRole('SHIPPER')")
    public AssignedShipper requireAssignedShipper(@NotNull @Positive Long shipmentId, @NotNull AssignmentType type) {
        User actor = requireActor("ROLE_SHIPPER");
        Employee employee = employees.findByUserId(actor.getId()).orElseThrow(this::forbidden);
        if (employee.getUser() == null || !actor.getId().equals(employee.getUser().getId())
                || employee.getStatus() != EmployeeStatus.ACTIVE || employee.getPosition() != EmployeePosition.COURIER_DRIVER
                || employee.getBranch() == null || employee.getBranch().getStatus() != BranchStatus.ACTIVE) {
            throw forbidden();
        }
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
        if (actor.getStatus() != UserStatus.ACTIVE
                || !roles.findRoleNamesByUserId(actor.getId()).contains(requiredRole)) { throw forbidden(); }
        return actor;
    }

    private AccessDeniedException forbidden() { return new AccessDeniedException("Forbidden"); }
    /** Internal context for future operation services, never a web response DTO. */
    public record AssignedShipper(User actor, Employee employee, ShipmentAssignment assignment) { }
}
