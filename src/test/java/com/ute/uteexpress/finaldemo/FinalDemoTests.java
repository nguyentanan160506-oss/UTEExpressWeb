package com.ute.uteexpress.finaldemo;

import com.ute.uteexpress.UteexpressApplication;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.entity.Role;
import com.ute.uteexpress.entity.id.UserRoleId;
import com.ute.uteexpress.dto.shipment.ShipmentCreateForm;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.*;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.util.*;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** All SQL here is disposable H2 only. This does not assert SQL Server migration success. */
class FinalDemoTests {
    @Test void strictTargetAndApprovalRejectOverridesAndProtectedDestinations()throws Exception {
        assertThrows(IllegalArgumentException.class,()->FinalDemoTarget.requireApproval("migrate",Map.of()));
        var env=Map.of("UTE_FINAL_DEMO_USERNAME",FinalDemoTarget.LOGIN,"UTE_FINAL_DEMO_PASSWORD",UUID.randomUUID().toString());
        FinalDemoTarget.requireApproval("inspect",env);
        assertThrows(IllegalArgumentException.class,()->FinalDemoTarget.requireApproval("seed",env));
        for(String db:List.of("UTEExpressDB","UTEExpress_Demo","UTEExpress_Test","UTEExpress_V14_QA_20261029_7dc87af7"))assertThrows(SQLException.class,()->identity(db,"SQLEXPRESS",1433,true));
        assertThrows(SQLException.class,()->identity(FinalDemoTarget.DATABASE,"MSSQLSERVER",1433,true));
        assertThrows(SQLException.class,()->identity(FinalDemoTarget.DATABASE,"SQLEXPRESS",1434,true));
        assertThrows(SQLException.class,()->identity(FinalDemoTarget.DATABASE,"SQLEXPRESS",1433,false));
        identity(FinalDemoTarget.DATABASE,"SQLEXPRESS",1433,true);
        assertThrows(IllegalArgumentException.class,()->FinalDemoSeed.requirePassword(null));
        assertThrows(IllegalArgumentException.class,()->FinalDemoSeed.requirePassword("x".repeat(25)));
    }
    private void identity(String db,String instance,int port,boolean isolated)throws SQLException {FinalDemoTarget.requireIdentity("Microsoft SQL Server",db,db,"DESKTOP-31GJQ3T",instance,FinalDemoTarget.LOGIN,port,true,isolated);}
    @Test void networkDatasetHasExactWardCoverageAndNoPlaceholderGeography(){
        var rows=FinalDemoNetwork.nodes();assertEquals(42,rows.size());assertEquals(34,rows.stream().map(FinalDemoNetwork.Node::provinceCode).distinct().count());
        for(var row:rows){assertFalse(row.ward().contains("demo"));assertFalse(row.legacyArea().contains("demo"));assertTrue(row.address().contains("mô phỏng"));}
    }
    @Test void fullSeedUsesRealServicesAndIsAtomicIdempotentAndPreservesLaterEdits(){
        try(var app=new SpringApplicationBuilder(UteexpressApplication.class,TestConfig.class).web(WebApplicationType.NONE).run(
            "--spring.config.location=classpath:/final-demo.properties","--spring.profiles.active=final-demo-h2-test","--spring.flyway.enabled=false","--spring.jpa.hibernate.ddl-auto=create-drop","--uteexpress.notifications.worker.enabled=false")){
            var em=app.getBean(EntityManager.class);var tx=new TransactionTemplate(app.getBean(PlatformTransactionManager.class));var seed=app.getBean(FinalDemoSeed.class);
            tx.execute(st->{for(String name:List.of("ROLE_ADMIN","ROLE_MANAGER","ROLE_USER","ROLE_SHIPPER")){var r=new Role();r.setName(name);em.persist(r);}return null;});
            assertThrows(IllegalArgumentException.class,()->seed.seed(null));
            assertEquals(Long.valueOf(0),tx.execute(st->em.createQuery("select count(u) from User u",Long.class).getSingleResult()));
            String password=UUID.randomUUID()+"A!";
            tx.execute(st->{assertTrue(seed.seed(password));st.setRollbackOnly();return null;});
            tx.execute(st->{for(String type:List.of("User","Branch","Shipment","TransportTrip","PricingVersion","ShipmentEvent"))assertEquals(0L,em.createQuery("select count(x) from "+type+" x",Long.class).getSingleResult());return null;});
            assertTrue(seed.seed(password));
            app.getBean(FinalDemoValidation.class).validate();
            var statistics=app.getBean(jakarta.persistence.EntityManagerFactory.class).unwrap(org.hibernate.SessionFactory.class).getStatistics();statistics.setStatisticsEnabled(true);statistics.clear();
            var coverage=app.getBean(BranchNetworkService.class).quoteCoverageOptions();assertEquals(70,coverage.size());assertEquals(1L,statistics.getPrepareStatementCount(),"Coverage is bounded and uses one fetch query, no per-area branch/hub lookups");
            tx.execute(st->{var branch=em.createQuery("select b from Branch b where b.code='FD-B-79'",Branch.class).getSingleResult();branch.setStatus(BranchStatus.INACTIVE);em.flush();assertEquals(68,app.getBean(BranchNetworkService.class).quoteCoverageOptions().size());st.setRollbackOnly();return null;});
            assertEquals(70,app.getBean(BranchNetworkService.class).quoteCoverageOptions().size());

            tx.execute(st->{for(var item:Map.of("User",90L,"Branch",42L,"TransportRoute",112L,"BranchServiceArea",70L,"Employee",77L,"ManagerBranch",42L,"Shipment",20L,"DeliveryProof",1L,"ReturnReceipt",1L,"SupportTicket",1L).entrySet())assertEquals(item.getValue(),em.createQuery("select count(x) from "+item.getKey()+" x",Long.class).getSingleResult(),item.getKey());
                assertEquals(ShipmentStatus.DELIVERED,shipment(em,"DELIVERED").getStatus());assertEquals(ShipmentStatus.RETURNED,shipment(em,"RETURNED").getStatus());
                for(String code:List.of("ORIGIN_INTAKE_HOME","ORIGIN_INTAKE_DROP")){var s=shipment(em,code);assertEquals(1L,em.createQuery("select count(i) from ShipmentIntake i where i.shipment=:s",Long.class).setParameter("s",s).getSingleResult());}
                for(var s:em.createQuery("select s from Shipment s",Shipment.class).getResultList())assertEquals(s.getShipmentMethod()==ShipmentMethod.HOME_PICKUP?1:0,s.getPickupFee().signum());return null;});
            var before=tx.execute(st->{var map=new TreeMap<String,Long>();for(var type:em.getMetamodel().getEntities())map.put(type.getName(),em.createQuery("select count(x) from "+type.getName()+" x",Long.class).getSingleResult());return map;});
            String hash=tx.execute(st->{var u=em.createQuery("select u from User u where u.username='final-user-01'",User.class).getSingleResult();u.setFullName("Customer edited name");return u.getPasswordHash();});
            assertFalse(seed.seed(UUID.randomUUID()+"B!"));
            tx.execute(st->{before.forEach((type,c)->assertEquals(c,em.createQuery("select count(x) from "+type+" x",Long.class).getSingleResult()));var u=em.createQuery("select u from User u where u.username='final-user-01'",User.class).getSingleResult();assertEquals(hash,u.getPasswordHash());assertEquals("Customer edited name",u.getFullName());return null;});
            newCustomerCanQuoteEveryCoveredDestination(app,em,tx,password);
        }
    }
    private void newCustomerCanQuoteEveryCoveredDestination(ConfigurableApplicationContext app,EntityManager em,TransactionTemplate tx,String password){
        var role=tx.execute(st->{var u=new User();u.setUsername("new-finaldemo-customer");u.setEmail("new-finaldemo-customer@example.test");u.setFullName("Khách mới kiểm thử");u.setPhoneNumber("0000009999");u.setStatus(UserStatus.ACTIVE);u.setPasswordHash(app.getBean(PasswordEncoder.class).encode(password));em.persist(u);em.flush();var r=em.createQuery("select r from Role r where r.name='ROLE_USER'",Role.class).getSingleResult();var link=new UserRole();link.setUser(u);link.setRole(r);link.setId(new UserRoleId(u.getId(),r.getId()));em.persist(link);return u.getEmail();});
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(role,null,List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        try{
            for(var n:FinalDemoNetwork.nodes())if(n.type()==BranchType.LOCAL_BRANCH)for(var method:ShipmentMethod.values()){
                var f=tx.execute(st->{var from=em.createQuery("select b from Branch b where b.code='FD-B-79'",Branch.class).getSingleResult();var to=em.createQuery("select b from Branch b where b.code=:c",Branch.class).setParameter("c","FD-"+n.code()).getSingleResult();var form=new ShipmentCreateForm();form.setShipmentMethod(method);form.setSenderName("Người gửi kiểm thử");form.setSenderPhone("0000009999");form.setReceiverName("Người nhận kiểm thử");form.setReceiverPhone("0000009998");form.setSenderProvince(from.getProvince());form.setSenderDistrict(from.getDistrict());form.setSenderWard(from.getWard());form.setSenderAddress(from.getDetailAddress());form.setReceiverProvince(to.getProvince());form.setReceiverDistrict(to.getDistrict());form.setReceiverWard(to.getWard());form.setReceiverAddress(to.getDetailAddress());form.setOriginBranchId(from.getId());form.setDestinationBranchId(to.getId());form.setServiceId(em.createQuery("select s.id from ShippingService s where s.code='FD-STANDARD'",Long.class).getSingleResult());form.setPackageDescription("Kiện kiểm thử khách mới");form.setWeightKg(new java.math.BigDecimal("1.50"));return form;});
                String quote=app.getBean(QuoteService.class).quote(f);assertNotNull(quote);
                if(n.code().equals("B-48"))assertNotNull(app.getBean(QuoteService.class).confirm(quote));
            }
        }finally{SecurityContextHolder.clearContext();}
    }
    private Shipment shipment(EntityManager em,String code){return em.createQuery("select s from Shipment s where s.note=:note",Shipment.class).setParameter("note","FD-SCENARIO:"+code).getSingleResult();}
    @TestConfiguration(proxyBeanMethods=false) @Profile("final-demo-h2-test")
    static class TestConfig {
        @Bean(destroyMethod="close") DataSource dataSource()throws Exception {
            var ds=new HikariDataSource();ds.setJdbcUrl("jdbc:h2:mem:uteexpress_finaldemo_test_"+UUID.randomUUID().toString().replace("-","")+";MODE=MSSQLServer;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");ds.setUsername("finaldemo_test");ds.setPassword(UUID.randomUUID().toString());
            try(var c=ds.getConnection();var s=c.createStatement()){s.execute("CREATE SCHEMA dbo");s.execute("CREATE ALIAS SYSUTCDATETIME FOR 'com.ute.uteexpress.operations.LocalDemoApplication.utcNow'");}return ds;
        }
        @Bean @Primary PasswordEncoder testEncoder(){return new BCryptPasswordEncoder(4);}
        @Bean FinalDemoSeed finalDemoSeed(){return new FinalDemoSeed();}
        @Bean FinalDemoValidation finalDemoValidation(){return new FinalDemoValidation();}
    }
}
