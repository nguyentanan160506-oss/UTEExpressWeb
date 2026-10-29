package com.ute.uteexpress.release.v15;

import com.ute.uteexpress.controller.UserShipmentController;
import com.ute.uteexpress.dto.pricing.*;
import com.ute.uteexpress.dto.shipment.ShipmentCreateForm;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.entity.Role;
import com.ute.uteexpress.entity.id.UserRoleId;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.*;
import jakarta.persistence.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.beanvalidation.*;
import org.springframework.web.server.ResponseStatusException;
import static com.ute.uteexpress.release.v15.V15Sql.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Real application services over the caller's fixed QA datasource. No Boot/autoconfiguration/HTTP/scheduler. */
final class V15BusinessChecks {
    private V15BusinessChecks() { }
    static void run(DataSource source)throws Exception {
        try(var context=new AnnotationConfigApplicationContext()) {
            context.registerBean("qaDataSource",DataSource.class,()->source,definition->definition.setDestroyMethodName(""));
            context.register(Config.class);context.refresh();
            pass("hibernate-v15-schema-validate-no-orm-override");
            new Journey(context).run();
        }finally{SecurityContextHolder.clearContext();}
    }
    @TestConfiguration(proxyBeanMethods=false) @EnableTransactionManagement @EnableMethodSecurity
    @EnableJpaRepositories(basePackages="com.ute.uteexpress.repository")
    @Import({QuoteService.class,BranchNetworkService.class,TrackingNumberGenerator.class,AdminPricingService.class,UserShipmentService.class})
    static class Config {
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(ds);factory.setPackagesToScan("com.ute.uteexpress.entity");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.jdbc.time_zone","UTC","hibernate.show_sql",false));return factory;
        }
        @Bean @Primary EntityManager entityManager(EntityManagerFactory f){return SharedEntityManagerCreator.createSharedEntityManager(f);}
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory f){return new JpaTransactionManager(f);}
        @Bean QaClock clock(){return new QaClock();}
        @Bean LocalValidatorFactoryBean validator(){return new LocalValidatorFactoryBean();}
        @Bean static MethodValidationPostProcessor methodValidation(){var p=new MethodValidationPostProcessor();p.setProxyTargetClass(true);return p;}
        @Bean static org.springframework.core.convert.ConversionService conversionService(){return org.springframework.boot.convert.ApplicationConversionService.getSharedInstance();}
    }
    static final class QaClock extends Clock {
        private volatile Instant now=Instant.now().truncatedTo(ChronoUnit.SECONDS);
        void advance(long seconds){now=now.plusSeconds(seconds);}
        LocalDateTime utc(){return LocalDateTime.ofInstant(now,ZoneOffset.UTC);}
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return Clock.fixed(now,zone);}
        @Override public Instant instant(){return now;}
    }
    record Tariff(RateScopeType scope,BigDecimal base,BigDecimal included,BigDecimal step,BigDecimal extra) { }
    static List<Tariff> tariffs()throws IOException {
        var stream=V15BusinessChecks.class.getResourceAsStream("/demo/nationwide-shipping-prices.csv");
        if(stream==null)throw new IOException("Test-only tariff fixture missing");
        try(var reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))) {
            require("scope,base_price,base_weight_kg,extra_weight_step_kg,extra_price_per_step".equals(reader.readLine()),"Tariff fixture header mismatch");
            var rows=new ArrayList<Tariff>();String line;
            while((line=reader.readLine())!=null){var p=line.split(",",-1);require(p.length==5,"Malformed test tariff");rows.add(new Tariff(RateScopeType.valueOf(p[0]),amount(p[1]),amount(p[2]),amount(p[3]),amount(p[4])));}
            require(rows.size()==5&&rows.stream().map(Tariff::scope).collect(java.util.stream.Collectors.toSet()).equals(new HashSet<>(RateScopeType.nationwide())),"Test tariff scopes mismatch");
            return List.copyOf(rows);
        }
    }
    private static final class RollbackProbe extends RuntimeException { }
    private static final class Journey {
        final EntityManager em;final TransactionTemplate tx;final QuoteService quotes;final AdminPricingService admin;
        final UserShipmentService userShipments;final QaClock clock;final List<Tariff> tariff;
        User owner,other,administrator,shipper;Branch origin,destination;long service,firstVersion;
        Journey(AnnotationConfigApplicationContext context)throws IOException {
            em=context.getBean(EntityManager.class);tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            quotes=context.getBean(QuoteService.class);admin=context.getBean(AdminPricingService.class);userShipments=context.getBean(UserShipmentService.class);
            clock=context.getBean(QaClock.class);tariff=tariffs();
        }
        <T>T db(Supplier<T> work){return tx.execute(status->work.get());}
        long count(Class<?> type){return db(()->em.createQuery("select count(x) from "+type.getSimpleName()+" x",Long.class).getSingleResult());}
        void run()throws Exception {
            fixtures();as(administrator,"ROLE_ADMIN");
            service=admin.createService(new PricingCommands.Catalog("V15QA-NATIONAL","Synthetic V15 QA national service",null,48,amount("30.00"),null,null,null));
            firstVersion=publish("V15QA-FIRST",clock.utc().plusSeconds(60),null,true,"10000.00");
            admin.serviceStatus(service,serviceVersion(),true);clock.advance(61);pass("admin-create-draft-five-groups-pickups-publish-enable");
            nationalMatrix();as(owner,"ROLE_USER");
            String drop=quotes.quote(form(ShipmentMethod.BRANCH_DROPOFF,false));money(quotes.review(drop).pickupFee(),"0.00");
            Long oldShipment=quotes.confirm(drop);money(userShipments.detail(oldShipment,0).totalFee(),"50000.00");
            require(oldShipment.equals(quotes.confirm(drop)),"Repeated confirmation returned a different shipment");
            snapshot(oldShipment,"48","79","45000.00","5000.00");
            String home=quotes.quote(form(ShipmentMethod.HOME_PICKUP,false));money(quotes.review(home).pickupFee(),"12000.00");
            Long homeId=quotes.confirm(home);money(userShipments.detail(homeId,0).totalFee(),"62000.00");
            String held=quotes.quote(form(ShipmentMethod.HOME_PICKUP,true));money(quotes.review(held).pickupFee(),"10000.00");money(quotes.review(held).totalFee(),"60000.00");
            pass("both-methods-branch-override-global-fallback-idempotency-snapshots");
            ownershipAndRbac(home);coverage();tamper();businessRollback();concurrent();
            as(administrator,"ROLE_ADMIN");LocalDateTime change=clock.utc().plusSeconds(60);
            admin.retireVersion(firstVersion,bookVersion(firstVersion),change,true);
            publish("V15QA-SECOND",change,amount("50000.00"),false,"11000.00");clock.advance(61);
            as(owner,"ROLE_USER");money(userShipments.detail(oldShipment,0).totalFee(),"50000.00");
            snapshot(oldShipment,"48","79","45000.00","5000.00");
            Long heldShipment=quotes.confirm(held);money(userShipments.detail(heldShipment,0).totalFee(),"60000.00");
            String fresh=quotes.quote(form(ShipmentMethod.HOME_PICKUP,false));money(quotes.review(fresh).totalFee(),"66000.00");
            Long newShipment=quotes.confirm(fresh);snapshot(newShipment,"48","79","50000.00","5000.00");
            require(db(()->em.createQuery("select count(a) from AuditLog a where a.actor.id=:id",Long.class).setParameter("id",administrator.getId()).getSingleResult())>0,"Admin mutations missing audit trail");
            pass("admin-new-version-old-shipment-and-held-quote-prices-immutable-audited");
        }
        void fixtures() {
            db(()->{
                owner=actor("owner","ROLE_USER",1);other=actor("other","ROLE_USER",2);administrator=actor("admin","ROLE_ADMIN",3);shipper=actor("shipper","ROLE_SHIPPER",4);
                var originHub=branch("HUB-CENTRAL","Da Nang QA hub",BranchType.CENTRAL_HUB,"Đà Nẵng",null);
                var destHub=branch("HUB-SOUTH","HCM QA hub",BranchType.CENTRAL_HUB,"Hồ Chí Minh",null);
                origin=branch("ORIGIN","Da Nang QA branch",BranchType.LOCAL_BRANCH,"Đà Nẵng",originHub);
                destination=branch("DESTINATION","HCM QA branch",BranchType.LOCAL_BRANCH,"Hồ Chí Minh",destHub);
                for(var b:List.of(origin,destination))for(var method:ShipmentMethod.values()) {
                    var area=new BranchServiceArea();area.setBranch(b);area.setProvince(b.getProvince());area.setDistrict("QA");area.setWard("QA");area.setShipmentMethod(method);area.setSupportsDelivery(true);em.persist(area);
                }
                em.flush();return null;
            });
        }
        User actor(String label,String authority,int phone) {
            Role role=em.createQuery("select r from Role r where r.name=:name",Role.class).setParameter("name",authority).getSingleResult();
            User u=new User();u.setUsername("v15qa_"+label);u.setEmail("v15qa-"+label+"@example.test");u.setFullName("Synthetic V15 QA "+label);
            u.setPhoneNumber("099900000"+phone);u.setPasswordHash(new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString()));u.setStatus(UserStatus.ACTIVE);em.persist(u);em.flush();
            var membership=new UserRole();membership.setUser(u);membership.setRole(role);membership.setId(new UserRoleId(u.getId(),role.getId()));em.persist(membership);return u;
        }
        Branch branch(String code,String name,BranchType type,String province,Branch hub) {
            var b=new Branch();b.setCode("V15QA-"+code);b.setName(name);b.setProvince(province);b.setDistrict("QA");b.setWard("QA");b.setDetailAddress("Synthetic QA location");b.setPhone("0000000000");b.setBranchType(type);b.setParentHub(hub);em.persist(b);return b;
        }
        Long serviceVersion(){return db(()->em.find(ShippingService.class,service).getVersion());}
        Long bookVersion(long id){return db(()->em.find(PricingVersion.class,id).getVersion());}
        long publish(String code,LocalDateTime from,BigDecimal centralSouth,boolean specific,String defaultFee) {
            long id=admin.createDraft(new PricingCommands.Draft(service,code,from,null));
            for(var row:tariff)admin.shippingRule(id,bookVersion(id),new PricingCommands.ShippingRule(null,row.scope(),row.included(),centralSouth!=null&&row.scope()==RateScopeType.CENTRAL_SOUTH?centralSouth:row.base(),row.step(),row.extra(),true));
            admin.pickupRule(id,bookVersion(id),new PricingCommands.PickupRule(null,null,amount(defaultFee),true));
            if(specific)admin.pickupRule(id,bookVersion(id),new PricingCommands.PickupRule(null,origin.getId(),amount("12000.00"),true));
            require(admin.check(id).isEmpty(),"Synthetic pricing draft did not validate");admin.publish(id,bookVersion(id),true);return id;
        }
        GuestQuoteForm national(String from,String to,ShipmentMethod method,String weight) {
            var form=new GuestQuoteForm();form.setNationwide(true);form.setServiceId(service);form.setSenderProvince(from);form.setReceiverProvince(to);form.setShipmentMethod(method);form.setWeightKg(amount(weight));return form;
        }
        void nationalMatrix() {
            SecurityContextHolder.clearContext();long countBefore=count(ShipmentQuote.class);int pairs=0;
            var prices=new EnumMap<RateScopeType,Tariff>(RateScopeType.class);for(var row:tariff)prices.put(row.scope(),row);
            for(var from:ProvinceCatalog.all())for(var to:ProvinceCatalog.all()) {
                var scope=ProvinceCatalog.classify(from.code(),to.code());var row=prices.get(scope);
                for(var method:ShipmentMethod.values()) {
                    var result=quotes.estimate(national(from.name(),to.name(),method,"1.01"));
                    var pickup=amount(method==ShipmentMethod.HOME_PICKUP?"10000.00":"0.00");
                    require(result.totalFee().compareTo(row.base().add(row.extra()).add(pickup))==0&&result.breakdown().scope()==scope,"National estimate differs from published database rule");
                }
                pairs++;
            }
            require(pairs==1156&&countBefore==count(ShipmentQuote.class),"National estimate created records or lost pairs");
            for(String[] boundary:List.of(new String[]{"0.01","45000.00"},new String[]{"1.00","45000.00"},new String[]{"1.01","50000.00"},new String[]{"2.00","50000.00"},new String[]{"2.01","55000.00"}))
                money(quotes.estimate(national("Đà Nẵng","Hồ Chí Minh",ShipmentMethod.BRANCH_DROPOFF,boundary[0])).totalFee(),boundary[1]);
            rejects(ShipmentCreationException.class,()->quotes.estimate(national("Đà Nẵng","Hồ Chí Minh",ShipmentMethod.BRANCH_DROPOFF,"30.01")));
            pass("1156-directed-pairs-both-methods-five-weight-boundaries-and-weight-limit");
        }
        ShipmentCreateForm form(ShipmentMethod method,boolean reverse) {
            var f=new ShipmentCreateForm();var a=reverse?destination:origin;var b=reverse?origin:destination;
            f.setShipmentMethod(method);f.setOriginBranchId(a.getId());f.setDestinationBranchId(b.getId());f.setServiceId(service);
            f.setSenderName("Synthetic QA sender");f.setSenderPhone("0000000000");f.setSenderProvince(a.getProvince());f.setSenderDistrict("QA");f.setSenderWard("QA");f.setSenderAddress("Synthetic pickup location");
            f.setReceiverName("Synthetic QA recipient");f.setReceiverPhone("0000000001");f.setReceiverProvince(b.getProvince());f.setReceiverDistrict("QA");f.setReceiverWard("QA");f.setReceiverAddress("Synthetic delivery location");
            f.setPackageDescription("Synthetic V15 QA parcel");f.setWeightKg(amount("1.01"));f.setCodAmount(amount("0.00"));f.setDeclaredValue(amount("0.00"));f.setPaymentPayer(PaymentPayer.SENDER);return f;
        }
        void ownershipAndRbac(String consumed) {
            as(other,"ROLE_USER");var forbidden=rejects(ResponseStatusException.class,()->quotes.confirm(consumed));require(forbidden.getStatusCode().value()==404,"Quote ownership error leaked existence");
            rejects(AccessDeniedException.class,admin::authorize);
            as(shipper,"ROLE_SHIPPER");rejects(AccessDeniedException.class,admin::authorize);rejects(AccessDeniedException.class,()->quotes.quote(form(ShipmentMethod.BRANCH_DROPOFF,false)));
            as(owner,"ROLE_ADMIN");rejects(AccessDeniedException.class,admin::authorize); // Claimed authority cannot bypass fresh database membership.
            as(owner,"ROLE_USER");pass("quote-owner-and-admin-user-shipper-database-rbac");
        }
        void coverage() {
            long before=count(ShipmentQuote.class);var f=form(ShipmentMethod.HOME_PICKUP,false);f.setSenderProvince("Hà Nội");f.setReceiverProvince("Cà Mau");
            rejects(ShipmentCreationException.class,()->quotes.quote(f));require(before==count(ShipmentQuote.class),"Unserved quote persisted");pass("national-estimate-does-not-grant-unserved-shipment-coverage");
        }
        void tamper()throws Exception {
            String token=quotes.quote(form(ShipmentMethod.BRANCH_DROPOFF,false));long before=count(Shipment.class);
            var mvc=MockMvcBuilders.standaloneSetup(new UserShipmentController(userShipments,quotes)).build();
            var response=mvc.perform(post("/user/shipments").param("quoteToken",token).param("totalFee","0.00").param("shippingFee","0.00")).andReturn().getResponse();
            require(response.getStatus()==400&&before==count(Shipment.class)&&!quotes.review(token).consumed(),"Client price fields were accepted");
            pass("controller-binding-rejects-client-fees-no-http-server");
        }
        void businessRollback() {
            String token=quotes.quote(form(ShipmentMethod.HOME_PICKUP,false));long shipments=count(Shipment.class),events=count(ShipmentEvent.class);
            rejects(RollbackProbe.class,()->db(()->{quotes.confirm(token);em.flush();throw new RollbackProbe();}));
            require(shipments==count(Shipment.class)&&events==count(ShipmentEvent.class)&&!quotes.review(token).consumed(),"Business transaction did not roll back");
            pass("service-confirmation-event-consumption-business-rollback");
        }
        void concurrent()throws Exception {
            String token=quotes.quote(form(ShipmentMethod.BRANCH_DROPOFF,false));long shipments=count(Shipment.class),events=count(ShipmentEvent.class);
            var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
            try {
                Callable<Long> task=()->{as(owner,"ROLE_USER");try{if(!start.await(15,TimeUnit.SECONDS))throw new IllegalStateException("Concurrent start timed out");return quotes.confirm(token);}finally{SecurityContextHolder.clearContext();}};
                var first=pool.submit(task);var second=pool.submit(task);start.countDown();
                require(first.get(45,TimeUnit.SECONDS).equals(second.get(45,TimeUnit.SECONDS)),"Concurrent confirmation IDs differ");
                require(count(Shipment.class)==shipments+1&&count(ShipmentEvent.class)==events+1,"Concurrent confirmation duplicated records");
            }finally{pool.shutdownNow();require(pool.awaitTermination(25,TimeUnit.SECONDS),"Concurrent workers did not stop");}
            pass("two-concurrent-confirmations-one-shipment-one-event");
        }
        void snapshot(long id,String from,String to,String base,String extra) {
            db(()->{var s=em.find(Shipment.class,id).getPriceSnapshot();require(s!=null&&from.equals(s.getOriginProvinceCode())&&to.equals(s.getDestinationProvinceCode())&&s.getScope()==RateScopeType.CENTRAL_SOUTH,"Shipment snapshot codes/scope mismatch");
                money(s.getBaseFee(),base);money(s.getExtraFee(),extra);money(s.getBaseWeightKg(),"1.00");money(s.getStepWeightKg(),"1.00");money(s.getStepPrice(),"5000.00");return null;});
        }
    }
    private static <T extends Throwable>T rejects(Class<T> expected,Runnable action) {
        try{action.run();}catch(Throwable error){if(expected.isInstance(error))return expected.cast(error);throw error;}
        throw new IllegalStateException("Expected failure was not raised: "+expected.getSimpleName());
    }
    private static void as(User user,String role){SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user.getEmail(),null,List.of(new SimpleGrantedAuthority(role))));}
    private static void money(BigDecimal actual,String expected){require(actual!=null&&actual.compareTo(amount(expected))==0,"Monetary snapshot mismatch");}
    private static void pass(String name){System.out.println("PASS "+name);}
}
