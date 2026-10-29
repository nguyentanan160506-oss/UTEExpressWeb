package com.ute.uteexpress.controller;

import com.ute.uteexpress.dto.shipment.*;
import com.ute.uteexpress.dto.shipper.PickupForms;
import com.ute.uteexpress.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.dao.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequestMapping("/shipper") @PreAuthorize("hasRole('SHIPPER')")
public class ShipperPickupController {
    private final ShipperPickupService shipper;
    private final ShipmentLifecycleService lifecycle;
    public ShipperPickupController(ShipperPickupService shipper, ShipmentLifecycleService lifecycle) {
        this.shipper = shipper; this.lifecycle = lifecycle;
    }
    @InitBinder("collectionForm") void collectionFields(WebDataBinder binder) {
        binder.setAllowedFields("assignmentId", "expectedVersion", "result", "physicallyCollected", "reference", "notes", "_csrf");
    }
    @InitBinder("handoffForm") void handoffFields(WebDataBinder binder) {
        binder.setAllowedFields("assignmentId", "expectedVersion", "notes", "_csrf");
    }
    @ModelAttribute("statusLabels") Object statuses() { return ShipmentStatusLabels.VALUES; }
    @GetMapping({"", "/"}) String dashboard(Model model) {
        model.addAttribute("dashboard", shipper.dashboard()); return "shipper/dashboard";
    }
    @GetMapping("/pickups") String tasks(@RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page, Model model) {
        model.addAttribute("tasks", shipper.tasks(page)); return "shipper/pickups";
    }
    @GetMapping("/pickups/{assignmentId}") String detail(@PathVariable @Positive Long assignmentId,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int evidencePage,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int handoffPage, Model model) {
        model.addAttribute("detail", shipper.detail(assignmentId, evidencePage, handoffPage)); return "shipper/detail";
    }
    @PostMapping("/pickups/{assignmentId}/collect") String collect(@PathVariable @Positive Long assignmentId,
            @Valid @ModelAttribute("collectionForm") PickupForms.Collection form, BindingResult errors, RedirectAttributes flash) {
        return mutate("/shipper/pickups/" + assignmentId, errors, flash, () -> lifecycle.collectPickup(new PickupCollectionCommand(
            assignmentId, form.getExpectedVersion(), form.getResult(), form.isPhysicallyCollected(), form.getReference(), form.getNotes())));
    }
    @PostMapping("/pickups/{assignmentId}/handoffs") String handoff(@PathVariable @Positive Long assignmentId,
            @Valid @ModelAttribute("handoffForm") PickupForms.Request form, BindingResult errors, RedirectAttributes flash) {
        return mutate("/shipper/pickups/" + assignmentId, errors, flash, () -> lifecycle.requestOriginHandoff(
            new OriginHandoffCommand(assignmentId, form.getExpectedVersion(), form.getNotes())));
    }
    static String mutate(String redirect, BindingResult errors, RedirectAttributes flash, Runnable action) {
        return mutateWithNotice(redirect, errors, flash, () -> { action.run(); return "Đã ghi nhận thao tác."; });
    }
    static String mutateWithNotice(String redirect, BindingResult errors, RedirectAttributes flash, java.util.function.Supplier<String> action) {
        if (errors.getSuppressedFields().length > 0) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trường không được phép."); }
        if (errors.hasErrors()) { flash.addFlashAttribute("error", "Thông tin không hợp lệ. Vui lòng kiểm tra và gửi lại."); }
        else {
            try { flash.addFlashAttribute("notice", action.get()); }
            catch (OptimisticLockingFailureException conflict) { flash.addFlashAttribute("error", "Vận đơn đã thay đổi. Vui lòng tải lại trước khi thao tác."); }
            catch (LogisticsRuleException rule) { flash.addFlashAttribute("error", rule.getMessage()); }
            catch (DataAccessException failure) { flash.addFlashAttribute("error", "Không thể ghi nhận. Vui lòng tải lại kiểm tra; chưa xác nhận thành công."); }
        }
        return "redirect:" + redirect;
    }
}
