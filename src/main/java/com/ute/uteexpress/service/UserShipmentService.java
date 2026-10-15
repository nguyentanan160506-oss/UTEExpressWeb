package com.ute.uteexpress.service;

import com.ute.uteexpress.dto.shipment.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
    private final ShippingRateRuleRepository rates;
    private final TrackingNumberGenerator tracking;
    private final Clock clock;
    private final Validator validator;

    public UserShipmentService(UserRepository users, UserRoleRepository roles, ShipmentRepository shipments,
            ShipmentEventRepository events, BranchRepository branches, ShippingServiceRepository services,
            ShippingRateRuleRepository rates, TrackingNumberGenerator tracking, Clock clock, Validator validator) {
        this.users = users; this.roles = roles; this.shipments = shipments; this.events = events;
        this.branches = branches; this.services = services; this.rates = rates;
        this.tracking = tracking; this.clock = clock; this.validator = validator;
    }

    public ShipmentOptions options() {
        requireUser();
        return new ShipmentOptions(branches.findByStatusOrderByNameAsc(BranchStatus.ACTIVE).stream()
            .map(branch -> new ShipmentOptions.BranchOption(branch.getId(), branch.getName(), branch.getProvince())).toList(),
            services.findByIsActiveTrueOrderByNameAsc().stream()
                .map(service -> new ShipmentOptions.ServiceOption(service.getId(), service.getName())).toList());
    }

    @Transactional
    public Long create(@NotNull @Valid ShipmentCreateForm form) {
        User owner = requireUser();
        var violations = validator.validate(form);
        if (!violations.isEmpty()) { throw new ConstraintViolationException(violations); }
        Branch origin = branch(form.getOriginBranchId(), form.getSenderProvince(), "originBranchId");
        Branch destination = branch(form.getDestinationBranchId(), form.getReceiverProvince(), "destinationBranchId");
        ShippingService service = services.findByIdAndIsActiveTrue(form.getServiceId())
            .orElseThrow(() -> new ShipmentCreationException("serviceId", "Dịch vụ không tồn tại hoặc đã ngừng hoạt động."));
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        RateScopeType scope = form.getSenderProvince().equalsIgnoreCase(form.getReceiverProvince())
            ? RateScopeType.INTRA_PROVINCE : RateScopeType.INTER_PROVINCE;
        List<ShippingRateRule> effective = rates.findEffectiveRules(service.getId(), scope, now);
        if (effective.isEmpty()) {
            throw new ShipmentCreationException("serviceId", "Chưa có bảng giá đang hiệu lực phù hợp cho dịch vụ và tuyến này. Vui lòng chọn dịch vụ khác hoặc liên hệ hỗ trợ.");
        }
        if (effective.size() != 1) {
            throw new ShipmentCreationException("serviceId", "Có nhiều bảng giá cùng hiệu lực. Vui lòng liên hệ hỗ trợ để kiểm tra.");
        }
        BigDecimal fee = shippingFee(effective.getFirst(), form.getWeightKg());
        Shipment shipment = new Shipment();
        shipment.setTrackingNumber(tracking.next());
        shipment.setSender(owner);
        shipment.setSenderName(form.getSenderName()); shipment.setSenderPhone(form.getSenderPhone());
        shipment.setSenderProvince(form.getSenderProvince()); shipment.setSenderDistrict(form.getSenderDistrict());
        shipment.setSenderWard(form.getSenderWard()); shipment.setSenderAddress(form.getSenderAddress());
        shipment.setReceiverName(form.getReceiverName()); shipment.setReceiverPhone(form.getReceiverPhone());
        shipment.setReceiverProvince(form.getReceiverProvince()); shipment.setReceiverDistrict(form.getReceiverDistrict());
        shipment.setReceiverWard(form.getReceiverWard()); shipment.setReceiverAddress(form.getReceiverAddress());
        shipment.setOriginBranch(origin); shipment.setDestinationBranch(destination);
        // A new request has not physically arrived at a branch; do not invent a current location.
        shipment.setService(service); shipment.setPackageDescription(form.getPackageDescription());
        shipment.setWeightKg(form.getWeightKg()); shipment.setLengthCm(form.getLengthCm());
        shipment.setWidthCm(form.getWidthCm()); shipment.setHeightCm(form.getHeightCm());
        shipment.setCodAmount(form.getCodAmount()); shipment.setDeclaredValue(form.getDeclaredValue());
        shipment.setShippingFee(fee);
        // No insurance product or insurance pricing rule exists in this flow; retain the schema default.
        shipment.setTotalFee(fee.add(shipment.getInsuranceFee()));
        shipment.setStatus(ShipmentStatus.CREATED); shipment.setPaymentStatus(ShipmentPaymentStatus.UNPAID);
        shipment.setPaymentPayer(form.getPaymentPayer()); shipment.setNote(form.getNote());
        shipment.setCreatedAt(now); shipment.setUpdatedAt(now);
        // The existing SQL UNIQUE constraint is the final protection against concurrent tracking collisions.
        Shipment saved = shipments.saveAndFlush(shipment);
        ShipmentEvent event = new ShipmentEvent();
        event.setShipment(saved); event.setActor(owner); event.setToStatus(ShipmentStatus.CREATED);
        event.setEventTitle("Đã tạo vận đơn"); event.setOccurredAt(now);
        events.saveAndFlush(event);
        return saved.getId();
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
            shipment.getStatus(), shipment.getNote(), shipment.getCreatedAt(), shipment.getUpdatedAt(), timeline);
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

    private Branch branch(Long id, String province, String field) {
        Branch branch = branches.findByIdAndStatus(id, BranchStatus.ACTIVE)
            .orElseThrow(() -> new ShipmentCreationException(field, "Chi nhánh không tồn tại hoặc đã ngừng hoạt động."));
        if (!branch.getProvince().strip().equalsIgnoreCase(province)) {
            throw new ShipmentCreationException(field, "Vui lòng chọn chi nhánh thuộc tỉnh/thành đã nhập.");
        }
        return branch;
    }

    private BigDecimal shippingFee(ShippingRateRule rule, BigDecimal weight) {
        BigDecimal extra = weight.subtract(rule.getBaseWeightKg()).max(BigDecimal.ZERO);
        BigDecimal steps = extra.divide(rule.getExtraWeightStepKg(), 0, RoundingMode.CEILING);
        BigDecimal fee = rule.getBasePrice().add(steps.multiply(rule.getExtraPricePerStep())).setScale(2);
        if (fee.compareTo(new BigDecimal("9999999999999999.99")) > 0) {
            throw new ShipmentCreationException("weightKg", "Phí vượt giới hạn lưu trữ. Vui lòng liên hệ hỗ trợ.");
        }
        return fee;
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
