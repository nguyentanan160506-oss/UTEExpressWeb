package com.ute.uteexpress.operations;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;

/** Presenter inbox: enabled only explicitly on disposable H2; normal ADMIN session and main security chain. */
@Controller
@Profile({"demo-local","demo-sql"})
@ConditionalOnProperty(name="uteexpress.demo.test-mail.enabled",havingValue="true")
public class DemoMailboxController {
    private final DemoSmtpServer smtp;
    public DemoMailboxController(DemoSmtpServer smtp){this.smtp=smtp;}
    @GetMapping("/admin/demo-mailbox")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<String> mailbox() {
        StringBuilder html=new StringBuilder("""
            <!doctype html><html lang="vi"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex">
            <title>Hộp thư demo</title><link rel="stylesheet" href="/css/vendor/bootstrap.min.css"></head>
            <body><main class="container py-4"><h1>Hộp thư SMTP giả lập — demo cục bộ</h1>
            <p>Chỉ ADMIN demo trên laptop này. Không gửi email ra Internet. Thư và mã tự hết hạn sau 5 phút, mất khi dừng ứng dụng.</p>
            <p><a href="/admin/demo-mailbox">Làm mới hộp thư</a> · <a href="/admin/users">Quản trị</a></p>
            <table class="table"><thead><tr><th>Email test</th><th>Nhận lúc UTC</th><th>Mã xác minh</th></tr></thead><tbody>
            """);
        var mails=smtp.messages();for(int i=mails.size()-1;i>=0;i--) {
            var mail=mails.get(i);
            html.append("<tr data-recipient=\"").append(HtmlUtils.htmlEscape(mail.recipient)).append("\"><td>")
                .append(HtmlUtils.htmlEscape(mail.recipient)).append("</td><td>").append(mail.received)
                .append("</td><td class=\"otp\">").append(mail.code).append("</td></tr>");
        }
        html.append("</tbody></table></main></body></html>");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/html;charset=UTF-8")).cacheControl(CacheControl.noStore())
            .header("Referrer-Policy","no-referrer").header("X-Robots-Tag","noindex").body(html.toString());
    }
}
