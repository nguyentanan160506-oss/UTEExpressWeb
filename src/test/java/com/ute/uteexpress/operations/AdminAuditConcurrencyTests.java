package com.ute.uteexpress.operations;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.dto.admin.RoleUpdateForm;
import com.ute.uteexpress.enums.UserStatus;
import com.ute.uteexpress.repository.AuditLogRepository;
import com.ute.uteexpress.service.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import static com.ute.uteexpress.operations.OperationsJdbcFixture.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real H2 JDBC lock order inherited from AdminUserService; not a mocked mutex model. */
@SpringJUnitConfig(AdminReportingJdbcConfig.class)
class AdminAuditConcurrencyTests extends OperationsJdbcSupport {
    @Autowired AdminUserService admin;@Autowired AuditLogRepository auditRows;
    <T>List<T> pair(Callable<T> first,Callable<T> second)throws Exception{var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);try{var a=pool.submit(()->{start.await();return first.call();});var b=pool.submit(()->{start.await();return second.call();});start.countDown();return List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));}finally{pool.shutdownNow();}}
    boolean lock(User actor){as(actor,"ROLE_ADMIN");try{admin.lock(f.owner.getId());return true;}catch(AdminRuleException alreadyLocked){return false;}finally{SecurityContextHolder.clearContext();}}
    @Test void twoAdminsLockingSameUserWriteOneActualChangeAndOneAudit()throws Exception{var second=db(()->f.user("second-admin-audit","ROLE_ADMIN"));var result=pair(()->lock(f.admin),()->lock(second));assertEquals(1,result.stream().filter(Boolean::booleanValue).count());assertEquals(UserStatus.LOCKED,db(()->em.find(User.class,f.owner.getId()).getStatus()));assertEquals(1,auditRows.count());assertTrue(Set.of(f.admin.getId(),second.getId()).contains(auditRows.findAll().getFirst().getActor().getId()));assertEquals(0,notices.count());}
    @Test void concurrentSameRoleSelectionDoesNotDuplicateNoopAudit()throws Exception{var second=db(()->f.user("second-admin-roles","ROLE_ADMIN"));var form=new RoleUpdateForm();form.setRoleIds(List.of(f.roles.get("ROLE_USER").getId(),f.roles.get("ROLE_MANAGER").getId()));pair(()->{as(f.admin,"ROLE_ADMIN");try{admin.updateRoles(f.owner.getId(),form);return true;}finally{SecurityContextHolder.clearContext();}},()->{as(second,"ROLE_ADMIN");try{admin.updateRoles(f.owner.getId(),form);return true;}finally{SecurityContextHolder.clearContext();}});assertEquals(1,auditRows.count());assertEquals("USER_ROLES_CHANGED",auditRows.findAll().getFirst().getAction());}
}
