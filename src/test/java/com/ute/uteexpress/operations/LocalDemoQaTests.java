package com.ute.uteexpress.operations;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.ShipmentStatus;
import jakarta.persistence.EntityManager;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Embedded server, real network HTTP, actual full application/security/repositories, disposable H2 only. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LocalDemoQaTests {
    ConfigurableApplicationContext app;
    String password,base;
    LocalDemoSeed seed;
    Map<String,Long> manifest;
    EntityManager em;
    TransactionTemplate tx;
    @BeforeAll void start() {
        password=UUID.randomUUID().toString();
        String ambientAddress=System.getProperty("server.address");
        // Another loopback address safely proves ambient deployment settings cannot override demo isolation.
        System.setProperty("server.address","127.0.0.2");
        try {app=LocalDemoApplication.start(password,0);}
        finally {if(ambientAddress==null)System.clearProperty("server.address");else System.setProperty("server.address",ambientAddress);}
        base="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort();
        seed=app.getBean(LocalDemoSeed.class);manifest=seed.seed(password);em=seed.em;
        tx=new TransactionTemplate(app.getBean(PlatformTransactionManager.class));
    }
    @AfterAll void stop() {if(app!=null)app.close();password=null;}
    HttpClient client() {return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).followRedirects(HttpClient.Redirect.NEVER).build();}
    HttpResponse<String> get(HttpClient client,String path) throws Exception {return client.send(HttpRequest.newBuilder(URI.create(base+path)).GET().build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));}
    String csrf(String html) {var matcher=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);assertTrue(matcher.find(),"Server-rendered form contains CSRF token");return matcher.group(1);}
    String enc(String value) {return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    HttpResponse<String> post(HttpClient client,String path,String body) throws Exception {return client.send(HttpRequest.newBuilder(URI.create(base+path)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));}
    HttpClient login(String name) throws Exception {
        var client=client();var response=post(client,"/login","_csrf="+enc(csrf(get(client,"/login").body()))+"&email="+enc(name+"@example.test")+"&password="+enc(password));
        assertEquals(302,response.statusCode());assertEquals(URI.create(base+"/account"),URI.create(base).resolve(response.headers().firstValue("location").orElseThrow()));return client;
    }
    <T>T query(java.util.function.Supplier<T> work) {return tx.execute(status->work.get());}
    long count(String type) {return query(()->em.createQuery("select count(x) from "+type+" x",Long.class).getSingleResult());}
    String tracking(String scenario) {return query(()->em.find(Shipment.class,manifest.get(scenario)).getTrackingNumber());}

    @Test void datasetHasEighteenConsistentServiceGeneratedScenariosAndPhysicalDocuments() {
        assertEquals(18,manifest.size());assertEquals(18,count("Shipment"));assertEquals(1,count("DeliveryProof"));assertEquals(1,count("ReturnReceipt"));
        assertEquals(1,count("TripDiscrepancy"));assertEquals(1,count("SupportTicket"));assertTrue(count("Notification")>0);
        query(()->{assertEquals(ShipmentStatus.DELIVERED,em.find(Shipment.class,manifest.get("DELIVERED")).getStatus());assertEquals(ShipmentStatus.RETURNED,em.find(Shipment.class,manifest.get("RETURNED")).getStatus());assertEquals(ShipmentStatus.RETURNING,em.find(Shipment.class,manifest.get("RETURNING")).getStatus());assertNull(em.find(Shipment.class,manifest.get("LINEHAUL")).getCurrentBranch());
            for(Long id:manifest.values()){var s=em.find(Shipment.class,id);assertNotNull(s.getShipmentMethod());assertNotNull(s.getTotalFee());assertTrue(em.createQuery("select count(e) from ShipmentEvent e where e.shipment.id=:id",Long.class).setParameter("id",id).getSingleResult()>0);}return null;});
    }
    @Test void secondSeedIsNoOpForEveryMappedTableAndKeepsIdsAndCredentials() {
        var before=query(()->{var rows=new TreeMap<String,Long>();for(var entity:em.getMetamodel().getEntities())rows.put(entity.getName(),em.createQuery("select count(x) from "+entity.getName()+" x",Long.class).getSingleResult());return rows;});
        assertEquals(manifest,seed.seed(password));
        query(()->{before.forEach((type,total)->assertEquals(total,em.createQuery("select count(x) from "+type+" x",Long.class).getSingleResult()));return null;});
    }
    @ParameterizedTest @CsvSource({"owner,/user/shipments,/admin/users","manager,/manager/shipments,/admin/users","destination-manager,/manager/trips,/admin/users","shipper,/shipper/deliveries,/manager/trips","admin,/admin/reports,/manager/trips"})
    void realBcryptLoginAndRoleSpecificTemplatesDoNotInventInheritance(String account,String allowed,String denied) throws Exception {
        var client=login(account);assertEquals(200,get(client,allowed).statusCode(),allowed);assertEquals(403,get(client,denied).statusCode(),denied);
        assertEquals(200,get(client,"/account").statusCode());
    }
    @Test void publicPagesAssetsAndErrorsRenderOverRealHttp() throws Exception {
        var client=client();for(String path:List.of("/login","/register","/tracking","/quote","/css/vendor/bootstrap.min.css","/js/notifications.js"))assertEquals(200,get(client,path).statusCode(),path);
        assertEquals(401,get(client,"/user/shipments/"+manifest.get("NEW")).statusCode());
        var home=get(client,"/");assertEquals(302,home.statusCode());assertEquals(URI.create(base+"/tracking"),URI.create(base).resolve(home.headers().firstValue("location").orElseThrow()));
        assertEquals(403,post(client,"/","").statusCode(),"Public root does not bypass CSRF");
        var missing=get(client,"/tracking?trackingNumber=UTE00000000000000000000000000000000");assertEquals(200,missing.statusCode());
    }
    @Test void loginAndLogoutRequireCsrfAndLogoutRevokesRealSession() throws Exception {
        var client=login("owner");assertEquals(403,post(client,"/logout","").statusCode());
        assertEquals(200,get(client,"/user/shipments").statusCode());String token=csrf(get(client,"/account").body());
        assertEquals(302,post(client,"/logout","_csrf="+enc(token)).statusCode());assertEquals(401,get(client,"/user/shipments").statusCode());
        assertEquals(403,post(client(),"/login","email=invalid&password=invalid").statusCode());
    }
    @Test void profileAndAddressCrudAreOwnerScopedValidatedAndCsrfProtected()throws Exception {
        assertEquals(401,get(client(),"/account/profile").statusCode());
        assertEquals(403,get(login("admin"),"/account/profile").statusCode());
        var owner=login("owner");var other=login("other");
        String page=get(owner,"/account/profile").body();assertTrue(page.contains("Hồ sơ và địa chỉ"));
        assertEquals(403,post(owner,"/account/profile","fullName=Changed&phoneNumber=0987654321").statusCode());
        String body="_csrf="+enc(csrf(page))+"&contactName="+enc("Liên hệ QA")+"&contactPhone=0982309999&province="+enc("Đà Nẵng")
            +"&district="+enc("Khu vực demo")+"&ward="+enc("Phường demo")+"&detailAddress="+enc("Địa chỉ mô phỏng QA")+"&defaultAddress=true";
        assertEquals(302,post(owner,"/account/profile/addresses",body).statusCode());
        Long address=query(()->em.createQuery("select a.id from UserAddress a where a.contactPhone='0982309999'",Long.class).getSingleResult());
        assertEquals(302,post(owner,"/account/profile/addresses/"+address,body.replace("&defaultAddress=true","")).statusCode());
        assertFalse(query(()->em.find(UserAddress.class,address).isDefault()));
        assertFalse(get(other,"/account/profile").body().contains("0982309999"));
        String otherCsrf=csrf(get(other,"/account/profile").body());
        assertEquals(404,post(other,"/account/profile/addresses/"+address+"/delete","_csrf="+enc(otherCsrf)).statusCode());
        assertEquals(404,post(other,"/account/profile/addresses/"+address,body.replace(enc(csrf(page)),enc(otherCsrf))).statusCode());
        assertEquals(302,post(owner,"/account/profile/addresses/"+address,body.replace("Li%C3%AAn+h%E1%BB%87+QA",enc("Đã cập nhật QA"))).statusCode());
        assertTrue(get(owner,"/account/profile").body().contains("Đã cập nhật QA"));
        assertEquals(200,post(owner,"/account/profile","_csrf="+enc(csrf(page))+"&fullName=&phoneNumber=short").statusCode());
        assertEquals(302,post(owner,"/account/profile/addresses/"+address+"/delete","_csrf="+enc(csrf(page))).statusCode());
        assertEquals(0,query(()->em.createQuery("select count(a) from UserAddress a where a.id=:id",Long.class).setParameter("id",address).getSingleResult()));
    }
    @Test void ownerOtherCustomerGuestAndManagerReceiveOnlyTheirPermittedContent() throws Exception {
        Long id=manifest.get("SUPPORT");var owner=login("owner");var other=login("other");
        assertEquals(200,get(owner,"/user/shipments/"+id).statusCode());assertEquals(404,get(other,"/user/shipments/"+id).statusCode());
        Long ticket=query(()->em.createQuery("select t.id from SupportTicket t",Long.class).getSingleResult());
        String publicMarker="Phản hồi công khai demo",internalMarker="GHI_CHU_NOI_BO_DEMO_KHONG_CONG_KHAI";
        var customer=get(owner,"/account/support/"+ticket);assertEquals(200,customer.statusCode());assertTrue(customer.body().contains(publicMarker));assertFalse(customer.body().contains(internalMarker));
        assertEquals(404,get(other,"/account/support/"+ticket).statusCode());
        var manager=get(login("destination-manager"),"/manager/tickets/"+ticket);assertEquals(200,manager.statusCode());assertTrue(manager.body().contains(internalMarker));
        var guest=get(client(),"/tracking?trackingNumber="+tracking("SUPPORT"));assertEquals(200,guest.statusCode());assertFalse(guest.body().contains(internalMarker));assertFalse(guest.body().contains("0901234567"));
    }
    @Test void shipmentStatusLabelsRenderForBothCustomersAndManagers() throws Exception {
        String customer=get(login("owner"),"/user/shipments").body();
        assertTrue(customer.contains("status-created\">Đã tạo</span>"));
        assertTrue(customer.contains("value=\"CREATED\">Đã tạo</option>"));
        String manager=get(login("manager"),"/manager/shipments").body();
        assertTrue(manager.contains("value=\"CREATED\">Đã tạo</option>"));
    }
    @Test void registrationReportsIsolatedMailFailureAndRollsBackAccountInsteadOfGenericDatabaseError() throws Exception {
        var client=client();String token=csrf(get(client,"/register").body());long users=count("User"),roles=count("UserRole"),otps=count("EmailOtp");
        var response=post(client,"/register","_csrf="+enc(token)+"&fullName=Browser+Registration&username=browser-registration&email=browser-registration%40example.test&phoneNumber=0909998444&password="+enc(password)+"&confirmPassword="+enc(password));
        assertEquals(200,response.statusCode());assertTrue(response.body().contains("Chức năng gửi email chưa được cấu hình"));assertFalse(response.body().contains("Không thể xử lý yêu cầu lúc này"));
        assertEquals(users,count("User"));assertEquals(roles,count("UserRole"));assertEquals(otps,count("EmailOtp"));
    }
    @Test void registrationPersistsUnverifiedStatusWithGeneratedH2Constraints() {
        query(()->{
            var constraints=em.createNativeQuery("select check_clause from information_schema.check_constraints where constraint_name in (select constraint_name from information_schema.table_constraints where table_schema='DBO' and table_name='USERS')",String.class).getResultList();
            var user=new User();user.setUsername("registration-regression");user.setEmail("registration-regression@example.test");user.setFullName("Registration regression");user.setPhoneNumber("0909998333");user.setPasswordHash(app.getBean(PasswordEncoder.class).encode(password));user.setStatus(com.ute.uteexpress.enums.UserStatus.UNVERIFIED);
            try {em.persist(user);em.flush();}catch(RuntimeException error){throw new AssertionError("Generated USERS checks: "+constraints,error);}
            em.remove(user);em.flush();return null;
        });
    }
    @Test void demoAccountsHaveBcryptAndExactDatabaseRoles() {
        var encoder=app.getBean(PasswordEncoder.class);
        query(()->{for(var user:em.createQuery("select u from User u",User.class).getResultList())assertTrue(encoder.matches(password,user.getPasswordHash()));assertEquals(4L,em.createQuery("select count(r) from Role r",Long.class).getSingleResult());return null;});
        assertEquals(List.of("demo-local"),List.of(app.getEnvironment().getActiveProfiles()));assertEquals("false",app.getEnvironment().getProperty("spring.flyway.enabled"));assertEquals("127.0.0.1",app.getEnvironment().getProperty("server.address"));
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"short","abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxy","界界界界界界界界界界界界界界界界界界界界界界界界界"})
    void invalidDemoCredentialIsRejectedBeforeAnyDatabaseIsAllocated(String value) {assertThrows(IllegalArgumentException.class,()->LocalDemoApplication.start(value,0));}
}
