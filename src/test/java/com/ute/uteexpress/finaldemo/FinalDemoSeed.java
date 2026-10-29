package com.ute.uteexpress.finaldemo;

import com.ute.uteexpress.dto.delivery.DeliveryCommands.*;
import com.ute.uteexpress.dto.operations.CustomerTicketCommands;
import com.ute.uteexpress.dto.shipment.*;
import com.ute.uteexpress.dto.transport.TripCommands.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.entity.id.UserRoleId;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.*;
import com.ute.uteexpress.service.operations.TicketService;
import jakarta.persistence.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.*;

/** Test-classpath only. One atomic seed; real services generate all simulated operations/documents. */
public class FinalDemoSeed {
    @PersistenceContext EntityManager em;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired PasswordEncoder encoder;
    @Autowired Clock clock;
    @Autowired QuoteService quotes;
    @Autowired ShipmentLifecycleService lifecycle;
    @Autowired TransportTripService transport;
    @Autowired LastMileService delivery;
    @Autowired TicketService tickets;
    static final String SERVICE="FD-STANDARD", VERSION="FD-INITIAL";
    private final Map<String,Branch> branches=new LinkedHashMap<>();
    private final Map<Long,User> operators=new HashMap<>(),couriers=new HashMap<>();
    private final Map<Long,Long> employees=new HashMap<>();
    private final Map<String,Long> routes=new HashMap<>();
    private final Map<String,Long> scenarios=new LinkedHashMap<>();
    private final List<User> owners=new ArrayList<>();
    private ShippingService catalog;
    private Branch source,hub,hubB,destination;
    private String scenario;
    public static void requirePassword(String value) {
        if(value==null||value.length()<20||value.getBytes(StandardCharsets.UTF_8).length>72
                ||java.util.stream.Stream.of("[a-z]","[A-Z]","[0-9]","[^a-zA-Z0-9]").filter(p->java.util.regex.Pattern.compile(p).matcher(value).find()).count()<3)
            throw new IllegalArgumentException("Strong private website password required");
    }
    @Transactional(isolation=Isolation.SERIALIZABLE)
    public boolean seed(String password) {
        try {
            requireTarget();
            long marker=em.createQuery("select count(p) from PricingVersion p where p.code=:code",Long.class).setParameter("code",VERSION).getSingleResult();
            if(marker==1){requireComplete();return false;}
            for(String type:List.of("User","Branch","ShippingService","Shipment"))if(count(type)!=0)throw new IllegalStateException("Fresh reference tables required; no overwrite or repair");
            requirePassword(password);
            branches.clear();operators.clear();couriers.clear();employees.clear();routes.clear();scenarios.clear();owners.clear();
            references(password);
            for(String code:SCENARIOS){scenario=code;Long id=switch(code){
                case "NEW_DROP","SUPPORT"->created(ShipmentMethod.BRANCH_DROPOFF);
                case "CONFIRMED_DROP"->confirmed(ShipmentMethod.BRANCH_DROPOFF);
                case "PICKUP_ASSIGNED"->pickupAssigned();
                case "PICKED_UP"->collected();
                case "HANDOFF_PENDING"->handoffPending();
                case "ORIGIN_INTAKE_HOME"->homeIntake();
                case "ORIGIN_INTAKE_DROP"->intake(ShipmentMethod.BRANCH_DROPOFF);
                case "ORIGIN_HUB"->{Long x=intake(ShipmentMethod.BRANCH_DROPOFF);travel(x,source,hub);yield x;}
                case "LINEHAUL"->{Long x=intake(ShipmentMethod.BRANCH_DROPOFF);travel(x,source,hub);depart(manifest(x,hub,hubB));yield x;}
                case "DEST_HUB"->{Long x=intake(ShipmentMethod.BRANCH_DROPOFF);travel(x,source,hub);travel(x,hub,hubB);yield x;}
                case "DEST_BRANCH"->atDestination();
                case "DELIVERY_ASSIGNED"->{Long x=atDestination();assign(x);yield x;}
                case "DELIVERY_RUNNING"->{Long x=atDestination();running(x);yield x;}
                case "DELIVERED"->{Long x=atDestination(),task=running(x);delivery.complete(new Complete(task,version(x),true,DeliveryRecipientType.RECIPIENT,"Người nhận mô phỏng","FD-SIM-DELIVERY","Chứng từ mô phỏng"));yield x;}
                case "DELIVERY_FAILED"->{Long x=atDestination();fail(x,running(x));yield x;}
                case "REDELIVERY"->{Long x=atDestination();fail(x,running(x));operator(destination);delivery.redelivery(new Redelivery(x,version(x),employees.get(destination.getId()),"Giao lại mô phỏng"));yield x;}
                case "RETURNING"->returning();
                case "RETURNED"->{Long x=returning();travel(x,destination,hubB);travel(x,hubB,hub);travel(x,hub,source);operator(source);delivery.returnToSender(new SenderReceipt(x,version(x),true,ReturnRecipientType.SENDER,"Người gửi mô phỏng","FD-SIM-SENDER","Hoàn hàng mô phỏng"));yield x;}
                case "CANCELLED"->{Long x=created(ShipmentMethod.BRANCH_DROPOFF);operator(source);lifecycle.transition(new ShipmentTransitionCommand(x,version(x),ShipmentStatus.CANCELLED,"Hủy kiện mô phỏng theo yêu cầu người gửi"));yield x;}
                default->throw new IllegalStateException("Unknown scenario");
            };scenarios.put(code,id);}
            as(owners.get((SCENARIOS.indexOf("SUPPORT"))%owners.size()),"USER");
            tickets.createCustomer(new CustomerTicketCommands.Create(scenarios.get("SUPPORT"),TicketCategory.DELAY,"Hỗ trợ mô phỏng","Nội dung hỗ trợ mô phỏng",token()));
            em.flush();requireComplete();return true;
        }finally{SecurityContextHolder.clearContext();}
    }
    public static final List<String> SCENARIOS=List.of("NEW_DROP","CONFIRMED_DROP","PICKUP_ASSIGNED","PICKED_UP","HANDOFF_PENDING","ORIGIN_INTAKE_DROP","ORIGIN_INTAKE_HOME","ORIGIN_HUB","LINEHAUL","DEST_HUB","DEST_BRANCH","DELIVERY_ASSIGNED","DELIVERY_RUNNING","DELIVERED","DELIVERY_FAILED","REDELIVERY","RETURNING","RETURNED","CANCELLED","SUPPORT");
    private void requireTarget(){
        try(var c=dataSource.getConnection()){
            if("H2".equals(c.getMetaData().getDatabaseProductName())&&c.getMetaData().getURL().startsWith("jdbc:h2:mem:uteexpress_finaldemo_test_"))return;
            FinalDemoTarget.verify(c);
        }catch(java.sql.SQLException e){throw new IllegalStateException("Final Demo seed target rejected",e);}
    }
    private long count(String type){return em.createQuery("select count(x) from "+type+" x",Long.class).getSingleResult();}
    public void requireComplete(){
        for(var expected:Map.of("Branch",42L,"TransportRoute",112L,"BranchServiceArea",70L,"Employee",77L,"ManagerBranch",42L).entrySet())
            if(count(expected.getKey())<expected.getValue())throw new IllegalStateException("Incomplete Final Demo namespace");
        for(String code:SCENARIOS)if(em.createQuery("select count(s) from Shipment s where s.note=:note",Long.class).setParameter("note","FD-SCENARIO:"+code).getSingleResult()!=1)throw new IllegalStateException("Incomplete scenario namespace");
        for(var n:FinalDemoNetwork.nodes())if(em.createQuery("select count(b) from Branch b where b.code=:code",Long.class).setParameter("code","FD-"+n.code()).getSingleResult()!=1)throw new IllegalStateException("Missing demo branch");
    }
    private void references(String password){
        Map<String,Role> roles=new HashMap<>();for(Role r:em.createQuery("select r from Role r",Role.class).getResultList())roles.put(r.getName(),r);
        if(!roles.keySet().containsAll(Set.of("ROLE_ADMIN","ROLE_MANAGER","ROLE_SHIPPER","ROLE_USER")))throw new IllegalStateException("Migrated roles missing");
        var nodes=FinalDemoNetwork.nodes();
        for(var n:nodes.stream().sorted(Comparator.comparing(n->n.type()==BranchType.CENTRAL_HUB?0:1)).toList()){
            Branch b=new Branch();b.setCode("FD-"+n.code());b.setName(n.name()+" (mô phỏng)");b.setProvince(n.province());b.setDistrict(n.legacyArea());b.setWard(n.ward());b.setDetailAddress(n.address());b.setPhone("0000000000");b.setBranchType(n.type());
            if(n.type()==BranchType.LOCAL_BRANCH)b.setParentHub(branches.get(n.hub()));em.persist(b);branches.put(n.code(),b);
        }
        int number=0;
        User admin=user("admin","ROLE_ADMIN",password,roles,++number);
        for(var n:nodes){var b=branches.get(n.code());var manager=user("manager-"+n.code().toLowerCase(Locale.ROOT),"ROLE_MANAGER",password,roles,++number);employee(manager,b,true);operators.put(b.getId(),manager);
            if(n.type()==BranchType.LOCAL_BRANCH){var courier=user("shipper-"+n.code().toLowerCase(Locale.ROOT),"ROLE_SHIPPER",password,roles,++number);employees.put(b.getId(),employee(courier,b,false));couriers.put(b.getId(),courier);
                for(var method:ShipmentMethod.values()){var area=new BranchServiceArea();area.setBranch(b);area.setProvince(b.getProvince());area.setDistrict(b.getDistrict());area.setWard(b.getWard());area.setShipmentMethod(method);area.setSupportsDelivery(true);em.persist(area);}
                route(b,b.getParentHub(),TransportLeg.COLLECTION);route(b.getParentHub(),b,TransportLeg.DISTRIBUTION);
                var support=new SupportTicketRoute();support.setOriginBranch(b);support.setHandlingBranch(b);em.persist(support);
            }
        }
        for(var a:branches.values())for(var b:branches.values())if(a!=b&&a.getBranchType()==BranchType.CENTRAL_HUB&&b.getBranchType()==BranchType.CENTRAL_HUB)route(a,b,TransportLeg.LINEHAUL);
        for(int i=1;i<=12;i++)owners.add(user(String.format("user-%02d",i),"ROLE_USER",password,roles,++number));
        catalog=new ShippingService();catalog.setCode(SERVICE);catalog.setName("Tiêu chuẩn mô phỏng - giá giả định học thuật");catalog.setEstimatedDeliveryHours(72);catalog.setCreatedBy(admin);catalog.setUpdatedBy(admin);em.persist(catalog);
        var since=LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC).minusDays(1);
        var version=new PricingVersion();version.setCode(VERSION);version.setService(catalog);version.setStatus(PricingVersionStatus.PUBLISHED);version.setCreatedBy(admin);version.setUpdatedBy(admin);version.setPublishedBy(admin);version.setValidFrom(since);version.setPublishedAt(since);em.persist(version);
        try(var in=getClass().getResourceAsStream("/final-demo/prices.csv")){
            if(in==null)throw new IllegalStateException("Price fixture missing");var seen=EnumSet.noneOf(RateScopeType.class);
            for(String line:new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8)).lines().skip(1).toList()){
                var c=line.split(",");var scope=RateScopeType.valueOf(c[0]);if(!RateScopeType.nationwide().contains(scope)||!seen.add(scope))throw new IllegalStateException("Invalid demo tariff");
                var r=new ShippingRateRule();r.setService(catalog);r.setPricingVersion(version);r.setScopeType(scope);r.setBasePrice(new BigDecimal(c[1]));r.setBaseWeightKg(new BigDecimal(c[2]));r.setExtraWeightStepKg(new BigDecimal(c[3]));r.setExtraPricePerStep(new BigDecimal(c[4]));r.setEffectiveFrom(since);em.persist(r);
            }if(seen.size()!=5)throw new IllegalStateException("Five scopes required");
        }catch(IOException e){throw new UncheckedIOException(e);}
        var fee=new PickupFeeRule();fee.setService(catalog);fee.setPricingVersion(version);fee.setEffectiveFrom(since);fee.setAmount(pickupAmount());em.persist(fee);
        source=branches.get("B-79");hub=source.getParentHub();destination=branches.get("B-48");hubB=destination.getParentHub();em.flush();
    }
    private BigDecimal pickupAmount(){
        try(var in=getClass().getResourceAsStream("/final-demo/pickup-fees.csv")){
            if(in==null)throw new IllegalStateException("Pickup price fixture missing");
            var rows=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8)).lines().skip(1).toList();
            if(rows.size()!=1)throw new IllegalStateException("One academic pickup tariff required");
            var amount=new BigDecimal(rows.getFirst());if(amount.signum()<=0)throw new IllegalStateException("Positive configured pickup fee required");return amount;
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    private User user(String name,String role,String password,Map<String,Role> roles,int number){
        var u=new User();u.setUsername("final-"+name);u.setEmail(u.getUsername()+"@example.test");u.setFullName("Tài khoản mô phỏng "+name);u.setPhoneNumber(String.format("000%07d",number));u.setStatus(UserStatus.ACTIVE);u.setPasswordHash(encoder.encode(password));em.persist(u);em.flush();
        var membership=new UserRole();membership.setUser(u);membership.setRole(roles.get(role));membership.setId(new UserRoleId(u.getId(),roles.get(role).getId()));em.persist(membership);return u;
    }
    private Long employee(User u,Branch b,boolean manager){var e=new Employee();e.setUser(u);e.setBranch(b);e.setEmployeeCode("FD-"+u.getId());e.setFullName(u.getFullName());e.setPhoneNumber(u.getPhoneNumber());e.setPosition(manager?EmployeePosition.DISPATCHER:EmployeePosition.COURIER_DRIVER);em.persist(e);if(manager){var m=new ManagerBranch();m.setManager(u);m.setBranch(b);em.persist(m);}em.flush();return e.getId();}
    private void route(Branch from,Branch to,TransportLeg leg){var r=new TransportRoute();r.setOrigin(from);r.setDestination(to);r.setLeg(leg);em.persist(r);em.flush();routes.put(from.getId()+":"+to.getId(),r.getId());}
    private <T>T db(Supplier<T> work){em.flush();return work.get();}
    private void as(User u,String role){SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(u.getEmail(),null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));}
    private void operator(Branch b){as(operators.get(b.getId()),"MANAGER");}
    private String token(){return UUID.randomUUID().toString().replace("-","");}
    private Long version(Long id){return db(()->em.find(Shipment.class,id).getVersion());}
    private Long tripVersion(Long id){return db(()->em.find(TransportTrip.class,id).getVersion());}
    private Long assignment(Long id,AssignmentType type){return db(()->em.createQuery("select a.id from ShipmentAssignment a where a.shipment.id=:id and a.assignmentType=:type and a.completedAt is null",Long.class).setParameter("id",id).setParameter("type",type).getSingleResult());}
    private Long created(ShipmentMethod method){
        as(owners.get(SCENARIOS.indexOf(scenario)%owners.size()),"USER");var form=new ShipmentCreateForm();form.setShipmentMethod(method);form.setSenderName("Người gửi mô phỏng");form.setSenderPhone("0000000001");form.setReceiverName("Người nhận mô phỏng");form.setReceiverPhone("0000000002");
        form.setSenderProvince(source.getProvince());form.setSenderDistrict(source.getDistrict());form.setSenderWard(source.getWard());form.setSenderAddress(source.getDetailAddress());form.setReceiverProvince(destination.getProvince());form.setReceiverDistrict(destination.getDistrict());form.setReceiverWard(destination.getWard());form.setReceiverAddress(destination.getDetailAddress());
        form.setOriginBranchId(source.getId());form.setDestinationBranchId(destination.getId());form.setServiceId(catalog.getId());form.setPackageDescription("Sách - kiện mô phỏng");form.setWeightKg(new BigDecimal("1.25"));form.setNote("FD-SCENARIO:"+scenario);
        return quotes.confirm(quotes.quote(form));
    }
    private Long confirmed(ShipmentMethod m){Long id=created(m);operator(source);lifecycle.transition(new ShipmentTransitionCommand(id,version(id),ShipmentStatus.CONFIRMED));return id;}
    private Long pickupAssigned(){Long id=confirmed(ShipmentMethod.HOME_PICKUP);operator(source);lifecycle.assignPickup(new PickupAssignmentCommand(id,version(id),employees.get(source.getId()),null,"Mô phỏng"));return id;}
    private Long collected(){Long id=pickupAssigned();as(couriers.get(source.getId()),"SHIPPER");lifecycle.collectPickup(new PickupCollectionCommand(assignment(id,AssignmentType.PICKUP),version(id),PickupResult.SUCCESS,true,"FD-SIM-PICKUP","Mô phỏng"));return id;}
    private Long handoffPending(){Long id=collected();lifecycle.requestOriginHandoff(new OriginHandoffCommand(assignment(id,AssignmentType.PICKUP),version(id),"Bàn giao mô phỏng"));return id;}
    private Long homeIntake(){Long id=handoffPending();Long handoff=db(()->em.createQuery("select h.id from OriginHandoff h where h.shipment.id=:id",Long.class).setParameter("id",id).getSingleResult());operator(source);lifecycle.reviewOriginHandoff(new HandoffReviewCommand(handoff,version(id),true,true,"Tiếp nhận mô phỏng"));return id;}
    private Long intake(ShipmentMethod m){Long id=confirmed(m);operator(source);lifecycle.receiveDropoff(new BranchIntakeCommand(id,version(id),true,"Tiếp nhận mô phỏng"));return id;}
    private record Leg(Long trip,Branch from,Branch to){}
    private Leg manifest(Long id,Branch from,Branch to){operator(from);Long trip=transport.create(new Create(routes.get(from.getId()+":"+to.getId()),token()));transport.addItems(new Manifest(trip,tripVersion(trip),List.of(id)));return new Leg(trip,from,to);}
    private Long item(Long trip,Long id){return db(()->em.createQuery("select i.id from TransportTripItem i where i.trip.id=:trip and i.shipment.id=:id",Long.class).setParameter("trip",trip).setParameter("id",id).getSingleResult());}
    private Change change(Long trip){return new Change(trip,tripVersion(trip),true,"FD-SIM-TRIP","Chuyến mô phỏng");}
    private void depart(Leg leg){operator(leg.from());transport.seal(change(leg.trip()));transport.depart(change(leg.trip()));}
    private void travel(Long id,Branch from,Branch to){var leg=manifest(id,from,to);depart(leg);operator(to);transport.arrive(change(leg.trip()));transport.receive(new Receive(leg.trip(),tripVersion(leg.trip()),item(leg.trip(),id),true,"FD-SIM-RECEIPT","Mô phỏng",false));transport.close(change(leg.trip()));}
    private Long atDestination(){Long id=intake(ShipmentMethod.BRANCH_DROPOFF);travel(id,source,hub);travel(id,hub,hubB);travel(id,hubB,destination);return id;}
    private Long assign(Long id){operator(destination);delivery.assign(new Assign(id,version(id),employees.get(destination.getId()),null,"Mô phỏng"));return assignment(id,AssignmentType.DELIVERY);}
    private Long running(Long id){Long task=assign(id);delivery.prepare(new Prepare(id,version(id),task,true,"FD-SIM-HANDOFF","Mô phỏng"));as(couriers.get(destination.getId()),"SHIPPER");delivery.accept(new Action(task,version(id),true,"FD-SIM-ACCEPT"));delivery.start(new Action(task,version(id),true,"FD-SIM-START"));return task;}
    private void fail(Long id,Long task){as(couriers.get(destination.getId()),"SHIPPER");delivery.fail(new Fail(task,version(id),true,DeliveryFailureCode.ABSENT,"Người nhận mô phỏng vắng mặt","Mô phỏng"));}
    private Long returning(){Long id=atDestination(),task=running(id);fail(id,task);operator(destination);delivery.initiateReturn(new ReturnStart(id,version(id),"Yêu cầu hoàn mô phỏng"));as(couriers.get(destination.getId()),"SHIPPER");delivery.requestRecovery(new Recover(task,version(id),"Mô phỏng"));Long recovery=db(()->em.createQuery("select r.id from DeliveryRecovery r where r.shipment.id=:id",Long.class).setParameter("id",id).getSingleResult());operator(destination);delivery.reviewRecovery(new ReviewRecovery(id,version(id),recovery,true,true,"FD-SIM-RECOVERY","Mô phỏng"));return id;}
}
