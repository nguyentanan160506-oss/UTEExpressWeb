package com.ute.uteexpress.operations;

import com.ute.uteexpress.config.SecurityConfig;
import com.ute.uteexpress.controller.*;
import com.ute.uteexpress.dto.operations.TicketCommands.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import static com.ute.uteexpress.operations.OperationsJdbcFixture.NOW;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/** Actual MVC/security/Thymeleaf/services and H2 JDBC; no public portal or SQL Server. */
@SpringJUnitWebConfig(OperationsMvcTests.Config.class)
@TestPropertySource(properties={"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration","spring.flyway.enabled=false","spring.sql.init.mode=never","debug=false","logging.level.root=INFO"})
class OperationsMvcTests extends OperationsJdbcSupport {
    @TestConfiguration(proxyBeanMethods=false) @EnableAutoConfiguration
    @Import({OperationsJdbcConfig.class,SecurityConfig.class,SecurityErrorHandlers.class,AuthenticationResponseHandlers.class,DatabaseUserDetailsService.class,
        OperationsDashboardController.class,ManagerTicketController.class,OperationsErrorAdvice.class}) static class Config { }
    @Autowired WebApplicationContext context;
    MockMvc mvc;
    @BeforeEach void mvc(){mvc=webAppContextSetup(context).apply(springSecurity()).defaultResponseCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8).build();}
    Long ticket(){return tickets.create(command(db(()->f.shipment(f.source,f.destination,ShipmentStatus.CREATED,NOW.minusHours(1)))));}
    @Test void managerDashboardRendersChartsTablesEmptyStateAndNoFakeWrites()throws Exception{
        mvc.perform(get("/manager/operations").with(user(f.manager.getEmail()).roles("MANAGER"))).andExpect(status().isOk()).andExpect(view().name("operations/dashboard")).andExpect(result->{String html=result.getResponse().getContentAsString();assertTrue(html.contains("N/A"));assertTrue(html.contains("Số liệu dạng bảng"));assertTrue(html.contains("Không có đơn nguồn"));assertTrue(html.contains("UTC"));assertTrue(html.contains("/js/notifications.js"));assertTrue(html.contains("/js/operations.js"));});assertEquals(0,notices.count());assertEquals(0,eventRows.count());
    }
    @Test void actualTicketPagesAndReadonlyDetailEscapeUntrustedContent()throws Exception{
        var s=db(()->f.shipment(f.source,f.destination,ShipmentStatus.CREATED,NOW));var c=command(s);Long id=tickets.create(new Create(c.branchId(),c.trackingNumber(),"<script>alert(1)</script>","<img src=x onerror=alert(1)>",c.category(),c.priority(),c.source(),c.token()));
        mvc.perform(get("/manager/tickets").with(user(f.manager.getEmail()).roles("MANAGER"))).andExpect(status().isOk()).andExpect(result->assertTrue(result.getResponse().getContentAsString().contains("&lt;script&gt;")));
        mvc.perform(get("/manager/tickets/"+id).with(user(f.manager.getEmail()).roles("MANAGER"))).andExpect(status().isOk()).andExpect(result->{String html=result.getResponse().getContentAsString();assertTrue(html.contains("&lt;img"));assertFalse(html.contains("<script>alert(1)</script>"));assertTrue(html.contains("Thông tin tiếp nhận nội bộ"));assertTrue(html.contains("name=\"_csrf\""));});
        mvc.perform(get("/manager/tickets/new").with(user(f.manager.getEmail()).roles("MANAGER"))).andExpect(status().isOk()).andExpect(view().name("tickets/new"));
    }
    @Test void csrfRequiredAndCreateDoesNotTrustActorOrRequesterFields()throws Exception{
        var s=db(()->f.shipment(f.source,f.destination,ShipmentStatus.CREATED,NOW));var c=command(s);
        mvc.perform(post("/manager/tickets").with(user(f.manager.getEmail()).roles("MANAGER"))).andExpect(status().isForbidden());
        mvc.perform(post("/manager/tickets").with(user(f.manager.getEmail()).roles("MANAGER")).with(csrf()).param("branchId",c.branchId().toString()).param("trackingNumber",c.trackingNumber()).param("subject",c.subject()).param("content",c.content()).param("category",c.category().name()).param("priority",c.priority().name()).param("source",c.source().name()).param("token",c.token()).param("userId",f.other.getId().toString()).param("createdBy",f.outsider.getId().toString()).param("status","CLOSED"))
            .andExpect(status().is3xxRedirection());var t=ticketRows.findAll().getFirst();assertEquals(f.owner.getId(),t.getUser().getId());assertEquals(f.manager.getId(),t.getCreatedBy().getId());assertEquals(TicketStatus.OPEN,t.getStatus());
    }
    @ParameterizedTest @ValueSource(strings={"/manager/operations","/manager/tickets","/manager/tickets/new","/manager/tickets/999"})
    void guestCannotAccessAnyManagerOperationsEndpoint(String path)throws Exception{mvc.perform(get(path).with(anonymous())).andExpect(status().isUnauthorized());}
    @ParameterizedTest @ValueSource(strings={"USER","SHIPPER","ADMIN"})
    void otherRolesDoNotGetManagerDashboardOrTickets(String role)throws Exception{mvc.perform(get("/manager/operations").with(user(f.admin.getEmail()).roles(role))).andExpect(status().isForbidden());mvc.perform(get("/manager/tickets").with(user(f.admin.getEmail()).roles(role))).andExpect(status().isForbidden());}
    @Test void branchAndTicketIdEnumerationAreBlockedByActualDatabaseScope()throws Exception{
        Long id=ticket();mvc.perform(get("/manager/operations").param("branchId",f.foreign.getId().toString()).with(user(f.manager.getEmail()).roles("MANAGER"))).andExpect(status().isForbidden());mvc.perform(get("/manager/tickets/"+id).with(user(f.outsider.getEmail()).roles("MANAGER"))).andExpect(status().isNotFound());
        mvc.perform(post("/manager/tickets/"+id+"/notes").with(user(f.outsider.getEmail()).roles("MANAGER")).with(csrf()).param("version","0").param("body","Cannot write")) .andExpect(status().isNotFound());assertEquals(0,messageRows.count());
    }
    @ParameterizedTest @CsvSource({"period,INVALID","page,-1","from,not-a-date"})
    void invalidDashboardParametersFailFriendly(String key,String value)throws Exception{mvc.perform(get("/manager/operations").param(key,value).with(user(f.manager.getEmail()).roles("MANAGER"))).andExpect(status().isBadRequest()).andExpect(view().name("operations/error"));}
    @Test void invalidRangeSortAndOversizePageAreRejectedWithoutSqlInjection()throws Exception{
        mvc.perform(get("/manager/operations").param("period","CUSTOM").param("from","2026-10-24").param("to","2026-10-23").with(user(f.manager.getEmail()).roles("MANAGER"))).andExpect(status().isBadRequest());
        mvc.perform(get("/manager/tickets").param("sort","createdAt; DROP TABLE users").with(user(f.manager.getEmail()).roles("MANAGER"))).andExpect(status().isBadRequest());mvc.perform(get("/manager/tickets").param("size","100000").with(user(f.manager.getEmail()).roles("MANAGER"))).andExpect(status().isBadRequest());
    }
    @Test void mutationFormsRequireVersionAndProvideFriendlyConflict()throws Exception{
        Long id=ticket();mvc.perform(post("/manager/tickets/"+id+"/notes").with(user(f.manager.getEmail()).roles("MANAGER")).with(csrf()).param("body","Missing version")).andExpect(status().isBadRequest());
        mvc.perform(post("/manager/tickets/"+id+"/notes").with(user(f.manager.getEmail()).roles("MANAGER")).with(csrf()).param("version","0").param("body","First note")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/manager/tickets/"+id+"/reply").with(user(f.manager.getEmail()).roles("MANAGER")).with(csrf()).param("version","0").param("body","Stale reply")).andExpect(status().isConflict()).andExpect(view().name("operations/error"));assertEquals(1,messageRows.count());
    }
    @Test void getDoesNotPerformMutationAndNewCustomerRoutesStayClosed()throws Exception{
        Long id=ticket();mvc.perform(get("/manager/tickets/"+id+"/resolve").with(user(f.manager.getEmail()).roles("MANAGER"))).andExpect(status().isMethodNotAllowed());assertEquals(TicketStatus.OPEN,ticketRows.findById(id).orElseThrow().getStatus());
        mvc.perform(get("/user/tickets/"+id).with(user(f.owner.getEmail()).roles("USER"))).andExpect(status().isForbidden());mvc.perform(get("/api/tickets/"+id).with(anonymous())).andExpect(status().isUnauthorized());
    }
    @ParameterizedTest @ValueSource(strings={"assign","status","classify","notes","reply","resolve","close","reopen"})
    void everyManagerMutationRequiresCsrfBeforeBinding(String operation)throws Exception{Long id=ticket();mvc.perform(post("/manager/tickets/"+id+"/"+operation).with(user(f.manager.getEmail()).roles("MANAGER"))).andExpect(status().isForbidden());assertEquals(1,eventRows.count());}
    @Test void activeManagerWithNoBranchesSeesExplicitEmptyScope()throws Exception{db(()->{em.createQuery("delete from ManagerBranch m where m.manager.id=:id").setParameter("id",f.manager.getId()).executeUpdate();return null;});mvc.perform(get("/manager/operations").with(user(f.manager.getEmail()).roles("MANAGER"))).andExpect(status().isOk()).andExpect(result->assertTrue(result.getResponse().getContentAsString().contains("Chưa có chi nhánh")));mvc.perform(get("/manager/tickets").with(user(f.manager.getEmail()).roles("MANAGER"))).andExpect(status().isOk());}

}
