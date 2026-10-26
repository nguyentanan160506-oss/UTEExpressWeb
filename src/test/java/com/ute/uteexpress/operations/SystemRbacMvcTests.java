package com.ute.uteexpress.operations;

import com.ute.uteexpress.dto.admin.AdminUserView;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import static com.ute.uteexpress.operations.OperationsJdbcFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(SystemSecurityWebSupport.Config.class)
@TestPropertySource(properties={SystemSecurityWebSupport.EXCLUDE,"spring.flyway.enabled=false","spring.sql.init.mode=never","debug=false"})
class SystemRbacMvcTests extends SystemSecurityWebSupport {
    @ParameterizedTest @ValueSource(strings={"GUEST","USER","SHIPPER","MANAGER","ADMIN"})
    void realDatabaseLoginAndRouteMatrixDoNotInventRoleInheritance(String role) throws Exception {
        User actor=switch(role){case "USER"->f.owner;case "SHIPPER"->pickupCourier;case "MANAGER"->f.manager;case "ADMIN"->f.admin;default->null;};
        var session=actor==null?new org.springframework.mock.web.MockHttpSession():login(actor);
        for(String path:List.of("/tracking","/quote","/account","/user/shipments","/account/support","/shipper/pickups","/manager/trips","/admin/users","/admin/reports","/admin/audit","/api/notifications")) {
            boolean publicRoute=path.equals("/tracking")||path.equals("/quote");String required=path.startsWith("/admin")?"ADMIN":path.startsWith("/manager")?"MANAGER":path.startsWith("/shipper")?"SHIPPER":path.startsWith("/user")||path.startsWith("/account/support")?"USER":null;
            int expected=publicRoute?200:actor==null?401:required==null||required.equals(role)?200:403;
            var response=mvc.perform(get(path).session(session)).andReturn().getResponse();assertEquals(expected,response.getStatus(),role+" -> "+path);safe(response.getContentAsString());
        }
        assertEquals(0,auditRows.count());assertEquals(0,notices.count());
    }
    @Test void adminLockUnlockControlsExistingSessionWithoutDeletingHistoryOrGrantingRoles() throws Exception {
        Long id=created(ShipmentMethod.BRANCH_DROPOFF);var customerSession=login(f.owner);var adminSession=login(f.admin);List<String> before=roles(f.owner.getId());
        mvc.perform(post("/admin/users/"+f.owner.getId()+"/lock").session(adminSession).with(csrf()).param("actorId",secondAdmin.getId().toString()))
            .andExpect(redirectedUrl("/admin/users/"+f.owner.getId())).andExpect(flash().attributeExists("notice"));
        for(String path:List.of("/user/shipments/"+id,"/account/support","/api/notifications"))mvc.perform(get(path).session(customerSession)).andExpect(status().isForbidden());
        mvc.perform(post("/login").with(csrf()).param("email",f.owner.getEmail()).param("password",credential(f.owner))).andExpect(redirectedUrl("/login?error"));
        mvc.perform(post("/admin/users/"+f.owner.getId()+"/unlock").session(adminSession).with(csrf())).andExpect(flash().attributeExists("notice"));
        mvc.perform(get("/user/shipments/"+id).session(customerSession)).andExpect(status().isOk());assertEquals(before,roles(f.owner.getId()));assertEquals(1,count(Shipment.class));assertEquals(1,count(ShipmentEvent.class));
        var rows=auditRows.findAll();assertEquals(List.of("USER_LOCKED","USER_UNLOCKED"),rows.stream().map(AuditLog::getAction).toList());
        for(var row:rows){assertEquals(f.admin.getId(),row.getActor().getId());assertEquals(f.owner.getId().toString(),row.getResourceId());}
    }
    @Test void databaseRoleGrantNeedsFreshLoginAndRevocationDeniesOldAdminSession() throws Exception {
        var original=login(f.owner);as(f.admin,"ROLE_ADMIN");adminUsers.updateRoles(f.owner.getId(),selected("ROLE_USER","ROLE_ADMIN"));
        releaseFixturePrincipal();
        mvc.perform(get("/admin/users").session(original)).andExpect(status().isForbidden());
        var elevated=login(f.owner);mvc.perform(get("/admin/users").session(elevated)).andExpect(status().isOk());
        as(f.admin,"ROLE_ADMIN");adminUsers.updateRoles(f.owner.getId(),selected("ROLE_USER"));
        releaseFixturePrincipal();
        for(String path:List.of("/admin/users","/admin/reports","/admin/audit","/admin/reports/export"))mvc.perform(get(path).session(elevated)).andExpect(status().isForbidden());
        mvc.perform(get("/user/shipments").session(elevated)).andExpect(status().isOk());assertEquals(List.of("ROLE_USER"),roles(f.owner.getId()));assertEquals(2,auditRows.count());
    }
    @ParameterizedTest @ValueSource(strings={"LOCKED","UNVERIFIED","WRONG","UNKNOWN"})
    void authenticationFailuresAreGenericAndNeverRenderCredentialMaterial(String scenario) throws Exception {
        String secret=credential(f.owner);if(scenario.equals("LOCKED")||scenario.equals("UNVERIFIED"))db(()->{em.find(User.class,f.owner.getId()).setStatus(UserStatus.valueOf(scenario));return null;});
        String email=scenario.equals("UNKNOWN")?"absent@example.test":f.owner.getEmail();String input=scenario.equals("WRONG")?UUID.randomUUID().toString():secret;
        var response=mvc.perform(post("/login").with(csrf()).accept(MediaType.APPLICATION_JSON).param("email",email).param("password",input))
            .andExpect(status().isUnauthorized()).andExpect(content().json("{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401}")).andReturn().getResponse();safe(response.getContentAsString());assertEquals(0,auditRows.count());
    }
    @Test void inactiveEmployeeCanAuthenticateButCannotUsePhysicalShipperWorkflow() throws Exception {
        db(()->{em.find(Employee.class,pickupEmployee.getId()).setStatus(EmployeeStatus.INACTIVE);return null;});var session=login(pickupCourier);
        mvc.perform(get("/account").session(session)).andExpect(status().isOk());mvc.perform(get("/shipper/pickups").session(session)).andExpect(status().isForbidden());
    }
    @Test void logoutAndLoginCsrfFollowActualFilterChainAndDoNotReuseSession() throws Exception {
        var session=login(f.admin);mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());assertFalse(session.isInvalid());
        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(redirectedUrl("/login?logout"));assertTrue(session.isInvalid());
        mvc.perform(get("/admin/users").with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(post("/login").param("email",f.admin.getEmail()).param("password",credential(f.admin))).andExpect(status().isForbidden());
        mvc.perform(post("/login").with(csrf().useInvalidToken()).param("email",f.admin.getEmail()).param("password",credential(f.admin))).andExpect(status().isForbidden());
    }
    @Test void adminFormsEnforceCsrfRejectMassAssignmentAndUseSafeTargetWithoutCredentialModels() throws Exception {
        var session=login(f.admin);String path="/admin/users/"+f.owner.getId();
        mvc.perform(post(path+"/lock").session(session)).andExpect(status().isForbidden());mvc.perform(post(path+"/lock").session(session).with(csrf().useInvalidToken())).andExpect(status().isForbidden());
        mvc.perform(post(path+"/roles").session(session).with(csrf()).param("roleIds",f.roles.get("ROLE_ADMIN").getId().toString()).param("status","ACTIVE").param("actorId",f.owner.getId().toString()))
            .andExpect(redirectedUrl(path)).andExpect(flash().attributeExists("error"));assertEquals(List.of("ROLE_USER"),roles(f.owner.getId()));assertEquals(0,auditRows.count());
        mvc.perform(get(path).session(session)).andExpect(status().isOk()).andExpect(result->{Object value=result.getModelAndView().getModel().get("user");assertInstanceOf(AdminUserView.class,value);safe(value.toString());safe(result.getResponse().getContentAsString());});
        mvc.perform(get(path+"/lock").session(session)).andExpect(status().isMethodNotAllowed());assertEquals(UserStatus.ACTIVE,db(()->em.find(User.class,f.owner.getId()).getStatus()));
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","9223372036854775807"})
    void invalidAdminTargetIdsAreSafeAndDoNotProduceAudit(String id) throws Exception {
        var session=login(f.admin);mvc.perform(post("/admin/users/"+id+"/lock").session(session).with(csrf())).andExpect(status().is(id.startsWith("-")||id.equals("0")?400:404));assertEquals(0,auditRows.count());
    }
}
