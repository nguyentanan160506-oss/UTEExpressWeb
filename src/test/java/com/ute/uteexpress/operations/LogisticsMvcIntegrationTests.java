package com.ute.uteexpress.operations;

import com.ute.uteexpress.config.SecurityConfig;
import com.ute.uteexpress.controller.*;
import com.ute.uteexpress.dto.shipment.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.security.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import static com.ute.uteexpress.operations.OperationsJdbcFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/** Real filters/controllers/Thymeleaf and logistics JDBC; HTTP servlet requests are simulated. */
@SpringJUnitWebConfig(LogisticsMvcIntegrationTests.Config.class)
@TestPropertySource(properties={"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration","spring.flyway.enabled=false","spring.sql.init.mode=never","debug=false"})
class LogisticsMvcIntegrationTests extends LogisticsJourneySupport {
    @TestConfiguration(proxyBeanMethods=false) @EnableAutoConfiguration
    @Import({LogisticsJourneyConfig.class,SecurityConfig.class,SecurityErrorHandlers.class,AuthenticationResponseHandlers.class,
        DatabaseUserDetailsService.class,ManagerLogisticsController.class,ShipperPickupController.class,ManagerHandoffController.class,
        TransportTripController.class,ManagerDeliveryController.class,ShipperDeliveryController.class,OperationsDashboardController.class,OperationsErrorAdvice.class})
    static class Config { }
    @Autowired WebApplicationContext context;MockMvc mvc;
    @BeforeEach void mvc() {mvc=webAppContextSetup(context).apply(springSecurity()).defaultResponseCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8).build();}

    @ParameterizedTest @ValueSource(strings={"/manager/shipments","/manager/trips","/manager/deliveries","/shipper/pickups","/shipper/deliveries"})
    void anonymousCannotAccessOperationalRoutes(String path) throws Exception {
        mvc.perform(get(path).with(anonymous()).accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized()).andExpect(content().json("{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401}"));
    }
    @ParameterizedTest @ValueSource(strings={"USER","ADMIN","SHIPPER"})
    void otherDatabaseRolesHaveNoImplicitManagerAuthority(String role) throws Exception {
        User actor=switch(role){case "USER"->f.owner;case "ADMIN"->f.admin;default->pickupCourier;};
        mvc.perform(get("/manager/trips").with(user(actor.getEmail()).roles(role))).andExpect(status().isForbidden());
        Long id=created(ShipmentMethod.BRANCH_DROPOFF);
        mvc.perform(post("/manager/shipments/"+id+"/confirm").with(user(actor.getEmail()).roles(role)).with(csrf()).param("expectedVersion",shipmentVersion(id).toString())).andExpect(status().isForbidden());
        assertCustody(id,null,ShipmentStatus.CREATED);
    }
    @Test void validManagerPostUsesPathScopeAndRejectsDuplicateWithFlashError() throws Exception {
        Long id=created(ShipmentMethod.BRANCH_DROPOFF);String version=shipmentVersion(id).toString();
        mvc.perform(post("/manager/shipments/"+id+"/confirm").with(user(f.manager.getEmail()).roles("MANAGER")).with(csrf()).param("expectedVersion",version)).andExpect(redirectedUrl("/manager/shipments/"+id)).andExpect(flash().attributeExists("notice"));
        mvc.perform(post("/manager/shipments/"+id+"/confirm").with(user(f.manager.getEmail()).roles("MANAGER")).with(csrf()).param("expectedVersion",version)).andExpect(redirectedUrl("/manager/shipments/"+id)).andExpect(flash().attributeExists("error"));
        assertCustody(id,null,ShipmentStatus.CONFIRMED);assertEquals(2,count(ShipmentEvent.class));
    }
    @Test void csrfMissingAndInvalidPreventManagerAndShipperWrites() throws Exception {
        Long id=pickupAssigned(),task=assignment(id,AssignmentType.PICKUP);long events=count(ShipmentEvent.class);
        mvc.perform(post("/manager/shipments/"+id+"/cancel").with(user(f.manager.getEmail()).roles("MANAGER"))).andExpect(status().isForbidden());
        mvc.perform(post("/shipper/pickups/"+task+"/collect").with(user(pickupCourier.getEmail()).roles("SHIPPER")).with(csrf().useInvalidToken())).andExpect(status().isForbidden());
        assertEquals(events,count(ShipmentEvent.class));assertEquals(0,count(PickupEvidence.class));
    }
    @Test void branchAndResourceParameterTamperingDoNotGrantOperationalScope() throws Exception {
        Long id=created(ShipmentMethod.BRANCH_DROPOFF);
        mvc.perform(post("/manager/shipments/"+id+"/confirm").with(user(f.outsider.getEmail()).roles("MANAGER")).with(csrf()).param("expectedVersion",shipmentVersion(id).toString())).andExpect(status().isForbidden());
        mvc.perform(get("/manager/shipments").with(user(f.manager.getEmail()).roles("MANAGER")).param("branchId",f.foreign.getId().toString())).andExpect(status().isForbidden());
        assertCustody(id,null,ShipmentStatus.CREATED);
    }
    @ParameterizedTest @ValueSource(strings={"actorId","status","currentBranchId","shippingFee"})
    void managerBinderRejectsMassAssignmentWithoutChangingShipment(String field) throws Exception {
        Long id=created(ShipmentMethod.BRANCH_DROPOFF);
        mvc.perform(post("/manager/shipments/"+id+"/confirm").with(user(f.manager.getEmail()).roles("MANAGER")).with(csrf()).param("expectedVersion",shipmentVersion(id).toString()).param(field,"forged"))
            .andExpect(status().isBadRequest());assertCustody(id,null,ShipmentStatus.CREATED);assertEquals(1,count(ShipmentEvent.class));
    }
    @Test void pickupMvcCollectThenManagerPhysicalHandoffPersistsActualActors() throws Exception {
        Long id=pickupAssigned(),task=assignment(id,AssignmentType.PICKUP);
        mvc.perform(post("/shipper/pickups/"+task+"/collect").with(user(pickupCourier.getEmail()).roles("SHIPPER")).with(csrf()).param("expectedVersion",shipmentVersion(id).toString()).param("result","SUCCESS").param("physicallyCollected","true").param("reference","MVC-PROOF"))
            .andExpect(redirectedUrl("/shipper/pickups/"+task)).andExpect(flash().attributeExists("notice"));
        assertCustody(id,null,ShipmentStatus.PICKED_UP);assertEquals(0,count(ShipmentIntake.class));Long handoff=pendingOriginHandoff(id);
        mvc.perform(post("/manager/handoffs/"+handoff+"/accept").with(user(f.manager.getEmail()).roles("MANAGER")).with(csrf()).param("expectedVersion",shipmentVersion(id).toString()).param("physicallyReceived","true").param("receiptReference","MVC-INTAKE"))
            .andExpect(redirectedUrl("/manager/handoffs/"+handoff)).andExpect(flash().attributeExists("notice"));
        assertCustody(id,f.source,ShipmentStatus.AT_ORIGIN_BRANCH);
        db(()->{assertEquals(pickupCourier.getId(),em.createQuery("select p.actor.id from PickupEvidence p",Long.class).getSingleResult());assertEquals(f.manager.getId(),em.createQuery("select i.receivedBy.id from ShipmentIntake i",Long.class).getSingleResult());return null;});
    }
    @Test void invalidPickupEnumAndUnauthorizedAssignmentCannotProduceEvidence() throws Exception {
        Long id=pickupAssigned(),task=assignment(id,AssignmentType.PICKUP);
        mvc.perform(post("/shipper/pickups/"+task+"/collect").with(user(pickupCourier.getEmail()).roles("SHIPPER")).with(csrf()).param("expectedVersion",shipmentVersion(id).toString()).param("result","INVENTED").param("physicallyCollected","true").param("reference","REF"))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
        mvc.perform(get("/shipper/pickups/"+task).with(user(unauthorizedCourier.getEmail()).roles("SHIPPER"))).andExpect(status().isNotFound());assertEquals(0,count(PickupEvidence.class));
    }
    @Test void pickupEvidenceEscapesHtmlAndFilterInjectionDoesNotExpandBranchScope() throws Exception {
        Long id=pickupAssigned(),task=assignment(id,AssignmentType.PICKUP);as(pickupCourier,"ROLE_SHIPPER");
        lifecycle.collectPickup(new PickupCollectionCommand(task,shipmentVersion(id),PickupResult.FAILED,false,null,"<script>alert(1)</script>"));
        mvc.perform(get("/shipper/pickups/"+task).with(user(pickupCourier.getEmail()).roles("SHIPPER"))).andExpect(status().isOk()).andExpect(result->{String html=result.getResponse().getContentAsString();assertTrue(html.contains("&lt;script&gt;"));assertFalse(html.contains("<script>alert(1)</script>"));});
        mvc.perform(get("/manager/shipments").with(user(f.manager.getEmail()).roles("MANAGER")).param("q","' OR 1=1 --")).andExpect(status().isOk()).andExpect(result->{var page=(org.springframework.data.domain.Page<?>)result.getModelAndView().getModel().get("shipments");assertTrue(page.isEmpty());});
    }
    @Test void tripDetailRendersForBothEndsAndGetRequestsDoNotModifyState() throws Exception {
        Long id=intake(ShipmentMethod.BRANCH_DROPOFF);var leg=manifest(List.of(id),f.source,f.hub);long events=count(ShipmentEvent.class),tripEvents=count(TransportTripEvent.class);
        for(User actor:List.of(f.manager,hubManagerA))mvc.perform(get("/manager/trips/"+leg.trip()).with(user(actor.getEmail()).roles("MANAGER"))).andExpect(status().isOk()).andExpect(view().name("transport/detail"));
        mvc.perform(get("/manager/shipments/"+id+"/confirm").with(user(f.manager.getEmail()).roles("MANAGER"))).andExpect(status().isMethodNotAllowed());
        assertEquals(events,count(ShipmentEvent.class));assertEquals(tripEvents,count(TransportTripEvent.class));assertEquals(0,notices.count());
    }
    @Test void invalidIdsAndStaleDatabaseRoleProduceSafeErrors() throws Exception {
        Long id=created(ShipmentMethod.BRANCH_DROPOFF);
        mvc.perform(get("/manager/shipments/0").with(user(f.manager.getEmail()).roles("MANAGER"))).andExpect(status().isBadRequest());
        db(()->{em.createQuery("delete from UserRole r where r.user.id=:id").setParameter("id",f.manager.getId()).executeUpdate();return null;});
        mvc.perform(post("/manager/shipments/"+id+"/confirm").with(user(f.manager.getEmail()).roles("MANAGER")).with(csrf()).accept(MediaType.APPLICATION_JSON).param("expectedVersion",shipmentVersion(id).toString()))
            .andExpect(status().isForbidden()).andExpect(content().json("{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403}"));
        assertCustody(id,null,ShipmentStatus.CREATED);
    }
}
