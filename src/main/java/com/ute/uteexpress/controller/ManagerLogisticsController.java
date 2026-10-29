package com.ute.uteexpress.controller;

import com.ute.uteexpress.dto.manager.ManagerForms;
import com.ute.uteexpress.dto.shipment.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.dao.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequestMapping("/manager") @PreAuthorize("hasRole('MANAGER')")
public class ManagerLogisticsController {
    private final ManagerLogisticsService manager;
    private final ShipmentLifecycleService lifecycle;
    public ManagerLogisticsController(ManagerLogisticsService manager, ShipmentLifecycleService lifecycle) {
        this.manager = manager; this.lifecycle = lifecycle;
    }
    @InitBinder("requestForm") void requestFields(WebDataBinder binder) {
        // MVC also offers the URI id to the binder; no form has an id property.
        // Operations always use the separately validated @PathVariable.
        binder.setAllowedFields("expectedVersion", "reason", "_csrf", "id");
    }
    @InitBinder("pickupForm") void pickupFields(WebDataBinder binder) {
        binder.setAllowedFields("expectedVersion", "reason", "employeeId", "replacesAssignmentId", "_csrf", "id");
    }
    @InitBinder("intakeForm") void intakeFields(WebDataBinder binder) {
        binder.setAllowedFields("expectedVersion", "physicallyReceived", "notes", "_csrf", "id");
    }
    @ModelAttribute("statusLabels") Map<ShipmentStatus, String> statuses() { return ShipmentStatusLabels.VALUES; }

    @GetMapping({"", "/"})
    public String dashboard(@RequestParam(required = false) @Positive Long branchId, Model model) {
        model.addAttribute("dashboard", manager.dashboard(branchId));
        model.addAttribute("branches", manager.branches()); model.addAttribute("selectedBranch", branchId);
        return "manager/dashboard";
    }
    @GetMapping("/shipments")
    public String list(@RequestParam(required = false) @Positive Long branchId,
            @RequestParam(required = false) ShipmentStatus status, @RequestParam(required = false) ShipmentMethod method,
            @RequestParam(defaultValue = "") @Size(max = 50) String q,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, Model model) {
        model.addAttribute("shipments", manager.list(branchId, status, method, q, PageRequest.of(page, size)));
        model.addAttribute("branches", manager.branches()); model.addAttribute("selectedBranch", branchId);
        model.addAttribute("selectedStatus", status); model.addAttribute("selectedMethod", method);
        model.addAttribute("statuses", ShipmentStatus.values()); model.addAttribute("q", q); model.addAttribute("size", size);
        return "manager/shipments";
    }
    @GetMapping("/shipments/{id}")
    public String detail(@PathVariable @Positive Long id,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int eventPage,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int assignmentPage,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int employeePage, Model model) {
        model.addAttribute("detail", manager.detail(id, eventPage, assignmentPage, employeePage));
        return "manager/detail";
    }
    @PostMapping("/shipments/{id}/confirm")
    public String confirm(@PathVariable @Positive Long id, @Valid @ModelAttribute("requestForm") ManagerForms.Request form,
            BindingResult errors, RedirectAttributes flash) {
        return mutate(id, errors, flash, () -> lifecycle.transition(new ShipmentTransitionCommand(id, form.getExpectedVersion(), ShipmentStatus.CONFIRMED)));
    }
    @PostMapping("/shipments/{id}/cancel")
    public String cancel(@PathVariable @Positive Long id, @Valid @ModelAttribute("requestForm") ManagerForms.Request form,
            BindingResult errors, RedirectAttributes flash) {
        return mutate(id, errors, flash, () -> lifecycle.transition(new ShipmentTransitionCommand(id, form.getExpectedVersion(), ShipmentStatus.CANCELLED, form.getReason())));
    }
    @PostMapping("/shipments/{id}/pickup-assignments")
    public String assign(@PathVariable @Positive Long id, @Valid @ModelAttribute("pickupForm") ManagerForms.Pickup form,
            BindingResult errors, RedirectAttributes flash) {
        return mutate(id, errors, flash, () -> lifecycle.assignPickup(new PickupAssignmentCommand(id, form.getExpectedVersion(),
            form.getEmployeeId(), form.getReplacesAssignmentId(), form.getReason())));
    }
    @PostMapping("/shipments/{id}/intake")
    public String intake(@PathVariable @Positive Long id, @Valid @ModelAttribute("intakeForm") ManagerForms.Intake form,
            BindingResult errors, RedirectAttributes flash) {
        return mutateWithNotice(id, errors, flash, () -> lifecycle.receiveDropoff(new BranchIntakeCommand(id, form.getExpectedVersion(),
            form.getPhysicallyReceived(), form.getNotes())).notice());
    }
    private String mutate(Long id, BindingResult errors, RedirectAttributes flash, Runnable operation) {
        return mutateWithNotice(id, errors, flash, () -> { operation.run(); return "Đã ghi nhận thao tác thành công."; });
    }
    private String mutateWithNotice(Long id, BindingResult errors, RedirectAttributes flash, java.util.function.Supplier<String> operation) {
        if (errors.getSuppressedFields().length > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Yêu cầu chứa trường không được phép.");
        }
        if (errors.hasErrors()) { flash.addFlashAttribute("error", "Thông tin thao tác không hợp lệ. Vui lòng kiểm tra và gửi lại."); }
        else {
            try { flash.addFlashAttribute("notice", operation.get()); }
            catch (OptimisticLockingFailureException conflict) { flash.addFlashAttribute("error", "Vận đơn đã thay đổi. Vui lòng kiểm tra dữ liệu mới trước khi thao tác lại."); }
            catch (LogisticsRuleException rule) { flash.addFlashAttribute("error", rule.getMessage()); }
            catch (DataAccessException failure) { flash.addFlashAttribute("error", "Không thể ghi nhận thao tác. Vui lòng tải lại để kiểm tra; chưa xác nhận thành công."); }
        }
        return "redirect:/manager/shipments/" + id;
    }
}
