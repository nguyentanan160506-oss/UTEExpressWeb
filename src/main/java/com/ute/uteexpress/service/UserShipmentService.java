package com.ute.uteexpress.service;

import com.ute.uteexpress.dto.shipment.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

@Service
@Validated
@PreAuthorize("hasRole('USER')")
@Transactional(readOnly = true)
public class UserShipmentService {
    private final UserRepository users;
    private final UserRoleRepository roles;
    private final ShipmentRepository shipments;
    private final ShipmentEventRepository events;
    private final BranchRepository branches;
    private final ShippingServiceRepository services;
    public UserShipmentService(UserRepository users, UserRoleRepository roles, ShipmentRepository shipments,
            ShipmentEventRepository events, BranchRepository branches, ShippingServiceRepository services) {
        this.users = users; this.roles = roles; this.shipments = shipments; this.events = events;
        this.branches = branches; this.services = services;
    }

    public ShipmentOptions options() {
        requireUser();
        return new ShipmentOptions(branches.findByStatusOrderByNameAsc(BranchStatus.ACTIVE).stream()
            .map(branch -> new ShipmentOptions.BranchOption(branch.getId(), branch.getName(), branch.getProvince())).toList(),
            services.findByIsActiveTrueOrderByNameAsc().stream()
                .map(service -> new ShipmentOptions.ServiceOption(service.getId(), service.getName())).toList());
    }

    public Page<ShipmentSummary> list(@Size(max = 50) String q, ShipmentStatus status, Pageable requested) {
        User owner = requireUser();
        PageRequest page = PageRequest.of(Math.max(0, requested.getPageNumber()),
            Math.max(1, Math.min(100, requested.getPageSize())),
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return shipments.findAll(ownedSearch(owner.getId(), q, status), page)
            .map(shipment -> new ShipmentSummary(shipment.getId(), shipment.getTrackingNumber(),
                shipment.getReceiverName(), shipment.getCreatedAt(), shipment.getStatus()));
    }

    public ShipmentDetailView detail(@Positive Long id, @Min(0) @Max(1000000) int eventPage) {
        User owner = requireUser();
        Shipment shipment = shipments.findByIdAndSenderId(id, owner.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy vận đơn."));
        var timeline = events.findByShipmentIdAndShipmentSenderId(id, owner.getId(),
            PageRequest.of(eventPage, 20, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))))
            .map(event -> new ShipmentDetailView.EventView(event.getEventTitle(), event.getDescription(),
                event.getLocationText(), event.getToStatus(), event.getOccurredAt()));
        return new ShipmentDetailView(shipment.getId(), shipment.getTrackingNumber(),
            new ShipmentDetailView.Contact(shipment.getSenderName(), shipment.getSenderPhone(),
                shipment.getSenderProvince(), shipment.getSenderDistrict(), shipment.getSenderWard(), shipment.getSenderAddress()),
            new ShipmentDetailView.Contact(shipment.getReceiverName(), shipment.getReceiverPhone(),
                shipment.getReceiverProvince(), shipment.getReceiverDistrict(), shipment.getReceiverWard(), shipment.getReceiverAddress()),
            shipment.getOriginBranch().getName(), shipment.getDestinationBranch().getName(),
            shipment.getCurrentBranch() == null ? null : shipment.getCurrentBranch().getName(), shipment.getService().getName(),
            shipment.getPackageDescription(), shipment.getWeightKg(), shipment.getLengthCm(), shipment.getWidthCm(),
            shipment.getHeightCm(), shipment.getCodAmount(), shipment.getDeclaredValue(), shipment.getShippingFee(),
            shipment.getInsuranceFee(), shipment.getTotalFee(), shipment.getPaymentPayer(), shipment.getPaymentStatus(),
            shipment.getStatus(), shipment.getNote(), shipment.getCreatedAt(), shipment.getUpdatedAt(), timeline,
            shipment.getShipmentMethod(), shipment.getPickupFee(), com.ute.uteexpress.dto.pricing.PriceBreakdown.of(shipment.getPriceSnapshot()));
    }

    private User requireUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream().noneMatch(role -> "ROLE_USER".equals(role.getAuthority()))) {
            throw new AccessDeniedException("Forbidden");
        }
        User owner = users.findByEmail(authentication.getName())
            .orElseThrow(() -> new AccessDeniedException("Forbidden"));
        if (owner.getStatus() != UserStatus.ACTIVE
                || !roles.findRoleNamesByUserId(owner.getId()).contains("ROLE_USER")) {
            throw new AccessDeniedException("Forbidden");
        }
        return owner;
    }

    static Specification<Shipment> ownedSearch(Long ownerId, String search, ShipmentStatus status) {
        return (root, query, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("sender").get("id"), ownerId));
            if (status != null) { predicates.add(builder.equal(root.get("status"), status)); }
            if (search != null && !search.isBlank()) {
                String text = search.strip().toUpperCase(Locale.ROOT).replace("\\", "\\\\")
                    .replace("%", "\\%").replace("_", "\\_").replace("[", "\\[");
                predicates.add(builder.like(builder.upper(root.get("trackingNumber")), "%" + text + "%", '\\'));
            }
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
