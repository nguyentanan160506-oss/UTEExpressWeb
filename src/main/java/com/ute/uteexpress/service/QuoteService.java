package com.ute.uteexpress.service;

import com.ute.uteexpress.dto.shipment.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

@Service @Validated
@PreAuthorize("hasRole('USER')")
@Transactional(readOnly = true)
public class QuoteService {
    private static final BigDecimal MAX_MONEY = new BigDecimal("9999999999999999.99");
    private final UserRepository users;
    private final UserRoleRepository roles;
    private final ShipmentQuoteRepository quotes;
    private final ShipmentRepository shipments;
    private final ShipmentEventRepository events;
    private final BranchRepository branches;
    private final ShippingServiceRepository services;
    private final ShippingRateRuleRepository rates;
    private final PickupFeeRuleRepository pickups;
    private final BranchNetworkService network;
    private final TrackingNumberGenerator tracking;
    private final Clock clock;
    private final Validator validator;
    private final Duration validity;

    public QuoteService(UserRepository users, UserRoleRepository roles, ShipmentQuoteRepository quotes,
            ShipmentRepository shipments, ShipmentEventRepository events, BranchRepository branches,
            ShippingServiceRepository services, ShippingRateRuleRepository rates, PickupFeeRuleRepository pickups,
            BranchNetworkService network, TrackingNumberGenerator tracking, Clock clock, Validator validator,
            @Value("${uteexpress.quotes.validity:PT15M}") Duration validity) {
        if (validity.isNegative() || validity.isZero() || validity.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalArgumentException("Quote validity must be positive and at most 24 hours");
        }
        this.users = users; this.roles = roles; this.quotes = quotes; this.shipments = shipments;
        this.events = events; this.branches = branches; this.services = services; this.rates = rates;
        this.pickups = pickups; this.network = network; this.tracking = tracking; this.clock = clock;
        this.validator = validator; this.validity = validity;
    }

    public record BranchChoices(List<ShipmentOptions.BranchOption> origins, List<ShipmentOptions.BranchOption> destinations) { }

    public BranchChoices branchChoices(@NotNull ShipmentCreateForm form) {
        requireUser();
        if (form.getShipmentMethod() == null) { throw error("shipmentMethod", "Vui lòng chọn phương thức gửi hàng."); }
        for (String[] part : new String[][] {
                {"senderProvince", form.getSenderProvince()}, {"senderDistrict", form.getSenderDistrict()},
                {"senderWard", form.getSenderWard()}, {"receiverProvince", form.getReceiverProvince()},
                {"receiverDistrict", form.getReceiverDistrict()}, {"receiverWard", form.getReceiverWard()}}) {
            if (part[1] == null || part[1].isBlank() || part[1].length() > 100) {
                throw error(part[0], "Vui lòng nhập tỉnh, huyện, xã hợp lệ trước khi tìm chi nhánh.");
            }
        }
        return choices(form);
    }

    @Transactional
    public String quote(@NotNull @Valid ShipmentCreateForm form) {
        User owner = requireUser();
        var violations = validator.validate(form);
        if (!violations.isEmpty()) { throw new ConstraintViolationException(violations); }
        BranchChoices choices = choices(form);
        Long originId = form.getOriginBranchId();
        if (form.getShipmentMethod() == ShipmentMethod.HOME_PICKUP && originId == null && choices.origins().size() == 1) {
            originId = choices.origins().getFirst().id();
        }
        requireChoice(originId, choices.origins(), "originBranchId",
            "Không xác định được chi nhánh nguồn duy nhất. Vui lòng chọn chi nhánh trong phạm vi phục vụ hợp lệ.");
        requireChoice(form.getDestinationBranchId(), choices.destinations(), "destinationBranchId",
            "Chi nhánh đích không phục vụ địa chỉ nhận hoặc chưa được cấu hình giao đến.");
        Branch origin = activeBranch(originId, "originBranchId");
        Branch destination = activeBranch(form.getDestinationBranchId(), "destinationBranchId");
        Branch originHub = activeBranch(hub(originId, "originBranchId"), "originBranchId");
        Branch destinationHub = activeBranch(hub(destination.getId(), "destinationBranchId"), "destinationBranchId");
        ShippingService service = activeService(form.getServiceId());
        LocalDateTime now = now();
        RateScopeType scope = form.getSenderProvince().equalsIgnoreCase(form.getReceiverProvince())
            ? RateScopeType.INTRA_PROVINCE : RateScopeType.INTER_PROVINCE;
        List<ShippingRateRule> effective = rates.findEffectiveRules(service.getId(), scope, now);
        if (effective.isEmpty()) { throw error("serviceId", "Chưa có bảng giá đang hiệu lực phù hợp cho dịch vụ và tuyến này."); }
        if (effective.size() != 1) { throw error("serviceId", "Có nhiều bảng giá cùng hiệu lực. Vui lòng liên hệ hỗ trợ."); }
        ShippingRateRule rate = effective.getFirst();
        BigDecimal shipping = shippingFee(rate, form.getWeightKg());
        PickupFeeRule pickup = null;
        BigDecimal pickupFee = new BigDecimal("0.00"); // BRANCH_DROPOFF has no pickup service.
        LocalDateTime expires = earlier(now.plus(validity), rate.getEffectiveTo());
        if (form.getShipmentMethod() == ShipmentMethod.HOME_PICKUP) {
            var rules = pickups.findEffectiveRules(originId, service.getId(), now);
            if (rules.size() != 1) { throw error("shipmentMethod", "Chưa có đúng một bảng phụ phí lấy hàng đang hiệu lực cho chi nhánh và dịch vụ này."); }
            pickup = rules.getFirst();
            if (pickup.getAmount() == null || pickup.getAmount().signum() <= 0) {
                throw error("shipmentMethod", "Bảng phụ phí lấy hàng không hợp lệ.");
            }
            pickupFee = money(pickup.getAmount());
            expires = earlier(expires, pickup.getEffectiveTo());
        }
        if (!expires.isAfter(now)) { throw error("serviceId", "Bảng giá đã hết hiệu lực. Vui lòng báo giá lại."); }
        ShipmentQuote quote = new ShipmentQuote();
        ShipmentQuoteInputs.capture(form, quote);
        quote.setToken(UUID.randomUUID().toString().replace("-", "")); quote.setRequester(owner);
        quote.setShipmentMethod(form.getShipmentMethod()); quote.setOriginBranch(origin); quote.setDestinationBranch(destination);
        quote.setOriginHub(originHub); quote.setDestinationHub(destinationHub); quote.setService(service);
        quote.setShippingRateRule(rate); quote.setPickupFeeRule(pickup);
        quote.setShippingFee(shipping); quote.setPickupFee(pickupFee);
        // No insurance product/rule exists: no insurance service is sold in this flow.
        quote.setInsuranceFee(new BigDecimal("0.00")); quote.setTotalFee(money(shipping.add(pickupFee)));
        quote.setCreatedAt(now); quote.setExpiresAt(expires); quotes.saveAndFlush(quote);
        return quote.getToken();
    }

    public ShipmentQuoteView review(@NotNull @Pattern(regexp = "[a-f0-9]{32}") String token) {
        User owner = requireUser();
        ShipmentQuote q = quotes.findByTokenAndRequesterId(token, owner.getId()).orElseThrow(this::notFound);
        return new ShipmentQuoteView(q.getToken(), q.getShipmentMethod(),
            new ShipmentDetailView.Contact(q.getSenderName(), q.getSenderPhone(), q.getSenderProvince(), q.getSenderDistrict(), q.getSenderWard(), q.getSenderAddress()),
            new ShipmentDetailView.Contact(q.getReceiverName(), q.getReceiverPhone(), q.getReceiverProvince(), q.getReceiverDistrict(), q.getReceiverWard(), q.getReceiverAddress()),
            q.getOriginBranch().getName(), q.getDestinationBranch().getName(), q.getService().getName(),
            q.getPackageDescription(), q.getWeightKg(), q.getLengthCm(), q.getWidthCm(), q.getHeightCm(), q.getCodAmount(),
            q.getDeclaredValue(), q.getPaymentPayer(), q.getNote(), q.getShippingFee(), q.getPickupFee(), q.getInsuranceFee(),
            q.getTotalFee(), q.getExpiresAt(), q.getConsumedAt() == null && now().isBefore(q.getExpiresAt()), q.getConsumedAt() != null);
    }

    @Transactional
    public Long confirm(@NotNull @Pattern(regexp = "[a-f0-9]{32}") String token) {
        User owner = requireUser();
        // Serializes confirmations across all application instances, not just one HTTP session.
        ShipmentQuote quote = quotes.findOwnedForUpdate(token, owner.getId()).orElseThrow(this::notFound);
        if (quote.getConsumedAt() != null) {
            return shipments.findByQuoteIdAndSenderId(quote.getId(), owner.getId()).map(Shipment::getId)
                .orElseThrow(() -> new IllegalStateException("Consumed quote has no shipment"));
        }
        LocalDateTime now = now();
        if (!now.isBefore(quote.getExpiresAt())) { throw error("shipmentMethod", "Báo giá đã hết hạn. Vui lòng lấy báo giá mới."); }
        // Honor stored prices until expiry, but do not use a disabled service or invalid route.
        activeService(quote.getService().getId());
        BranchChoices choices = choices(ShipmentQuoteInputs.form(quote));
        requireChoice(quote.getOriginBranch().getId(), choices.origins(), "originBranchId", "Chi nhánh nguồn không còn phục vụ địa chỉ gửi.");
        requireChoice(quote.getDestinationBranch().getId(), choices.destinations(), "destinationBranchId", "Chi nhánh đích không còn phục vụ địa chỉ nhận.");
        if (!Objects.equals(hub(quote.getOriginBranch().getId(), "originBranchId"), quote.getOriginHub().getId())
                || !Objects.equals(hub(quote.getDestinationBranch().getId(), "destinationBranchId"), quote.getDestinationHub().getId())) {
            throw error("originBranchId", "Mạng lưới đã thay đổi. Vui lòng lấy báo giá mới.");
        }
        Shipment shipment = new Shipment(); ShipmentQuoteInputs.apply(quote, shipment);
        shipment.setTrackingNumber(tracking.next()); shipment.setSender(owner); shipment.setQuote(quote);
        shipment.setShipmentMethod(quote.getShipmentMethod()); shipment.setOriginBranch(quote.getOriginBranch());
        shipment.setDestinationBranch(quote.getDestinationBranch()); shipment.setOriginHub(quote.getOriginHub());
        shipment.setDestinationHub(quote.getDestinationHub()); shipment.setService(quote.getService());
        shipment.setShippingFee(quote.getShippingFee()); shipment.setPickupFee(quote.getPickupFee());
        shipment.setInsuranceFee(quote.getInsuranceFee()); shipment.setTotalFee(quote.getTotalFee());
        shipment.setStatus(ShipmentStatus.CREATED); shipment.setPaymentStatus(ShipmentPaymentStatus.UNPAID);
        shipment.setCreatedAt(now); shipment.setUpdatedAt(now);
        // No current branch or physical event is inferred from the selected route.
        shipments.saveAndFlush(shipment);
        ShipmentEvent event = new ShipmentEvent(); event.setShipment(shipment); event.setActor(owner);
        event.setToStatus(ShipmentStatus.CREATED); event.setEventTitle("Đã tạo vận đơn"); event.setOccurredAt(now);
        events.saveAndFlush(event);
        quote.setConsumedAt(now); quotes.saveAndFlush(quote);
        return shipment.getId();
    }

    private BranchChoices choices(ShipmentCreateForm form) {
        List<ShipmentOptions.BranchOption> origins;
        List<ShipmentOptions.BranchOption> destinations;
        try { origins = network.servingBranches(form.getSenderProvince(), form.getSenderDistrict(), form.getSenderWard(), form.getShipmentMethod()); }
        catch (LogisticsRuleException failure) { throw error("originBranchId", failure.getMessage()); }
        try { destinations = network.deliveryBranches(form.getReceiverProvince(), form.getReceiverDistrict(), form.getReceiverWard()); }
        catch (LogisticsRuleException failure) { throw error("destinationBranchId", failure.getMessage()); }
        return new BranchChoices(origins, destinations);
    }
    private void requireChoice(Long id, List<ShipmentOptions.BranchOption> choices, String field, String message) {
        if (id == null || choices.stream().noneMatch(option -> id.equals(option.id()))) { throw error(field, message); }
    }
    private Long hub(Long id, String field) {
        try { return network.configuredHub(id); }
        catch (LogisticsRuleException failure) { throw error(field, failure.getMessage()); }
    }
    private Branch activeBranch(Long id, String field) {
        return branches.findByIdAndStatus(id, BranchStatus.ACTIVE)
            .orElseThrow(() -> error(field, "Chi nhánh không tồn tại hoặc đã ngừng hoạt động."));
    }
    private ShippingService activeService(Long id) {
        return services.findByIdAndIsActiveTrue(id)
            .orElseThrow(() -> error("serviceId", "Dịch vụ không tồn tại hoặc đã ngừng hoạt động."));
    }
    private BigDecimal shippingFee(ShippingRateRule rule, BigDecimal weight) {
        if (rule.getBaseWeightKg() == null || rule.getBaseWeightKg().signum() <= 0
                || rule.getExtraWeightStepKg() == null || rule.getExtraWeightStepKg().signum() <= 0
                || rule.getBasePrice() == null || rule.getBasePrice().signum() < 0
                || rule.getExtraPricePerStep() == null || rule.getExtraPricePerStep().signum() < 0) {
            throw error("serviceId", "Bảng giá vận chuyển không hợp lệ.");
        }
        BigDecimal steps = weight.subtract(rule.getBaseWeightKg()).max(BigDecimal.ZERO)
            .divide(rule.getExtraWeightStepKg(), 0, RoundingMode.CEILING);
        return money(rule.getBasePrice().add(steps.multiply(rule.getExtraPricePerStep())));
    }
    private BigDecimal money(BigDecimal value) {
        if (value.signum() < 0 || value.compareTo(MAX_MONEY) > 0) {
            throw error("weightKg", "Phí vượt giới hạn lưu trữ. Vui lòng liên hệ hỗ trợ.");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }
    private LocalDateTime earlier(LocalDateTime one, LocalDateTime two) { return two != null && two.isBefore(one) ? two : one; }
    private LocalDateTime now() {
        LocalDateTime time = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return time.withNano(time.getNano() / 100 * 100);
    }
    private User requireUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getAuthorities().stream()
                .noneMatch(role -> "ROLE_USER".equals(role.getAuthority()))) { throw new AccessDeniedException("Forbidden"); }
        User owner = users.findByEmail(authentication.getName()).orElseThrow(() -> new AccessDeniedException("Forbidden"));
        if (owner.getStatus() != UserStatus.ACTIVE || !roles.findRoleNamesByUserId(owner.getId()).contains("ROLE_USER")) {
            throw new AccessDeniedException("Forbidden");
        }
        return owner;
    }
    private ShipmentCreationException error(String field, String message) { return new ShipmentCreationException(field, message); }
    private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy báo giá."); }
}
