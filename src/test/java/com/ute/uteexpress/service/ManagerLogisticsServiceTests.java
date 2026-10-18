package com.ute.uteexpress.service;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import jakarta.persistence.criteria.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import static com.ute.uteexpress.manager.ManagerFixtures.*;
import static com.ute.uteexpress.shipment.ShipmentFixtures.branch;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagerLogisticsServiceTests {
    final LogisticsAccessService access = mock(LogisticsAccessService.class);
    final ManagerBranchRepository members = mock(ManagerBranchRepository.class);
    final ShipmentRepository shipments = mock(ShipmentRepository.class);
    final ShipmentEventRepository events = mock(ShipmentEventRepository.class);
    final ShipmentAssignmentRepository assignments = mock(ShipmentAssignmentRepository.class);
    final EmployeeRepository employees = mock(EmployeeRepository.class);
    final ShipmentIntakeRepository intakes = mock(ShipmentIntakeRepository.class);
    final ManagerLogisticsService service = new ManagerLogisticsService(access, members, shipments, events, assignments, employees, intakes);
    @BeforeEach void setup() { when(access.requireManager()).thenReturn(manager()); }
    @Test void multipleActiveManagedBranchesAreAvailableWithoutLoadingShipments() {
        when(members.findManagedBranches(1L, BranchStatus.ACTIVE)).thenReturn(List.of(branch(11L), branch(12L)));
        assertEquals(List.of(11L, 12L), service.branches().stream().map(b -> b.id()).toList()); verifyNoInteractions(shipments);
    }
    @Test void listUsesScopedQueryAndBoundedServerPaginationWithFixedSort() {
        when(shipments.findAll(org.mockito.ArgumentMatchers.<Specification<Shipment>>any(), any(Pageable.class)))
            .thenAnswer(call -> new PageImpl<>(List.of(shipment(ShipmentStatus.CREATED, null)), call.getArgument(1), 400));
        var page = service.list(11L, ShipmentStatus.CREATED, null, "UTE", PageRequest.of(2, 200, Sort.by("sender.passwordHash")));
        assertEquals(100, page.getSize()); assertEquals(2, page.getNumber()); assertEquals(400, page.getTotalElements());
        assertEquals(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")), page.getSort());
        assertNull(page.getContent().getFirst().method()); verify(access).requireManagerForBranch(11L);
        verify(shipments, never()).findAll();
    }
    @Test void selectedForeignBranchIsRejectedBeforeAnyShipmentQuery() {
        doThrow(new AccessDeniedException("Forbidden")).when(access).requireManagerForBranch(99L);
        assertThrows(AccessDeniedException.class, () -> service.list(99L, null, null, "", PageRequest.of(0, 20)));
        assertThrows(AccessDeniedException.class, () -> service.dashboard(99L)); verifyNoInteractions(shipments);
    }
    @Test void missingAndForeignDetailAreSame404WithoutReadingAnyChildData() {
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.detail(99L, 0, 0, 0)).getStatusCode().value());
        verifyNoInteractions(events, assignments, employees, intakes); verify(shipments, never()).findById(anyLong());
    }
    @Test void detailChildrenArePagedAndOnlyLoadedAfterScopeAuthorization() {
        when(shipments.findOne(org.mockito.ArgumentMatchers.<Specification<Shipment>>any()))
            .thenReturn(Optional.of(shipment(ShipmentStatus.CONFIRMED, ShipmentMethod.HOME_PICKUP)));
        when(events.findByShipmentId(eq(31L), any())).thenReturn(Page.empty());
        when(assignments.findByShipmentId(eq(31L), any())).thenReturn(Page.empty());
        when(employees.findPickupCandidates(eq(11L), any())).thenReturn(new PageImpl<>(List.of(courier(21L))));
        var result = service.detail(31L, 2, 3, 4);
        assertTrue(result.canAssign()); assertFalse(result.canReceive()); assertEquals(21L, result.candidates().getContent().getFirst().id());
        verify(events).findByShipmentId(31L, PageRequest.of(2, 20, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))));
        verify(assignments).findByShipmentId(31L, PageRequest.of(3, 20, Sort.by(Sort.Order.desc("assignedAt"), Sort.Order.desc("id"))));
        verify(employees).findPickupCandidates(11L, PageRequest.of(4, 20, Sort.by("id")));
    }
    @Test void managerMissingReceiverProfileCanViewButCannotReceive() {
        when(shipments.findOne(org.mockito.ArgumentMatchers.<Specification<Shipment>>any()))
            .thenReturn(Optional.of(shipment(ShipmentStatus.CONFIRMED, ShipmentMethod.BRANCH_DROPOFF)));
        when(events.findByShipmentId(eq(31L), any())).thenReturn(Page.empty()); when(assignments.findByShipmentId(eq(31L), any())).thenReturn(Page.empty());
        doThrow(new AccessDeniedException("Forbidden")).when(access).requireIntakeEmployee(11L);
        assertFalse(service.detail(31L, 0, 0, 0).canReceive()); verifyNoInteractions(employees);
    }
    @Test void dashboardNeverCountsWithoutScope() {
        when(shipments.count(org.mockito.ArgumentMatchers.<Specification<Shipment>>any())).thenReturn(7L, 1L, 2L, 3L, 1L);
        var result = service.dashboard(null); assertEquals(7, result.total()); assertEquals(1, result.created());
        verify(shipments, times(5)).count(org.mockito.ArgumentMatchers.<Specification<Shipment>>any()); verify(shipments, never()).count();
    }
    @SuppressWarnings("unchecked")
    @Test void filtersAreAndedWithCurrentManagerMembershipAndNeverBroadenScope() {
        Root<Shipment> root = mock(Root.class, RETURNS_DEEP_STUBS); CriteriaQuery<?> query = mock(CriteriaQuery.class);
        Subquery<Long> membership = mock(Subquery.class); Root<ManagerBranch> member = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaBuilder cb = mock(CriteriaBuilder.class); Predicate scope = mock(Predicate.class);
        when(query.subquery(Long.class)).thenReturn(membership); when(membership.from(ManagerBranch.class)).thenReturn(member);
        when(membership.select(any())).thenReturn(membership); when(membership.where(any(Predicate[].class))).thenReturn(membership);
        when(cb.exists(membership)).thenReturn(scope);
        service.managedSearch(1L, 11L, ShipmentStatus.CONFIRMED, ShipmentMethod.HOME_PICKUP, "x%_[").toPredicate(root, query, cb);
        verify(cb).equal(member.get("manager").get("id"), 1L);
        verify(cb).equal(member.get("branch").get("id"), root.get("originBranch").get("id"));
        verify(cb).equal(member.get("branch").get("status"), BranchStatus.ACTIVE);
        verify(cb).equal(root.get("originBranch").get("id"), 11L); verify(cb).equal(root.get("status"), ShipmentStatus.CONFIRMED);
        verify(cb).equal(root.get("shipmentMethod"), ShipmentMethod.HOME_PICKUP);
        verify(cb).like(any(), eq("%X\\%\\_\\[%"), eq('\\'));
        var and = org.mockito.ArgumentCaptor.forClass(Predicate[].class); verify(cb).and(and.capture());
        assertSame(scope, and.getValue()[0]); assertEquals(5, and.getValue().length); verify(cb, never()).or(any(Predicate[].class));
    }
}
