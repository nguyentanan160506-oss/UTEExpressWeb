package com.ute.uteexpress.demo;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.entity.Role;
import com.ute.uteexpress.operations.OperationsJdbcConfig;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Seed semantics on disposable H2. Does NOT claim SQL Server persistence or migrations have been tested. */
@SpringJUnitConfig(SqlDemoSeedTests.Config.class)
class SqlDemoSeedTests {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false) @Import(OperationsJdbcConfig.class)
    static class Config {
        @Bean SqlDemoSeed seed(){return new SqlDemoSeed();}
        @Bean PasswordEncoder encoder(){return new BCryptPasswordEncoder(4);}
    }
    @Autowired SqlDemoSeed seed;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired PasswordEncoder encoder;
    @Test void initializationIsAtomicRepeatableAndPreservesCustomerChanges() {
        TransactionTemplate tx=new TransactionTemplate(manager);
        tx.execute(s->{for(String name:List.of("ROLE_USER","ROLE_ADMIN","ROLE_MANAGER","ROLE_SHIPPER")){Role role=new Role();role.setName(name);em.persist(role);}return null;});
        assertThrows(IllegalArgumentException.class,()->seed.seed(null));
        Long usersAfterFailure=tx.execute(s->em.createQuery("select count(u) from User u",Long.class).getSingleResult());
        assertEquals(0L,usersAfterFailure.longValue());
        String password=UUID.randomUUID().toString();assertTrue(seed.seed(password));
        Map<String,Long> before=counts(tx);
        assertEquals(9L,before.get("User"));assertEquals(4L,before.get("Branch"));assertEquals(6L,before.get("TransportRoute"));
        assertEquals(4L,before.get("BranchServiceArea"));assertEquals(0L,before.get("Shipment"));assertEquals(0L,before.get("ShipmentEvent"));
        String hash=tx.execute(s->{var u=em.createQuery("select u from User u where u.username='demo23-owner'",User.class).getSingleResult();
            assertTrue(encoder.matches(password,u.getPasswordHash()));u.setFullName("Customer changed name");return u.getPasswordHash();});
        assertFalse(seed.seed(UUID.randomUUID().toString()));assertEquals(before,counts(tx));
        tx.execute(s->{var u=em.createQuery("select u from User u where u.username='demo23-owner'",User.class).getSingleResult();
            assertEquals(hash,u.getPasswordHash());assertEquals("Customer changed name",u.getFullName());return null;});
    }
    private Map<String,Long> counts(TransactionTemplate tx){return tx.execute(s->{var result=new TreeMap<String,Long>();
        for(var type:em.getMetamodel().getEntities())result.put(type.getName(),em.createQuery("select count(x) from "+type.getName()+" x",Long.class).getSingleResult());return result;});}
}
