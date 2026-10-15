package com.ute.uteexpress.controller;

import com.ute.uteexpress.service.PublicTrackingService;
import com.ute.uteexpress.service.TrackingRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PublicTrackingController {
    private static final String NOT_FOUND = "Không tìm thấy thông tin tra cứu công khai. Vui lòng kiểm tra lại mã vận đơn.";
    private final PublicTrackingService tracking;
    private final TrackingRateLimiter limiter;

    public PublicTrackingController(PublicTrackingService tracking, TrackingRateLimiter limiter) {
        this.tracking = tracking; this.limiter = limiter;
    }

    @GetMapping("/tracking")
    public String tracking(@RequestParam(required = false) String trackingNumber, Model model,
            HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Robots-Tag", "noindex, nofollow");
        model.addAttribute("trackingNumber", "");
        if (trackingNumber == null) { return "tracking/index"; }
        // Invalid attempts also consume quota. Forwarded/X-Forwarded-For are deliberately not trusted.
        var decision = limiter.check(request.getRemoteAddr());
        if (!decision.allowed()) {
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
            model.addAttribute("error", "Bạn đã tra cứu quá nhiều lần. Vui lòng chờ một phút rồi thử lại.");
            return "tracking/index";
        }
        try {
            String number = PublicTrackingService.normalize(trackingNumber);
            model.addAttribute("trackingNumber", number);
            var result = tracking.lookup(number);
            if (result.isPresent()) { model.addAttribute("result", result.get()); }
            else { model.addAttribute("error", NOT_FOUND); }
        } catch (PublicTrackingService.InvalidTrackingNumberException exception) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("error", "Mã vận đơn không hợp lệ. Mã bắt đầu bằng UTE và có tổng cộng 35 ký tự.");
        } catch (DataAccessException exception) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            model.addAttribute("error", "Chưa thể tra cứu lúc này. Vui lòng thử lại sau.");
        }
        return "tracking/index";
    }
}
