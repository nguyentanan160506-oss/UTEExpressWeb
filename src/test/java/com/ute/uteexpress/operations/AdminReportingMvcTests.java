package com.ute.uteexpress.operations;

import com.ute.uteexpress.config.SecurityConfig;
import com.ute.uteexpress.controller.*;
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
import static com.ute.uteexpress.operations.OperationsJdbcFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/** Real Security/MVC/Thymeleaf/report services and H2 JDBC, without DB/SMTP production auto-configuration. */
@SpringJUnitWebConfig(AdminReportingMvcTests.Config.class)
@TestPropertySource(properties={"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration","spring.flyway.enabled=false","spring.sql.init.mode=never","debug=false","logging.level.root=INFO"})
class AdminReportingMvcTests extends OperationsJdbcSupport {
    @TestConfiguration(proxyBeanMethods=false) @EnableAutoConfiguration
    @Import({AdminReportingJdbcConfig.class,SecurityConfig.class,SecurityErrorHandlers.class,AuthenticationResponseHandlers.class,DatabaseUserDetailsService.class,
        AdminReportsController.class,AdminAuditController.class,AdminReportingErrorAdvice.class,AdminController.class})static class Config { }
    @Autowired WebApplicationContext context;MockMvc mvc;
    @BeforeEach void mvc(){as(f.admin,"ROLE_ADMIN");mvc=webAppContextSetup(context).apply(springSecurity()).defaultResponseCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8).build();}
    @ParameterizedTest @ValueSource(strings={"/admin/reports","/admin/audit"})
    void validAdminRendersResponsiveVietnameseLayoutAndReadonlyEmptyState(String path)throws Exception{mvc.perform(get(path).with(user(f.admin.getEmail()).roles("ADMIN"))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(result->{String html=result.getResponse().getContentAsString();assertTrue(html.contains("width=device-width"));assertTrue(html.contains("/css/vendor/bootstrap.min.css"));assertTrue(html.contains("Chưa có"));assertTrue(html.contains("/admin/reports"));assertTrue(html.contains("/admin/audit"));assertTrue(html.contains("/js/operations.js"));});assertEquals(0,notices.count());}
    @ParameterizedTest @EnumSource(com.ute.uteexpress.dto.admin.ReportViews.Dataset.class)
    void everyDatasetRendersAndExportsSuccessfully(com.ute.uteexpress.dto.admin.ReportViews.Dataset dataset)throws Exception{mvc.perform(get("/admin/reports").param("dataset",dataset.name()).with(user(f.admin.getEmail()).roles("ADMIN"))).andExpect(status().isOk());mvc.perform(get("/admin/reports/export").param("dataset",dataset.name()).with(user(f.admin.getEmail()).roles("ADMIN"))).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/csv"));}
    @ParameterizedTest @ValueSource(strings={"USER","MANAGER","SHIPPER"})
    void otherRolesCannotReadOrExportEvenWithForgedRoleActorAndBranchParameters(String role)throws Exception{for(String path:java.util.List.of("/admin/reports","/admin/audit","/admin/reports/export"))mvc.perform(get(path).with(user(f.admin.getEmail()).roles(role)).param("role","ADMIN").param("adminId",f.admin.getId().toString()).param("branchId",f.source.getId().toString())).andExpect(status().isForbidden());}
    @ParameterizedTest @ValueSource(strings={"/admin/reports","/admin/audit","/admin/reports/export","/api/admin/audit"})
    void guestCannotAccessAnyReportAuditOrExport(String path)throws Exception{mvc.perform(get(path).with(anonymous()).accept(org.springframework.http.MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());}
    @ParameterizedTest @EnumSource(value=UserStatus.class,names={"LOCKED","UNVERIFIED"})
    void disabledAdminIsDeniedByDatabaseDespiteStaleHttpRole(UserStatus status)throws Exception{db(()->{em.find(User.class,f.admin.getId()).setStatus(status);return null;});for(String path:java.util.List.of("/admin/reports","/admin/audit","/admin/reports/export"))mvc.perform(get(path).with(user(f.admin.getEmail()).roles("ADMIN"))).andExpect(status().isForbidden());}
    @Test void revokedAdminRoleBlocksDirectCsvRequest()throws Exception{db(()->{em.createQuery("delete from UserRole r where r.user.id=:id").setParameter("id",f.admin.getId()).executeUpdate();return null;});mvc.perform(get("/admin/reports/export").with(user(f.admin.getEmail()).roles("ADMIN"))).andExpect(status().isForbidden());}
    @ParameterizedTest @CsvSource({"dataset,UNKNOWN","period,INVALID","status,x' OR 1=1","page,-1","size,51","branchId,999999","serviceId,-1","from,not-a-date"})
    void invalidReportFiltersReturnFriendly400(String key,String value)throws Exception{mvc.perform(get("/admin/reports").with(user(f.admin.getEmail()).roles("ADMIN")).param(key,value)).andExpect(status().isBadRequest()).andExpect(view().name("reports/error"));}
    @Test void customRangeAndAuditInjectionAreRejectedWithoutStackTrace()throws Exception{mvc.perform(get("/admin/reports").with(user(f.admin.getEmail()).roles("ADMIN")).param("period","CUSTOM").param("from","2026-01-01").param("to","2026-10-24")).andExpect(status().isBadRequest());mvc.perform(get("/admin/audit").with(user(f.admin.getEmail()).roles("ADMIN")).param("action","' OR 1=1 --")).andExpect(status().isBadRequest()).andExpect(result->{String html=result.getResponse().getContentAsString();assertFalse(html.contains("Exception"));assertFalse(html.contains("SELECT"));});}
    @Test void arbitrarySortCannotChangeSqlOrStableOrder()throws Exception{db(()->{f.shipment(f.source,f.destination,ShipmentStatus.CREATED,NOW);return null;});mvc.perform(get("/admin/reports").with(user(f.admin.getEmail()).roles("ADMIN")).param("sort","createdAt; DROP TABLE users")).andExpect(status().isOk());assertEquals(1,db(()->em.createQuery("select count(s) from Shipment s",Long.class).getSingleResult()));}
    @Test void csvHasCorrectHeadersEncodingAndServerChosenFilename()throws Exception{mvc.perform(get("/admin/reports/export").with(user(f.admin.getEmail()).roles("ADMIN")).param("filename","../../secret.txt")).andExpect(status().isOk()).andExpect(header().string("Content-Type","text/csv;charset=UTF-8")).andExpect(header().string("Cache-Control","no-store")).andExpect(result->{assertTrue(result.getResponse().getHeader("Content-Disposition").contains("attachment; filename=\"uteexpress-shipments-"));assertFalse(result.getResponse().getHeader("Content-Disposition").contains("secret"));byte[] bytes=result.getResponse().getContentAsByteArray();assertEquals(0xef,bytes[0]&255);});}
    @Test void auditHtmlEscapesLegacyActionAndEntityIdWithoutRawPayloadOrUsername()throws Exception{db(()->{var a=new AuditLog();a.setAction("<script>alert(1)</script>");a.setResourceType(AuditResourceType.USER);a.setResourceId("<img src=x onerror=alert(1)>");a.setActorUsername("PRIVATE EMAIL LEGACY");a.setOldValue("INTERNAL TICKET NOTE PASSWORD");a.setNewValue("AUTH TOKEN");a.setCreatedAt(NOW);em.persist(a);return null;});mvc.perform(get("/admin/audit").with(user(f.admin.getEmail()).roles("ADMIN"))).andExpect(status().isOk()).andExpect(result->{String html=result.getResponse().getContentAsString();assertTrue(html.contains("&lt;script&gt;"));assertTrue(html.contains("&lt;img"));assertFalse(html.contains("<script>alert(1)"));assertFalse(html.contains("PRIVATE EMAIL"));assertFalse(html.contains("PASSWORD"));assertFalse(html.contains("AUTH TOKEN"));assertTrue(html.contains("Không xác định (legacy)"));});}
    @Test void reportHtmlEscapesStoredBranchNames()throws Exception{db(()->{em.find(Branch.class,f.source.getId()).setName("<script>alert(1)</script>");return null;});mvc.perform(get("/admin/reports").with(user(f.admin.getEmail()).roles("ADMIN")).param("dataset","BRANCHES")).andExpect(status().isOk()).andExpect(result->{String html=result.getResponse().getContentAsString();assertTrue(html.contains("&lt;script&gt;"));assertFalse(html.contains("<script>alert(1)"));});}
    @Test void noAuditEditDeleteOrWriteEndpointsExist()throws Exception{mvc.perform(post("/admin/audit").with(user(f.admin.getEmail()).roles("ADMIN"))).andExpect(status().isForbidden());mvc.perform(post("/admin/audit").with(user(f.admin.getEmail()).roles("ADMIN")).with(csrf())).andExpect(status().isMethodNotAllowed());mvc.perform(delete("/admin/audit").with(user(f.admin.getEmail()).roles("ADMIN")).with(csrf())).andExpect(status().isMethodNotAllowed());mvc.perform(post("/admin/reports").with(user(f.admin.getEmail()).roles("ADMIN")).with(csrf())).andExpect(status().isMethodNotAllowed());}
    @Test void adminDoesNotAcquireManagerAuthorityFromReports()throws Exception{mvc.perform(get("/admin").with(user(f.admin.getEmail()).roles("ADMIN"))).andExpect(status().isOk()).andExpect(result->{String html=result.getResponse().getContentAsString();assertTrue(html.contains("/admin/reports"));assertTrue(html.contains("/admin/audit"));});mvc.perform(get("/manager/operations").with(user(f.admin.getEmail()).roles("ADMIN"))).andExpect(status().isForbidden());mvc.perform(get("/manager/tickets").with(user(f.admin.getEmail()).roles("ADMIN"))).andExpect(status().isForbidden());}
}
