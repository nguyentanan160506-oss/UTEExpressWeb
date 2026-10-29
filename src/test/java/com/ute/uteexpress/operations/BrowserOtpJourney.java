package com.ute.uteexpress.operations;

import java.util.Map;

/** Reads the captured SMTP mail through the ADMIN-only H2 inbox, never a verification bypass. */
final class BrowserOtpJourney {
    private final BrowserQaTool browser;
    BrowserOtpJourney(BrowserQaTool browser){this.browser=browser;}
    private void check(String name,boolean ok,String detail){
        browser.result(name,ok,detail);if(!ok)throw new IllegalStateException("OTP browser checkpoint failed: "+name);
    }
    void activateCurrentRegistration()throws Exception {
        String email="browserqa-registration@example.test";
        check("OTP registration sends mail and opens verify form",browser.location().equals("/verify-email"),"Actual RegistrationService and production SMTP sender, local sink only");
        browser.page("Guest cannot read demo inbox","/admin/demo-mailbox",401,null);
        try(var inbox=browser.isolatedSession()){
            inbox.login("admin");inbox.page("Admin H2 test SMTP inbox","/admin/demo-mailbox",200,"Hộp thư SMTP giả lập");
            String code=inbox.js("document.querySelector('tr[data-recipient="+BrowserQaTool.q(email)+"] .otp')?.innerText.trim()||''").asText();
            browser.rememberSecret(code);
            check("OTP received through real local SMTP",code.matches("[0-9]{6}"),"Code held in memory only; value never logged or written");
            browser.nav("/login");browser.fill(Map.of("email",email,"password",browser.password));browser.submit("/login",Map.of());
            check("UNVERIFIED account cannot log in",browser.location().equals("/login")
                &&browser.js("location.search.includes('error')").asBoolean(),"No automatic activation");
            browser.nav("/verify-email");
            String wrong=String.format("%06d",(Integer.parseInt(code)+1)%1_000_000);
            browser.rememberSecret(wrong);
            browser.fill(Map.of("email",email,"otp",wrong));browser.submit("/verify-email",Map.of());
            check("Wrong OTP rejected in browser",browser.location().equals("/verify-email")&&browser.text().contains("Mã không hợp lệ"),"Verification remains mandatory");
            browser.fill(Map.of("email",email,"otp",code));browser.submit("/verify-email",Map.of());
            check("Correct received OTP activates account",browser.location().equals("/login"),"Normal verification endpoint, no DB/service mutation from runner");
            browser.login("browserqa-registration");
            browser.page("New verified USER can enter shipments","/user/shipments",200,null);
            browser.page("New USER profile and empty address book","/account/profile",200,"Bạn chưa lưu địa chỉ nào");
            browser.submit("/account/profile",Map.of("fullName","Khách hàng Browser QA"));
            check("New USER updates own profile",browser.text().contains("Đã lưu thay đổi")
                &&browser.js("document.querySelector('#fullName').value==='Khách hàng Browser QA'").asBoolean(),"Name persists through redirect; login identity and roles unchanged");
            browser.submit("/account/profile/addresses",Map.of("contactName","Liên hệ Browser QA","contactPhone","0982399999",
                "province","Đà Nẵng","district","Khu vực demo","ward","Phường demo","detailAddress","Địa điểm mô phỏng QA"));
            check("New USER saves own address",browser.text().contains("Liên hệ Browser QA")
                ||browser.js("Array.from(document.querySelectorAll('input[name=contactName]')).some(e=>e.value==='Liên hệ Browser QA')").asBoolean(),"Actual form, CSRF and owner-scoped persistence");
            String deleteAddress=browser.js("document.querySelector('form[action$=\"/delete\"]').getAttribute('action')").asText();
            try(var unrelated=browser.isolatedSession()){
                unrelated.login("other");unrelated.page("Other USER profile hides new account address","/account/profile",200,null);
                check("Address is private to new USER",!unrelated.text().contains("Liên hệ Browser QA"),"No shared address book");
                browser.merge(unrelated);
            }
            browser.submit(deleteAddress,Map.of());
            check("New USER deletes own address",browser.text().contains("Bạn chưa lưu địa chỉ nào"),"POST mutation retains CSRF protection");
            browser.nav("/user/shipments/create");
            check("New USER sees coverage instructions before lookup",
                browser.text().contains("Địa chỉ được phục vụ trong bản demo")&&browser.text().contains("Danh sách được tải sau khi")
                    &&browser.js("document.querySelector('#destinationBranchId').options.length===1").asBoolean(),
                "Fresh account sees why options are initially empty; no employee or shipment history prerequisite");
            browser.fill(Map.of("shipmentMethod","HOME_PICKUP","senderProvince","Đà Nẵng","senderDistrict","huyện núi thành","senderWard","xã núi thành",
                "receiverProvince","Hồ Chí Minh","receiverDistrict","Khu vực demo","receiverWard","Phường demo"));
            double reportedOriginPage=browser.js("performance.timeOrigin").asDouble();
            browser.js("document.querySelector('#destinationBranchId').closest('div').querySelector('button[formaction]').click();true");
            browser.waitNavigation(reportedOriginPage);
            check("Reported pickup area does not hide a valid destination",browser.text().contains("Không có chi nhánh gửi đang hoạt động")
                &&browser.js("Array.from(document.querySelector('#destinationBranchId').options).some(o=>o.value&&o.text.includes('Chi nhánh TP.HCM demo'))").asBoolean(),
                "Canonical province selected; reported Nui Thanh district/ward still has no demo coverage; receiver lookup remains independent");
            browser.fill(Map.of("senderProvince","Đà Nẵng","senderDistrict","Khu vực demo","senderWard","Phường demo",
                "receiverProvince","Đà Nẵng","receiverDistrict","huyện núi thành","receiverWard","xã núi thành"));
            double unsupportedPage=browser.js("performance.timeOrigin").asDouble();
            browser.js("document.querySelector('#destinationBranchId').closest('div').querySelector('button[formaction]').click();true");
            browser.waitNavigation(unsupportedPage);
            check("New USER sees explicit unsupported destination error",browser.text().contains("Không có chi nhánh nhận đang hoạt động")
                &&browser.js("document.querySelector('#destinationBranchId').options.length===1").asBoolean(),"No unfiltered fallback or fabricated branch");
            browser.fill(Map.of("receiverProvince","Hồ Chí Minh","receiverDistrict","Khu vực demo","receiverWard","Phường demo"));
            double beforeLookup=browser.js("performance.timeOrigin").asDouble();
            browser.js("document.querySelector('#destinationBranchId').closest('div').querySelector('button[formaction]').click();true");
            browser.waitNavigation(beforeLookup);
            check("New USER can load destination using nearby lookup button",
                browser.js("Array.from(document.querySelector('#destinationBranchId').options).some(o=>o.value&&o.text.includes('Chi nhánh TP.HCM demo'))").asBoolean(),
                "Valid active receiver branch comes from DB after explicit lookup");
            String newUserShipment=browser.create("HOME_PICKUP");
            check("Fresh registered USER completes quote and shipment creation",newUserShipment!=null,
                "Regression covers register -> OTP -> login -> branch lookup -> quote -> create with the same new account");
            browser.page("New USER cannot read SMTP inbox","/admin/demo-mailbox",403,null);
            browser.nav("/verify-email");browser.fill(Map.of("email",email,"otp",code));browser.submit("/verify-email",Map.of());
            check("Used OTP cannot be replayed",browser.location().equals("/verify-email")&&browser.text().contains("Mã không hợp lệ"),"Single-use code enforced");
            browser.merge(inbox);
        }
        browser.call("Network.clearBrowserCookies",Map.of());
        browser.result("Registration SMTP OTP browser journey complete",true,"register -> SMTP -> ADMIN test inbox -> reject wrong code -> verify -> login USER -> reject reuse");
    }
}
