package com.ute.uteexpress.finaldemo;

import com.ute.uteexpress.UteexpressApplication;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.entity.id.UserRoleId;
import com.ute.uteexpress.enums.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletResponse;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import static org.junit.jupiter.api.Assertions.*;

/** Real servlet error dispatch and cookie isolation, disposable H2 only. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FriendlyErrorPagesTests {
    ConfigurableApplicationContext app;String base,password;
    @BeforeAll void start(){
        password=UUID.randomUUID()+"A!";
        app=new SpringApplicationBuilder(UteexpressApplication.class,FinalDemoTests.TestConfig.class,ProbeConfig.class).run(
            "--spring.config.location=classpath:/final-demo.properties","--spring.profiles.active=final-demo-h2-test,final-stability-h2-probe",
            "--server.address=127.0.0.1","--server.port=0","--spring.flyway.enabled=false","--spring.jpa.hibernate.ddl-auto=create-drop","--uteexpress.notifications.worker.enabled=false");
        base="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort();
        new TransactionTemplate(app.getBean(PlatformTransactionManager.class)).execute(st->{
            var em=app.getBean(EntityManager.class);var r=new com.ute.uteexpress.entity.Role();r.setName("ROLE_USER");em.persist(r);
            var u=new User();u.setUsername("stability-test");u.setEmail("stability-test@example.test");u.setFullName("Khách kiểm thử");u.setPhoneNumber("0000000001");u.setPasswordHash(app.getBean(PasswordEncoder.class).encode(password));u.setStatus(UserStatus.ACTIVE);em.persist(u);em.flush();var link=new UserRole();link.setUser(u);link.setRole(r);link.setId(new UserRoleId(u.getId(),r.getId()));em.persist(link);
            var hub=new Branch();hub.setCode("QA-HUB");hub.setName("Hub kiểm thử");hub.setProvince("Đà Nẵng");hub.setDistrict("Địa bàn QA");hub.setWard("Phường QA");hub.setDetailAddress("Điểm kiểm thử H2");hub.setPhone("0000000000");hub.setBranchType(com.ute.uteexpress.enums.BranchType.CENTRAL_HUB);em.persist(hub);
            var local=new Branch();local.setCode("QA-LOCAL");local.setName("Chi nhánh kiểm thử phản hồi HTML lớn");local.setProvince("Đà Nẵng");local.setDistrict("Địa bàn QA");local.setWard("Phường QA");local.setDetailAddress("Điểm kiểm thử H2");local.setPhone("0000000000");local.setBranchType(com.ute.uteexpress.enums.BranchType.LOCAL_BRANCH);local.setParentHub(hub);em.persist(local);
            for(int i=0;i<70;i++){var area=new BranchServiceArea();area.setBranch(local);area.setProvince("Đà Nẵng");area.setDistrict("Địa bàn QA phản hồi lớn");area.setWard("Phường kiểm thử "+i);area.setShipmentMethod(com.ute.uteexpress.enums.ShipmentMethod.HOME_PICKUP);area.setSupportsDelivery(true);em.persist(area);}
            return null;});
    }
    @AfterAll void stop(){if(app!=null)app.close();password=null;}
    HttpClient client(CookieManager cookies){return HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();}
    HttpResponse<String> get(HttpClient c,String path)throws Exception{return c.send(HttpRequest.newBuilder(URI.create(base+path)).header("Accept","text/html").GET().build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));}
    String token(String html){var m=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);assertTrue(m.find(),"CSRF form token exists");return m.group(1);}
    String enc(String v){return URLEncoder.encode(v,StandardCharsets.UTF_8);}
    HttpResponse<String> post(HttpClient c,String path,String body)throws Exception{return c.send(HttpRequest.newBuilder(URI.create(base+path)).header("Accept","text/html").header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));}
    void friendly(HttpResponse<String> r,int code){assertEquals(code,r.statusCode());assertTrue(r.body().contains("data-error-page"));assertFalse(r.body().contains("Whitelabel"));assertFalse(r.body().contains("PRIVATE_QA_FAILURE"));assertFalse(r.body().contains("java.lang."));}
    HttpClient login(CookieManager cookies)throws Exception{var c=client(cookies);var form=get(c,"/login");assertEquals(302,post(c,"/login","_csrf="+enc(token(form.body()))+"&email=stability-test%40example.test&password="+enc(password)).statusCode());return c;}
    @Test void unauthorizedHtmlRemains401AndUsesFriendlyTemplate()throws Exception{friendly(get(client(new CookieManager(null,CookiePolicy.ACCEPT_ALL)),"/user/shipments"),401);}
    @Test void csrf403IsFriendlyAndNotConvertedToSuccess()throws Exception{var c=client(new CookieManager(null,CookiePolicy.ACCEPT_ALL));friendly(post(c,"/login",""),403);}
    @Test void missingStatic404IsFriendly()throws Exception{friendly(get(client(new CookieManager(null,CookiePolicy.ACCEPT_ALL)),"/css/stability-missing.css"),404);}
    @Test void unknownOrUnownedShipment404IsFriendly()throws Exception{friendly(get(login(new CookieManager(null,CookiePolicy.ACCEPT_ALL)),"/user/shipments/9223372036854775807"),404);}
    @Test void role403RemainsForbidden()throws Exception{friendly(get(login(new CookieManager(null,CookiePolicy.ACCEPT_ALL)),"/admin/users"),403);}
    @Test void server500DoesNotExposeExceptionOrErrorMessage()throws Exception{friendly(get(login(new CookieManager(null,CookiePolicy.ACCEPT_ALL)),"/user/shipments/qa-probe-error"),500);}
    @Test void malformedForm400UsesGeneric4xxTemplate()throws Exception{friendly(get(login(new CookieManager(null,CookiePolicy.ACCEPT_ALL)),"/user/shipments?page=not-a-number"),400);}
    @Test void api401And403KeepProblemJsonContract()throws Exception{
        var c=client(new CookieManager(null,CookiePolicy.ACCEPT_ALL));
        var response=c.send(HttpRequest.newBuilder(URI.create(base+"/api/notifications")).header("Accept","application/json").GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(401,response.statusCode());assertTrue(response.headers().firstValue("content-type").orElse("").contains("application/problem+json"));assertFalse(response.body().contains("<html"));
        var forbidden=c.send(HttpRequest.newBuilder(URI.create(base+"/login")).header("Accept","application/json").POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());assertEquals(403,forbidden.statusCode());assertFalse(forbidden.body().contains("<html"));
    }
    @Test void firstAnonymousQuoteWithLargeCoverageMaterializesCsrfBeforeStreaming()throws Exception{
        var response=get(client(new CookieManager(null,CookiePolicy.ACCEPT_ALL)),"/quote");
        assertEquals(200,response.statusCode());assertTrue(response.body().length()>20000,"Real coverage table exceeds servlet output buffer");assertTrue(response.body().contains("name=\"_csrf\""));assertTrue(response.body().contains("Vùng phục vụ đang cấu hình"));assertFalse(response.body().contains("Whitelabel"));
    }
    @Test void namedCookieRotatesAndDoesNotOverwriteOtherLocalAppSession()throws Exception{
        var cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);var marker=new HttpCookie("JSESSIONID","other-app-marker");marker.setPath("/");cookies.getCookieStore().add(URI.create("http://127.0.0.1:8083/"),marker);
        var c=client(cookies);var form=get(c,"/login");String before=cookies.getCookieStore().getCookies().stream().filter(v->v.getName().equals("UTEEXPRESS_FINALDEMO_SESSION")).findFirst().orElseThrow().getValue();
        var signed=post(c,"/login","_csrf="+enc(token(form.body()))+"&email=stability-test%40example.test&password="+enc(password));assertEquals(302,signed.statusCode());
        String after=cookies.getCookieStore().getCookies().stream().filter(v->v.getName().equals("UTEEXPRESS_FINALDEMO_SESSION")).findFirst().orElseThrow().getValue();assertTrue(!before.equals(after),"Session IDs rotate; values omitted");
        assertTrue(cookies.getCookieStore().getCookies().stream().anyMatch(v->v.getName().equals("JSESSIONID")&&v.getValue().equals("other-app-marker")),"Unrelated app cookie preserved");
        assertEquals(200,get(c,"/user/shipments").statusCode());assertEquals(302,post(c,"/logout","_csrf="+enc(token(get(c,"/account").body()))).statusCode());assertEquals(401,get(c,"/account").statusCode());
    }
    @TestConfiguration(proxyBeanMethods=false) static class ProbeConfig {
        @Bean org.springframework.boot.web.servlet.ServletRegistrationBean<jakarta.servlet.http.HttpServlet> probeServlet(){
            return new org.springframework.boot.web.servlet.ServletRegistrationBean<>(new jakarta.servlet.http.HttpServlet(){
                @Override protected void doGet(jakarta.servlet.http.HttpServletRequest request,HttpServletResponse response)throws java.io.IOException{response.sendError(500,"PRIVATE_QA_FAILURE");}
            },"/user/shipments/qa-probe-error");
        }
    }
}
