package com.ute.uteexpress.controller;

import com.ute.uteexpress.dto.delivery.*;
import com.ute.uteexpress.dto.shipment.ShipmentStatusLabels;
import com.ute.uteexpress.enums.ShipmentStatus;
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

@Controller @RequestMapping("/manager/deliveries") @PreAuthorize("hasRole('MANAGER')")
public class ManagerDeliveryController {
    private final DeliveryReadService read;
    private final LastMileService delivery;
    public ManagerDeliveryController(DeliveryReadService read, LastMileService delivery) { this.read = read; this.delivery = delivery; }
    @InitBinder("assignForm") void assign(WebDataBinder b) { b.setAllowedFields("shipmentId", "expectedVersion", "employeeId", "replacesAssignmentId", "reason", "_csrf"); }
    @InitBinder("prepareForm") void prepare(WebDataBinder b) { b.setAllowedFields("shipmentId", "expectedVersion", "assignmentId", "confirmed", "reference", "notes", "_csrf"); }
    @InitBinder("reviewForm") void review(WebDataBinder b) { b.setAllowedFields("shipmentId", "expectedVersion", "recoveryId", "confirmed", "reference", "reason", "_csrf"); }
    @InitBinder("decisionForm") void decision(WebDataBinder b) { b.setAllowedFields("shipmentId", "expectedVersion", "employeeId", "reason", "_csrf"); }
    @InitBinder("senderForm") void sender(WebDataBinder b) { b.setAllowedFields("shipmentId", "expectedVersion", "confirmed", "reference", "notes", "recipientName", "recipientType", "_csrf"); }
    @ModelAttribute void labels(Model m) { m.addAttribute("statusLabels", ShipmentStatusLabels.VALUES); m.addAttribute("dispatchLabels", DeliveryLabels.DISPATCH); m.addAttribute("failureLabels", DeliveryLabels.FAILURE); m.addAttribute("returnLabels", DeliveryLabels.RETURN); }
    @GetMapping({"", "/"}) String list(@RequestParam(required = false) ShipmentStatus status, @RequestParam(defaultValue = "") @Size(max = 50) String tracking,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page, Model m) {
        m.addAttribute("shipments", read.managerList(status, tracking, page)); m.addAttribute("selectedStatus", status); m.addAttribute("tracking", tracking); return "delivery/manager-list";
    }
    @GetMapping("/{shipmentId}") String detail(@PathVariable @Positive Long shipmentId, @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page, Model m) {
        m.addAttribute("detail", read.managerDetail(shipmentId, page)); return "delivery/manager-detail";
    }
    @PostMapping({"/{shipmentId}/assign", "/{shipmentId}/reassign"}) String assign(@PathVariable @Positive Long shipmentId,
            @Valid @ModelAttribute("assignForm") DeliveryForms.Assign f, BindingResult e, RedirectAttributes a) {
        return mutate(shipmentId, e, a, () -> delivery.assign(new DeliveryCommands.Assign(shipmentId, f.getExpectedVersion(), f.getEmployeeId(), f.getReplacesAssignmentId(), f.getReason())));
    }
    @PostMapping("/{shipmentId}/handoff") String handoff(@PathVariable @Positive Long shipmentId, @Valid @ModelAttribute("prepareForm") DeliveryForms.Prepare f, BindingResult e, RedirectAttributes a) {
        return mutate(shipmentId, e, a, () -> delivery.prepare(new DeliveryCommands.Prepare(shipmentId, f.getExpectedVersion(), f.getAssignmentId(), f.isConfirmed(), f.getReference(), f.getNotes())));
    }
    @PostMapping("/{shipmentId}/redelivery") String retry(@PathVariable @Positive Long shipmentId, @Valid @ModelAttribute("decisionForm") DeliveryForms.Decision f, BindingResult e, RedirectAttributes a) {
        return mutate(shipmentId, e, a, () -> delivery.redelivery(new DeliveryCommands.Redelivery(shipmentId, f.getExpectedVersion(), f.getEmployeeId(), f.getReason())));
    }
    @PostMapping("/{shipmentId}/return") String initiateReturn(@PathVariable @Positive Long shipmentId, @Valid @ModelAttribute("decisionForm") DeliveryForms.Decision f, BindingResult e, RedirectAttributes a) {
        return mutate(shipmentId, e, a, () -> delivery.initiateReturn(new DeliveryCommands.ReturnStart(shipmentId, f.getExpectedVersion(), f.getReason())));
    }
    @PostMapping("/{shipmentId}/recoveries/accept") String receive(@PathVariable @Positive Long shipmentId, @Valid @ModelAttribute("reviewForm") DeliveryForms.Review f, BindingResult e, RedirectAttributes a) {
        return review(shipmentId, true, f, e, a);
    }
    @PostMapping("/{shipmentId}/recoveries/reject") String reject(@PathVariable @Positive Long shipmentId, @Valid @ModelAttribute("reviewForm") DeliveryForms.Review f, BindingResult e, RedirectAttributes a) {
        return review(shipmentId, false, f, e, a);
    }
    @PostMapping("/{shipmentId}/return-to-sender") String sender(@PathVariable @Positive Long shipmentId, @Valid @ModelAttribute("senderForm") DeliveryForms.Sender f, BindingResult e, RedirectAttributes a) {
        return mutate(shipmentId, e, a, () -> delivery.returnToSender(new DeliveryCommands.SenderReceipt(shipmentId, f.getExpectedVersion(), f.isConfirmed(), f.getRecipientType(), f.getRecipientName(), f.getReference(), f.getNotes())));
    }
    private String review(Long id, boolean accept, DeliveryForms.Review f, BindingResult e, RedirectAttributes a) { return mutate(id, e, a,
        () -> delivery.reviewRecovery(new DeliveryCommands.ReviewRecovery(id, f.getExpectedVersion(), f.getRecoveryId(), accept, f.isConfirmed(), f.getReference(), f.getReason()))); }
    private String mutate(Long id, BindingResult e, RedirectAttributes a, Runnable operation) { return ShipperPickupController.mutate("/manager/deliveries/" + id, e, a, operation); }
}
