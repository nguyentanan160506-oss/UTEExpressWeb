package com.ute.uteexpress.operations;

import com.ute.uteexpress.dto.admin.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.entity.id.UserRoleId;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.AdminRuleException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import static com.ute.uteexpress.operations.OperationsJdbcFixture.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(SystemSecurityIntegrationConfig.class)
class SystemAdminIntegrationTests extends SystemSecuritySupport {
    @ParameterizedTest @ValueSource(strings={"USER","MANAGER","SHIPPER"})
    void serviceAuthorizationCannotBeBypassedWithoutTheAdminHttpController(String role) {
        User actor=role.equals("USER")?f.owner:role.equals("MANAGER")?f.manager:pickupCourier;as(actor,"ROLE_"+role);
        assertThrows(AccessDeniedException.class,()->adminUsers.lock(f.other.getId()));assertThrows(AccessDeniedException.class,()->adminUsers.updateRoles(f.other.getId(),selected("ROLE_ADMIN")));
        assertEquals(UserStatus.ACTIVE,db(()->em.find(User.class,f.other.getId()).getStatus()));assertEquals(List.of("ROLE_USER"),roles(f.other.getId()));assertEquals(0,auditRows.count());
    }
    @ParameterizedTest @ValueSource(strings={"SELF_LOCK","SELF_DEMOTE","UNVERIFIED_LOCK","UNVERIFIED_UNLOCK","ACTIVE_UNLOCK","UNKNOWN_ROLE"})
    void rejectedAdminMutationsPreserveStatusRolesAndAudit(String scenario) {
        as(f.admin,"ROLE_ADMIN");Long target=scenario.startsWith("SELF")?f.admin.getId():f.owner.getId();
        if(scenario.startsWith("UNVERIFIED"))db(()->{em.find(User.class,target).setStatus(UserStatus.UNVERIFIED);return null;});
        var before=roles(target);var status=db(()->em.find(User.class,target).getStatus());
        assertThrows(AdminRuleException.class,()->{switch(scenario){
            case "SELF_LOCK","UNVERIFIED_LOCK" -> adminUsers.lock(target);
            case "SELF_DEMOTE" -> adminUsers.updateRoles(target,selected("ROLE_USER"));
            case "UNVERIFIED_UNLOCK","ACTIVE_UNLOCK" -> adminUsers.unlock(target);
            case "UNKNOWN_ROLE" -> {var form=new RoleUpdateForm();form.setRoleIds(List.of(Long.MAX_VALUE));adminUsers.updateRoles(target,form);}
            default -> throw new AssertionError(scenario);
        }});
        assertEquals(before,roles(target));assertEquals(status,db(()->em.find(User.class,target).getStatus()));assertEquals(0,auditRows.count());
    }
    @Test void duplicateRoleIdsAreDeduplicatedAndNoopDoesNotInventAnAuditChange() {
        as(f.admin,"ROLE_ADMIN");var form=selected("ROLE_USER","ROLE_USER");adminUsers.updateRoles(f.owner.getId(),form);
        assertEquals(List.of("ROLE_USER"),roles(f.owner.getId()));assertEquals(0,auditRows.count());
        form=selected("ROLE_MANAGER","ROLE_MANAGER");adminUsers.updateRoles(f.owner.getId(),form);adminUsers.updateRoles(f.owner.getId(),form);
        assertEquals(List.of("ROLE_MANAGER"),roles(f.owner.getId()));assertEquals(1,auditRows.count());
    }
    @Test void removingAllRolesDoesNotCauseUnlockOrAuthenticationToInventUserAuthority() {
        as(f.admin,"ROLE_ADMIN");adminUsers.updateRoles(f.owner.getId(),selected());adminUsers.lock(f.owner.getId());adminUsers.unlock(f.owner.getId());
        assertTrue(roles(f.owner.getId()).isEmpty());as(f.owner,"ROLE_USER");assertThrows(AccessDeniedException.class,()->userShipments.list("",null,org.springframework.data.domain.PageRequest.of(0,20)));
        assertEquals(3,auditRows.count());assertEquals(UserStatus.ACTIVE,db(()->em.find(User.class,f.owner.getId()).getStatus()));
    }
    @Test void legacyUnknownRoleIsPreservedButNotOfferedAsAnEditableGrant() {
        Long unknown=db(()->{var role=new Role();role.setName("ROLE_OBSERVER_TEST");em.persist(role);em.flush();var membership=new UserRole();membership.setId(new UserRoleId(f.owner.getId(),role.getId()));membership.setUser(em.find(User.class,f.owner.getId()));membership.setRole(role);em.persist(membership);return role.getId();});
        as(f.admin,"ROLE_ADMIN");adminUsers.updateRoles(f.owner.getId(),selected("ROLE_MANAGER"));
        assertEquals(List.of("ROLE_MANAGER","ROLE_OBSERVER_TEST"),roles(f.owner.getId()));assertFalse(adminUsers.editableRoles().stream().anyMatch(r->r.id().equals(unknown)));
        var row=auditRows.findAll().getFirst();assertEquals("USER_ROLES_CHANGED",row.getAction());assertEquals(f.owner.getId().toString(),row.getResourceId());assertEquals(f.admin.getId(),row.getActor().getId());
        assertTrue(row.getOldValue().matches("roleIds=[0-9,]+"));assertTrue(row.getNewValue().matches("roleIds=[0-9,]+"));assertNull(row.getIpAddress());assertNull(row.getActorUsername());
    }
    @ParameterizedTest @ValueSource(strings={"USER_LOCKED","USER_UNLOCKED","USER_ROLES_CHANGED"})
    void auditFailureRollsBackAccountAndRolesAndAllowsCleanRetry(String action) {
        Long shipment=created(ShipmentMethod.BRANCH_DROPOFF);as(f.admin,"ROLE_ADMIN");if(action.equals("USER_UNLOCKED"))adminUsers.lock(f.owner.getId());
        var beforeRoles=roles(f.owner.getId());var beforeStatus=db(()->em.find(User.class,f.owner.getId()).getStatus());long beforeAudit=auditRows.count(),beforeEvents=count(ShipmentEvent.class);
        Runnable mutation=switch(action){case "USER_LOCKED"->()->adminUsers.lock(f.owner.getId());case "USER_UNLOCKED"->()->adminUsers.unlock(f.owner.getId());default->()->adminUsers.updateRoles(f.owner.getId(),selected("ROLE_USER","ROLE_MANAGER"));};
        constraint("ALTER TABLE dbo.audit_logs ADD CONSTRAINT TEST_SYSTEM_AUDIT_FAILURE CHECK (action <> '"+action+"')");
        try {assertThrows(DataAccessException.class,mutation::run);assertEquals(beforeStatus,db(()->em.find(User.class,f.owner.getId()).getStatus()));assertEquals(beforeRoles,roles(f.owner.getId()));assertEquals(beforeAudit,auditRows.count());assertEquals(beforeEvents,count(ShipmentEvent.class));assertEquals(0,notices.count());}
        finally {constraint("ALTER TABLE dbo.audit_logs DROP CONSTRAINT TEST_SYSTEM_AUDIT_FAILURE");}
        mutation.run();assertEquals(beforeAudit+1,auditRows.count());assertEquals(1,count(Shipment.class));assertNotNull(db(()->em.find(Shipment.class,shipment).getTotalFee()));
    }
}
