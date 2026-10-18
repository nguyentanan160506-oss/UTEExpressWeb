package com.ute.uteexpress.controller;

import com.ute.uteexpress.dto.shipment.HandoffReviewCommand;
import com.ute.uteexpress.dto.shipper.PickupForms;
import com.ute.uteexpress.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequestMapping("/manager/handoffs") @PreAuthorize("hasRole('MANAGER')")
public class ManagerHandoffController {
    private final ManagerHandoffService manager;
    private final ShipmentLifecycleService lifecycle;
    public ManagerHandoffController(ManagerHandoffService manager, ShipmentLifecycleService lifecycle) {
        this.manager = manager; this.lifecycle = lifecycle;
    }
    @InitBinder("reviewForm") void fields(WebDataBinder binder) {
        binder.setAllowedFields("handoffId", "expectedVersion", "physicallyReceived", "receiptReference", "reason", "_csrf");
    }
    @GetMapping({"", "/"}) String pending(@RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page, Model model) {
        model.addAttribute("handoffs", manager.pending(page)); return "manager/handoffs";
    }
    @GetMapping("/{handoffId}") String detail(@PathVariable @Positive Long handoffId, Model model) {
        model.addAttribute("review", manager.detail(handoffId)); return "manager/handoff-detail";
    }
    @PostMapping("/{handoffId}/accept") String accept(@PathVariable @Positive Long handoffId,
            @Valid @ModelAttribute("reviewForm") PickupForms.Review form, BindingResult errors, RedirectAttributes flash) {
        return review(handoffId, true, form, errors, flash);
    }
    @PostMapping("/{handoffId}/reject") String reject(@PathVariable @Positive Long handoffId,
            @Valid @ModelAttribute("reviewForm") PickupForms.Review form, BindingResult errors, RedirectAttributes flash) {
        return review(handoffId, false, form, errors, flash);
    }
    private String review(Long id, boolean accept, PickupForms.Review form, BindingResult errors, RedirectAttributes flash) {
        return ShipperPickupController.mutate("/manager/handoffs/" + id, errors, flash,
            () -> lifecycle.reviewOriginHandoff(new HandoffReviewCommand(id, form.getExpectedVersion(), accept,
                form.isPhysicallyReceived(), form.getReceiptReference(), form.getReason())));
    }
}
