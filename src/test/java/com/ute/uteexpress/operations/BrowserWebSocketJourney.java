package com.ute.uteexpress.operations;

import java.util.Map;
import java.util.concurrent.Callable;

/** Actual UI refresh and reconnect, independent Chrome cookie/storage contexts, no production JS changes. */
final class BrowserWebSocketJourney {
    private final BrowserQaTool manager;
    BrowserWebSocketJourney(BrowserQaTool manager){this.manager=manager;}
    private void check(String name,boolean ok,String detail){
        manager.result(name,ok,detail);if(!ok)throw new IllegalStateException("WebSocket UI checkpoint failed: "+name);
    }
    private boolean await(Callable<Boolean> condition,int seconds)throws Exception {
        long until=System.nanoTime()+seconds*1_000_000_000L;
        do {if(condition.call())return true;Thread.sleep(100);}while(System.nanoTime()<until);
        return false;
    }
    private boolean notice(BrowserQaTool user,String tracking,String title)throws Exception {
        return user.js("Array.from(document.querySelectorAll('[data-notification-items] article')).some(a=>a.innerText.includes("
            +BrowserQaTool.q(tracking)+")&&a.querySelector('h2')?.innerText.includes("+BrowserQaTool.q(title)+"))").asBoolean();
    }
    void run()throws Exception {
        manager.login("owner");String id=manager.create("BRANCH_DROPOFF");
        check("WS test uses new owned shipment",id!=null,"Fresh UI-created parcel for independent status changes");
        String tracking=manager.js("document.querySelector('h1').innerText.trim()").asText();
        try(var owner=manager.isolatedSession();var other=manager.isolatedSession()){
            owner.login("owner");owner.nav("/notifications");
            other.login("other");other.nav("/notifications");
            check("Two independent USER sessions connect STOMP",await(()->owner.stompConnected.get()>0&&other.stompConnected.get()>0,15),
                "Private BrowserContexts, real CONNECTED frames; no cookies/tokens in report");
            double origin=owner.js("performance.timeOrigin").asDouble();int before=owner.stompMessages.get();
            manager.login("manager");manager.nav("/manager/shipments/"+id);
            check("Manager confirms while customer inbox stays open",manager.submit("/manager/shipments/"+id+"/confirm",Map.of()),"Separate manager browser session");
            check("WebSocket updates customer UI without reload",await(()->owner.stompMessages.get()>before&&notice(owner,tracking,"Đã xác nhận"),15)
                &&owner.js("performance.timeOrigin").asDouble()==origin,"Received MESSAGE and rendered matching new notification before 60-second fallback");
            check("Other customer does not receive owned parcel content",!notice(other,tracking,"")&&!other.text().contains(tracking),"Unrelated session inbox excludes tracking");
            String noticeId=owner.js("Array.from(document.querySelectorAll('[data-notification-items] article')).find(a=>a.innerText.includes("
                +BrowserQaTool.q(tracking)+")&&a.querySelector('h2')?.innerText.includes('Đã xác nhận'))?.dataset.id||''").asText();
            check("Authoritative notification has private ID",noticeId.matches("[0-9]+"),"Rendered server notification");
            int forbidden=other.js("(async()=>{const r=await fetch('/notifications/"+noticeId+"/open',{redirect:'manual'});return r.status;})()").asInt();
            check("Other customer cannot open specific owner notification",forbidden==404,"Direct numeric notification lookup remains ownership-checked");
            int connections=owner.stompConnected.get();
            owner.call("Network.emulateNetworkConditions",Map.of("offline",true,"latency",0,"downloadThroughput",-1,"uploadThroughput",-1));
            owner.js("window.__qaSockets.forEach(s=>s.close());true");
            manager.nav("/manager/shipments/"+id);
            check("Manager changes state during client disconnect",manager.submit("/manager/shipments/"+id+"/cancel",Map.of("reason","C22 reconnect validation")),"Client outage does not lose persisted event");
            Thread.sleep(4500); // Longer than the unchanged production worker delay; client is offline throughout.
            owner.call("Network.emulateNetworkConditions",Map.of("offline",false,"latency",0,"downloadThroughput",-1,"uploadThroughput",-1));
            check("Reconnect reconciles missed status without page reload",
                await(()->owner.stompConnected.get()>connections&&notice(owner,tracking,"Đã hủy"),20)
                &&owner.js("performance.timeOrigin").asDouble()==origin,
                "Production reconnect/backoff and DB refresh recover latest notification; no manual refresh");
            check("Other session still isolated after reconnect",!other.text().contains(tracking),"No cross-user delivery");
            manager.merge(owner);manager.merge(other);
        }
        manager.result("WebSocket UI two-session journey complete",true,"Manager transition -> owner inbox MESSAGE/DOM update; other excluded; offline/reconnect recovers cancelled event");
    }
}
