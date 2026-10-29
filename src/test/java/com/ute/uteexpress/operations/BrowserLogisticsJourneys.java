package com.ute.uteexpress.operations;

import java.util.*;

/** Two new parcels, all business mutations through rendered browser forms, no service/SQL shortcuts. */
final class BrowserLogisticsJourneys {
    record Parcel(String id,String tracking) { }
    private final BrowserQaTool browser;
    BrowserLogisticsJourneys(BrowserQaTool browser) { this.browser=browser; }
    void check(String name,boolean ok,String detail) {
        browser.result(name,ok,detail);
        if(!ok)throw new IllegalStateException("Logistics browser checkpoint failed: "+name);
    }
    boolean act(String path,Map<String,?> values)throws Exception {
        boolean found=browser.submit(path,values);
        check("E2E form "+path,found,"Rendered CSRF/version form submitted");
        check("E2E no rejected mutation "+path,
            !browser.js("Array.from(document.querySelectorAll('.alert-danger')).some(e=>e.innerText.trim())").asBoolean(),
            "No business validation/error flash after submit");
        return found;
    }
    String linkByTracking(String prefix,Parcel parcel)throws Exception {
        String value=browser.js("Array.from(document.querySelectorAll('a')).find(a=>a.getAttribute('href')?.startsWith("
            +BrowserQaTool.q(prefix)+")&&a.innerText.trim()==="+BrowserQaTool.q(parcel.tracking())+")?.getAttribute('href')||''").asText();
        check("E2E exact parcel link "+prefix,!value.isBlank(),"Matches tracking of newly created shipment "+parcel.id());
        return value;
    }
    Parcel create()throws Exception {
        browser.login("owner");String id=browser.create("HOME_PICKUP");
        check("E2E new HOME_PICKUP parcel",id!=null,"Never substitute seeded shipment");
        String tracking=browser.js("document.querySelector('h1').innerText.trim()").asText();
        check("E2E tracking identifier",tracking.matches("UTE[A-Z0-9]{32}"),"Public tracking format");
        return new Parcel(id,tracking);
    }
    void pickup(Parcel p)throws Exception {
        browser.login("manager");browser.nav("/manager/shipments/"+p.id());
        act("/manager/shipments/"+p.id()+"/confirm",Map.of());
        String employee=browser.js("document.querySelector('select[name=employeeId] option:not([value=\"\"])').value").asText();
        act("/manager/shipments/"+p.id()+"/pickup-assignments",Map.of("employeeId",employee));
        browser.login("pickup-courier");browser.nav("/shipper/pickups");
        String task=linkByTracking("/shipper/pickups/",p);browser.nav(task);
        act(task+"/collect",Map.of("result","SUCCESS","reference","C22-PICKUP-"+p.id(),"physicallyCollected",true));
        check("E2E pickup evidence "+p.id(),browser.text().contains("C22-PICKUP-"+p.id()),"Evidence visible after save");
        act(task+"/handoffs",Map.of("notes","C22-HANDOFF-"+p.id()));
        browser.login("manager");browser.nav("/manager/handoffs");
        String handoff=linkByTracking("/manager/handoffs/",p);browser.nav(handoff);
        act(handoff+"/accept",Map.of("physicallyReceived",true));
        browser.nav("/manager/shipments/"+p.id());
        check("E2E independent source receipt "+p.id(),browser.text().contains("RCV-")
            &&browser.text().contains("Chi nhánh nguồn đã nhận hàng"),"Source custody only after physical acceptance");
    }
    void leg(List<Parcel> parcels,String from,String to,String destination,String label,boolean returning)throws Exception {
        browser.login(from);browser.nav("/manager/trips/new");
        String route=browser.js("Array.from(document.querySelectorAll('select[name=routeId] option')).find(o=>o.value&&o.text.includes("
            +BrowserQaTool.q("→ "+destination)+"))?.value||''").asText();
        check("E2E configured route "+label,!route.isBlank(),"Destination="+destination+"; no guessed route ID");
        act("/manager/trips",Map.of("routeId",route));String trip=browser.location();
        check("E2E draft trip "+label,trip.matches("/manager/trips/[0-9]+"),"Trip created via UI");
        String marker=returning?"return-candidate-":"candidate-";
        List<String> ids=parcels.stream().map(p->marker+p.id()).toList();
        browser.js("(()=>{const ids="+BrowserQaTool.q(ids)+";for(const id of ids){const e=document.getElementById(id);if(!e)throw Error('Parcel not eligible at this point');e.checked=true;}return true;})()");
        String manifestSelector="form:has(#"+ids.getFirst()+")";
        check("E2E manifest "+label,browser.submitSelector(manifestSelector,Map.of()),"Only the new parcels selected; correct forward/reverse form");
        act(trip+"/seal",Map.of("reference","C22-"+label+"-SEAL","confirmed",true));
        act(trip+"/depart",Map.of("reference","C22-"+label+"-DEPART","confirmed",true));
        browser.login(to);browser.nav(trip);
        act(trip+"/arrive",Map.of("reference","C22-"+label+"-ARRIVE","confirmed",true));
        for(Parcel p:parcels) {
            String item=browser.js("Array.from(document.querySelectorAll('article')).find(a=>a.querySelector('h3')?.innerText.trim()==="
                +BrowserQaTool.q(p.tracking())+")?.querySelector('input[name=itemId]')?.value||''").asText();
            check("E2E manifested item "+label+"/"+p.id(),!item.isBlank(),"Receipt targets exact tracking, not first row");
            String selector="form[action='"+trip+"/receipts']:has(input[name=itemId][value='"+item+"'])";
            check("E2E physical receipt "+label+"/"+p.id(),
                browser.submitSelector(selector,Map.of("reference","C22-"+label+"-RECEIPT-"+p.id(),"confirmed",true)),
                "Receiving manager supplies physical receipt");
            check("E2E persisted receipt "+label+"/"+p.id(),browser.text().contains("C22-"+label+"-RECEIPT-"+p.id()),"Receipt reference visible");
        }
        act(trip+"/close",Map.of("reference","C22-"+label+"-CLOSE","confirmed",true));
        check("E2E closed trip "+label,browser.text().contains("C22-"+label+"-CLOSE"),"No unresolved parcel/discrepancy before close");
    }
    String delivery(Parcel p,boolean success)throws Exception {
        browser.login("destination-manager");String path="/manager/deliveries/"+p.id();browser.nav(path);
        String employee=browser.js("document.querySelector('select[name=employeeId] option:not([value=\"\"])').value").asText();
        act(path+"/assign",Map.of("employeeId",employee));
        act(path+"/handoff",Map.of("reference","C22-DELIVERY-HANDOFF-"+p.id(),"confirmed",true));
        browser.login("shipper");browser.nav("/shipper/deliveries");
        String task=linkByTracking("/shipper/deliveries/",p);browser.nav(task);
        act(task+"/accept",Map.of("reference","C22-ACCEPT-"+p.id(),"confirmed",true));
        act(task+"/start",Map.of("reference","C22-START-"+p.id(),"confirmed",true));
        if(success) {
            act(task+"/complete",Map.of("recipientType","RECIPIENT","recipientName","Người nhận E2E","reference","C22-PROOF-"+p.id(),"confirmed",true));
            browser.login("destination-manager");browser.nav(path);
            check("E2E delivered with proof",browser.text().contains("C22-PROOF-"+p.id())&&browser.text().contains("Đã giao"),"Same parcel from user quote through all three forward legs");
        } else {
            act(task+"/fail",Map.of("code","ABSENT","reason","C22-FAILED-"+p.id(),"confirmed",true));
            browser.login("destination-manager");browser.nav(path);
            act(path+"/return",Map.of("reason","Người gửi đề nghị hoàn kiện sau lần giao thất bại E2E"));
            browser.login("shipper");browser.nav(task);act(task+"/return-to-branch",Map.of("notes","C22-RECOVERY-"+p.id()));
            browser.login("destination-manager");browser.nav(path);
            act(path+"/recoveries/accept",Map.of("reference","C22-RECOVERY-RECEIPT-"+p.id(),"confirmed",true));
            check("E2E failed parcel physically recovered",browser.text().contains("C22-RECOVERY-RECEIPT-"+p.id()),"Return decision does not invent custody");
        }
        return task;
    }
    void finalChecks(Parcel p,String label,String document)throws Exception {
        browser.login("owner");browser.page("E2E owner final detail "+p.id(),"/user/shipments/"+p.id(),200,label);
        check("E2E original fee snapshot retained "+p.id(),browser.text().contains("60.000,00"),"No repricing on delivery/return");
        browser.login("other");browser.page("E2E other customer denied "+p.id(),"/user/shipments/"+p.id(),404,null);
        browser.call("Network.clearBrowserCookies",Map.of());
        browser.page("E2E Guest numeric ID denied "+p.id(),"/user/shipments/"+p.id(),401,null);
        browser.page("E2E public final timeline "+p.id(),"/tracking?trackingNumber="+p.tracking(),200,label);
        check("E2E final tracking privacy "+p.id(),!browser.text().contains("0901234567")&&!browser.text().contains(document),"Only public milestone timeline, no internal proof/contact details");
    }
    void run()throws Exception {
        Parcel delivered=create(),returned=create();
        pickup(delivered);pickup(returned);
        List<Parcel> batch=List.of(delivered,returned);
        leg(batch,"manager","hub-origin-manager","Hub miền Trung demo","FWD-COLLECTION",false);
        leg(batch,"hub-origin-manager","hub-dest-manager","Hub phía Nam demo","FWD-LINEHAUL",false);
        leg(batch,"hub-dest-manager","destination-manager","Chi nhánh TP.HCM demo","FWD-DISTRIBUTION",false);
        delivery(delivered,true);delivery(returned,false);
        List<Parcel> back=List.of(returned);
        leg(back,"destination-manager","hub-dest-manager","Hub phía Nam demo","REV-COLLECTION",true);
        leg(back,"hub-dest-manager","hub-origin-manager","Hub miền Trung demo","REV-LINEHAUL",true);
        leg(back,"hub-origin-manager","manager","Chi nhánh Đà Nẵng demo","REV-DISTRIBUTION",true);
        browser.nav("/manager/deliveries/"+returned.id());
        act("/manager/deliveries/"+returned.id()+"/return-to-sender",
            Map.of("recipientType","SENDER","recipientName","Người gửi E2E","reference","C22-SENDER-RECEIPT","confirmed",true));
        check("E2E RETURNED requires sender receipt",browser.text().contains("C22-SENDER-RECEIPT")&&browser.text().contains("Đã hoàn hàng"),"All three reverse legs received before sender handover");
        browser.page("E2E complete return trip history","/manager/trips/shipments/"+returned.id()+"/history",200,null);
        check("E2E trip history includes forward and reverse",browser.text().contains("C22-FWD-COLLECTION-RECEIPT-"+returned.id())
            &&browser.text().contains("C22-REV-DISTRIBUTION-RECEIPT-"+returned.id()),"Physical receipt references survive the full chain");
        finalChecks(delivered,"Đã giao","C22-PROOF-"+delivered.id());
        finalChecks(returned,"Đã hoàn hàng","C22-SENDER-RECEIPT");
        browser.result("Coherent logistics browser journeys complete",true,
            "New HOME_PICKUP shipments "+delivered.id()+" DELIVERED and "+returned.id()+" RETURNED; 3 forward and 3 reverse legs, no seeded-parcel substitution");
    }
}
