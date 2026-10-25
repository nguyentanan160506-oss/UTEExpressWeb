package com.ute.uteexpress.operations;

import com.ute.uteexpress.config.OperationsSettings;
import com.ute.uteexpress.repository.OperationsDashboardQueries;
import com.ute.uteexpress.service.LogisticsAccessService;
import com.ute.uteexpress.service.operations.*;
import com.ute.uteexpress.service.notifications.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

/** In-memory H2 JDBC only. Flyway and application.properties are never loaded by this context. */
@TestConfiguration(proxyBeanMethods=false) @EnableTransactionManagement @EnableMethodSecurity
@EnableJpaRepositories(basePackages="com.ute.uteexpress.repository")
@Import({LogisticsAccessService.class,OperationsAccessService.class,OperationsDashboardReadService.class,OperationsDashboardQueries.class,
    TicketService.class,TicketReadService.class,TicketCustomerReadService.class,NotificationIdentity.class,NotificationService.class,TicketNotificationProcessor.class,NotificationDelivery.class})
public class OperationsJdbcConfig {
    public static LocalDateTime utcNow(){return LocalDateTime.now(ZoneOffset.UTC);}
    @Bean(destroyMethod="close") DataSource dataSource() throws Exception {
        String url="jdbc:h2:mem:operations_"+UUID.randomUUID().toString().replace("-","")+";MODE=MSSQLServer;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000";
        var ds=new HikariDataSource();ds.setJdbcUrl(url);ds.setUsername("test_operations");ds.setPassword(UUID.randomUUID().toString());ds.setDriverClassName("org.h2.Driver");ds.setMaximumPoolSize(8);ds.setMinimumIdle(1);ds.setMaxLifetime(0);ds.setIdleTimeout(0);
        try(var connection=ds.getConnection();var statement=connection.createStatement()){
            statement.execute("CREATE SCHEMA dbo");
            statement.execute("CREATE ALIAS SYSUTCDATETIME FOR 'com.ute.uteexpress.operations.OperationsJdbcConfig.utcNow'");
        }
        return ds;
    }
    @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds){
        var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(ds);f.setPackagesToScan("com.ute.uteexpress.entity");f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create-drop","hibernate.hbm2ddl.halt_on_error",true,"hibernate.show_sql",false,"hibernate.generate_statistics",true));return f;
    }
    @Bean PlatformTransactionManager transactionManager(EntityManagerFactory emf){return new JpaTransactionManager(emf);}
    @Bean @Primary EntityManager entityManager(EntityManagerFactory emf){return SharedEntityManagerCreator.createSharedEntityManager(emf);}
    @Bean MutableClock clock(){return new MutableClock();}
    @Bean OperationsSettings settings(){return new OperationsSettings(Duration.ofHours(24),Duration.ofHours(24));}
    @Bean static MethodValidationPostProcessor validation(){return new MethodValidationPostProcessor();}
    @Bean List<Message<?>> socketSignals(){return new CopyOnWriteArrayList<>();}
    @Bean SimpMessagingTemplate broker(List<Message<?>> socketSignals){var channel=new ExecutorSubscribableChannel();channel.subscribe(socketSignals::add);return new SimpMessagingTemplate(channel);}
    public static class MutableClock extends Clock {
        public static final LocalDateTime NOW=LocalDateTime.of(2026,10,24,12,0);
        volatile Instant value=NOW.toInstant(ZoneOffset.UTC);
        public void set(LocalDateTime time){value=time.toInstant(ZoneOffset.UTC);}
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return Clock.fixed(value,zone);}
        @Override public Instant instant(){return value;}
    }
}
