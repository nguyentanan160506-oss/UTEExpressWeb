package com.ute.uteexpress.demo;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.entity.id.UserRoleId;
import com.ute.uteexpress.enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Reference data only, one serializable transaction. Never generates shipments, events or fake operational proof. */
public class SqlDemoSeed {
    @PersistenceContext EntityManager em;
    @Autowired PasswordEncoder encoder;
    @Autowired Clock clock;
    static final String SERVICE="DEMO23-STANDARD";
    static final List<String> ACCOUNTS=List.of("owner","other","admin","manager","hub-origin-manager",
        "hub-dest-manager","destination-manager","pickup-courier","shipper");
    @Transactional(isolation=Isolation.SERIALIZABLE)
    public boolean seed(String password) {
        // The fixed, per-connection guarded DataSource is supplied only by SqlDemoApplication.
        if(count("ShippingService","code",SERVICE)>0) {
            if(count("Branch","code","DEMO23-DN")!=1||count("Branch","code","DEMO23-HCM")!=1
                ||ACCOUNTS.stream().anyMatch(n->count("User","username","demo23-"+n)!=1))
                throw new IllegalStateException("Incomplete demo namespace; stop for investigation, never repair or overwrite");
            return false; // No password reset, no repricing, no deletion, no account activation.
        }
        if(em.createQuery("select count(u) from User u",Long.class).getSingleResult()!=0
                ||em.createQuery("select count(b) from Branch b",Long.class).getSingleResult()!=0
                ||em.createQuery("select count(s) from ShippingService s",Long.class).getSingleResult()!=0)
            throw new IllegalStateException("First seed requires fresh reference tables; never seed an existing customer database");
        com.ute.uteexpress.operations.LocalDemoSeed.requirePassword(password);
        Map<String,Role> roles=new HashMap<>();
        for(Role role:em.createQuery("select r from Role r",Role.class).getResultList())roles.put(role.getName(),role);
        if(!roles.keySet().containsAll(Set.of("ROLE_USER","ROLE_ADMIN","ROLE_MANAGER","ROLE_SHIPPER")))
            throw new IllegalStateException("Flyway roles missing");
        Map<String,User> users=new HashMap<>();int number=0;
        for(String name:ACCOUNTS) {
            User u=new User();u.setUsername("demo23-"+name);u.setEmail(u.getUsername()+"@example.test");
            u.setFullName("Demo "+name);u.setPhoneNumber(String.format("09823%05d",++number));
            u.setPasswordHash(encoder.encode(password));u.setStatus(UserStatus.ACTIVE);em.persist(u);em.flush();
            String role=name.equals("admin")?"ROLE_ADMIN":name.contains("manager")?"ROLE_MANAGER":
                Set.of("shipper","pickup-courier").contains(name)?"ROLE_SHIPPER":"ROLE_USER";
            UserRole membership=new UserRole();membership.setUser(u);membership.setRole(roles.get(role));
            membership.setId(new UserRoleId(u.getId(),roles.get(role).getId()));em.persist(membership);users.put(name,u);
        }
        Branch hubA=branch("HUB-DN","Hub miền Trung demo","Đà Nẵng",BranchType.CENTRAL_HUB,null);
        Branch hubB=branch("HUB-HCM","Hub phía Nam demo","Hồ Chí Minh",BranchType.CENTRAL_HUB,null);
        Branch source=branch("DN","Chi nhánh Đà Nẵng demo","Đà Nẵng",BranchType.LOCAL_BRANCH,hubA);
        Branch destination=branch("HCM","Chi nhánh TP.HCM demo","Hồ Chí Minh",BranchType.LOCAL_BRANCH,hubB);
        employee(users.get("manager"),source,true);employee(users.get("hub-origin-manager"),hubA,true);
        employee(users.get("hub-dest-manager"),hubB,true);employee(users.get("destination-manager"),destination,true);
        employee(users.get("pickup-courier"),source,false);employee(users.get("shipper"),destination,false);
        for(Branch branch:List.of(source,destination))for(ShipmentMethod method:ShipmentMethod.values()) {
            BranchServiceArea a=new BranchServiceArea();a.setBranch(branch);a.setProvince(branch.getProvince());
            a.setDistrict("Khu vực demo");a.setWard("Phường demo");a.setShipmentMethod(method);a.setSupportsDelivery(true);em.persist(a);
        }
        route(source,hubA,TransportLeg.COLLECTION);route(hubA,hubB,TransportLeg.LINEHAUL);route(hubB,destination,TransportLeg.DISTRIBUTION);
        route(destination,hubB,TransportLeg.COLLECTION);route(hubB,hubA,TransportLeg.LINEHAUL);route(hubA,source,TransportLeg.DISTRIBUTION);
        User admin=users.get("admin");ShippingService service=new ShippingService();service.setCode(SERVICE);
        service.setName("Giao hàng tiêu chuẩn demo");service.setEstimatedDeliveryHours(48);service.setCreatedBy(admin);service.setUpdatedBy(admin);em.persist(service);
        LocalDateTime since=LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC).minusMinutes(1);
        PricingVersion version=new PricingVersion();version.setService(service);version.setCode("DEMO23-INITIAL");
        version.setStatus(PricingVersionStatus.PUBLISHED);version.setCreatedBy(admin);version.setUpdatedBy(admin);version.setPublishedBy(admin);
        version.setValidFrom(since);version.setPublishedAt(since);em.persist(version);
        for(RateScopeType scope:List.of(RateScopeType.INTRA_PROVINCE,RateScopeType.INTER_PROVINCE)) {
            ShippingRateRule r=new ShippingRateRule();r.setService(service);r.setPricingVersion(version);r.setScopeType(scope);
            r.setBaseWeightKg(new BigDecimal("1.00"));r.setBasePrice(new BigDecimal(scope==RateScopeType.INTRA_PROVINCE?"20000.00":"40000.00"));
            r.setExtraWeightStepKg(new BigDecimal("0.50"));r.setExtraPricePerStep(new BigDecimal("5000.00"));r.setEffectiveFrom(since);em.persist(r);
        }
        for(Branch branch:List.of(source,destination)) {
            PickupFeeRule fee=new PickupFeeRule();fee.setBranch(branch);fee.setService(service);fee.setPricingVersion(version);
            fee.setAmount(new BigDecimal("7500.00"));fee.setEffectiveFrom(since);em.persist(fee);
            SupportTicketRoute routing=new SupportTicketRoute();routing.setOriginBranch(branch);routing.setHandlingBranch(branch);em.persist(routing);
        }
        em.flush();return true;
    }
    private long count(String entity,String field,String value){return em.createQuery("select count(x) from "+entity+" x where x."+field+"=:value",Long.class).setParameter("value",value).getSingleResult();}
    private Branch branch(String code,String name,String province,BranchType type,Branch hub){
        Branch b=new Branch();b.setCode("DEMO23-"+code);b.setName(name);b.setProvince(province);b.setDistrict("Khu vực demo");b.setWard("Phường demo");
        b.setDetailAddress("Địa điểm mô phỏng phục vụ bảo vệ đồ án");b.setPhone("0000000000");b.setBranchType(type);b.setParentHub(hub);em.persist(b);return b;
    }
    private void employee(User user,Branch branch,boolean manager){
        Employee e=new Employee();e.setUser(user);e.setBranch(branch);e.setEmployeeCode("D23-"+user.getId());e.setFullName(user.getFullName());
        e.setPhoneNumber(user.getPhoneNumber());e.setPosition(manager?EmployeePosition.DISPATCHER:EmployeePosition.COURIER_DRIVER);em.persist(e);
        if(manager){ManagerBranch m=new ManagerBranch();m.setManager(user);m.setBranch(branch);em.persist(m);}
    }
    private void route(Branch from,Branch to,TransportLeg leg){TransportRoute r=new TransportRoute();r.setOrigin(from);r.setDestination(to);r.setLeg(leg);em.persist(r);}
}
