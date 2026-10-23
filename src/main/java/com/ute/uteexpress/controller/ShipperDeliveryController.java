package com.ute.uteexpress.controller;

import com.ute.uteexpress.dto.delivery.*;
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

@Controller @RequestMapping("/shipper/deliveries") @PreAuthorize("hasRole('SHIPPER')")
public class ShipperDeliveryController {
    private final DeliveryReadService read;
    private final LastMileService delivery;
    public ShipperDeliveryController(DeliveryReadService read, LastMileService delivery) { this.read = read; this.delivery = delivery; }
    @InitBinder("actionForm") void actionFields(WebDataBinder b) { b.setAllowedFields("assignmentId", "expectedVersion", "confirmed", "reference", "_csrf"); }
    @InitBinder("completeForm") void completeFields(WebDataBinder b) { b.setAllowedFields("assignmentId", "expectedVersion", "confirmed", "reference", "notes", "recipientName", "recipientType", "_csrf"); }
    @InitBinder("failForm") void failFields(WebDataBinder b) { b.setAllowedFields("assignmentId", "expectedVersion", "confirmed", "code", "reason", "notes", "_csrf"); }
    @InitBinder("recoverForm") void recoverFields(WebDataBinder b) { b.setAllowedFields("assignmentId", "expectedVersion", "notes", "_csrf"); }
    @ModelAttribute void labels(Model m) { m.addAttribute("dispatchLabels", DeliveryLabels.DISPATCH); m.addAttribute("failureLabels", DeliveryLabels.FAILURE); }
    @GetMapping({"", "/"}) String list(@RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page, Model m) { m.addAttribute("tasks", read.shipperList(page)); return "delivery/shipper-list"; }
    @GetMapping("/{assignmentId}") String detail(@PathVariable @Positive Long assignmentId, @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page, Model m) {
        m.addAttribute("detail", read.shipperDetail(assignmentId, page)); return "delivery/shipper-detail";
    }
    @PostMapping("/{assignmentId}/accept") String accept(@PathVariable @Positive Long assignmentId, @Valid @ModelAttribute("actionForm") DeliveryForms.Action f, BindingResult e, RedirectAttributes a) {
        return mutate(assignmentId, e, a, () -> delivery.accept(new DeliveryCommands.Action(assignmentId, f.getExpectedVersion(), f.isConfirmed(), f.getReference())));
    }
    @PostMapping("/{assignmentId}/start") String start(@PathVariable @Positive Long assignmentId, @Valid @ModelAttribute("actionForm") DeliveryForms.Action f, BindingResult e, RedirectAttributes a) {
        return mutate(assignmentId, e, a, () -> delivery.start(new DeliveryCommands.Action(assignmentId, f.getExpectedVersion(), f.isConfirmed(), f.getReference())));
    }
    @PostMapping("/{assignmentId}/complete") String complete(@PathVariable @Positive Long assignmentId, @Valid @ModelAttribute("completeForm") DeliveryForms.Complete f, BindingResult e, RedirectAttributes a) {
        return mutate(assignmentId, e, a, () -> delivery.complete(new DeliveryCommands.Complete(assignmentId, f.getExpectedVersion(), f.isConfirmed(), f.getRecipientType(), f.getRecipientName(), f.getReference(), f.getNotes())));
    }
    @PostMapping("/{assignmentId}/fail") String fail(@PathVariable @Positive Long assignmentId, @Valid @ModelAttribute("failForm") DeliveryForms.Fail f, BindingResult e, RedirectAttributes a) {
        return mutate(assignmentId, e, a, () -> delivery.fail(new DeliveryCommands.Fail(assignmentId, f.getExpectedVersion(), f.isConfirmed(), f.getCode(), f.getReason(), f.getNotes())));
    }
    @PostMapping("/{assignmentId}/return-to-branch") String recover(@PathVariable @Positive Long assignmentId, @Valid @ModelAttribute("recoverForm") DeliveryForms.Recover f, BindingResult e, RedirectAttributes a) {
        return mutate(assignmentId, e, a, () -> delivery.requestRecovery(new DeliveryCommands.Recover(assignmentId, f.getExpectedVersion(), f.getNotes())));
    }
    private String mutate(Long id, BindingResult e, RedirectAttributes a, Runnable operation) { return ShipperPickupController.mutate("/shipper/deliveries/" + id, e, a, operation); }
}
