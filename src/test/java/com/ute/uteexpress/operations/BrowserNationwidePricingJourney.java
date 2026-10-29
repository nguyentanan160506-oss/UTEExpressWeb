package com.ute.uteexpress.operations;

import java.time.*;
import java.util.*;

/** Real browser forms/CSRF on the runner's isolated H2, no JDBC access. */
final class BrowserNationwidePricingJourney {
    private final BrowserQaTool b;
    BrowserNationwidePricingJourney(BrowserQaTool b){this.b=b;}
    void guest()throws Exception {
        b.call("Network.clearBrowserCookies",Map.of());b.nav("/quote");
        b.result("National quote has exactly 34 origin and destination choices",b.js("['senderProvince','receiverProvince'].every(n=>document.getElementsByName(n)[0].options.length===35)").asBoolean(),"Official catalog + one placeholder");
        b.result("National estimate does not require invented branches",b.js("!document.querySelector('select[name=originBranchId]')&&!document.querySelector('input[name=senderDistrict]')").asBoolean(),"Non-binding national pricing mode");
        String[][] examples={{"Hà Nội","Hà Nội","20000.00"},{"Hà Nội","Hải Phòng","30000.00"},{"Hà Nội","Đà Nẵng","40000.00"},{"Đà Nẵng","Hồ Chí Minh","45000.00"},{"Hà Nội","Hồ Chí Minh","60000.00"}};
        for(var row:examples)for(var method:List.of("BRANCH_DROPOFF","HOME_PICKUP")) {
            b.nav("/quote");b.submit("/quote",Map.of("senderProvince",row[0],"receiverProvince",row[1],"shipmentMethod",method,"serviceId","1","weightKg","1.01"));
            String amount=new java.math.BigDecimal(row[2]).add(new java.math.BigDecimal(method.equals("HOME_PICKUP")?"15000.00":"5000.00")).toPlainString();
            b.result("National price "+row[0]+" → "+row[1]+" "+method,b.js("document.querySelector('section.card dd:last-of-type')?.innerText.trim()").asText().equals(amount+" VND")&&b.text().contains("phí cân vượt 5000.00 VND"),"Server-rendered base + ceil(overweight) + DB pickup; expected="+amount);
        }
        b.nav("/quote");b.fill(Map.of("senderProvince","Hà Nội","receiverProvince","Cà Mau","shipmentMethod","BRANCH_DROPOFF","serviceId","1","weightKg","0.50"));
        b.submit("/quote",Map.of());b.result("National weight below 1 kg has no extra charge",b.text().contains("phí cân vượt 0.00 VND"),"Included first kilogram");
        b.nav("/quote");int forged=b.js("(async()=>{const form=document.querySelector('form[action=\"/quote\"]');const data=new URLSearchParams(new FormData(form));Object.entries({senderProvince:'Hà Nội',receiverProvince:'Cà Mau',shipmentMethod:'HOME_PICKUP',serviceId:'1',weightKg:'1.01',totalFee:'0'}).forEach(([k,v])=>data.set(k,v));return (await fetch('/quote',{method:'POST',body:data})).status;})()").asInt();
        b.result("National client-supplied amount rejected",forged==400,"Allowlist rejects price fields HTTP="+forged);
        b.nav("/quote");int noCsrf=b.browserPost("/quote",false);b.result("National quote CSRF enforced",noCsrf==403,"No public POST CSRF exemption");
        b.nav("/quote");b.js("document.getElementsByName('senderProvince')[0].add(new Option('Không hợp lệ','UNKNOWN'));true");
        b.submit("/quote",Map.of("senderProvince","UNKNOWN","receiverProvince","Cà Mau","shipmentMethod","BRANCH_DROPOFF","serviceId","1","weightKg","1"));
        b.result("National invalid province rejected by backend",b.text().contains("danh mục 34 tỉnh/thành")&&!b.js("!!document.querySelector('section.card')").asBoolean(),"No guessed region or zero tariff");
        b.nav("/quote?nationwide=false");b.result("Coverage-aware quote remains available",b.js("!!document.querySelector('select[name=destinationBranchId]')&&!!document.querySelector('input[name=senderDistrict]')").asBoolean(),"Actual creation still requires explicit coverage");
    }
    void admin()throws Exception {
        b.login("admin");b.nav("/admin/services/new");
        b.submit("/admin/services",Map.of("code","BROWSER-NATIONAL","name","Bảng giá toàn quốc QA H2","estimatedDeliveryHours","48"));
        b.result("Admin creates real JPA service with ID and audit",b.text().contains("Bảng giá toàn quốc QA H2")&&!b.text().contains("Không thể"),"Regresses managed save result issue");
        String serviceHref=b.js("Array.from(document.querySelectorAll('a')).find(a=>a.innerText.includes('BROWSER-NATIONAL'))?.getAttribute('href')||''").asText();
        String serviceId=serviceHref.substring(serviceHref.lastIndexOf('/')+1);
        if(!serviceId.matches("[0-9]+")){b.blocked("National admin version journey","Created service is not listed");return;}
        b.nav("/admin/pricing/new?serviceId="+serviceId);
        String future=LocalDateTime.now(ZoneOffset.UTC).plusMinutes(15).withNano(0).toString();
        b.submit("/admin/pricing",Map.of("code","BROWSER-NATIONAL-V1","validFrom",future));
        String href=b.js("Array.from(document.querySelectorAll('a')).find(a=>a.innerText.includes('BROWSER-NATIONAL-V1'))?.getAttribute('href')||''").asText();
        b.result("Admin creates real JPA draft with ID and audit",href.matches("/admin/pricing/[0-9]+"),"New pricing version listed");
        if(href.isBlank())return;b.nav(href);String id=href.substring(href.lastIndexOf('/')+1),rules=href+"/rules";
        for(var scope:List.of("INTRA_PROVINCE","SAME_REGION","NORTH_CENTRAL","CENTRAL_SOUTH","NORTH_SOUTH")) {
            b.submitSelector("form[action='"+rules+"']:not(:has(input[name=ruleId]))",Map.of("scope",scope,"baseWeightKg","1.00","basePrice","30000.00","extraWeightStepKg","1.00","extraPricePerStep","5000.00","active",true));
            b.result("Admin configures "+scope,b.text().contains(scope)&&!b.js("!!document.querySelector('.alert-danger')").asBoolean(),"Existing versioned pricing UI");
        }
        b.submitSelector("form[action='"+rules+"']:has(input[name=ruleId])",Map.of("basePrice","31000.00","extraPricePerStep","6000.00"));
        b.result("Admin edits base and extra-kilogram tariff in draft",b.text().contains("31000.00")&&b.text().contains("6000.00"),"Published versions remain immutable");
        b.submitSelector("form[action='"+href+"/pickup-rules']:not(:has(input[name=ruleId]))",Map.of("branchId","","amount","10000.00","active",true));
        b.result("Admin configures nationwide pickup default",b.text().contains("Toàn quốc (mặc định)")&&b.text().contains("10000.00"),"No fake branch");
        b.submit(href+"/check",Map.of());b.result("Admin five-group version validation",b.text().contains("Hợp lệ tại thời điểm kiểm tra"),"No overlapping price period");
        b.submit(href+"/publish",Map.of("confirmed",true));b.result("Admin publishes five-group book",b.text().contains("Đã công bố")&&!b.js("!!document.querySelector('.alert-danger')").asBoolean(),"Scheduled future; running demo tariffs unchanged");
        b.result("Published price forms disappear",!b.js("!!document.querySelector('form[action=\""+rules+"\"]')").asBoolean(),"Immutable published book");
        b.login("owner");b.page("USER cannot open nationwide pricing admin","/admin/pricing/"+id,403,null);
        b.login("pickup-courier");b.page("SHIPPER cannot open nationwide pricing admin","/admin/pricing/"+id,403,null);
    }
}
