package com.ute.uteexpress.release;

import com.ute.uteexpress.UteexpressApplication;
import com.ute.uteexpress.dto.admin.*;
import com.ute.uteexpress.dto.delivery.DeliveryCommands;
import com.ute.uteexpress.dto.operations.*;
import com.ute.uteexpress.dto.shipment.*;
import com.ute.uteexpress.dto.transport.TripCommands;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.entity.id.UserRoleId;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.*;
import com.ute.uteexpress.service.notifications.*;
import com.ute.uteexpress.service.operations.TicketService;
import com.ute.uteexpress.service.reports.*;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Explicit operator-only SQL smoke. No default JUnit discovery, demo seeder, committed fixture or schema DDL. */
final class SqlServerBusinessSmoke {
    private final ConfigurableApplicationContext app;
    private final EntityManager em;
    private final String prefix="QA"+UUID.randomUUID().toString().substring(0,8);
    private final String password=UUID.randomUUID().toString();
    private final LocalDateTime now=LocalDateTime.now(ZoneOffset.UTC).withNano(0);
    private final Map<Long,Authentication> authentications=new HashMap<>();
    private final Map<Long,User> managers=new HashMap<>();
    private final Map<String,Long> routes=new HashMap<>();
    private Branch source,hubA,hubB,destination;
    private User owner,other,admin,pickup,delivery;
    private Employee pickupEmployee,deliveryEmployee;
    private ShippingService catalog;
    private int phone=1000;
    private String stage="context";
    private SqlServerBusinessSmoke(ConfigurableApplicationContext app) {
        this.app=app;em=SharedEntityManagerCreator.createSharedEntityManager(app.getBean(EntityManagerFactory.class));
    }
    static void run(SqlServerQaTool.Settings settings) throws Exception {
        SqlServerQaTool.requireApprovedUrl(settings.url());
        // DevTools is on Maven's test classpath; disable restart before Spring's first startup event.
        System.setProperty("spring.devtools.restart.enabled","false");
        System.setProperty("spring.devtools.add-properties","false");
        try(var ds=new HikariDataSource()) {
            ds.setJdbcUrl(settings.url());ds.setUsername(settings.user());ds.setPassword(settings.password());
            ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");ds.setMaximumPoolSize(3);ds.setMinimumIdle(0);
            Map<String,Long> before=counts(ds);
            try(var app=new SpringApplicationBuilder(UteexpressApplication.class)
                    .initializers(context->{
                        var definition=new org.springframework.beans.factory.support.RootBeanDefinition(DataSource.class);
                        definition.setInstanceSupplier(()->ds);definition.setDestroyMethodName("");
                        ((org.springframework.beans.factory.support.BeanDefinitionRegistry)context.getBeanFactory()).registerBeanDefinition("dataSource",definition);
                    })
                    .run("--spring.config.location=classpath:/sqlserver-qa.properties","--spring.profiles.active=sql-qa",
                        "--debug=false","--trace=false","--logging.level.root=WARN","--logging.level.org.springframework=WARN")) {
                check(app.getBean(DataSource.class)==ds,"guarded DataSource must be used");
                check(!app.containsBean("notificationWorker")&&!app.containsBean("ticketNotificationWorker"),"background SQL workers must be disabled");
                var smoke=new SqlServerBusinessSmoke(app);
                var tx=new TransactionTemplate(app.getBean(PlatformTransactionManager.class));tx.setTimeout(180);
                try {
                    tx.execute(status->{try{smoke.exercise();return null;}finally{status.setRollbackOnly();SecurityContextHolder.clearContext();}});
                } catch(RuntimeException error) {
                    System.out.println("SQL BUSINESS SMOKE FAIL stage="+smoke.stage+" exception="+error.getClass().getSimpleName());throw error;
                } finally {
                    check(before.equals(counts(ds)),"rollback must preserve every table row count");
                    System.out.println("SQL rollback verified: every table row count unchanged");
                }
                System.out.println("SQL BUSINESS SMOKE PASS; all table row counts unchanged after rollback; no demo seed. Identity counters may advance.");
            }
        }
    }
    private static Map<String,Long> counts(DataSource ds) throws Exception {
        try(var c=ds.getConnection()) {
            SqlServerQaTool.checkTarget(c,"UTEExpress_Test");var tables=new ArrayList<String>();
            try(var s=c.createStatement();var r=s.executeQuery("SELECT name FROM sys.tables WHERE schema_id=SCHEMA_ID('dbo') AND is_ms_shipped=0 ORDER BY name")){while(r.next())tables.add(r.getString(1));}
            var counts=new LinkedHashMap<String,Long>();
            for(String table:tables){check(table.matches("[A-Za-z0-9_]+"),"safe catalog identifier");try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT_BIG(*) FROM dbo.["+table+"]")){r.next();counts.put(table,r.getLong(1));}}
            return counts;
        }
    }
    private <T>T bean(Class<T> type){return app.getBean(type);}
    private static void check(boolean result,String description){if(!result)throw new IllegalStateException(description);}
    private void passed(String label){em.flush();System.out.println("SQL smoke PASS "+label);}
    private static String token(){return UUID.randomUUID().toString().replace("-","");}
    private void as(User user){SecurityContextHolder.getContext().setAuthentication(authentications.get(user.getId()));}
    private void operator(Branch branch){as(managers.get(branch.getId()));}
    private Branch branch(String suffix,BranchType type) {
        var b=new Branch();b.setCode(prefix+suffix);b.setName("Chi nhánh kiểm thử "+suffix);b.setBranchType(type);
        b.setProvince(suffix);b.setDistrict("Test");b.setWard("Test");b.setDetailAddress("Địa chỉ tổng hợp QA");b.setPhone("0000000000");em.persist(b);return b;
    }
    private User user(String suffix,String role) {
        var u=new User();u.setUsername(prefix+suffix);u.setEmail(prefix+suffix+"@example.test");u.setFullName("QA "+suffix);
        u.setPasswordHash(bean(PasswordEncoder.class).encode(password));u.setPhoneNumber("090"+String.format("%07d",phone++));u.setStatus(UserStatus.ACTIVE);em.persist(u);
        var r=em.createQuery("select r from Role r where r.name=:name",Role.class).setParameter("name",role).getSingleResult();
        var mapping=new UserRole();mapping.setUser(u);mapping.setRole(r);mapping.setId(new UserRoleId(u.getId(),r.getId()));em.persist(mapping);em.flush();
        authentications.put(u.getId(),bean(AuthenticationManager.class).authenticate(UsernamePasswordAuthenticationToken.unauthenticated(u.getEmail(),password)));return u;
    }
    private Employee employee(User user,Branch branch,EmployeePosition position) {
        var e=new Employee();e.setEmployeeCode(prefix+user.getId());e.setUser(user);e.setBranch(branch);e.setFullName(user.getFullName());e.setPhoneNumber("0000000000");e.setPosition(position);e.setStatus(EmployeeStatus.ACTIVE);em.persist(e);return e;
    }
    private void manager(Branch branch,String suffix) {
        var u=user(suffix,"ROLE_MANAGER");employee(u,branch,EmployeePosition.DISPATCHER);var m=new ManagerBranch();m.setManager(u);m.setBranch(branch);em.persist(m);managers.put(branch.getId(),u);
    }
    private void route(Branch from,Branch to,TransportLeg leg) {var r=new TransportRoute();r.setOrigin(from);r.setDestination(to);r.setLeg(leg);em.persist(r);routes.put(from.getId()+":"+to.getId(),r.getId());}
    private void fixtures() {
        source=branch("SRC",BranchType.LOCAL_BRANCH);hubA=branch("HA",BranchType.CENTRAL_HUB);hubB=branch("HB",BranchType.CENTRAL_HUB);destination=branch("DST",BranchType.LOCAL_BRANCH);
        source.setParentHub(hubA);destination.setParentHub(hubB);
        owner=user("owner","ROLE_USER");other=user("other","ROLE_USER");admin=user("admin","ROLE_ADMIN");pickup=user("pickup","ROLE_SHIPPER");delivery=user("delivery","ROLE_SHIPPER");
        pickupEmployee=employee(pickup,source,EmployeePosition.COURIER_DRIVER);deliveryEmployee=employee(delivery,destination,EmployeePosition.COURIER_DRIVER);
        manager(source,"ms");manager(hubA,"ma");manager(hubB,"mb");manager(destination,"md");
        catalog=new ShippingService();catalog.setCode(prefix+"SVC");catalog.setName("Dịch vụ QA tổng hợp");catalog.setEstimatedDeliveryHours(48);em.persist(catalog);
        var version=new PricingVersion();version.setService(catalog);version.setCode(prefix+"PRICE");version.setStatus(PricingVersionStatus.PUBLISHED);version.setCreatedBy(admin);version.setUpdatedBy(admin);version.setPublishedBy(admin);version.setPublishedAt(now.minusDays(1));version.setValidFrom(now.minusDays(1));em.persist(version);
        // Synthetic test input only: always rolled back, never a deployed/default price or demo dataset.
        var rate=new ShippingRateRule();rate.setService(catalog);rate.setScopeType(RateScopeType.INTER_PROVINCE);rate.setBaseWeightKg(new BigDecimal("1.00"));rate.setBasePrice(new BigDecimal("40000.00"));rate.setExtraWeightStepKg(new BigDecimal("0.50"));rate.setExtraPricePerStep(new BigDecimal("5000.00"));rate.setEffectiveFrom(now.minusDays(1));rate.setPricingVersion(version);em.persist(rate);
        var fee=new PickupFeeRule();fee.setBranch(source);fee.setService(catalog);fee.setAmount(new BigDecimal("7500.00"));fee.setEffectiveFrom(now.minusDays(1));fee.setPricingVersion(version);em.persist(fee);
        for(Branch b:List.of(source,destination))for(ShipmentMethod method:ShipmentMethod.values()){var a=new BranchServiceArea();a.setBranch(b);a.setProvince(b.getProvince());a.setDistrict("Test");a.setWard("Test");a.setShipmentMethod(method);a.setSupportsDelivery(true);em.persist(a);}
        route(source,hubA,TransportLeg.COLLECTION);route(hubA,hubB,TransportLeg.LINEHAUL);route(hubB,destination,TransportLeg.DISTRIBUTION);
        route(destination,hubB,TransportLeg.COLLECTION);route(hubB,hubA,TransportLeg.LINEHAUL);route(hubA,source,TransportLeg.DISTRIBUTION);
        var support=new SupportTicketRoute();support.setOriginBranch(source);support.setHandlingBranch(source);em.persist(support);em.flush();
    }
    private ShipmentCreateForm form(ShipmentMethod method) {
        var f=new ShipmentCreateForm();f.setShipmentMethod(method);f.setOriginBranchId(source.getId());f.setDestinationBranchId(destination.getId());f.setServiceId(catalog.getId());
        f.setSenderName("Người gửi QA");f.setSenderPhone("0901234567");f.setSenderProvince(source.getProvince());f.setSenderDistrict("Test");f.setSenderWard("Test");f.setSenderAddress("Địa chỉ QA");
        f.setReceiverName("Người nhận QA");f.setReceiverPhone("0901234568");f.setReceiverProvince(destination.getProvince());f.setReceiverDistrict("Test");f.setReceiverWard("Test");f.setReceiverAddress("Địa chỉ QA");f.setPackageDescription("Sách kiểm thử");f.setWeightKg(new BigDecimal("1.01"));return f;
    }
    private Long v(Long id){em.flush();em.clear();return em.find(Shipment.class,id).getVersion();}
    private Long tv(Long id){em.flush();em.clear();return em.find(TransportTrip.class,id).getVersion();}
    private Long task(Long id,AssignmentType type){return em.createQuery("select a.id from ShipmentAssignment a where a.shipment.id=:id and a.assignmentType=:type and a.completedAt is null",Long.class).setParameter("id",id).setParameter("type",type).getSingleResult();}
    private Long create(ShipmentMethod method) {
        as(owner);var quotes=bean(QuoteService.class);var quote=quotes.quote(form(method));Long id=quotes.confirm(quote);check(id.equals(quotes.confirm(quote)),"quote confirmation must be idempotent");
        var s=em.find(Shipment.class,id);check(s.getPickupFee().compareTo(method==ShipmentMethod.HOME_PICKUP?new BigDecimal("7500"):BigDecimal.ZERO)==0,"pickup fee snapshot");return id;
    }
    private void intake(Long id,ShipmentMethod method) {
        var lifecycle=bean(ShipmentLifecycleService.class);operator(source);lifecycle.transition(new ShipmentTransitionCommand(id,v(id),ShipmentStatus.CONFIRMED));
        if(method==ShipmentMethod.HOME_PICKUP){
            lifecycle.assignPickup(new PickupAssignmentCommand(id,v(id),pickupEmployee.getId(),null,null));as(pickup);Long task=task(id,AssignmentType.PICKUP);
            lifecycle.collectPickup(new PickupCollectionCommand(task,v(id),PickupResult.SUCCESS,true,"QA-PICKUP",null));lifecycle.requestOriginHandoff(new OriginHandoffCommand(task,v(id),null));
            Long handoff=em.createQuery("select h.id from OriginHandoff h where h.shipment.id=:id and h.status=:status",Long.class).setParameter("id",id).setParameter("status",HandoffStatus.PENDING).getSingleResult();
            operator(source);lifecycle.reviewOriginHandoff(new HandoffReviewCommand(handoff,v(id),true,true,null));
        }else lifecycle.receiveDropoff(new BranchIntakeCommand(id,v(id),true,null));
        check(em.find(Shipment.class,id).getCurrentBranch().getId().equals(source.getId()),"origin custody requires real intake");
    }
    private TripCommands.Change change(Long trip){return new TripCommands.Change(trip,tv(trip),true,"QA-TRIP",null);}
    private void travel(Long shipment,Branch from,Branch to) {
        var trips=bean(TransportTripService.class);operator(from);Long trip=trips.create(new TripCommands.Create(routes.get(from.getId()+":"+to.getId()),token()));
        bean(TransportTripReadService.class).detail(trip,0,0,0,0);
        trips.addItems(new TripCommands.Manifest(trip,tv(trip),List.of(shipment)));
        Long item=em.createQuery("select i.id from TransportTripItem i where i.trip.id=:id",Long.class).setParameter("id",trip).getSingleResult();
        trips.seal(change(trip));trips.depart(change(trip));operator(to);trips.arrive(change(trip));
        check(em.find(Shipment.class,shipment).getCurrentBranch()==null,"arrival must not invent parcel custody");
        trips.receive(new TripCommands.Receive(trip,tv(trip),item,true,"QA-RECEIPT",null,false));trips.close(change(trip));
        check(em.find(Shipment.class,shipment).getCurrentBranch().getId().equals(to.getId()),"receipt must establish destination custody");
    }
    private void forward(Long id){travel(id,source,hubA);travel(id,hubA,hubB);travel(id,hubB,destination);}
    private Long startDelivery(Long id) {
        var last=bean(LastMileService.class);operator(destination);last.assign(new DeliveryCommands.Assign(id,v(id),deliveryEmployee.getId(),null,null));Long task=task(id,AssignmentType.DELIVERY);
        last.prepare(new DeliveryCommands.Prepare(id,v(id),task,true,"QA-DISPATCH",null));as(delivery);last.accept(new DeliveryCommands.Action(task,v(id),true,"QA-ACCEPT"));last.start(new DeliveryCommands.Action(task,v(id),true,"QA-START"));return task;
    }
    private void exercise() {
        stage="fixtures/auth";fixtures();passed(stage);
        stage="pricing/home-pickup";Long delivered=create(ShipmentMethod.HOME_PICKUP);intake(delivered,ShipmentMethod.HOME_PICKUP);passed(stage);
        stage="multi-leg/enum-regression";forward(delivered);check(em.createQuery("select count(r) from TransportRoute r where r.leg=:leg",Long.class).setParameter("leg",TransportLeg.LINEHAUL).getSingleResult()==2,"TransportLeg VARCHAR parameter round-trip");passed(stage);
        stage="delivery";Long deliveryTask=startDelivery(delivered);bean(LastMileService.class).complete(new DeliveryCommands.Complete(deliveryTask,v(delivered),true,DeliveryRecipientType.RECIPIENT,"Người nhận QA","QA-PROOF",null));check(em.find(Shipment.class,delivered).getStatus()==ShipmentStatus.DELIVERED,"delivered status");passed(stage);
        stage="dropoff/failed-delivery/return";Long returned=create(ShipmentMethod.BRANCH_DROPOFF);intake(returned,ShipmentMethod.BRANCH_DROPOFF);forward(returned);Long failed=startDelivery(returned);
        var last=bean(LastMileService.class);last.fail(new DeliveryCommands.Fail(failed,v(returned),true,DeliveryFailureCode.ABSENT,"Không có người nhận QA",null));last.requestRecovery(new DeliveryCommands.Recover(failed,v(returned),null));
        Long recovery=em.createQuery("select r.id from DeliveryRecovery r where r.shipment.id=:id",Long.class).setParameter("id",returned).getSingleResult();operator(destination);last.reviewRecovery(new DeliveryCommands.ReviewRecovery(returned,v(returned),recovery,true,true,"QA-RECOVERY",null));last.initiateReturn(new DeliveryCommands.ReturnStart(returned,v(returned),"Yêu cầu hoàn QA"));
        travel(returned,destination,hubB);travel(returned,hubB,hubA);travel(returned,hubA,source);operator(source);last.returnToSender(new DeliveryCommands.SenderReceipt(returned,v(returned),true,ReturnRecipientType.SENDER,"Người gửi QA","QA-RETURN",null));check(em.find(Shipment.class,returned).getStatus()==ShipmentStatus.RETURNED,"returned status");passed(stage);
        stage="support/V12/idempotency";as(owner);var tickets=bean(TicketService.class);Long ticket=tickets.createCustomer(new CustomerTicketCommands.Create(delivered,TicketCategory.OTHER,"Hỗ trợ QA","Nội dung QA",token()));
        var message=new CustomerTicketCommands.Message(em.find(SupportTicket.class,ticket).getVersion(),"Phản hồi khách QA",token());tickets.customerMessage(ticket,message);tickets.customerMessage(ticket,message);em.flush();check(em.createQuery("select count(m) from SupportTicketMessage m where m.ticket.id=:id",Long.class).setParameter("id",ticket).getSingleResult()==1,"customer reply retry must be idempotent");passed(stage);
        stage="notifications/SQL-inbox";
        // Process inside the enclosing rollback-only transaction. REQUIRES_NEW/retry/concurrency is deliberately not claimed.
        ShipmentNotificationProcessor shipmentProcessor=AopTestUtils.getUltimateTargetObject(bean(ShipmentNotificationProcessor.class));
        for(Long event:em.createQuery("select e.id from ShipmentEvent e where e.notificationEnabled=true order by e.id",Long.class).getResultList())shipmentProcessor.process(event);
        TicketNotificationProcessor ticketProcessor=AopTestUtils.getUltimateTargetObject(bean(TicketNotificationProcessor.class));
        for(Long event:em.createQuery("select e.id from SupportTicketEvent e order by e.id",Long.class).getResultList())ticketProcessor.process(event);
        as(owner);check(bean(NotificationService.class).list(0,20,false).totalElements()>0,"private SQL inbox");passed(stage);
        stage="reports/audit/Unicode";as(admin);
        var log=new AuditLog();log.setActor(admin);log.setAction("QA_SMOKE");log.setResourceType(AuditResourceType.USER);log.setResourceId(owner.getId().toString());String unicode="Kiểm thử tiếng Việt "+"ữ".repeat(5000);log.setNewValue(unicode);em.persist(log);em.flush();Long logId=log.getId();em.clear();check(unicode.equals(em.find(AuditLog.class,logId).getNewValue()),"NVARCHAR(MAX) round-trip");
        for(ReportViews.Dataset dataset:ReportViews.Dataset.values())bean(AdminReportService.class).read(new ReportViews.Request(dataset,ReportViews.Period.TODAY,null,null,null,null,null,ReportViews.Trend.DAY),0,50);
        check(bean(AdminAuditService.class).read(new AuditViews.Request(ReportViews.Period.TODAY,null,null,AuditViews.Source.ALL,null,null,null,null,null),0,50).total()>0,"audit UNION SQL query");passed(stage);
        stage="HTTP/security/Thymeleaf";http();passed(stage);
        stage="authorization-cache-regression";var access=bean(LogisticsAccessService.class);operator(source);access.requireManager();
        em.createNativeQuery("UPDATE dbo.users SET status='LOCKED' WHERE id=:id").setParameter("id",managers.get(source.getId()).getId()).executeUpdate();
        denied(access::requireManager,"cached manager must refresh current account status");
        as(delivery);access.requireShipper();
        em.createNativeQuery("UPDATE dbo.employees SET status='INACTIVE' WHERE id=:id").setParameter("id",deliveryEmployee.getId()).executeUpdate();
        denied(access::requireShipper,"cached employee must refresh current status");
        em.createNativeQuery("UPDATE dbo.employees SET status='ACTIVE' WHERE id=:id").setParameter("id",deliveryEmployee.getId()).executeUpdate();em.clear();access.requireShipper();
        em.createNativeQuery("UPDATE dbo.branches SET status='INACTIVE' WHERE id=:id").setParameter("id",destination.getId()).executeUpdate();
        denied(access::requireShipper,"cached branch must refresh current status");
        em.createNativeQuery("UPDATE dbo.branches SET status='ACTIVE' WHERE id=:id").setParameter("id",destination.getId()).executeUpdate();em.clear();access.requireShipper();
        Long userRole=em.createQuery("select r.id from Role r where r.name='ROLE_USER'",Long.class).getSingleResult();
        em.createNativeQuery("UPDATE dbo.user_roles SET role_id=:role WHERE user_id=:user").setParameter("role",userRole).setParameter("user",delivery.getId()).executeUpdate();
        denied(access::requireShipper,"cached authorities must not bypass database role revocation");passed(stage);
    }
    private void denied(Runnable action,String label){boolean denied=false;try{action.run();}catch(org.springframework.security.access.AccessDeniedException expected){denied=true;}check(denied,label);}
    private void http() {
        try {
            SecurityContextHolder.clearContext();org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
            var mvc=MockMvcBuilders.webAppContextSetup((WebApplicationContext)app).apply(springSecurity()).build();
            check(mvc.perform(get("/tracking")).andReturn().getResponse().getStatus()==200,"guest tracking HTML");
            check(mvc.perform(post("/login").param("email",owner.getEmail()).param("password",password)).andReturn().getResponse().getStatus()==403,"login CSRF");
            var session=new org.springframework.mock.web.MockHttpSession();String previous=session.getId();
            var login=mvc.perform(post("/login").session(session).with(csrf()).param("email",owner.getEmail()).param("password",password)).andReturn();
            check(login.getResponse().getStatus()==302&&!previous.equals(session.getId()),"BCrypt login/session fixation");
            int status=mvc.perform(get("/user/shipments").session(session)).andReturn().getResponse().getStatus();System.out.println("SQL HTTP user shipment status="+status);check(status==200,"user HTML rendering");
            check(mvc.perform(get("/admin/users").session(session)).andReturn().getResponse().getStatus()==403,"RBAC");
            check(mvc.perform(post("/logout").session(session)).andReturn().getResponse().getStatus()==403,"logout CSRF");
            check(mvc.perform(post("/logout").session(session).with(csrf())).andReturn().getResponse().getStatus()==302&&session.isInvalid(),"logout invalidation");
        }catch(Exception error){throw new IllegalStateException("SQL HTTP smoke failed",error);}
    }
}
