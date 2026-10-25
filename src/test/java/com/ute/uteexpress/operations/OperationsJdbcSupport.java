package com.ute.uteexpress.operations;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.dto.operations.OperationsDashboardViews.*;
import com.ute.uteexpress.dto.operations.OperationsDashboardViews.Period;
import com.ute.uteexpress.dto.operations.TicketCommands.*;
import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.service.operations.*;
import com.ute.uteexpress.service.notifications.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.ute.uteexpress.operations.OperationsJdbcFixture.*;
import static org.junit.jupiter.api.Assertions.*;

/** Shared isolated H2 setup for service and MockMvc JDBC tests. */
abstract class OperationsJdbcSupport {
    @Autowired EntityManager em;@Autowired EntityManagerFactory emf;@Autowired PlatformTransactionManager transactionManager;
    @Autowired OperationsDashboardReadService dashboard;@Autowired OperationsDashboardQueries queries;@Autowired OperationsAccessService access;
    @Autowired TicketService tickets;@Autowired TicketReadService read;@Autowired TicketCustomerReadService customer;
    @Autowired SupportTicketRepository ticketRows;@Autowired SupportTicketEventRepository eventRows;@Autowired SupportTicketMessageRepository messageRows;
    @Autowired TicketNotificationProcessor notifications;@Autowired NotificationRepository notices;
    @Autowired OperationsJdbcConfig.MutableClock clock;
    OperationsJdbcFixture f;TransactionTemplate tx;
    <T>T db(Supplier<T> action){return tx.execute(status->action.get());}
    @BeforeEach void setup(){tx=new TransactionTemplate(transactionManager);clock.set(NOW);db(()->{
        em.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE").executeUpdate();
        for(var entity:emf.getMetamodel().getEntities()){var table=entity.getJavaType().getAnnotation(Table.class);em.createNativeQuery("TRUNCATE TABLE dbo."+table.name()+" RESTART IDENTITY").executeUpdate();}
        em.createNativeQuery("SET REFERENTIAL_INTEGRITY TRUE").executeUpdate();f=new OperationsJdbcFixture(em);return null;
    });as(f.manager,"ROLE_MANAGER");}
    @AfterEach void close(){SecurityContextHolder.clearContext();}
    Create command(Shipment s){return new Create(f.source.getId(),s.getTrackingNumber(),"Phản ánh test","Nội dung tiếp nhận nội bộ",TicketCategory.DELAY,TicketPriority.MEDIUM,TicketSource.MANAGER_PHONE,UUID.randomUUID().toString().replace("-",""));}
}
