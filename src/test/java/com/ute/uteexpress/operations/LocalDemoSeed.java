package com.ute.uteexpress.operations;

import com.ute.uteexpress.dto.delivery.DeliveryCommands.*;
import com.ute.uteexpress.dto.operations.CustomerTicketCommands;
import com.ute.uteexpress.dto.operations.TicketCommands;
import com.ute.uteexpress.dto.shipment.*;
import com.ute.uteexpress.dto.transport.TripCommands.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.*;
import com.ute.uteexpress.service.operations.TicketService;
import com.ute.uteexpress.service.notifications.*;
import com.ute.uteexpress.shipment.ShipmentFixtures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Bootstrap synthetic reference data, then create every shipment and physical document through real services. */
public final class LocalDemoSeed {
    @PersistenceContext EntityManager em;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired PasswordEncoder encoder;
    @Autowired Clock clock;
    @Autowired QuoteService quotes;
    @Autowired ShipmentLifecycleService lifecycle;
    @Autowired TransportTripService transport;
    @Autowired LastMileService delivery;
    @Autowired TicketService tickets;
    @Autowired NotificationWorker shipmentWorker;
    @Autowired TicketNotificationWorker ticketWorker;
    private TransactionTemplate tx;
    private OperationsJdbcFixture f;
    private Branch hubB;
    private User pickup, hubAOperator, hubBOperator;
    private Long pickupEmployee, deliveryEmployee;
    private boolean started;
    private Map<String,Long> completed;
    private final Map<Long,User> operators=new HashMap<>();
    private final Map<String,Long> routes=new HashMap<>();
    public static void requirePassword(String value) {
        if (value==null || value.length()<16 || value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72) {
            throw new IllegalArgumentException("Set UTE_DEMO_PASSWORD to a dedicated 16–72 character demo credential; value is never printed.");
        }
    }
    private <T>T db(Supplier<T> work) { return tx.execute(status->work.get()); }
    public synchronized Map<String,Long> seed(String password) {
        requirePassword(password);
        if (completed!=null) { return completed; }
        if (started) { throw new IllegalStateException("Incomplete seed: restart this disposable demo process; do not resume partial workflows."); }
        try (var c=dataSource.getConnection()) {
            if (!c.getMetaData().getURL().startsWith("jdbc:h2:mem:uteexpress_demo_")
                    || !"H2".equals(c.getMetaData().getDatabaseProductName())) {
                throw new IllegalStateException("Demo seed accepts only its own newly allocated H2 memory database.");
            }
        } catch (java.sql.SQLException error) { throw new IllegalStateException("Cannot verify disposable demo database"); }
        started=true; tx=new TransactionTemplate(transactionManager);
        try {
            referenceData(password);
            var scenarios=new LinkedHashMap<String,Long>();
            scenarios.put("NEW", created(ShipmentMethod.BRANCH_DROPOFF));
            scenarios.put("CONFIRMED", confirmed(ShipmentMethod.BRANCH_DROPOFF));
            scenarios.put("HOME_PICKUP", pickupAssigned());
            scenarios.put("BRANCH_DROPOFF", confirmed(ShipmentMethod.BRANCH_DROPOFF));
            scenarios.put("PICKED_UP", collected());
            scenarios.put("ORIGIN_INTAKE", intake(ShipmentMethod.BRANCH_DROPOFF));
            Long hub=intake(ShipmentMethod.BRANCH_DROPOFF);travel(hub,f.source,f.hub);scenarios.put("SOURCE_HUB",hub);
            Long line=intake(ShipmentMethod.BRANCH_DROPOFF);travel(line,f.source,f.hub);var lineLeg=manifest(line,f.hub,hubB);depart(lineLeg);scenarios.put("LINEHAUL",line);
            Long dest=atDestination();scenarios.put("DESTINATION",dest);
            Long assigned=atDestination();assign(assigned);scenarios.put("DELIVERY_ASSIGNED",assigned);
            Long delivered=atDestination(),task=running(delivered);as(f.shipper,"SHIPPER");delivery.complete(new Complete(task,version(delivered),true,DeliveryRecipientType.RECIPIENT,"Người nhận demo","DEMO-DELIVERY",null));scenarios.put("DELIVERED",delivered);
            Long failed=atDestination();fail(failed,running(failed));scenarios.put("FAILED",failed);
            Long retry=atDestination();Long retryTask=running(retry);fail(retry,retryTask);operator(f.destination);delivery.redelivery(new Redelivery(retry,version(retry),deliveryEmployee,"Giao lại dữ liệu demo"));scenarios.put("REDELIVERY",retry);
            Long returning=returning();scenarios.put("RETURNING",returning);
            Long returned=returning();travel(returned,f.destination,hubB);travel(returned,hubB,f.hub);travel(returned,f.hub,f.source);operator(f.source);delivery.returnToSender(new SenderReceipt(returned,version(returned),true,ReturnRecipientType.SENDER,"Người gửi demo","DEMO-SENDER-RECEIPT",null));scenarios.put("RETURNED",returned);
            Long missing=intake(ShipmentMethod.BRANCH_DROPOFF);var missingLeg=manifest(missing,f.source,f.hub);depart(missingLeg);operator(f.hub);transport.arrive(change(missingLeg.trip()));transport.recordDiscrepancy(new Discrepancy(missingLeg.trip(),tripVersion(missingLeg.trip()),item(missingLeg.trip(),missing),TripDiscrepancyKind.MISSING,"DEMO-MISSING","Ghi chú điều tra nội bộ demo"));scenarios.put("DISCREPANCY",missing);
            Long support=created(ShipmentMethod.HOME_PICKUP);as(f.owner,"USER");Long ticket=tickets.createCustomer(new CustomerTicketCommands.Create(support,TicketCategory.DELAY,"Hỗ trợ đơn demo","Nội dung khách hàng demo",token()));operator(f.destination);tickets.reply(ticket,new TicketCommands.Message(ticketVersion(ticket),"Phản hồi công khai demo"));tickets.note(ticket,new TicketCommands.Message(ticketVersion(ticket),"GHI_CHU_NOI_BO_DEMO_KHONG_CONG_KHAI"));scenarios.put("SUPPORT",support);
            scenarios.put("NOTIFICATION",created(ShipmentMethod.BRANCH_DROPOFF));
            shipmentWorker.poll();ticketWorker.poll();
            completed=Collections.unmodifiableMap(scenarios);return completed;
        } finally { SecurityContextHolder.clearContext(); }
    }
    private void referenceData(String password) {
        db(()->{
            if (em.createQuery("select count(u) from User u",Long.class).getSingleResult()!=0) { throw new IllegalStateException("Demo requires an empty, newly allocated database"); }
            f=new OperationsJdbcFixture(em);
            location(f.source,"DEMO-DN","Chi nhánh Đà Nẵng demo","Đà Nẵng");
            location(f.hub,"DEMO-HUB-DN","Hub miền Trung demo","Đà Nẵng");
            location(f.destination,"DEMO-HCM","Chi nhánh TP.HCM demo","Hồ Chí Minh");
            location(f.foreign,"DEMO-OTHER","Chi nhánh ngoài phạm vi demo","Đồng Nai");
            hubB=f.branch("DEMO-HUB-HCM",BranchType.CENTRAL_HUB);location(hubB,"DEMO-HUB-HCM","Hub phía Nam demo","Hồ Chí Minh");
            f.source.setParentHub(f.hub);f.destination.setParentHub(hubB);
            pickup=f.user("pickup-courier","ROLE_SHIPPER");pickupEmployee=f.employee(pickup,f.source,EmployeePosition.COURIER_DRIVER).getId();
            hubAOperator=f.user("hub-origin-manager","ROLE_MANAGER");f.employee(hubAOperator,f.hub,EmployeePosition.WAREHOUSE_STAFF);f.membership(hubAOperator,f.hub);
            hubBOperator=f.user("hub-dest-manager","ROLE_MANAGER");f.employee(hubBOperator,hubB,EmployeePosition.WAREHOUSE_STAFF);f.membership(hubBOperator,hubB);
            em.flush();deliveryEmployee=em.createQuery("select e.id from Employee e where e.user.id=:id",Long.class).setParameter("id",f.shipper.getId()).getSingleResult();
            for (User user:em.createQuery("select u from User u",User.class).getResultList()) { user.setPasswordHash(encoder.encode(password));user.setFullName("Tài khoản demo "+user.getUsername()); }
            f.catalog.setCode("DEMO-STANDARD");f.catalog.setName("Dịch vụ tiêu chuẩn demo");
            for(Branch b:List.of(f.source,f.destination))for(ShipmentMethod method:ShipmentMethod.values()) {
                var area=new BranchServiceArea();area.setBranch(b);area.setProvince(b.getProvince());area.setDistrict("Khu vực demo");area.setWard("Phường demo");area.setShipmentMethod(method);area.setSupportsDelivery(true);em.persist(area);
            }
            LocalDateTime since=LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC).minusDays(1);
            var version=new PricingVersion();version.setService(f.catalog);version.setCode("DEMO-PUBLISHED");version.setStatus(PricingVersionStatus.PUBLISHED);version.setCreatedBy(f.admin);version.setUpdatedBy(f.admin);version.setPublishedBy(f.admin);version.setPublishedAt(since);version.setValidFrom(since);em.persist(version);
            for(RateScopeType scope:List.of(RateScopeType.INTRA_PROVINCE,RateScopeType.INTER_PROVINCE)) {
                var rate=ShipmentFixtures.rate();rate.setService(f.catalog);rate.setScopeType(scope);rate.setEffectiveFrom(since);rate.setPricingVersion(version);rate.setBasePrice(new BigDecimal(scope==RateScopeType.INTRA_PROVINCE?"20000.00":"40000.00"));em.persist(rate);
            }
            var fee=new PickupFeeRule();fee.setBranch(f.source);fee.setService(f.catalog);fee.setPricingVersion(version);fee.setEffectiveFrom(since);fee.setAmount(new BigDecimal("7500.00"));em.persist(fee);
            var routing=new SupportTicketRoute();routing.setOriginBranch(f.source);routing.setHandlingBranch(f.destination);em.persist(routing);
            route(f.source,f.hub,TransportLeg.COLLECTION);route(f.hub,hubB,TransportLeg.LINEHAUL);route(hubB,f.destination,TransportLeg.DISTRIBUTION);
            route(f.destination,hubB,TransportLeg.COLLECTION);route(hubB,f.hub,TransportLeg.LINEHAUL);route(f.hub,f.source,TransportLeg.DISTRIBUTION);
            return null;
        });
        operators.put(f.source.getId(),f.manager);operators.put(f.hub.getId(),hubAOperator);operators.put(hubB.getId(),hubBOperator);operators.put(f.destination.getId(),f.destinationManager);
    }
    private void location(Branch b,String code,String name,String province) { b.setCode(code);b.setName(name);b.setProvince(province);b.setDistrict("Khu vực demo");b.setWard("Phường demo");b.setDetailAddress("Địa điểm mô phỏng, không phải cơ sở vận hành thật"); }
    private void route(Branch from,Branch to,TransportLeg leg) {var route=new TransportRoute();route.setOrigin(from);route.setDestination(to);route.setLeg(leg);em.persist(route);em.flush();routes.put(from.getId()+":"+to.getId(),route.getId());}
    private void as(User user,String role) {SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user.getEmail(),null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));}
    private void operator(Branch b) {as(operators.get(b.getId()),"MANAGER");}
    private String token() {return UUID.randomUUID().toString().replace("-","");}
    private Long version(Long id) {return db(()->em.find(Shipment.class,id).getVersion());}
    private Long tripVersion(Long id) {return db(()->em.find(TransportTrip.class,id).getVersion());}
    private Long ticketVersion(Long id) {return db(()->em.find(SupportTicket.class,id).getVersion());}
    private Long assignment(Long id,AssignmentType type) {return db(()->em.createQuery("select a.id from ShipmentAssignment a where a.shipment.id=:id and a.assignmentType=:type and a.completedAt is null",Long.class).setParameter("id",id).setParameter("type",type).getSingleResult());}
    private Long created(ShipmentMethod method) {
        as(f.owner,"USER");var form=ShipmentFixtures.form();form.setShipmentMethod(method);form.setOriginBranchId(f.source.getId());form.setDestinationBranchId(f.destination.getId());form.setServiceId(f.catalog.getId());form.setSenderProvince(f.source.getProvince());form.setReceiverProvince(f.destination.getProvince());form.setSenderDistrict("Khu vực demo");form.setReceiverDistrict("Khu vực demo");form.setSenderWard("Phường demo");form.setReceiverWard("Phường demo");
        return quotes.confirm(quotes.quote(form));
    }
    private Long confirmed(ShipmentMethod method) {Long id=created(method);operator(f.source);lifecycle.transition(new ShipmentTransitionCommand(id,version(id),ShipmentStatus.CONFIRMED));return id;}
    private Long pickupAssigned() {Long id=confirmed(ShipmentMethod.HOME_PICKUP);operator(f.source);lifecycle.assignPickup(new PickupAssignmentCommand(id,version(id),pickupEmployee,null,null));return id;}
    private Long collected() {Long id=pickupAssigned();as(pickup,"SHIPPER");lifecycle.collectPickup(new PickupCollectionCommand(assignment(id,AssignmentType.PICKUP),version(id),PickupResult.SUCCESS,true,"DEMO-PICKUP",null));return id;}
    private Long intake(ShipmentMethod method) {Long id=confirmed(method);operator(f.source);lifecycle.receiveDropoff(new BranchIntakeCommand(id,version(id),true,null));return id;}
    private record Leg(Long trip,Branch from,Branch to) { }
    private Leg manifest(Long id,Branch from,Branch to) {operator(from);Long trip=transport.create(new Create(routes.get(from.getId()+":"+to.getId()),token()));transport.addItems(new Manifest(trip,tripVersion(trip),List.of(id)));return new Leg(trip,from,to);}
    private Long item(Long trip,Long id) {return db(()->em.createQuery("select i.id from TransportTripItem i where i.trip.id=:trip and i.shipment.id=:id",Long.class).setParameter("trip",trip).setParameter("id",id).getSingleResult());}
    private Change change(Long trip) {return new Change(trip,tripVersion(trip),true,"DEMO-TRIP",null);}
    private void depart(Leg leg) {operator(leg.from());transport.seal(change(leg.trip()));transport.depart(change(leg.trip()));}
    private void travel(Long id,Branch from,Branch to) {var leg=manifest(id,from,to);depart(leg);operator(to);transport.arrive(change(leg.trip()));transport.receive(new Receive(leg.trip(),tripVersion(leg.trip()),item(leg.trip(),id),true,"DEMO-RECEIPT",null,false));transport.close(change(leg.trip()));}
    private Long atDestination() {Long id=intake(ShipmentMethod.BRANCH_DROPOFF);travel(id,f.source,f.hub);travel(id,f.hub,hubB);travel(id,hubB,f.destination);return id;}
    private Long assign(Long id) {operator(f.destination);delivery.assign(new Assign(id,version(id),deliveryEmployee,null,null));return assignment(id,AssignmentType.DELIVERY);}
    private Long running(Long id) {Long task=assign(id);delivery.prepare(new Prepare(id,version(id),task,true,"DEMO-HANDOFF",null));as(f.shipper,"SHIPPER");delivery.accept(new Action(task,version(id),true,"DEMO-ACCEPT"));delivery.start(new Action(task,version(id),true,"DEMO-START"));return task;}
    private void fail(Long id,Long task) {as(f.shipper,"SHIPPER");delivery.fail(new Fail(task,version(id),true,DeliveryFailureCode.ABSENT,"Người nhận demo vắng mặt",null));}
    private Long returning() {Long id=atDestination(),task=running(id);fail(id,task);operator(f.destination);delivery.initiateReturn(new ReturnStart(id,version(id),"Yêu cầu hoàn demo"));as(f.shipper,"SHIPPER");delivery.requestRecovery(new Recover(task,version(id),null));Long recovery=db(()->em.createQuery("select r.id from DeliveryRecovery r where r.shipment.id=:id",Long.class).setParameter("id",id).getSingleResult());operator(f.destination);delivery.reviewRecovery(new ReviewRecovery(id,version(id),recovery,true,true,"DEMO-RECOVERY",null));return id;}
}
