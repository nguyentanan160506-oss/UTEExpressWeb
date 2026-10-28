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
            browser.page("New USER cannot read SMTP inbox","/admin/demo-mailbox",403,null);
            browser.nav("/verify-email");browser.fill(Map.of("email",email,"otp",code));browser.submit("/verify-email",Map.of());
            check("Used OTP cannot be replayed",browser.location().equals("/verify-email")&&browser.text().contains("Mã không hợp lệ"),"Single-use code enforced");
            browser.merge(inbox);
        }
        browser.call("Network.clearBrowserCookies",Map.of());
        browser.result("Registration SMTP OTP browser journey complete",true,"register -> SMTP -> ADMIN test inbox -> reject wrong code -> verify -> login USER -> reject reuse");
    }
}
