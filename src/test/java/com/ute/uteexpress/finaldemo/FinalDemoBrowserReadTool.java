package com.ute.uteexpress.finaldemo;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Read-only SQL FinalDemo browser observer. Dedicated Chrome contexts; no migration/seed or business mutations. */
public final class FinalDemoBrowserReadTool implements AutoCloseable {
    static final ObjectMapper JSON=new ObjectMapper();
    static final String BASE="http://127.0.0.1:18085";
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
    static final Set<String> READ_POSTS=Set.of("/login","/logout","/quote","/quote/branches","/user/shipments/branches");
    static boolean allowedRequest(String method,URI uri){
        return uri.toString().startsWith(BASE+"/")&&(method.equals("GET")||method.equals("HEAD")
            ||method.equals("POST")&&READ_POSTS.contains(uri.getPath()));
    }
    final AtomicInteger blockedRequests=new AtomicInteger();
    final List<Map<String,Object>> timings=new ArrayList<>();
    String password;
    String createdShipment,homeShipment,tracking,proofTracking;
    FinalDemoBrowserReadTool(String password)throws Exception {
        this(password,null);
    }
    FinalDemoBrowserReadTool(String password,String requestedSocket)throws Exception {
        FinalDemoSeed.requirePassword(password);this.password=password;
        rememberSecret(password);
        var version=JSON.readTree(http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:19225/json/version")).GET().build(),HttpResponse.BodyHandlers.ofString()).body());
        if(!version.path("User-Agent").asText().contains("HeadlessChrome"))throw new IllegalStateException("Dedicated headless Chrome required");
        var tabs=JSON.readTree(http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:19225/json/list")).GET().build(),HttpResponse.BodyHandlers.ofString()).body());
        String ws=requestedSocket;if(ws==null)for(var tab:tabs)if(tab.path("type").asText().equals("page")){ws=tab.path("webSocketDebuggerUrl").asText();break;}
        if(ws==null || !ws.startsWith("ws://127.0.0.1:19225/"))throw new IllegalStateException("No dedicated local target");
        socket=http.newWebSocketBuilder().buildAsync(URI.create(ws),new WebSocket.Listener(){
            final StringBuilder message=new StringBuilder();
            public void onOpen(WebSocket s){s.request(1);}
            public CompletionStage<?> onText(WebSocket s,CharSequence data,boolean last){
                message.append(data);if(last){try{var item=JSON.readTree(message.toString());message.setLength(0);
                    if(item.path("method").asText().equals("Fetch.requestPaused")){
                        var request=item.path("params").path("request");String method=request.path("method").asText();
                        var uri=URI.create(request.path("url").asText());
                        boolean allowed=allowedRequest(method,uri);
                        int command=sequence.incrementAndGet();
                        socket.sendText(JSON.writeValueAsString(Map.of("id",command,"method",allowed?"Fetch.continueRequest":"Fetch.failRequest",
                            "params",allowed?Map.of("requestId",item.path("params").path("requestId").asText()):Map.of("requestId",item.path("params").path("requestId").asText(),"errorReason","BlockedByClient"))),true);
                        if(!allowed)blockedRequests.incrementAndGet();
                    }
                    else if(item.has("id")){var promise=pending.remove(item.get("id").asInt());if(promise!=null){if(item.has("error"))promise.completeExceptionally(new IllegalStateException("CDP command failed"));else promise.complete(item.path("result"));}}
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
            call("Fetch.enable",Map.of("patterns",List.of(Map.of("urlPattern","*","requestStage","Request"))));
            call("Emulation.setDeviceMetricsOverride",Map.of("width",1366,"height",768,"deviceScaleFactor",1,"mobile",false));
            call("Page.addScriptToEvaluateOnNewDocument",Map.of("source",
                "(()=>{const Native=window.WebSocket;window.__qaSockets=[];window.WebSocket=class extends Native{constructor(...args){super(...args);window.__qaSockets.push(this);}};})()"));
        }
    }
    FinalDemoBrowserReadTool isolatedSession()throws Exception {
        String browserSocket=JSON.readTree(http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:19225/json/version")).GET().build(),HttpResponse.BodyHandlers.ofString()).body()).path("webSocketDebuggerUrl").asText();
        try(var control=new FinalDemoBrowserReadTool(password,browserSocket)){
            String context=control.call("Target.createBrowserContext",Map.of()).path("browserContextId").asText();
            ownedContexts.add(context);
            String target=control.call("Target.createTarget",Map.of("url","about:blank","browserContextId",context)).path("targetId").asText();
            var tabs=JSON.readTree(http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:19225/json/list")).GET().build(),HttpResponse.BodyHandlers.ofString()).body());
            for(var tab:tabs)if(target.equals(tab.path("id").asText()))return new FinalDemoBrowserReadTool(password,tab.path("webSocketDebuggerUrl").asText());
        }
        throw new IllegalStateException("Isolated browser target unavailable");
    }
    void merge(FinalDemoBrowserReadTool peer){blockedRequests.addAndGet(peer.blockedRequests.get());cases.addAll(peer.cases);javascriptErrors.addAll(peer.javascriptErrors);serverErrors.addAll(peer.serverErrors);runtimeSecrets.addAll(peer.runtimeSecrets);}
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
    void nav(String path)throws Exception {
        if(!path.startsWith("/")||path.startsWith("//"))throw new IllegalArgumentException("Local path only");
        double before=js("performance.timeOrigin").asDouble();
        var navigation=call("Page.navigate",Map.of("url",BASE+path));
        if(navigation.has("errorText"))throw new IllegalStateException("Navigation failed");
        waitNavigation(before);
    }
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
        var timing=js("(()=>{const p=performance.getEntriesByType('navigation')[0];return {ttfb:Math.round(p.responseStart-p.requestStart),duration:Math.round(p.duration)};})()");
        timings.add(Map.of("path",path,"ttfbMs",timing.path("ttfb").asLong(),"durationMs",timing.path("duration").asLong()));
    }
    void fill(Map<String,?> values)throws Exception {
        js("(()=>{const values="+q(values)+";for(const [name,value]of Object.entries(values)){const el=document.getElementsByName(name)[0];if(!el)throw Error('missing field');if(el.type==='checkbox')el.checked=!!value;else el.value=value;el.dispatchEvent(new Event('change',{bubbles:true}));}return true;})()");
    }
    boolean submit(String action,Map<String,?> values)throws Exception {
        if(!READ_POSTS.contains(action))throw new IllegalArgumentException("Browser write blocked");
        return submitSelector("form[action='"+action+"']",values);
    }
    boolean submitSelector(String selector,Map<String,?> values)throws Exception {
        if(!js("!!document.querySelector("+q(selector)+")").asBoolean())return false;
        String action=js("new URL(document.querySelector("+q(selector)+").action).pathname").asText();
        if(!READ_POSTS.contains(action))throw new IllegalArgumentException("Browser write blocked");
        double previous=js("performance.timeOrigin").asDouble();
        js("(()=>{const f=document.querySelector("+q(selector)+"),values="+q(values)+";for(const [name,value]of Object.entries(values)){const el=f.elements.namedItem(name);if(!el)throw Error('missing form field');if(el.type==='checkbox')el.checked=!!value;else el.value=value;}if(!f.checkValidity())throw Error('form invalid');f.requestSubmit();return true;})()");
        waitNavigation(previous);return true;
    }
    void login(String user)throws Exception {
        nav("/login");fill(Map.of("email",user+"@example.test","password",password));submit("/login",Map.of());
        result(user+" login",location().equals("/account"),"POST /login -> "+location());
    }
    int browserPost(String path,boolean csrf)throws Exception {
        if(!READ_POSTS.contains(path))throw new IllegalArgumentException("Browser write blocked");
        return js("(async()=>{const h={'Content-Type':'application/x-www-form-urlencoded'};const body=new URLSearchParams();"+(csrf?"body.set('_csrf',document.querySelector('input[name=_csrf]').value);":"")+"const r=await fetch("+q(path)+",{method:'POST',headers:h,body,redirect:'manual'});return r.status;})()").asInt();
    }
    void role(String name,String[] pages)throws Exception {
        page(name+" account","/account",200,null);
        result(name+" logout CSRF",browserPost("/logout",false)==403,"Missing CSRF rejected without logout");
        for(String path:pages){
            page(name+" "+path,path,200,null);
            if(path.equals("/user/shipments")||path.equals("/manager/shipments")||path.equals("/shipper/pickups")||path.equals("/shipper/deliveries")){
                var detail=js("[...document.querySelectorAll('a[href]')].map(a=>a.getAttribute('href')).find(h=>/^\\/(user\\/shipments|manager\\/shipments|shipper\\/pickups|shipper\\/deliveries)\\/[0-9]+$/.test(h))||''").asText();
                if(!detail.isEmpty())page(name+" owned detail",detail,200,null);
            }
        }
        for(String path:pages)page(name+" warm "+path,path,200,null);
        var links=js("[...document.querySelectorAll('a[href]')].map(a=>a.getAttribute('href')).filter(h=>/^\\/(user\\/shipments|manager\\/shipments|shipper\\/pickups|shipper\\/deliveries)\\/[0-9]+$/.test(h)).slice(0,1)");
        for(var link:links)page(name+" detail",link.asText(),200,null);
        nav("/account");result(name+" valid logout",submit("/logout",Map.of())&&location().equals("/login"),"Form logout with CSRF");
        page(name+" session revoked","/account",401,null);
    }
    void branchLookups()throws Exception {
        nav("/user/shipments/create");
        fill(Map.of("shipmentMethod","HOME_PICKUP","senderProvince","Hồ Chí Minh","senderDistrict","Quận 1 (địa bàn cũ)","senderWard","Phường Sài Gòn","receiverProvince","Đà Nẵng","receiverDistrict","Hải Châu (địa bàn cũ)","receiverWard","Phường Hải Châu"));
        lookup();result("SQL HOME_PICKUP valid coverage",js("document.querySelector('#originBranchId').options.length>1&&document.querySelector('#destinationBranchId').options.length>1").asBoolean(),"Both choices from configured DB; no quote persisted");
        fill(Map.of("receiverWard","Xã Núi Thành"));lookup();result("SQL unsupported ward explicit rejection",text().contains("Không có chi nhánh nhận")&&js("document.querySelector('#destinationBranchId').options.length===1").asBoolean(),"Province pricing does not grant ward coverage");
        fill(Map.of("shipmentMethod","BRANCH_DROPOFF","receiverWard","Phường Hải Châu"));lookup();result("SQL BRANCH_DROPOFF valid coverage",js("document.querySelector('#originBranchId').options.length>1&&document.querySelector('#destinationBranchId').options.length>1").asBoolean(),"Explicit branch lookup retained; no create/confirm");
    }
    void lookup()throws Exception {
        double before=js("performance.timeOrigin").asDouble();
        js("document.querySelector('button[formaction=\"/user/shipments/branches\"]').click();true");waitNavigation(before);
    }
    void publicPrices()throws Exception {
        for(String method:List.of("HOME_PICKUP","BRANCH_DROPOFF")){
            nav("/quote");String service=js("[...document.querySelector('[name=serviceId]').options].find(o=>o.value)?.value||''").asText();
            fill(Map.of("shipmentMethod",method,"serviceId",service,"senderProvince","Hồ Chí Minh","receiverProvince","Đà Nẵng","weightKg","1.5"));
            submit("/quote",Map.of());
            String expected=method.equals("HOME_PICKUP")?"v[1]>0":"v[1]===0";
            result("SQL public fee components "+method,js("(()=>{const v=[...document.querySelectorAll('section.card dl dd')].map(e=>Number(e.textContent.replace(/[^0-9.-]/g,'')));return v.length===4&&v[0]>0&&"+expected+"&&Math.abs(v[3]-v[0]-v[1]-v[2])<0.01;})()").asBoolean(),"Published DB price read only; no amount hardcoded or quote saved");
            fill(Map.of("weightKg","0"));result("SQL quote HTML weight validation "+method,js("!document.querySelector('[name=weightKg]').checkValidity()").asBoolean(),"Invalid amount blocked in browser; no write request");
        }
    }
    void runRead()throws Exception {
        for(String path:List.of("/","/tracking","/quote","/login","/register"))page("Guest "+path,path,200,null);
        page("Guest protected ID","/user/shipments/1",401,null);
        page("Missing static resource","/css/final-qa-missing.css",404,null);
        result("Friendly 404",!text().contains("Whitelabel")&&text().contains("Không tìm thấy"),"Default page must not leak errors");
        publicPrices();
        try(var user=isolatedSession();var manager=isolatedSession();var courier=isolatedSession();var admin=isolatedSession();var destinationCourier=isolatedSession()){
            user.login("final-user-01");manager.login("final-manager-b-79");courier.login("final-shipper-b-79");admin.login("final-admin");
            destinationCourier.login("final-shipper-b-48");
            user.result("FinalDemo cookie is scoped by application name",user.call("Network.getCookies",Map.of("urls",List.of(BASE+"/"))).path("cookies").findValuesAsText("name").contains("UTEEXPRESS_FINALDEMO_SESSION"),"Cookie values omitted");
            String cookieMarker=UUID.randomUUID().toString();user.rememberSecret(cookieMarker);
            user.call("Network.setCookie",Map.of("name","JSESSIONID","value",cookieMarker,"url",BASE+"/","path","/"));
            user.page("Other local app cookie does not replace FinalDemo session","/account",200,null);
            user.page("User context survives other role logins","/user/shipments",200,null);
            manager.page("Manager context survives other role logins","/manager/shipments",200,null);
            user.page("User cannot Admin","/admin/users",403,null);
            user.result("Friendly 403",!user.text().contains("Whitelabel")&&user.text().contains("403"),"403 remains forbidden");
            user.page("User form","/user/shipments/create",200,null);
            user.result("Form address coverage guidance",user.text().contains("Vùng phục vụ đang cấu hình"),"Data-driven guidance on actual coverage");
            user.page("Private missing ID","/user/shipments/9223372036854775807",404,null);
            manager.page("Manager cannot Admin","/admin/users",403,null);
            courier.page("Shipper cannot Manager","/manager/shipments",403,null);
            admin.page("Admin does not inherit Manager","/manager/shipments",403,null);
            user.branchLookups();
            user.role("final-user-01",new String[]{"/user/shipments","/user/shipments/create","/account/support","/notifications"});
            manager.role("final-manager-b-79",new String[]{"/manager","/manager/shipments","/manager/handoffs","/manager/trips","/manager/deliveries","/manager/tickets"});
            courier.role("final-shipper-b-79",new String[]{"/shipper","/shipper/pickups","/shipper/deliveries"});
            admin.role("final-admin",new String[]{"/admin","/admin/users","/admin/services","/admin/reports","/admin/audit"});
            destinationCourier.role("final-shipper-b-48",new String[]{"/shipper","/shipper/pickups","/shipper/deliveries"});
            merge(user);merge(manager);merge(courier);merge(admin);merge(destinationCourier);
            timings.addAll(user.timings);timings.addAll(manager.timings);timings.addAll(courier.timings);timings.addAll(admin.timings);timings.addAll(destinationCourier.timings);
        }
        for(int width:List.of(1366,768,390)){
            call("Emulation.setDeviceMetricsOverride",Map.of("width",width,"height",900,"deviceScaleFactor",1,"mobile",width<600));nav("/");
            result("Home viewport "+width,js("document.documentElement.scrollWidth<=innerWidth").asBoolean(),"No horizontal page overflow");
            Path dir=Path.of(System.getenv("UTE_FINAL_BROWSER_EVIDENCE"));Files.write(dir.resolve("home-"+width+".png"),Base64.getDecoder().decode(call("Page.captureScreenshot",Map.of("format","png")).path("data").asText()));
        }
        for(int width:List.of(1366,768,390)){
            call("Emulation.setDeviceMetricsOverride",Map.of("width",width,"height",900,"deviceScaleFactor",1,"mobile",width<600));
            page("Public coverage guide "+width,"/quote",200,"Vùng phục vụ đang cấu hình");
            js("document.querySelector('#coverage-help').open=true;true");
            result("Expanded coverage viewport "+width,js("document.querySelectorAll('#coverage-help tbody tr').length>0&&document.querySelectorAll('#coverage-help tbody tr').length<=200&&document.documentElement.scrollWidth<=innerWidth").asBoolean(),"Actual DB areas shown; horizontal scroll contained within table");
            Path dir=Path.of(System.getenv("UTE_FINAL_BROWSER_EVIDENCE"));Files.write(dir.resolve("coverage-"+width+".png"),Base64.getDecoder().decode(call("Page.captureScreenshot",Map.of("format","png")).path("data").asText()));
        }
        result("No JavaScript exceptions",javascriptErrors.isEmpty(),"Count="+javascriptErrors.size());
        result("No server 5xx",serverErrors.isEmpty(),"Count="+serverErrors.size());
        result("No unexpected write requests",blockedRequests.get()==0,"Write requests blocked="+blockedRequests.get());
    }
    public void close(){password=null;if(socket!=null)socket.abort();}
    public static void main(String[] args)throws Exception {
        if(args.length!=0||!"YES".equals(System.getenv("UTE_FINAL_BROWSER_READ_APPROVED")))throw new IllegalArgumentException("Explicit read-only browser approval required");
        Path dir=Path.of(System.getenv("UTE_FINAL_BROWSER_EVIDENCE"));Files.createDirectories(dir);
        var qa=new FinalDemoBrowserReadTool(System.getenv("UTE_FINAL_DEMO_WEB_PASSWORD"));String aborted=null;
        try{qa.runRead();}catch(Exception failure){aborted=failure.getClass().getSimpleName();qa.blocked("Completed read-only browser plan",aborted);}finally{qa.close();}
        var report=new LinkedHashMap<String,Object>();report.put("target",BASE);report.put("scope","Chrome headless, SQL FinalDemo, GET/read-only POST/auth only");report.put("cases",qa.cases);report.put("timings",qa.timings);report.put("javascriptErrorCount",qa.javascriptErrors.size());report.put("serverErrors",qa.serverErrors);report.put("aborted",aborted);
        String json=JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report);requireRedactedReport(json,qa.runtimeSecrets);Files.writeString(dir.resolve("sql-browser-read.json"),json+"\n");
        for(String state:List.of("PASS","FAIL","BLOCKED"))System.out.println(state+"="+qa.cases.stream().filter(c->state.equals(c.get("status"))).count());
        if(aborted!=null||qa.cases.stream().anyMatch(c->"FAIL".equals(c.get("status"))))System.exit(2);
    }
}
