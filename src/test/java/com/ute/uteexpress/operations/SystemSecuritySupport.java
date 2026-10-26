package com.ute.uteexpress.operations;

import com.ute.uteexpress.dto.admin.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.service.AdminUserService;
import com.ute.uteexpress.service.reports.*;
import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.dto.operations.CustomerTicketCommands;
import com.ute.uteexpress.enums.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

/** Reuse Commit19/20 data and true service journeys; a second real Admin is added per test. */
abstract class SystemSecuritySupport extends LogisticsJourneySupport {
    @Autowired AdminUserService adminUsers;
    @Autowired AdminAuditService audit;
    @Autowired ReportCsvService csv;
    @Autowired AuditLogRepository auditRows;
    @Autowired UserRoleRepository roleRows;
    @Autowired SystemSecurityIntegrationConfig.WorkflowProbe workflowProbe;
    User secondAdmin;
    @BeforeEach void secondAdmin() {secondAdmin=db(()->f.user("system-second-admin","ROLE_ADMIN"));}
    RoleUpdateForm selected(String... names) {var form=new RoleUpdateForm();form.setRoleIds(Arrays.stream(names).map(n->f.roles.get(n).getId()).toList());return form;}
    List<String> roles(Long id) {return roleRows.findRoleNamesByUserId(id).stream().sorted().toList();}
    AuditViews.Request auditRequest() {return new AuditViews.Request(ReportViews.Period.TODAY,null,null,AuditViews.Source.ALL,null,null,null,null,null);}
    ReportViews.Request reportRequest(ReportViews.Dataset dataset) {return new ReportViews.Request(dataset,ReportViews.Period.TODAY,null,null,null,null,null,ReportViews.Trend.DAY);}
    Long customerTicket(Long shipment) {
        db(()->{if(em.createQuery("select count(r) from SupportTicketRoute r",Long.class).getSingleResult()==0){var route=new SupportTicketRoute();route.setOriginBranch(f.source);route.setHandlingBranch(f.destination);em.persist(route);}return null;});
        com.ute.uteexpress.operations.OperationsJdbcFixture.as(f.owner,"ROLE_USER");
        return tickets.createCustomer(new CustomerTicketCommands.Create(shipment,TicketCategory.DELAY,"Customer issue","Public customer description",token()));
    }
    Long ticketVersion(Long id) {return ticketRows.findById(id).orElseThrow().getVersion();}
}
