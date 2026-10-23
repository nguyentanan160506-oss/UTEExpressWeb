package com.ute.uteexpress.security;

import com.ute.uteexpress.config.*;
import com.ute.uteexpress.controller.*;
import com.ute.uteexpress.entity.User;
import com.ute.uteexpress.enums.UserStatus;
import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.service.notifications.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual embedded Tomcat, HTTP login/session, WebSocket upgrade and raw STOMP frames. Persistence is mocked; no SQL Server. */
@SpringBootTest(classes=NotificationWebSocketTests.App.class, webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
    "spring.flyway.enabled=false", "spring.sql.init.mode=never", "debug=false", "logging.level.root=INFO"
})
class NotificationWebSocketTests {
    @Configuration(proxyBeanMethods=false) @EnableAutoConfiguration
    @Import({SecurityConfig.class,SecurityErrorHandlers.class,DatabaseUserDetailsService.class,AuthenticationResponseHandlers.class,
        AuthController.class,NotificationController.class,NotificationService.class,NotificationIdentity.class,NotificationSocketSessions.class,NotificationWebSocketConfig.class,OtpConfig.class})
    static class App { }
    @LocalServerPort int port;
    @Autowired PasswordEncoder encoder;
    @Autowired SimpMessagingTemplate broker;
    @Autowired NotificationSocketSessions sessions;
    @Autowired org.springframework.messaging.simp.user.SimpUserRegistry registry;
    @Autowired org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler simpleBroker;
    @MockitoBean UserRepository users;
    @MockitoBean UserRoleRepository roles;
    @MockitoBean NotificationRepository notices;
    final List<Probe> probes=new ArrayList<>();
    final Map<String,User> accounts=new ConcurrentHashMap<>();
    String password;
    @BeforeEach void setup() {
        accounts.clear();password=UUID.randomUUID().toString();String hash=encoder.encode(password);
        for(long id=1;id<=2;id++){User u=new User();u.setId(id);u.setEmail("socket"+id+"@example.test");u.setUsername("socket"+id);u.setStatus(UserStatus.ACTIVE);u.setPasswordHash(hash);accounts.put(u.getEmail(),u);}
        when(users.findByEmail(anyString())).thenAnswer(c->Optional.ofNullable(accounts.get(c.getArgument(0))));when(roles.findRoleNamesByUserId(anyLong())).thenReturn(List.of("ROLE_USER"));
    }
    @AfterEach void close() { for(Probe p:probes)if(p.socket!=null)p.socket.abort();probes.clear(); }
    URI uri(String path){return URI.create("http://localhost:"+port+path);}
    record Login(HttpClient http,String cookie,String csrfHeader,String csrf) { }
    static String extract(String text,String regex) {var m=Pattern.compile(regex).matcher(text);assertTrue(m.find(),"Expected CSRF field");return m.group(1);}
    Login login(long id) throws Exception {
        var cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);var http=HttpClient.newBuilder().cookieHandler(cookies).build();
        var page=http.send(HttpRequest.newBuilder(uri("/login")).GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,page.statusCode());
        String token=extract(page.body(),"name=\"_csrf\"[^>]*value=\"([^\"]+)\"");
        String form="email="+URLEncoder.encode("socket"+id+"@example.test",java.nio.charset.StandardCharsets.UTF_8)+"&password="+password+"&_csrf="+URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8);
        var result=http.send(HttpRequest.newBuilder(uri("/login")).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build(),HttpResponse.BodyHandlers.ofString());assertEquals(302,result.statusCode());assertEquals("/account",URI.create(result.headers().firstValue("Location").orElse("")).getPath());
        var csrf=http.send(HttpRequest.newBuilder(uri("/api/notifications/csrf")).header("Accept","application/json").GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,csrf.statusCode());
        String cookie=cookies.getCookieStore().getCookies().stream().map(c->c.getName()+"="+c.getValue()).collect(java.util.stream.Collectors.joining("; "));
        return new Login(http,cookie,extract(csrf.body(),"\"headerName\":\"([^\"]+)\""),extract(csrf.body(),"\"token\":\"([^\"]+)\""));
    }
    Probe upgrade(Login login,String origin) throws Exception {
        Probe p=new Probe();probes.add(p);p.socket=HttpClient.newHttpClient().newWebSocketBuilder().header("Cookie",login.cookie()).header("Origin",origin).subprotocols("v12.stomp")
            .connectTimeout(Duration.ofSeconds(5)).buildAsync(URI.create("ws://localhost:"+port+"/ws/notifications"),p).get(5,TimeUnit.SECONDS);return p;
    }
    Probe connected(Login login) throws Exception {
        Probe p=upgrade(login,"http://localhost:"+port);p.send("CONNECT\naccept-version:1.2\nhost:localhost\nheart-beat:0,0\n"+login.csrfHeader()+":"+login.csrf()+"\n\n\0");assertTrue(p.next().startsWith("CONNECTED"));return p;
    }
    void subscribe(Probe p) throws Exception {
        String subscription=UUID.randomUUID().toString();
        p.send("SUBSCRIBE\nid:"+subscription+"\ndestination:/user/queue/notifications\n\n\0");
        // The simple broker does not implement STOMP receipts. Observe its actual subscription registry.
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(System.nanoTime()<deadline){
            var subscriptions=registry.findSubscriptions(s->s.getId().equals(subscription));
            if(!subscriptions.isEmpty()){
                String session=subscriptions.iterator().next().getSession().getId();
                var headers=org.springframework.messaging.simp.SimpMessageHeaderAccessor.create(org.springframework.messaging.simp.SimpMessageType.MESSAGE);
                headers.setDestination("/queue/notifications-user"+session);
                var message=org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0],headers.getMessageHeaders());
                var resolved=simpleBroker.getSubscriptionRegistry().findSubscriptions(message);
                if(resolved.containsKey(session)&&resolved.get(session).contains(subscription))return;
            }
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        fail("Private subscription was not registered by broker");
    }
    @Test void actualAuthenticatedHandshakeAndPrivateDeliveryAreIsolatedBetweenTwoUsers() throws Exception {
        Probe a=connected(login(1)),b=connected(login(2));subscribe(a);subscribe(b);
        broker.convertAndSendToUser("socket1@example.test","/queue/notifications",Map.of("type","REFRESH"));String frame=a.next();assertTrue(frame.startsWith("MESSAGE"));assertTrue(frame.contains("REFRESH"));assertFalse(frame.contains("socket1@"));assertFalse(frame.contains("phone"));assertNull(b.frames.poll(300,TimeUnit.MILLISECONDS));
        broker.convertAndSendToUser("socket2@example.test","/queue/notifications",Map.of("type","REFRESH"));assertTrue(b.next().contains("REFRESH"));assertNull(a.frames.poll(200,TimeUnit.MILLISECONDS));
    }
    @Test void multipleTabsGetSameRefreshWithoutNewDatabaseWrites() throws Exception {
        Login l=login(1);Probe a=connected(l),b=connected(l);subscribe(a);subscribe(b);broker.convertAndSendToUser("socket1@example.test","/queue/notifications",Map.of("type","REFRESH"));assertTrue(a.next().startsWith("MESSAGE"));assertTrue(b.next().startsWith("MESSAGE"));verify(notices,never()).saveAndFlush(any());
    }
    @Test void anonymousUpgradeIsRejected() throws Exception {
        var http=HttpClient.newHttpClient();var response=http.send(HttpRequest.newBuilder(uri("/ws/notifications")).GET().build(),HttpResponse.BodyHandlers.discarding());assertEquals(401,response.statusCode());
        var p=new Probe();assertThrows(ExecutionException.class,()->http.newWebSocketBuilder().buildAsync(URI.create("ws://localhost:"+port+"/ws/notifications"),p).get(5,TimeUnit.SECONDS));
    }
    @Test void crossOriginUpgradeIsRejected() throws Exception {Login l=login(1);assertThrows(ExecutionException.class,()->upgrade(l,"https://untrusted.example"));}
    @Test void connectWithoutCsrfIsRejected() throws Exception {Probe p=upgrade(login(1),"http://localhost:"+port);p.send("CONNECT\naccept-version:1.2\nhost:localhost\n\n\0");assertTrue(p.next().startsWith("ERROR"));}
    @ParameterizedTest @ValueSource(strings={"/topic/notifications","/queue/notifications","/user/socket2@example.test/queue/notifications","/user/queue/notifications/extra","/topic/tracking/UTE123"})
    void unauthorizedSubscriptionsAreRejected(String path) throws Exception {Probe p=connected(login(1));p.send("SUBSCRIBE\nid:bad\ndestination:"+path+"\n\n\0");assertTrue(p.next().startsWith("ERROR"));}
    @Test void sendingFakeNotificationsIsRejected() throws Exception {Probe p=connected(login(1));subscribe(p);p.send("SEND\ndestination:/user/queue/notifications\n\nfake\0");assertTrue(p.next().startsWith("ERROR"));}
    @Test void lockedAccountCannotReceiveOnExistingSubscription() throws Exception {Probe p=connected(login(1));subscribe(p);accounts.get("socket1@example.test").setStatus(UserStatus.LOCKED);broker.convertAndSendToUser("socket1@example.test","/queue/notifications",Map.of("type","REFRESH"));assertTrue(p.closed.await(3,TimeUnit.SECONDS));assertNull(p.frames.poll(100,TimeUnit.MILLISECONDS));}
    @Test void removedRoleClosesExistingSocketOnRevalidation() throws Exception {Probe p=connected(login(1));subscribe(p);when(roles.findRoleNamesByUserId(1L)).thenReturn(List.of());sessions.revokeExpired();assertTrue(p.closed.await(3,TimeUnit.SECONDS));}
    @Test void logoutInvalidatesSessionAndClosesOpenSocket() throws Exception {
        Login l=login(1);Probe p=connected(l);subscribe(p);var result=l.http().send(HttpRequest.newBuilder(uri("/logout")).header(l.csrfHeader(),l.csrf()).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.discarding());assertEquals(302,result.statusCode());assertTrue(p.closed.await(3,TimeUnit.SECONDS));
        assertEquals(401,l.http().send(HttpRequest.newBuilder(uri("/api/notifications/unread-count")).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode());
    }
    @Test void inactiveHttpSessionIsClosedWithoutExtendingLifetimeThroughWebSocket() throws Exception {
        Probe p=connected(login(1));subscribe(p);
        @SuppressWarnings("unchecked") var sockets=(Map<String,org.springframework.web.socket.WebSocketSession>)org.springframework.test.util.ReflectionTestUtils.getField(sessions,"sockets");
        var socket=sockets.values().stream().filter(s->s.getPrincipal().getName().equals("socket1@example.test")).findFirst().orElseThrow();
        var httpSession=(jakarta.servlet.http.HttpSession)socket.getAttributes().get(NotificationSocketSessions.class.getName());httpSession.setMaxInactiveInterval(1);
        Thread.sleep(1200);sessions.revokeExpired();assertTrue(p.closed.await(3,TimeUnit.SECONDS));
    }
    @Test void reconnectCanReconcileAuthoritativeUnreadCount() throws Exception {
        Login l=login(1);Probe first=connected(l);subscribe(first);first.socket.abort();when(notices.unreadCount(1L)).thenReturn(7L);Probe second=connected(l);subscribe(second);
        var response=l.http().send(HttpRequest.newBuilder(uri("/api/notifications/unread-count")).header("Accept","application/json").GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,response.statusCode());assertTrue(response.body().contains("7"));
    }
    static class Probe implements WebSocket.Listener {
        WebSocket socket;final BlockingQueue<String> frames=new LinkedBlockingQueue<>();final CountDownLatch closed=new CountDownLatch(1);final StringBuilder buffer=new StringBuilder();
        @Override public void onOpen(WebSocket webSocket){webSocket.request(1);}
        @Override public CompletionStage<?> onText(WebSocket ws,CharSequence data,boolean last){buffer.append(data);int end;while((end=buffer.indexOf("\0"))>=0){frames.add(buffer.substring(0,end).stripLeading());buffer.delete(0,end+1);}if(buffer.toString().isBlank())buffer.setLength(0);ws.request(1);return null;}
        @Override public CompletionStage<?> onClose(WebSocket ws,int code,String reason){closed.countDown();return null;}
        @Override public void onError(WebSocket ws,Throwable error){closed.countDown();}
        void send(String text){socket.sendText(text,true).join();}
        String next() throws Exception {String frame=frames.poll(5,TimeUnit.SECONDS);assertNotNull(frame,"Timed out waiting for STOMP frame");return frame;}
    }
}
