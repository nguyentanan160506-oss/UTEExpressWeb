package com.ute.uteexpress.service.operations;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.service.LogisticsAccessService;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly = true) @PreAuthorize("hasRole('MANAGER')")
public class OperationsAccessService {
    private final LogisticsAccessService logistics;
    private final EmployeeRepository employees;
    private final ManagerBranchRepository memberships;
    private final BranchRepository branches;
    private final UserRoleRepository roles;
    public OperationsAccessService(LogisticsAccessService logistics, EmployeeRepository employees,
            ManagerBranchRepository memberships, BranchRepository branches, UserRoleRepository roles) {
        this.logistics=logistics; this.employees=employees; this.memberships=memberships; this.branches=branches; this.roles=roles;
    }
    public User manager() {
        User u=logistics.requireManager();
        Employee e=employees.findByUserId(u.getId()).orElseThrow(this::denied);
        if (e.getUser()==null || !u.getId().equals(e.getUser().getId()) || e.getStatus()!=EmployeeStatus.ACTIVE
                || e.getBranch()==null || e.getBranch().getStatus()!=BranchStatus.ACTIVE) { throw denied(); }
        return u;
    }
    public List<Branch> branches() { return memberships.findManagedBranches(manager().getId(), BranchStatus.ACTIVE); }
    public Scope scope(Long id) {
        User u=manager();
        List<Branch> permitted=memberships.findManagedBranches(u.getId(),BranchStatus.ACTIVE);
        Long chosen=id==null && !permitted.isEmpty()?permitted.getFirst().getId():id;
        Branch b=permitted.stream().filter(p->p.getId().equals(chosen)).findFirst().orElseThrow(this::denied);
        // Re-read the concrete branch and membership; query parameters never grant scope.
        if(branches.findByIdAndStatus(b.getId(),BranchStatus.ACTIVE).isEmpty() || !memberships.existsByManagerIdAndBranchId(u.getId(),b.getId()))throw denied();
        return new Scope(u,b);
    }
    public Employee assignee(Long employeeId,Long branchId) {
        Employee e=employees.findByIdAndBranchId(employeeId,branchId).orElseThrow(()->new OperationsRuleException("Nhân sự xử lý không hợp lệ."));
        if(e.getStatus()!=EmployeeStatus.ACTIVE || e.getBranch()==null || !e.getBranch().getId().equals(branchId)
                || e.getBranch().getStatus()!=BranchStatus.ACTIVE || e.getUser()==null || e.getUser().getStatus()!=UserStatus.ACTIVE
                || !roles.findRoleNamesByUserId(e.getUser().getId()).contains("ROLE_MANAGER")
                || !memberships.existsByManagerIdAndBranchId(e.getUser().getId(),branchId))throw new OperationsRuleException("Người xử lý phải là Manager hoạt động tại chi nhánh phụ trách.");
        return e;
    }
    public void currentAssignee(SupportTicket t) {
        if(t.getAssignedManager()==null)throw new OperationsRuleException("Cần phân công người xử lý trước thao tác này.");
        Employee e=employees.findByUserId(t.getAssignedManager().getId()).orElseThrow(()->new OperationsRuleException("Người xử lý không còn hợp lệ."));
        assignee(e.getId(),t.getHandlingBranch().getId());
    }
    private AccessDeniedException denied(){return new AccessDeniedException("Forbidden");}
    public record Scope(User actor,Branch branch) { }
}
