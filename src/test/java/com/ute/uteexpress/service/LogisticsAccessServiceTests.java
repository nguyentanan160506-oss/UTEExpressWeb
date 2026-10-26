package com.ute.uteexpress.service;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ute.uteexpress.shipment.ShipmentFixtures.*;

class LogisticsAccessServiceTests {
    private final UserRepository users = mock(UserRepository.class);
    private final UserRoleRepository roles = mock(UserRoleRepository.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final ManagerBranchRepository managers = mock(ManagerBranchRepository.class);
    private final BranchRepository branches = mock(BranchRepository.class);
    private final ShipmentAssignmentRepository assignments = mock(ShipmentAssignmentRepository.class);
    private final LogisticsAccessService service = new LogisticsAccessService(users, roles, employees, managers, branches, assignments, mock(jakarta.persistence.EntityManager.class));
    private User actor;
    private Employee employee;
    private Shipment shipment;
    private ShipmentAssignment assignment;

    @BeforeEach void setup() {
        actor = owner();
        authenticate("ROLE_SHIPPER");
        when(users.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(roles.findRoleNamesByUserId(actor.getId())).thenReturn(List.of("ROLE_SHIPPER"));
        shipment = shipment(); shipment.setShipmentMethod(ShipmentMethod.HOME_PICKUP);
        employee = new Employee(); employee.setId(21L); employee.setUser(actor);
        employee.setStatus(EmployeeStatus.ACTIVE); employee.setPosition(EmployeePosition.COURIER_DRIVER);
        employee.setBranch(shipment.getOriginBranch());
        when(employees.findByUserId(actor.getId())).thenReturn(Optional.of(employee));
        assignment = new ShipmentAssignment(); assignment.setEmployee(employee); assignment.setShipment(shipment);
        assignment.setAssignmentType(AssignmentType.PICKUP); assignment.setStatus(AssignmentStatus.ASSIGNED);
        when(assignments.findByShipmentIdAndEmployeeIdAndAssignmentTypeAndStatusIn(
            31L, 21L, AssignmentType.PICKUP, LogisticsAccessService.ACTIVE_ASSIGNMENTS)).thenReturn(List.of(assignment));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            actor.getEmail(), null, List.of(new SimpleGrantedAuthority(role))));
    }
    private void denied() {
        assertThrows(AccessDeniedException.class, () -> service.requireAssignedShipper(31L, AssignmentType.PICKUP));
    }

    @Test void activeLinkedCourierWithActualAssignmentIsAccepted() {
        var result = service.requireAssignedShipper(31L, AssignmentType.PICKUP);
        assertSame(actor, result.actor()); assertSame(employee, result.employee()); assertSame(assignment, result.assignment());
    }
    @Test void missingEmployeeLinkIsDenied() {
        when(employees.findByUserId(actor.getId())).thenReturn(Optional.empty()); denied();
        verifyNoInteractions(assignments);
    }
    @Test void wrongUserLinkIsDenied() {
        User other = owner(); other.setId(99L); employee.setUser(other); denied();
    }
    @Test void employeeWithoutUserIsDenied() { employee.setUser(null); denied(); }
    @ParameterizedTest @EnumSource(value = EmployeeStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "ACTIVE")
    void inactiveEmployeeIsDenied(EmployeeStatus status) { employee.setStatus(status); denied(); }
    @ParameterizedTest @EnumSource(value = EmployeePosition.class, mode = EnumSource.Mode.EXCLUDE, names = "COURIER_DRIVER")
    void nonCourierEmployeeIsDenied(EmployeePosition position) { employee.setPosition(position); denied(); }
    @ParameterizedTest @EnumSource(value = UserStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "ACTIVE")
    void inactiveAccountIsDenied(UserStatus status) { actor.setStatus(status); denied(); }
    @Test void revokedRoleInDatabaseIsDeniedEvenWithOldSession() {
        when(roles.findRoleNamesByUserId(actor.getId())).thenReturn(List.of("ROLE_USER")); denied();
    }
    @Test void databaseRoleDoesNotReplaceRequiredSessionAuthority() { authenticate("ROLE_USER"); denied(); }
    @Test void anonymousCannotSupplyActor() { SecurityContextHolder.clearContext(); denied(); verifyNoInteractions(users); }
    @Test void unassignedShipmentIsDenied() {
        when(assignments.findByShipmentIdAndEmployeeIdAndAssignmentTypeAndStatusIn(
            31L, 21L, AssignmentType.PICKUP, LogisticsAccessService.ACTIVE_ASSIGNMENTS)).thenReturn(List.of()); denied();
    }
    @Test void ambiguousAssignmentsAreDenied() {
        when(assignments.findByShipmentIdAndEmployeeIdAndAssignmentTypeAndStatusIn(
            31L, 21L, AssignmentType.PICKUP, LogisticsAccessService.ACTIVE_ASSIGNMENTS)).thenReturn(List.of(assignment, assignment)); denied();
    }
    @Test void assignmentForDifferentEmployeeIsDenied() {
        Employee other = new Employee(); other.setId(99L); assignment.setEmployee(other); denied();
    }
    @Test void assignmentForDifferentShipmentIsDenied() {
        Shipment other = shipment(); other.setId(99L); assignment.setShipment(other); denied();
    }
    @ParameterizedTest @EnumSource(value = AssignmentStatus.class, names = {"COMPLETED", "FAILED", "CANCELLED"})
    void inactiveAssignmentIsDenied(AssignmentStatus status) { assignment.setStatus(status); denied(); }
    @Test void completedAssignmentCannotBeReused() { assignment.setCompletedAt(LocalDateTime.now()); denied(); }
    @Test void otherBranchIsDenied() { employee.setBranch(branch(99L)); denied(); }
    @Test void inactiveBranchIsDenied() { employee.getBranch().setStatus(BranchStatus.INACTIVE); denied(); }
    @Test void legacyShipmentCannotBeAssumedToBeHomePickup() { shipment.setShipmentMethod(null); denied(); }
    @Test void dropoffHasNoHomePickupTask() { shipment.setShipmentMethod(ShipmentMethod.BRANCH_DROPOFF); denied(); }
    @Test void deliveryAssignmentUsesDestinationBranch() {
        assignment.setAssignmentType(AssignmentType.DELIVERY); employee.setBranch(shipment.getDestinationBranch());
        when(assignments.findByShipmentIdAndEmployeeIdAndAssignmentTypeAndStatusIn(
            31L, 21L, AssignmentType.DELIVERY, LogisticsAccessService.ACTIVE_ASSIGNMENTS)).thenReturn(List.of(assignment));
        assertSame(actor, service.requireAssignedShipper(31L, AssignmentType.DELIVERY).actor());
    }
    @Test void transferRequiresFutureTripModel() {
        assertThrows(LogisticsRuleException.class, () -> service.requireAssignedShipper(31L, AssignmentType.TRANSFER));
        verifyNoInteractions(assignments);
    }
    @Test void managerMustHaveActiveBranchMembership() {
        authenticate("ROLE_MANAGER"); when(roles.findRoleNamesByUserId(actor.getId())).thenReturn(List.of("ROLE_MANAGER"));
        when(branches.findByIdAndStatus(11L, BranchStatus.ACTIVE)).thenReturn(Optional.of(branch(11L)));
        assertThrows(AccessDeniedException.class, () -> service.requireManagerForBranch(11L));
        when(managers.existsByManagerIdAndBranchId(actor.getId(), 11L)).thenReturn(true);
        assertSame(actor, service.requireManagerForBranch(11L));
        assertThrows(AccessDeniedException.class, () -> service.requireManagerForBranch(12L));
    }

    private void managerScope() {
        authenticate("ROLE_MANAGER");
        when(roles.findRoleNamesByUserId(actor.getId())).thenReturn(List.of("ROLE_MANAGER"));
        when(branches.findByIdAndStatus(11L, BranchStatus.ACTIVE)).thenReturn(Optional.of(branch(11L)));
        when(managers.existsByManagerIdAndBranchId(actor.getId(), 11L)).thenReturn(true);
    }
    private void candidate() {
        managerScope();
        User shipper = owner(); shipper.setId(2L); employee.setUser(shipper);
        when(roles.findRoleNamesByUserId(2L)).thenReturn(List.of("ROLE_SHIPPER"));
        when(employees.findByIdAndBranchId(21L, 11L)).thenReturn(Optional.of(employee));
    }
    @Test void managerCanSelectOnlyActiveLinkedDatabaseShipperInOriginBranch() {
        candidate(); assertSame(employee, service.requireAssignableShipper(21L, 11L));
        when(employees.findByIdAndBranchId(21L, 11L)).thenReturn(Optional.empty());
        assertThrows(LogisticsRuleException.class, () -> service.requireAssignableShipper(21L, 11L));
    }
    @Test void foreignBranchCourierCannotBeAssignedEvenIfRepositoryReturnsIt() {
        candidate(); employee.setBranch(branch(99L));
        assertThrows(LogisticsRuleException.class, () -> service.requireAssignableShipper(21L, 11L));
    }
    @ParameterizedTest @EnumSource(value = EmployeeStatus.class, names = {"ON_LEAVE", "INACTIVE"})
    void inactiveCourierCannotBeSelected(EmployeeStatus status) {
        candidate(); employee.setStatus(status);
        assertThrows(LogisticsRuleException.class, () -> service.requireAssignableShipper(21L, 11L));
    }
    @ParameterizedTest @EnumSource(value = UserStatus.class, names = {"LOCKED", "UNVERIFIED"})
    void disabledShipperAccountCannotBeSelected(UserStatus status) {
        candidate(); employee.getUser().setStatus(status);
        assertThrows(LogisticsRuleException.class, () -> service.requireAssignableShipper(21L, 11L));
    }
    @Test void courierNeedsActualRoleAndUserLink() {
        candidate(); when(roles.findRoleNamesByUserId(2L)).thenReturn(List.of("ROLE_USER"));
        assertThrows(LogisticsRuleException.class, () -> service.requireAssignableShipper(21L, 11L));
        employee.setUser(null); assertThrows(LogisticsRuleException.class, () -> service.requireAssignableShipper(21L, 11L));
    }
    @Test void intakeRequiresLinkedActiveDispatcherOrWarehouseStaffInManagerScope() {
        managerScope(); employee.setPosition(EmployeePosition.DISPATCHER);
        assertSame(employee, service.requireIntakeEmployee(11L));
        employee.setPosition(EmployeePosition.WAREHOUSE_STAFF); assertSame(employee, service.requireIntakeEmployee(11L));
        employee.setPosition(EmployeePosition.COURIER_DRIVER);
        assertThrows(AccessDeniedException.class, () -> service.requireIntakeEmployee(11L));
    }
    @Test void managerWithoutEmployeeCannotReceivePhysicalPackage() {
        managerScope(); when(employees.findByUserId(actor.getId())).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> service.requireIntakeEmployee(11L));
    }
    @Test void intakeRejectsInactiveOrForeignEmployeeProfile() {
        managerScope(); employee.setPosition(EmployeePosition.DISPATCHER); employee.setStatus(EmployeeStatus.INACTIVE);
        assertThrows(AccessDeniedException.class, () -> service.requireIntakeEmployee(11L));
        employee.setStatus(EmployeeStatus.ACTIVE); employee.setBranch(branch(99L));
        assertThrows(AccessDeniedException.class, () -> service.requireIntakeEmployee(11L));
    }
}
