package com.ute.uteexpress.operations;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.entity.id.UserRoleId;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.shipment.ShipmentFixtures;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Synthetic data exists only inside a random H2 memory database. Never connects to SQL Server. */
public class OperationsJdbcFixture {
    public static final LocalDateTime NOW=OperationsJdbcConfig.MutableClock.NOW;
    final EntityManager em;
    public final Branch source,destination,foreign,hub;
    public final User owner,other,manager,secondManager,destinationManager,outsider,admin,shipper;
    public final Employee managerEmployee,secondEmployee,destinationEmployee,outsiderEmployee;
    public final ShippingService catalog;
    int nextPhone=1000;
    final Map<String,Role> roles=new HashMap<>();
    public OperationsJdbcFixture(EntityManager em){
        this.em=em;source=branch("SOURCE",BranchType.LOCAL_BRANCH);destination=branch("DESTINATION",BranchType.LOCAL_BRANCH);foreign=branch("FOREIGN",BranchType.LOCAL_BRANCH);hub=branch("HUB",BranchType.CENTRAL_HUB);
        owner=user("owner","ROLE_USER");other=user("other","ROLE_USER");manager=user("manager","ROLE_MANAGER");secondManager=user("second-manager","ROLE_MANAGER");destinationManager=user("destination-manager","ROLE_MANAGER");outsider=user("outsider","ROLE_MANAGER");admin=user("admin","ROLE_ADMIN");shipper=user("shipper","ROLE_SHIPPER");
        managerEmployee=employee(manager,source,EmployeePosition.DISPATCHER);secondEmployee=employee(secondManager,source,EmployeePosition.DISPATCHER);destinationEmployee=employee(destinationManager,destination,EmployeePosition.DISPATCHER);outsiderEmployee=employee(outsider,foreign,EmployeePosition.DISPATCHER);employee(shipper,destination,EmployeePosition.COURIER_DRIVER);
        membership(manager,source);membership(secondManager,source);membership(destinationManager,destination);membership(outsider,foreign);
        catalog=new ShippingService();catalog.setCode("TEST_CATALOG");catalog.setName("Dịch vụ trong test JDBC");catalog.setEstimatedDeliveryHours(48);em.persist(catalog);em.flush();
    }
    public Branch branch(String code,BranchType type){Branch b=new Branch();b.setCode(code);b.setName("Chi nhánh test "+code);b.setBranchType(type);b.setProvince(code);b.setDistrict("Test");b.setWard("Test");b.setDetailAddress("Synthetic test address");b.setPhone("0000000000");em.persist(b);return b;}
    public User user(String name,String role){User u=new User();u.setUsername(name);u.setEmail(name+"@example.test");u.setPasswordHash(UUID.randomUUID().toString());u.setFullName("Test "+name);u.setPhoneNumber("090"+String.format("%07d",nextPhone++));u.setStatus(UserStatus.ACTIVE);em.persist(u);Role r=roles.computeIfAbsent(role,k->{Role v=new Role();v.setName(k);em.persist(v);return v;});em.flush();UserRole m=new UserRole();m.setUser(u);m.setRole(r);m.setId(new UserRoleId(u.getId(),r.getId()));em.persist(m);return u;}
    public Employee employee(User user,Branch branch,EmployeePosition position){Employee e=new Employee();e.setUser(user);e.setBranch(branch);e.setEmployeeCode("EMP"+user.getId());e.setFullName(user.getFullName());e.setPhoneNumber("0000000000");e.setPosition(position);e.setStatus(EmployeeStatus.ACTIVE);em.persist(e);return e;}
    public void membership(User user,Branch branch){ManagerBranch m=new ManagerBranch();m.setManager(user);m.setBranch(branch);em.persist(m);}
    public Shipment shipment(Branch from,Branch to,ShipmentStatus status,LocalDateTime created){Shipment s=ShipmentFixtures.shipment();s.setId(null);s.setSender(owner);s.setOriginBranch(from);s.setDestinationBranch(to);s.setCurrentBranch(null);s.setService(catalog);s.setShipmentMethod(ShipmentMethod.BRANCH_DROPOFF);s.setTrackingNumber("UTETEST"+UUID.randomUUID().toString().replace("-","").substring(0,20).toUpperCase(Locale.ROOT));s.setCreatedAt(created);s.setUpdatedAt(created);s.setStatus(status);em.persist(s);em.flush();return s;}
    public ShipmentAssignment assignment(Shipment s,User courier){Employee employee=em.createQuery("select e from Employee e where e.user.id=:id",Employee.class).setParameter("id",courier.getId()).getSingleResult();ShipmentAssignment a=new ShipmentAssignment();a.setShipment(s);a.setEmployee(employee);a.setAssignedBy(destinationManager);a.setAssignmentType(AssignmentType.DELIVERY);a.setStatus(AssignmentStatus.IN_PROGRESS);a.setAssignedAt(NOW.minusHours(1));em.persist(a);return a;}
    public DeliveryAttempt attempt(Shipment s,ShipmentAssignment assignment,int number,DeliveryAttemptStatus result,LocalDateTime time){DeliveryAttempt a=new DeliveryAttempt();a.setShipment(s);a.setAssignment(assignment);a.setEmployee(assignment.getEmployee());a.setActor(assignment.getEmployee().getUser());a.setAttemptNumber(number);a.setStatus(result);a.setStartedAt(time.minusMinutes(30));a.setAttemptTime(time);if(result==DeliveryAttemptStatus.FAILED){a.setFailureCode(DeliveryFailureCode.ABSENT);a.setFailureReason("Synthetic result");}em.persist(a);return a;}
    public DeliveryProof proof(Shipment s,ShipmentAssignment assignment,DeliveryAttempt a,LocalDateTime at){DeliveryProof p=new DeliveryProof();p.setShipment(s);p.setAssignment(assignment);p.setAttempt(a);p.setActor(assignment.getEmployee().getUser());p.setEmployee(assignment.getEmployee());p.setRecipientType(DeliveryRecipientType.RECIPIENT);p.setRecipientName("Test recipient");p.setReference("PROOF-TEST");p.setDeliveredAt(at);em.persist(p);s.setDeliveredAt(at);return p;}
    public ShipmentEvent event(Shipment s,String title,ShipmentStatus from,ShipmentStatus to,LocalDateTime at){ShipmentEvent e=new ShipmentEvent();e.setShipment(s);e.setBranch(s.getOriginBranch());e.setActor(manager);e.setEventTitle(title);e.setFromStatus(from);e.setToStatus(to);e.setOccurredAt(at);em.persist(e);return e;}
    public ShipmentIntake intake(Shipment s,Branch branch,LocalDateTime at){ShipmentIntake i=new ShipmentIntake();i.setShipment(s);i.setBranch(branch);i.setEmployee(managerEmployee);i.setReceivedBy(manager);i.setShipmentMethod(ShipmentMethod.BRANCH_DROPOFF);i.setReceiptReference("TEST-INTAKE");i.setReceivedAt(at);em.persist(i);s.setCurrentBranch(branch);return i;}
    public static void as(User u,String role){SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(u.getEmail(),null,role));}
}
