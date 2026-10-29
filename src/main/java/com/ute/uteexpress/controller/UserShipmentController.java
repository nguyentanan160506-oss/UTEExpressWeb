package com.ute.uteexpress.controller;

import com.ute.uteexpress.dto.shipment.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/user/shipments")
@PreAuthorize("hasRole('USER')")
public class UserShipmentController {
    private final UserShipmentService shipments;
    private final QuoteService quotes;
    public UserShipmentController(UserShipmentService shipments, QuoteService quotes) { this.shipments = shipments; this.quotes = quotes; }

    @InitBinder("quoteConfirmation")
    void confirmationFields(WebDataBinder binder) { binder.setAllowedFields("quoteToken", "_csrf"); }

    @InitBinder("shipmentForm")
    void shipmentFields(WebDataBinder binder) {
        binder.setAllowedFields("shipmentMethod", "senderName", "senderProvince", "senderDistrict", "senderWard", "senderAddress",
            "senderPhone", "receiverName", "receiverProvince", "receiverDistrict", "receiverWard", "receiverAddress",
            "receiverPhone", "originBranchId", "destinationBranchId", "serviceId", "packageDescription", "weightKg",
            "lengthCm", "widthCm", "heightCm", "codAmount", "declaredValue", "paymentPayer", "note", "_csrf");
    }

    @ModelAttribute("statusLabels")
    Map<ShipmentStatus, String> statusLabels() { return ShipmentStatusLabels.VALUES; }

    @GetMapping("/create")
    public String createPage(Model model) {
        model.addAttribute("shipmentForm", new ShipmentCreateForm());
        return formPage(model);
    }

    @PostMapping("/quotes")
    public String quote(@Valid @ModelAttribute("shipmentForm") ShipmentCreateForm form,
            BindingResult errors, Model model) {
        if (errors.getSuppressedFields().length > 0) {
            errors.reject("shipment.fields", "Yêu cầu chứa trường không được phép. Vui lòng chỉ nhập thông tin trong biểu mẫu.");
        }
        if (errors.hasErrors()) { return formPage(model); }
        try {
            String token = quotes.quote(form);
            return "redirect:/user/shipments/quotes/" + token;
        } catch (ShipmentCreationException exception) {
            errors.rejectValue(exception.getField(), "shipment.configuration", exception.getMessage());
        } catch (DataAccessException exception) {
            // Failed quote persistence never creates a shipment.
            errors.reject("shipment.database", "Không thể lưu báo giá lúc này. Vui lòng thử lại; chưa có vận đơn được tạo.");
        }
        return formPage(model);
    }

    @PostMapping("/branches")
    public String branchChoices(@ModelAttribute("shipmentForm") ShipmentCreateForm form, BindingResult errors, Model model) {
        if (errors.getSuppressedFields().length > 0) { errors.reject("shipment.fields", "Yêu cầu chứa trường không được phép."); }
        if (!errors.hasErrors()) {
            try {
                var choices = quotes.branchChoices(form);
                model.addAttribute("branchChoices", choices);
                if (choices.origins().isEmpty()) {
                    errors.rejectValue("originBranchId", "shipment.coverage",
                        "Không có chi nhánh gửi đang hoạt động phục vụ địa chỉ đã nhập với phương thức này. Vui lòng kiểm tra tỉnh, quận/huyện, phường/xã.");
                }
                if (choices.destinations().isEmpty()) {
                    errors.rejectValue("destinationBranchId", "shipment.coverage",
                        "Không có chi nhánh nhận đang hoạt động phục vụ địa chỉ đã nhập. Vui lòng kiểm tra tỉnh, quận/huyện, phường/xã.");
                }
            }
            catch (ShipmentCreationException failure) { errors.rejectValue(failure.getField(), "shipment.coverage", failure.getMessage()); }
        }
        return formPage(model);
    }

    @GetMapping("/quotes/{token}")
    public String review(@PathVariable @Pattern(regexp = "[a-f0-9]{32}") String token, Model model,
            jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("quote", quotes.review(token));
        return "shipments/quote";
    }

    @PostMapping
    public String confirm(@Valid @ModelAttribute("quoteConfirmation") QuoteConfirmationForm form,
            BindingResult errors, RedirectAttributes flash) {
        if (errors.hasErrors() || errors.getSuppressedFields().length > 0) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                "Yêu cầu xác nhận báo giá không hợp lệ.");
        }
        try {
            Long id = quotes.confirm(form.getQuoteToken());
            flash.addFlashAttribute("notice", "Tạo vận đơn thành công.");
            return "redirect:/user/shipments/" + id;
        } catch (ShipmentCreationException failure) {
            flash.addFlashAttribute("error", failure.getMessage());
        } catch (DataAccessException failure) {
            flash.addFlashAttribute("error", "Chưa thể xác nhận kết quả. Vui lòng thử lại cùng báo giá để tránh tạo trùng.");
        }
        return "redirect:/user/shipments/quotes/" + form.getQuoteToken();
    }

    @GetMapping
    public String history(@RequestParam(defaultValue = "") @Size(max = 50) String q,
            @RequestParam(required = false) ShipmentStatus status,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, Model model) {
        model.addAttribute("shipments", shipments.list(q, status, PageRequest.of(page, size)));
        model.addAttribute("q", q); model.addAttribute("selectedStatus", status);
        model.addAttribute("size", size); model.addAttribute("statuses", ShipmentStatus.values());
        return "shipments/history";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable @Positive Long id,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int eventPage, Model model) {
        model.addAttribute("shipment", shipments.detail(id, eventPage));
        return "shipments/detail";
    }

    private String formPage(Model model) {
        model.addAttribute("provinces", ProvinceCatalog.all());
        model.addAttribute("options", shipments.options());
        model.addAttribute("payers", PaymentPayer.values());
        if (!model.containsAttribute("branchChoices")) {
            try { model.addAttribute("branchChoices", quotes.branchChoices((ShipmentCreateForm) model.getAttribute("shipmentForm"))); }
            catch (ShipmentCreationException failure) {
                model.addAttribute("branchChoices", new QuoteService.BranchChoices(java.util.List.of(), java.util.List.of()));
            }
        }
        return "shipments/create";
    }
}
