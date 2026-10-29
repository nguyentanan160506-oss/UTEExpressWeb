package com.ute.uteexpress.service;

import com.ute.uteexpress.dto.shipper.PickupViews.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.OriginHandoffRepository;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

@Service @Validated @Transactional(readOnly = true) @PreAuthorize("hasRole('MANAGER')")
public class ManagerHandoffService {
    private final LogisticsAccessService access;
    private final OriginHandoffRepository handoffs;
    private final com.ute.uteexpress.repository.ShipmentIntakeRepository intakes;
    public ManagerHandoffService(LogisticsAccessService access, OriginHandoffRepository handoffs,
            com.ute.uteexpress.repository.ShipmentIntakeRepository intakes) {
        this.access = access; this.handoffs = handoffs; this.intakes = intakes;
    }
    public Page<Handoff> pending(@Min(0) @Max(1000000) int page) {
        User actor = access.requireManager();
        return handoffs.findManaged(actor.getId(), HandoffStatus.PENDING,
            PageRequest.of(page, 20, Sort.by(Sort.Order.asc("requestedAt"), Sort.Order.asc("id"))))
            .map(ShipperPickupService::handoff);
    }
    public Review detail(@NotNull @Positive Long id) {
        access.requireManager();
        OriginHandoff h = handoffs.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bàn giao."));
        User actor = access.requireManagerForBranch(h.getBranch().getId());
        boolean canReview = h.getStatus() == HandoffStatus.PENDING && !actor.getId().equals(h.getRequestedBy().getId());
        if (canReview) {
            try { canReview = !access.requireIntakeEmployee(h.getBranch().getId()).getId().equals(h.getEmployee().getId()); }
            catch (AccessDeniedException denied) { canReview = false; }
        }
        return new Review(ShipperPickupService.handoff(h), ShipperPickupService.proof(h.getEvidence()),
            h.getShipment().getId(), h.getShipment().getVersion(), canReview,
            intakes.findByShipmentId(h.getShipment().getId()).map(ShipmentIntake::getReceiptReference).orElse(null));
    }
}
