package com.ute.uteexpress.operations;

import com.ute.uteexpress.enums.UserStatus;
import com.ute.uteexpress.repository.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real SMTP + application HTTP + H2 only; no production credentials or hardcoded OTP. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LocalDemoMailTests {
    ConfigurableApplicationContext app;
    String password,base;
    @BeforeAll void start(){
        password=UUID.randomUUID().toString();
        app=LocalDemoApplication.start(password,0,true);
        base="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort();
    }
    @AfterAll void stop(){if(app!=null)app.close();password=null;}
    HttpClient client(){return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();}
    HttpResponse<String> get(HttpClient client,String path)throws Exception{
        return client.send(HttpRequest.newBuilder(URI.create(base+path)).GET().build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    String csrf(String body){
        var match=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(body);
        assertTrue(match.find(),"CSRF form present");return match.group(1);
    }
    String enc(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    HttpResponse<String> post(HttpClient client,String path,Map<String,String> fields)throws Exception{
        var body=new StringJoiner("&");fields.forEach((key,value)->body.add(enc(key)+"="+enc(value)));
        return client.send(HttpRequest.newBuilder(URI.create(base+path)).header("Content-Type","application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    HttpClient login(String email)throws Exception{
        var client=client();var response=post(client,"/login",Map.of("_csrf",csrf(get(client,"/login").body()),"email",email,"password",password));
        assertEquals(302,response.statusCode());return client;
    }
    @Test void smtpIsLoopbackUnauthenticatedAndRequiresOwnedH2()throws Exception{
        var sink=app.getBean(DemoSmtpServer.class);var sender=app.getBean(JavaMailSenderImpl.class);
        assertEquals("127.0.0.1",sender.getHost());assertEquals(sink.port(),sender.getPort());
        assertEquals("false",sender.getJavaMailProperties().getProperty("mail.smtp.auth"));
        assertNull(sender.getUsername());assertNull(sender.getPassword());
        var source=mock(DataSource.class);var connection=mock(java.sql.Connection.class);var metadata=mock(java.sql.DatabaseMetaData.class);
        when(source.getConnection()).thenReturn(connection);when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("H2");when(metadata.getURL()).thenReturn("jdbc:h2:mem:unowned");
        assertThrows(IllegalStateException.class,()->DemoSmtpServer.requireDisposableH2(source));
    }
    @Test void actualSmtpRegistrationRequiresOtpThenAllowsLoginAndProtectsInbox()throws Exception{
        String email="mail-regression@example.test";var client=client();String token=csrf(get(client,"/register").body());
        var response=post(client,"/register",Map.of("_csrf",token,"fullName","Khách đăng ký H2","username","mail-regression",
            "email",email,"phoneNumber","0909998666","password",password,"confirmPassword",password));
        assertEquals(302,response.statusCode());assertEquals("/verify-email",URI.create(response.headers().firstValue("location").orElseThrow()).getPath());
        var users=app.getBean(UserRepository.class);var roles=app.getBean(UserRoleRepository.class);
        var user=users.findByEmail(email).orElseThrow();assertEquals(UserStatus.UNVERIFIED,user.getStatus());
        assertEquals(List.of("ROLE_USER"),roles.findRoleNamesByUserId(user.getId()));
        var mails=app.getBean(DemoSmtpServer.class).messages();
        var mail=mails.stream().filter(m->m.recipient.equals(email)).findFirst().orElseThrow();
        assertTrue(mail.code.matches("[0-9]{6}"),"Received valid code through SMTP");
        assertFalse(mail.toString().contains(mail.code),"No secret in diagnostic representation");
        var unverifiedLogin=post(client,"/login",Map.of("_csrf",csrf(get(client,"/login").body()),"email",email,"password",password));
        assertTrue(unverifiedLogin.headers().firstValue("location").orElse("").contains("error"),"Unverified login denied");
        String wrong=String.format("%06d",(Integer.parseInt(mail.code)+1)%1_000_000);
        var wrongReply=post(client,"/verify-email",Map.of("_csrf",csrf(get(client,"/verify-email").body()),"email",email,"otp",wrong));
        assertEquals(200,wrongReply.statusCode());assertEquals(UserStatus.UNVERIFIED,users.findByEmail(email).orElseThrow().getStatus());
        var verified=post(client,"/verify-email",Map.of("_csrf",csrf(wrongReply.body()),"email",email,"otp",mail.code));
        assertEquals(302,verified.statusCode());assertEquals(UserStatus.ACTIVE,users.findByEmail(email).orElseThrow().getStatus());
        var authenticated=login(email);assertEquals(200,get(authenticated,"/user/shipments").statusCode());
        assertNewRegisteredUserCanFindDestinationBranches(authenticated,user.getId());
        assertEquals(403,get(authenticated,"/admin/demo-mailbox").statusCode());
        assertEquals(401,get(client(),"/admin/demo-mailbox").statusCode());
        var inbox=get(login("admin@example.test"),"/admin/demo-mailbox");assertEquals(200,inbox.statusCode());
        assertTrue(inbox.body().contains(mail.code),"Only ADMIN test inbox displays captured code");
        assertTrue(inbox.headers().firstValue("cache-control").orElse("").contains("no-store"));
        var replay=post(client,"/verify-email",Map.of("_csrf",csrf(get(client,"/verify-email").body()),"email",email,"otp",mail.code));
        assertEquals(200,replay.statusCode());assertTrue(replay.body().contains("Mã không hợp lệ"));
    }
    private String destinationSelect(String html){
        var match=Pattern.compile("(?s)<select[^>]*id=\"destinationBranchId\"[^>]*>.*?</select>").matcher(html);
        assertTrue(match.find(),"Destination select rendered");return match.group();
    }
    private void assertNewRegisteredUserCanFindDestinationBranches(HttpClient authenticated,Long userId)throws Exception{
        assertTrue(app.getBean(EmployeeRepository.class).findByUserId(userId).isEmpty(),"New USER has no employee/branch membership");
        var branches=app.getBean(BranchRepository.class).findAll();
        assertEquals(5,branches.size());
        assertTrue(branches.stream().allMatch(b->b.getStatus()==com.ute.uteexpress.enums.BranchStatus.ACTIVE));
        var areas=app.getBean(BranchServiceAreaRepository.class).findAll();
        assertEquals(4,areas.size());assertTrue(areas.stream().allMatch(a->a.isActive()&&a.isSupportsDelivery()));
        var page=get(authenticated,"/user/shipments/create");assertEquals(200,page.statusCode());
        assertFalse(destinationSelect(page.body()).contains("Chi nhánh TP.HCM demo"),"Fresh form has not loaded address-dependent options");
        assertTrue(page.body().contains("Danh sách được tải sau khi"));
        assertTrue(page.body().contains("Hồ Chí Minh / Khu vực demo / Phường demo"),"Examples come from actual H2 service areas");
        long quotes=app.getBean(ShipmentQuoteRepository.class).count(),shipments=app.getBean(ShipmentRepository.class).count();
        var lookup=new HashMap<String,String>();
        lookup.put("_csrf",csrf(page.body()));lookup.put("shipmentMethod","HOME_PICKUP");
        lookup.put("senderProvince","Đà Nẵng");lookup.put("senderDistrict","Khu vực demo");lookup.put("senderWard","Phường demo");
        lookup.put("receiverProvince","Hồ Chí Minh");lookup.put("receiverDistrict","Khu vực demo");lookup.put("receiverWard","Phường demo");
        for(String method:List.of("HOME_PICKUP","BRANCH_DROPOFF")){
            lookup.put("shipmentMethod",method);lookup.put("receiverDistrict","Khu vực demo");
            var found=post(authenticated,"/user/shipments/branches",lookup);
            assertEquals(200,found.statusCode());assertTrue(destinationSelect(found.body()).contains("Chi nhánh TP.HCM demo"));
        }
        lookup.put("receiverDistrict","Quận chưa cấu hình");
        var outside=post(authenticated,"/user/shipments/branches",lookup);
        assertFalse(destinationSelect(outside.body()).contains("Chi nhánh TP.HCM demo"));
        assertTrue(outside.body().contains("Không có chi nhánh nhận đang hoạt động phục vụ địa chỉ đã nhập"));
        lookup.put("shipmentMethod","HOME_PICKUP");lookup.put("receiverDistrict","Khu vực demo");
        lookup.put("senderProvince","tp đà nẵng");lookup.put("senderDistrict","huyện núi thành");lookup.put("senderWard","xã núi thành");
        var reportedOrigin=post(authenticated,"/user/shipments/branches",lookup);
        assertTrue(reportedOrigin.body().contains("Không có chi nhánh gửi đang hoạt động"));
        assertTrue(destinationSelect(reportedOrigin.body()).contains("Chi nhánh TP.HCM demo"),"Origin coverage must not suppress valid receiver choices");
        lookup.put("receiverProvince","tp đà nẵng");lookup.put("receiverDistrict","huyện núi thành");lookup.put("receiverWard","xã núi thành");
        var reportedReceiver=post(authenticated,"/user/shipments/branches",lookup);
        assertTrue(reportedReceiver.body().contains("Không có chi nhánh nhận đang hoạt động"));
        assertFalse(destinationSelect(reportedReceiver.body()).contains("Chi nhánh TP.HCM demo"));
        assertEquals(quotes,app.getBean(ShipmentQuoteRepository.class).count(),"Lookup must not create a quote");
        assertEquals(shipments,app.getBean(ShipmentRepository.class).count(),"Lookup must not create a shipment");
    }
    @Test void externalMailRecipientIsRejectedAndAccountOtpAndRoleRollBack()throws Exception{
        var users=app.getBean(UserRepository.class);var roles=app.getBean(UserRoleRepository.class);var otps=app.getBean(EmailOtpRepository.class);
        long userCount=users.count(),roleCount=roles.count(),otpCount=otps.count();var client=client();
        var response=post(client,"/register",Map.of("_csrf",csrf(get(client,"/register").body()),"fullName","Khách SMTP ngoài phạm vi",
            "username","external-regression","email","external@outside.invalid","phoneNumber","0909998777","password",password,"confirmPassword",password));
        assertEquals(200,response.statusCode());assertTrue(response.body().contains("Không thể gửi email xác minh"));
        assertEquals(userCount,users.count());assertEquals(roleCount,roles.count());assertEquals(otpCount,otps.count());
        assertFalse(users.existsByEmail("external@outside.invalid"));
    }
}
