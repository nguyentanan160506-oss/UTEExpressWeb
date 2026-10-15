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
    public UserShipmentController(UserShipmentService shipments) { this.shipments = shipments; }

    @InitBinder("shipmentForm")
    void shipmentFields(WebDataBinder binder) {
        binder.setAllowedFields("senderName", "senderProvince", "senderDistrict", "senderWard", "senderAddress",
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

    @PostMapping
    public String create(@Valid @ModelAttribute("shipmentForm") ShipmentCreateForm form,
            BindingResult errors, Model model, RedirectAttributes flash) {
        if (errors.getSuppressedFields().length > 0) {
            errors.reject("shipment.fields", "Yêu cầu chứa trường không được phép. Vui lòng chỉ nhập thông tin trong biểu mẫu.");
        }
        if (errors.hasErrors()) { return formPage(model); }
        try {
            Long id = shipments.create(form);
            flash.addFlashAttribute("notice", "Tạo vận đơn thành công.");
            return "redirect:/user/shipments/" + id;
        } catch (ShipmentCreationException exception) {
            errors.rejectValue(exception.getField(), "shipment.configuration", exception.getMessage());
        } catch (DataAccessException exception) {
            // A unique tracking collision or any other database failure rolls back the whole service transaction.
            errors.reject("shipment.database", "Không thể tạo vận đơn lúc này. Vui lòng thử lại; hệ thống chưa xác nhận tạo thành công.");
        }
        return formPage(model);
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
        model.addAttribute("options", shipments.options());
        model.addAttribute("payers", PaymentPayer.values());
        return "shipments/create";
    }
}
