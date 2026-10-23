package com.ute.uteexpress.controller;

import com.ute.uteexpress.service.notifications.NotificationService;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller @PreAuthorize("isAuthenticated()")
public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service) { this.service = service; }
    @GetMapping("/notifications") public String page(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "false") boolean unread, Model model) {
        model.addAttribute("inbox", service.list(page, 20, unread)); model.addAttribute("unread", unread); return "notifications/index";
    }
    @GetMapping("/notifications/{id}/open") public String open(@PathVariable Long id) { return "redirect:" + service.target(id); }
    @GetMapping("/api/notifications") @ResponseBody public ResponseEntity<NotificationService.Inbox> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "false") boolean unread) {
        return noCache(service.list(page, size, unread));
    }
    @GetMapping("/api/notifications/unread-count") @ResponseBody public ResponseEntity<Map<String, Long>> count() { return noCache(Map.of("count", service.count())); }
    @GetMapping("/api/notifications/csrf") @ResponseBody public ResponseEntity<Map<String, String>> csrf(CsrfToken token) {
        service.count(); return noCache(Map.of("headerName", token.getHeaderName(), "token", token.getToken()));
    }
    @PostMapping("/api/notifications/{id}/read") @ResponseBody public ResponseEntity<Void> read(@PathVariable Long id) { service.read(id); return noCache(null); }
    @PostMapping("/api/notifications/read-all") @ResponseBody public ResponseEntity<Void> all() { service.readAll(); return noCache(null); }
    @PostMapping("/notifications/{id}/read") public String readPage(@PathVariable Long id) { service.read(id); return "redirect:/notifications"; }
    @PostMapping("/notifications/read-all") public String allPage() { service.readAll(); return "redirect:/notifications"; }
    private <T> ResponseEntity<T> noCache(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
