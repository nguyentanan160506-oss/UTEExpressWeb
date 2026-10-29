package com.ute.uteexpress.operations;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Explicit operator tool: isolated headless Chrome + loopback H2 demo only, never JDBC. */
public final class BrowserQaTool implements AutoCloseable {
    static final ObjectMapper JSON=new ObjectMapper();
    static final String BASE="http://127.0.0.1:18082";
    final HttpClient http=HttpClient.newHttpClient();
    final AtomicInteger sequence=new AtomicInteger();
    final Map<Integer,CompletableFuture<JsonNode>> pending=new ConcurrentHashMap<>();
    final List<Map<String,Object>> cases=new ArrayList<>();
    final List<String> javascriptErrors=new CopyOnWriteArrayList<>();
    final Set<String> serverErrors=new ConcurrentSkipListSet<>();
    final AtomicInteger stompConnected=new AtomicInteger(),stompMessages=new AtomicInteger();
    final List<String> ownedContexts=new ArrayList<>();
    private final List<String> runtimeSecrets=new ArrayList<>();
    WebSocket socket;
    String password;
    String createdShipment,homeShipment,tracking,proofTracking;
    BrowserQaTool(String password)throws Exception {
        this(password,null);
    }
    BrowserQaTool(String password,String requestedSocket)throws Exception {
        LocalDemoSeed.requirePassword(password);this.password=password;
        rememberSecret(password);
        var version=JSON.readTree(http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:19222/json/version")).GET().build(),HttpResponse.BodyHandlers.ofString()).body());
        if(!version.path("User-Agent").asText().contains("HeadlessChrome"))throw new IllegalStateException("Dedicated headless Chrome required");
        var tabs=JSON.readTree(http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:19222/json/list")).GET().build(),HttpResponse.BodyHandlers.ofString()).body());
        String ws=requestedSocket;if(ws==null)for(var tab:tabs)if(tab.path("type").asText().equals("page")){ws=tab.path("webSocketDebuggerUrl").asText();break;}
        if(ws==null || !ws.startsWith("ws://127.0.0.1:19222/"))throw new IllegalStateException("No dedicated local target");
        socket=http.newWebSocketBuilder().buildAsync(URI.create(ws),new WebSocket.Listener(){
            final StringBuilder message=new StringBuilder();
            public void onOpen(WebSocket s){s.request(1);}
            public CompletionStage<?> onText(WebSocket s,CharSequence data,boolean last){
                message.append(data);if(last){try{var item=JSON.readTree(message.toString());message.setLength(0);
                    if(item.has("id")){var promise=pending.remove(item.get("id").asInt());if(promise!=null){if(item.has("error"))promise.completeExceptionally(new IllegalStateException("CDP command failed"));else promise.complete(item.path("result"));}}
                    else if(item.path("method").asText().equals("Runtime.exceptionThrown"))javascriptErrors.add(item.path("params").path("exceptionDetails").path("text").asText());
                    else if(item.path("method").asText().equals("Runtime.consoleAPICalled") && item.path("params").path("type").asText().equals("error"))javascriptErrors.add("console.error observed (arguments deliberately omitted)");
                    else if(item.path("method").asText().equals("Network.responseReceived")){var response=item.path("params").path("response");if(response.path("status").asInt()>=500)serverErrors.add(URI.create(response.path("url").asText()).getPath()+":"+response.path("status").asInt());}
                    else if(item.path("method").asText().equals("Network.webSocketFrameReceived")){
                        // Inspect command only. Never retain/log raw frames: CONNECT carries CSRF.
                        String payload=item.path("params").path("response").path("payloadData").asText();
                        if(payload.startsWith("CONNECTED"))stompConnected.incrementAndGet();
                        if(payload.startsWith("MESSAGE"))stompMessages.incrementAndGet();
                    }
                }catch(Exception failure){for(var promise:pending.values())promise.completeExceptionally(new IllegalStateException("CDP message decode failed"));}}
                s.request(1);return CompletableFuture.completedFuture(null);
            }
            public void onError(WebSocket s,Throwable failure){for(var promise:pending.values())promise.completeExceptionally(new IllegalStateException("CDP transport failed"));}
        }).get(10,TimeUnit.SECONDS);
        if(!ws.contains("/devtools/browser/")){
            call("Page.enable",Map.of());call("Runtime.enable",Map.of());call("Network.enable",Map.of());
            call("Emulation.setDeviceMetricsOverride",Map.of("width",1366,"height",768,"deviceScaleFactor",1,"mobile",false));
            call("Page.addScriptToEvaluateOnNewDocument",Map.of("source",
                "(()=>{const Native=window.WebSocket;window.__qaSockets=[];window.WebSocket=class extends Native{constructor(...args){super(...args);window.__qaSockets.push(this);}};})()"));
        }
    }
    BrowserQaTool isolatedSession()throws Exception {
        String browserSocket=JSON.readTree(http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:19222/json/version")).GET().build(),HttpResponse.BodyHandlers.ofString()).body()).path("webSocketDebuggerUrl").asText();
        try(var control=new BrowserQaTool(password,browserSocket)){
            String context=control.call("Target.createBrowserContext",Map.of()).path("browserContextId").asText();
            ownedContexts.add(context);
            String target=control.call("Target.createTarget",Map.of("url","about:blank","browserContextId",context)).path("targetId").asText();
            var tabs=JSON.readTree(http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:19222/json/list")).GET().build(),HttpResponse.BodyHandlers.ofString()).body());
            for(var tab:tabs)if(target.equals(tab.path("id").asText()))return new BrowserQaTool(password,tab.path("webSocketDebuggerUrl").asText());
        }
        throw new IllegalStateException("Isolated browser target unavailable");
    }
    void merge(BrowserQaTool peer){cases.addAll(peer.cases);javascriptErrors.addAll(peer.javascriptErrors);serverErrors.addAll(peer.serverErrors);runtimeSecrets.addAll(peer.runtimeSecrets);}
    void rememberSecret(String value){runtimeSecrets.add(value);}
    static void requireRedactedReport(String report,Collection<String> secrets){
        if(secrets.stream().filter(value->value!=null&&!value.isEmpty()).anyMatch(report::contains))
            throw new IllegalStateException("Unsafe QA report rejected; runtime secret must not be serialized");
    }
    JsonNode call(String method,Object params)throws Exception {
        int id=sequence.incrementAndGet();var promise=new CompletableFuture<JsonNode>();pending.put(id,promise);
        socket.sendText(JSON.writeValueAsString(Map.of("id",id,"method",method,"params",params)),true).join();
        try{return promise.get(15,TimeUnit.SECONDS);}finally{pending.remove(id);}
    }
    JsonNode js(String expression)throws Exception {
        var r=call("Runtime.evaluate",Map.of("expression",expression,"awaitPromise",true,"returnByValue",true));
        if(r.has("exceptionDetails"))throw new IllegalStateException("Browser evaluation failed (expression omitted)");
        return r.path("result").path("value");
    }
    static String q(Object value)throws Exception{return JSON.writeValueAsString(value);}
    void waitPage()throws Exception {Thread.sleep(180);for(int i=0;i<60;i++){try{if(js("document.readyState").asText().equals("complete"))return;}catch(Exception ignored){}Thread.sleep(100);}throw new IllegalStateException("Page load timeout");}
    void waitNavigation(double previous)throws Exception {
        for(int i=0;i<100;i++){try{if(js("performance.timeOrigin").asDouble()!=previous){waitPage();return;}}catch(Exception ignored){}Thread.sleep(100);}
        throw new IllegalStateException("Form navigation timeout");
    }
    void nav(String path)throws Exception {if(!path.startsWith("/")||path.startsWith("//"))throw new IllegalArgumentException("Local path only");call("Page.navigate",Map.of("url",BASE+path));waitPage();}
    String text()throws Exception{return js("document.body.innerText").asText();}
    String location()throws Exception{return js("location.pathname").asText();}
    JsonNode failureDiagnostic()throws Exception {
        // Never serialize form values, cookies, headers, HTML or WebSocket frames.
        var diagnostic=js("(()=>{const path=p=>p.replace(/\\/quotes\\/[^/?]+/g,'/quotes/[redacted]');return {path:path(location.pathname),http:performance.getEntriesByType('navigation')[0]?.responseStatus,invalidFields:[...document.querySelectorAll('input:invalid,select:invalid,textarea:invalid')].map(e=>e.name),forms:[...document.querySelectorAll('form')].map(f=>({path:path(new URL(f.action).pathname),method:f.method,fields:[...f.elements].map(e=>e.name).filter(Boolean)})),alerts:[...document.querySelectorAll('.alert-danger,.invalid-feedback')].map(e=>e.innerText.trim()).filter(Boolean).map(t=>t.slice(0,300))};})()");
        requireRedactedReport(JSON.writeValueAsString(diagnostic),runtimeSecrets);
        return diagnostic;
    }
    void result(String name,boolean ok,String detail){cases.add(Map.of("name",name,"status",ok?"PASS":"FAIL","detail",detail));System.out.println((ok?"PASS ":"FAIL ")+name);}
    void blocked(String name,String reason){cases.add(Map.of("name",name,"status","BLOCKED","detail",reason));}
    void page(String name,String path,int status,String contains)throws Exception {
        nav(path);int actual=js("performance.getEntriesByType('navigation')[0].responseStatus").asInt();
        result(name,actual==status&&(contains==null||text().contains(contains)),path+" HTTP="+actual+" expected="+status);
    }
    void fill(Map<String,?> values)throws Exception {
        js("(()=>{const values="+q(values)+";for(const [name,value]of Object.entries(values)){const el=document.getElementsByName(name)[0];if(!el)throw Error('missing field');if(el.type==='checkbox')el.checked=!!value;else el.value=value;el.dispatchEvent(new Event('change',{bubbles:true}));}return true;})()");
    }
    boolean submit(String action,Map<String,?> values)throws Exception {
        return submitSelector("form[action='"+action+"']",values);
    }
    boolean submitSelector(String selector,Map<String,?> values)throws Exception {
        if(!js("!!document.querySelector("+q(selector)+")").asBoolean())return false;
        double previous=js("performance.timeOrigin").asDouble();
        js("(()=>{const f=document.querySelector("+q(selector)+"),values="+q(values)+";for(const [name,value]of Object.entries(values)){const el=f.elements.namedItem(name);if(!el)throw Error('missing form field');if(el.type==='checkbox')el.checked=!!value;else el.value=value;}if(!f.checkValidity())throw Error('form invalid');f.requestSubmit();return true;})()");
        waitNavigation(previous);return true;
    }
    void login(String user)throws Exception {
        call("Network.clearBrowserCookies",Map.of());nav("/login");fill(Map.of("email",user+"@example.test","password",password));submit("/login",Map.of());
        result(user+" login",location().equals("/account"),"POST /login -> "+location());
    }
    int browserPost(String path,boolean csrf)throws Exception {
        return js("(async()=>{const h={'Content-Type':'application/x-www-form-urlencoded'};const body=new URLSearchParams();"+(csrf?"body.set('_csrf',document.querySelector('input[name=_csrf]').value);":"")+"const r=await fetch("+q(path)+",{method:'POST',headers:h,body,redirect:'manual'});return r.status;})()").asInt();
    }
    void guest()throws Exception {
        call("Network.clearBrowserCookies",Map.of());
        for(String path:List.of("/","/tracking","/quote","/login","/register"))page("Guest "+path,path,200,path.equals("/")?"Tra cứu vận đơn":null);
        page("Guest cannot open private shipment ID","/user/shipments/1",401,null);
        page("Guest API 401","/api/notifications",401,null);
        nav("/register");js("document.querySelector('form').requestSubmit();true");result("Register HTML validation",js("document.querySelectorAll(':invalid').length>0").asBoolean(),"Empty required fields block submit");
        result("Register CSRF enforced",browserPost("/register",false)==403,"POST without token rejected");
        nav("/register");fill(Map.of("fullName","Khách Browser H2","username","browserqa-registration","email","browserqa-registration@example.test","phoneNumber","0909998123","password",password,"confirmPassword",password));submit("/register",Map.of());
        if("YES".equals(System.getenv("UTE_DEMO_TEST_MAIL")))new BrowserOtpJourney(this).activateCurrentRegistration();
        else {
            result("Register isolated SMTP error and cleared passwords",text().contains("Chức năng gửi email chưa được cấu hình")&&js("Array.from(document.querySelectorAll('input[type=password]')).every(e=>!e.value)").asBoolean(),"H2 has no SMTP credential; controlled error, no OTP bypass");
            blocked("Registration OTP activation end-to-end","Test SMTP not explicitly enabled");
        }
        nav("/tracking?trackingNumber=BAD");result("Tracking invalid format",text().contains("không hợp lệ")||text().contains("35"),"Invalid code gives validation, not private data");
        nav("/tracking?trackingNumber=UTE00000000000000000000000000000000");result("Tracking unknown",text().contains("Không tìm thấy"),"Unknown tracking number");
        nav("/login");result("Login CSRF enforced",browserPost("/login",false)==403,"Missing token gives 403");
        nav("/login");fill(Map.of("email","missing@example.test","password",password));submit("/login",Map.of());result("Invalid login generic error",location().equals("/login")&&js("location.search.includes('error')").asBoolean(),"Unknown account -> /login?error");
    }
    String create(String method)throws Exception {
        nav("/user/shipments/create");fill(Map.ofEntries(Map.entry("shipmentMethod",method),Map.entry("senderName","Người gửi Browser"),Map.entry("senderPhone","0901234567"),Map.entry("senderProvince","Đà Nẵng"),Map.entry("senderDistrict","Khu vực demo"),Map.entry("senderWard","Phường demo"),Map.entry("senderAddress","Địa chỉ giả lập H2"),Map.entry("receiverName","Người nhận Browser"),Map.entry("receiverPhone","0901234568"),Map.entry("receiverProvince","Hồ Chí Minh"),Map.entry("receiverDistrict","Khu vực demo"),Map.entry("receiverWard","Phường demo"),Map.entry("receiverAddress","Địa chỉ nhận giả lập H2"),Map.entry("packageDescription","Sách Browser QA"),Map.entry("weightKg","1.01"),Map.entry("serviceId","1")));
        double branchPage=js("performance.timeOrigin").asDouble();
        js("(()=>{const b=document.querySelector('button[formaction=\"/user/shipments/branches\"]');b.click();return true;})()");waitNavigation(branchPage);
        js("(()=>{for(const name of ['originBranchId','destinationBranchId']){const s=document.getElementsByName(name)[0];if(s.options.length>1)s.value=s.options[1].value;}return true;})()");
        String fieldNames=js("Array.from(new FormData(document.querySelector(\"form[action=\\\"/user/shipments/quotes\\\"]\")).keys()).join(\",\")").asText();
        submit("/user/shipments/quotes",Map.of());
        String quotePath=location();boolean quoted=quotePath.startsWith("/user/shipments/quotes/");
        result(method+" quote",quoted&&text().contains(method.equals("HOME_PICKUP")?"60.000,00":"50.000,00"),"Detailed quote with database price; "+quotePath.replaceAll("/quotes/.*","/quotes/[token]")+(quoted?"":" fields="+fieldNames+" errors="+js("Array.from(document.querySelectorAll(\".alert-danger,.invalid-feedback\")).map(e=>e.innerText).filter(Boolean).join(\"; \")").asText()));
        if(!quoted)return null;
        submit("/user/shipments",Map.of());String shipment=location();result(method+" confirmation creates shipment",shipment.matches("/user/shipments/[0-9]+")&&text().contains("Tạo vận đơn thành công"),shipment);
        if(shipment.matches("/user/shipments/[0-9]+")){nav(quotePath);submit("/user/shipments",Map.of());result(method+" repeated confirmation reopens same shipment",location().equals(shipment),"Same quote confirmation is idempotent");return shipment.substring(shipment.lastIndexOf('/')+1);}
        return null;
    }
    void customer()throws Exception {
        login("owner");page("USER list","/user/shipments",200,null);
        result("USER visible status labels",js("Array.from(document.querySelectorAll('.shipment-status')).every(e=>e.innerText.trim().length>0)").asBoolean(),"No empty status badge");
        tracking=js("document.querySelector('tbody tr td').innerText").asText();
        page("USER detail/timeline","/user/shipments/17",200,null);page("USER support list","/account/support",200,null);page("USER support conversation","/account/support/1",200,null);
        boolean sent=submit("/account/support/1/messages",Map.of("body","BROWSER-PUBLIC-MESSAGE"));result("USER sends support message",sent&&text().contains("BROWSER-PUBLIC-MESSAGE"),"POST conversation form");
        result("Support internal note hidden",!text().contains("GHI_CHU_NOI_BO_DEMO_KHONG_CONG_KHAI"),"Public conversation only");
        page("USER notifications","/notifications",200,null);page("USER denied ADMIN","/admin/users",403,null);page("USER denied Manager","/manager/trips",403,null);
        createdShipment=create("BRANCH_DROPOFF");homeShipment=create("HOME_PICKUP");
        nav("/account");result("Logout CSRF enforced",browserPost("/logout",false)==403,"Session retained after rejected logout");submit("/logout",Map.of());result("Logout redirect",location().equals("/login"),"Logout clears authentication");page("Logout revokes private route","/user/shipments",401,null);
        nav("/tracking?trackingNumber="+tracking);result("Public tracking valid and private fields absent",text().contains(tracking)&&!text().contains("0901234567")&&!text().contains("GHI_CHU_NOI_BO_DEMO_KHONG_CONG_KHAI"),"Only public tracking data");
        login("other");page("Other USER shipment ownership","/user/shipments/17",404,null);page("Other USER support ownership","/account/support/1",404,null);
    }
    void manager()throws Exception {
        login("manager");for(String path:List.of("/manager","/manager/operations","/manager/shipments","/manager/handoffs","/manager/trips","/manager/trips/new","/manager/deliveries","/manager/tickets"))page("Manager "+path,path,200,null);
        page("Manager denied ADMIN","/admin/users",403,null);
        if(createdShipment!=null){nav("/manager/shipments/"+createdShipment);boolean confirmed=submit("/manager/shipments/"+createdShipment+"/confirm",Map.of());result("Manager confirms Browser dropoff",confirmed&&text().contains("Đã xác nhận"),"Physical receipt is separate");boolean received=submit("/manager/shipments/"+createdShipment+"/intake",Map.of("physicallyReceived",true));result("Manager physical branch intake",received&&text().contains("RCV-"),"Receipt and timeline rendered");}
        if(homeShipment!=null){nav("/manager/shipments/"+homeShipment);submit("/manager/shipments/"+homeShipment+"/confirm",Map.of());String employee=js("document.querySelector('select[name=employeeId] option[value]:not([value=\"\"])')?.value || ''").asText();boolean assigned=!employee.isBlank()&&submit("/manager/shipments/"+homeShipment+"/pickup-assignments",Map.of("employeeId",employee));result("Manager assigns pickup",assigned&&text().contains("Đã phân công lấy hàng"),"Only source-branch eligible courier");}
        if(createdShipment!=null)transportCollection();
        login("destination-manager");page("Destination Manager ticket conversation","/manager/tickets/1",200,"GHI_CHU_NOI_BO_DEMO_KHONG_CONG_KHONG_CONG_KHAI".replace("KHONG_CONG_KHONG_CONG","KHONG_CONG"));
        for(String id:List.of("9","10")){
            nav("/manager/deliveries/"+id);
            if(js("!!document.querySelector('form[action$=\"/assign\"]') && !document.querySelector('input[name=replacesAssignmentId]')").asBoolean()){
                String employee=js("document.querySelector('select[name=employeeId] option:not([value=\"\"])').value").asText();
                submit("/manager/deliveries/"+id+"/assign",Map.of("employeeId",employee));
            }
            boolean prepared=submit("/manager/deliveries/"+id+"/handoff",Map.of("reference","BROWSER-DELIVERY-HANDOFF","confirmed",true));
            result("Manager prepares delivery "+id,prepared&&text().contains("BROWSER-DELIVERY-HANDOFF"),"Physical handoff remains subject to courier acceptance");
        }
    }
    void transportCollection()throws Exception {
        nav("/manager/trips/new");String route=js("document.querySelector('select[name=routeId] option:not([value=\"\"])').value").asText();
        boolean created=submit("/manager/trips",Map.of("routeId",route));String trip=location();result("Manager creates collection trip",created&&trip.matches("/manager/trips/[0-9]+"),trip);
        if(!trip.matches("/manager/trips/[0-9]+"))return;
        js("(()=>{const e=document.querySelector('input[name=shipmentIds][value=\""+createdShipment+"\"]');if(!e)throw Error('candidate missing');e.checked=true;return true;})()");
        boolean manifested=submit(trip+"/items",Map.of());result("Manager adds received shipment to manifest",manifested,"Selected newly created/physically received parcel");
        boolean sealed=submit(trip+"/seal",Map.of("reference","BROWSER-SEAL","confirmed",true));boolean departed=submit(trip+"/depart",Map.of("reference","BROWSER-DEPART","confirmed",true));
        result("Manager seals and departs collection trip",sealed&&departed&&text().contains("BROWSER-DEPART"),"Source branch -> configured hub");
        login("hub-origin-manager");nav(trip);boolean arrived=submit(trip+"/arrive",Map.of("reference","BROWSER-ARRIVE","confirmed",true));boolean received=submit(trip+"/receipts",Map.of("reference","BROWSER-TRIP-RECEIPT","confirmed",true));boolean closed=submit(trip+"/close",Map.of("reference","BROWSER-CLOSE","confirmed",true));
        result("Hub Manager arrives receives and closes trip",arrived&&received&&closed&&text().contains("BROWSER-TRIP-RECEIPT"),"Receipt controls parcel custody; distinct branch actor");
    }
    void courier()throws Exception {
        login("pickup-courier");page("Pickup courier dashboard","/shipper",200,null);page("Pickup courier tasks","/shipper/pickups",200,null);
        String task=js("Array.from(document.querySelectorAll('a')).map(a=>a.getAttribute('href')).find(h=>/^\\/shipper\\/pickups\\/[0-9]+$/.test(h))||''").asText();
        if(!task.isBlank()){
            page("Pickup task evidence/handoff forms",task,200,null);
            boolean collected=submit(task+"/collect",Map.of("result","SUCCESS","reference","BROWSER-PICKUP","physicallyCollected",true));
            result("Shipper pickup evidence",collected&&text().contains("BROWSER-PICKUP"),"Actual collection form");
            boolean handed=submit(task+"/handoffs",Map.of("notes","BROWSER-HANDOFF"));
            result("Shipper requests source handoff",handed&&text().contains("BROWSER-HANDOFF"),"Does not self-accept branch custody");
        }else blocked("Pickup task detail","No available pickup link");
        login("manager");nav("/manager/handoffs");
        String handoff=js("Array.from(document.querySelectorAll('a')).map(a=>a.getAttribute('href')).find(h=>/^\\/manager\\/handoffs\\/[0-9]+$/.test(h))||''").asText();
        if(!handoff.isBlank()){nav(handoff);String receiptPage=js("document.querySelector('a[href^=\"/manager/shipments/\"]').getAttribute('href')").asText();boolean received=submit(handoff+"/accept",Map.of("physicallyReceived",true));nav(receiptPage);result("Independent Manager accepts source handoff",received&&text().contains("RCV-"),"Follow linked source receipt and verify persisted reference");}
        login("shipper");page("Delivery courier dashboard","/shipper",200,null);page("Delivery task list","/shipper/deliveries",200,null);
        String delivery=js("Array.from(document.querySelectorAll('a')).map(a=>a.getAttribute('href')).find(h=>/^\\/shipper\\/deliveries\\/[0-9]+$/.test(h))||''").asText();
        if(!delivery.isBlank())page("Delivery task proof/failure forms",delivery,200,null);else blocked("Delivery task detail","No delivery task link");
        nav("/shipper/deliveries");var tasks=js("Array.from(document.querySelectorAll('a')).map(a=>a.getAttribute('href')).filter(h=>/^\\/shipper\\/deliveries\\/[0-9]+$/.test(h))");int completed=0;
        for(var candidate:tasks){String target=candidate.asText();nav(target);if(!js("!!document.querySelector('form[action$=\"/accept\"]')").asBoolean())continue;
            boolean accepted=submit(target+"/accept",Map.of("reference","BROWSER-ACCEPT","confirmed",true));
            boolean started=submit(target+"/start",Map.of("reference","BROWSER-START","confirmed",true));
            result("Courier accepts and starts delivery "+completed,accepted&&started,"Correct assigned courier and CSRF form");
            if(completed==0){proofTracking=js("document.querySelector('h1').innerText").asText();boolean done=submit(target+"/complete",Map.of("recipientType","RECIPIENT","recipientName","Người nhận Browser","reference","BROWSER-PROOF","confirmed",true));result("Courier successful delivery proof",done&&text().contains("Hoàn thành"),"Task completed; proof reference checked separately in Manager view");}
            else{boolean failed=submit(target+"/fail",Map.of("code","ABSENT","reason","Người nhận giả lập vắng mặt","confirmed",true));result("Courier failed attempt",failed&&text().contains("Người nhận giả lập vắng mặt"),"Failure reason and attempt displayed");}
            if(++completed==2)break;
        }
        if(completed<2)blocked("Both delivery outcomes","Not enough prepared accept-ready tasks for both results");
        page("Shipper denied Manager","/manager/trips",403,null);page("Shipper denied customer data","/user/shipments/17",403,null);
        if(proofTracking!=null){login("destination-manager");nav("/manager/deliveries?tracking="+proofTracking);String proofPage=js("Array.from(document.querySelectorAll('a')).map(a=>a.getAttribute('href')).find(h=>/^\\/manager\\/deliveries\\/[0-9]+$/.test(h))||''").asText();if(!proofPage.isBlank())nav(proofPage);result("Manager sees persisted delivery proof",text().contains("BROWSER-PROOF"),"Proof checked at its actual detail URL");}
    }
    void admin()throws Exception {
        login("admin");for(String path:List.of("/admin/users","/admin/reports","/admin/audit"))page("Admin "+path,path,200,null);
        nav("/admin/users");String other=js("Array.from(document.querySelectorAll('tbody tr')).find(r=>r.innerText.includes('other@example.test'))?.querySelector('a')?.getAttribute('href')||''").asText();
        if(!other.isBlank()){nav(other);boolean locked=submit(other+"/lock",Map.of());boolean unlocked=submit(other+"/unlock",Map.of());result("Admin lock and unlock customer",locked&&unlocked,"Only disposable other customer; exact role gates retained");}
        nav("/admin/reports");var csv=js("(async()=>{const f=document.querySelector('form[action=\"/admin/reports/export\"]');const u=f?f.action+'?'+new URLSearchParams(new FormData(f)):'/admin/reports/export';const r=await fetch(u);return {status:r.status,type:r.headers.get('content-type'),disposition:r.headers.get('content-disposition'),bytes:(await r.text()).length};})()");
        result("Admin CSV via browser download request",csv.path("status").asInt()==200&&csv.path("disposition").asText().contains("attachment"),"HTTP="+csv.path("status").asInt()+" content-type="+csv.path("type").asText()+" bytes="+csv.path("bytes").asInt());
        page("Admin no implicit Manager role","/manager/trips",403,null);
    }
    void responsive()throws Exception {
        call("Network.clearBrowserCookies",Map.of());
        for(int width:List.of(1366,390)){call("Emulation.setDeviceMetricsOverride",Map.of("width",width,"height",width==390?844:768,"deviceScaleFactor",1,"mobile",width==390));nav("/tracking");
            result("Tracking viewport "+width,js("document.documentElement.scrollWidth<=innerWidth").asBoolean(),"No page-level horizontal overflow");
            var screenshot=call("Page.captureScreenshot",Map.of("format","png"));Path dir=Path.of(System.getenv().getOrDefault("UTE_BROWSER_EVIDENCE_DIR",System.getProperty("java.io.tmpdir")));Files.createDirectories(dir);Files.write(dir.resolve("uteexpress-tracking-"+width+".png"),Base64.getDecoder().decode(screenshot.path("data").asText()));
        }
    }
    void run()throws Exception {guest();new BrowserNationwidePricingJourney(this).guest();customer();manager();courier();new BrowserLogisticsJourneys(this).run();new BrowserWebSocketJourney(this).run();admin();new BrowserNationwidePricingJourney(this).admin();if("YES".equals(System.getenv("UTE_UI_POLISH_QA")))new BrowserUiPolishJourney(this).run();responsive();result("No uncaught JavaScript or console.error",javascriptErrors.isEmpty(),"Observed="+javascriptErrors.size());result("No HTTP 5xx/Thymeleaf render failure",serverErrors.isEmpty(),serverErrors.toString());}
    public void close(){
        if(!ownedContexts.isEmpty()){
            try{
                String ws=JSON.readTree(http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:19222/json/version")).GET().build(),HttpResponse.BodyHandlers.ofString()).body()).path("webSocketDebuggerUrl").asText();
                try(var control=new BrowserQaTool(password,ws)){for(String context:ownedContexts)control.call("Target.disposeBrowserContext",Map.of("browserContextId",context));}
            }catch(Exception ignored){ /* Chrome process is owned/stopped by the outer runner as a final cleanup. */ }
            ownedContexts.clear();
        }
        password=null;if(socket!=null)socket.abort();
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=0||!"YES".equals(System.getenv("UTE_BROWSER_H2_CONFIRMED")))throw new IllegalArgumentException("Explicit local H2 operator confirmation required");
        var qa=new BrowserQaTool(System.getenv("UTE_DEMO_PASSWORD"));String aborted=null;JsonNode diagnostic=null;
        try{qa.run();}catch(Exception failure){aborted=failure.getClass().getSimpleName()+": "+failure.getMessage();qa.blocked("Automation completed all planned steps","Interrupted: "+aborted);System.out.println("AUTOMATION BLOCKED "+aborted);try{diagnostic=qa.failureDiagnostic();}catch(Exception ignored){/* Diagnostics are optional and must never weaken secret redaction. */}}
        finally{qa.close();}
        Map<String,Object> report=new LinkedHashMap<>();report.put("executedAt",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString());report.put("target",BASE);report.put("browser","Chrome headless via native Java 21 CDP WebSocket; isolated TEMP profile");report.put("database","Disposable H2 demo; no JDBC/SQL Server calls in this runner");report.put("cases",qa.cases);report.put("javascriptErrorCount",qa.javascriptErrors.size());report.put("serverErrors",qa.serverErrors);report.put("aborted",aborted);
        if(diagnostic!=null)report.put("interruptionDiagnostic",diagnostic);
        requireRedactedReport(JSON.writeValueAsString(report),qa.runtimeSecrets);
        report.put("runtimeSecretCheck","PASS");qa.runtimeSecrets.clear();
        Path reportFile="YES".equals(System.getenv("UTE_FINAL_STABILITY_QA"))
            ?Path.of(System.getenv("UTE_BROWSER_EVIDENCE_DIR")).resolve("h2-final-stability.json")
            :Path.of("YES".equals(System.getenv("UTE_UI_POLISH_QA"))?"docs/commit26-browser-results.json":"docs/browser-qa-results.json");
        Files.writeString(reportFile,JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report)+"\n");
        for(String state:List.of("PASS","FAIL","BLOCKED"))System.out.println(state+"="+qa.cases.stream().filter(c->state.equals(c.get("status"))).count());
        if(aborted!=null||qa.cases.stream().anyMatch(c->"FAIL".equals(c.get("status"))))System.exit(2);
    }
}
