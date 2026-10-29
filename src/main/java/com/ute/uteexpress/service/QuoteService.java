package com.ute.uteexpress.service;

import com.ute.uteexpress.dto.shipment.*;
import com.ute.uteexpress.dto.pricing.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import jakarta.validation.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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
    private final EntityManager entityManager;
    private final PricingVersionRepository versions;

    public QuoteService(UserRepository users, UserRoleRepository roles, ShipmentQuoteRepository quotes,
            ShipmentRepository shipments, ShipmentEventRepository events, BranchRepository branches,
            ShippingServiceRepository services, ShippingRateRuleRepository rates, PickupFeeRuleRepository pickups,
            BranchNetworkService network, TrackingNumberGenerator tracking, Clock clock, Validator validator,
            @Value("${uteexpress.quotes.validity:PT15M}") Duration validity, EntityManager entityManager, PricingVersionRepository versions) {
        if (validity.isNegative() || validity.isZero() || validity.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalArgumentException("Quote validity must be positive and at most 24 hours");
        }
        this.users = users; this.roles = roles; this.quotes = quotes; this.shipments = shipments;
        this.events = events; this.branches = branches; this.services = services; this.rates = rates;
        this.pickups = pickups; this.network = network; this.tracking = tracking; this.clock = clock;
        this.validator = validator; this.validity = validity; this.entityManager = entityManager; this.versions = versions;
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
        Route route = route(form);
        Branch origin = route.origin(), destination = route.destination(), originHub = route.originHub(), destinationHub = route.destinationHub();
        ShippingService service = activeService(form.getServiceId());
        requireUser(); // Refresh account/roles after any wait for the pricing gate.
        LocalDateTime now = now();
        RateScopeType scope = ProvinceCatalog.classify(form.getSenderProvince(), form.getReceiverProvince());
        conditions(service, form);
        Price price = price(service, scope, form.getShipmentMethod(), origin.getId(), form.getWeightKg(), now);
        ShippingRateRule rate = price.rate(); PickupFeeRule pickup = price.pickup();
        BigDecimal shipping = price.shipping(), pickupFee = price.pickupFee(); LocalDateTime expires = price.expires();
        ShipmentQuote quote = new ShipmentQuote();
        ShipmentQuoteInputs.capture(form, quote);
        quote.setPriceSnapshot(snapshot(form, rate, shipping));
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
            q.getTotalFee(), q.getExpiresAt(), q.getConsumedAt() == null && now().isBefore(q.getExpiresAt()), q.getConsumedAt() != null,
            PriceBreakdown.of(q.getPriceSnapshot()));
    }

    @Transactional
    public Long confirm(@NotNull @Pattern(regexp = "[a-f0-9]{32}") String token) {
        User owner = requireUser();
        // Serializes confirmations across all application instances, not just one HTTP session.
        ShipmentQuote quote = quotes.findOwnedForUpdate(token, owner.getId()).orElseThrow(this::notFound);
        requireUser();
        if (quote.getConsumedAt() != null) {
            return shipments.findByQuoteIdAndSenderId(quote.getId(), owner.getId()).map(Shipment::getId)
                .orElseThrow(() -> new IllegalStateException("Consumed quote has no shipment"));
        }
        LocalDateTime now = now();
        if (!now.isBefore(quote.getExpiresAt())) { throw error("shipmentMethod", "Báo giá đã hết hạn. Vui lòng lấy báo giá mới."); }
        // Honor stored prices until expiry, but do not use a disabled service or invalid route.
        ShippingService currentService = activeService(quote.getService().getId());
        requireUser();
        now = now(); // Recheck after waiting for the catalog lock.
        if (!now.isBefore(quote.getExpiresAt())) { throw error("shipmentMethod", "Báo giá đã hết hạn trong lúc chờ. Vui lòng lấy báo giá mới."); }
        conditions(currentService, ShipmentQuoteInputs.form(quote));
        BranchChoices choices = choices(ShipmentQuoteInputs.form(quote));
        requireChoice(quote.getOriginBranch().getId(), choices.origins(), "originBranchId", "Chi nhánh nguồn không còn phục vụ địa chỉ gửi.");
        requireChoice(quote.getDestinationBranch().getId(), choices.destinations(), "destinationBranchId", "Chi nhánh đích không còn phục vụ địa chỉ nhận.");
        if (!Objects.equals(hub(quote.getOriginBranch().getId(), "originBranchId"), quote.getOriginHub().getId())
                || !Objects.equals(hub(quote.getDestinationBranch().getId(), "destinationBranchId"), quote.getDestinationHub().getId())) {
            throw error("originBranchId", "Mạng lưới đã thay đổi. Vui lòng lấy báo giá mới.");
        }
        Shipment shipment = new Shipment(); ShipmentQuoteInputs.apply(quote, shipment);
        verifyStoredPrice(quote);
        shipment.setPriceSnapshot(quote.getPriceSnapshot() == null ? null : quote.getPriceSnapshot().copy());
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

    private record Route(Branch origin, Branch destination, Branch originHub, Branch destinationHub) { }
    private record Price(ShippingRateRule rate, PickupFeeRule pickup, BigDecimal shipping, BigDecimal pickupFee, LocalDateTime expires) { }
    private Route route(ShipmentCreateForm form) {
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
        return new Route(origin, destination, originHub, destinationHub);
    }
    private Price price(ShippingService service, RateScopeType scope, ShipmentMethod method, Long originId, BigDecimal weight, LocalDateTime now) {
        return price(service, scope, method, originId, weight, now, null, null);
    }
    private Price price(ShippingService service, RateScopeType scope, ShipmentMethod method, Long originId, BigDecimal weight, LocalDateTime now,
            ShippingRateRule shippingOverride, PickupFeeRule pickupOverride) {
        List<ShippingRateRule> effective = shippingOverride == null ? rates.findEffectiveRules(service.getId(), scope, now) : List.of(shippingOverride);
        if (effective.isEmpty()) { throw error("serviceId", "Chưa có bảng giá đang hiệu lực phù hợp cho dịch vụ và tuyến này."); }
        if (effective.size() != 1) { throw error("serviceId", "Có nhiều bảng giá cùng hiệu lực. Vui lòng liên hệ hỗ trợ."); }
        ShippingRateRule rate = effective.getFirst();
        BigDecimal shipping = shippingFee(rate, weight);
        PickupFeeRule pickup = null;
        BigDecimal pickupFee = new BigDecimal("0.00"); // BRANCH_DROPOFF has no pickup service.
        LocalDateTime expires = earlier(earlier(earlier(now.plus(validity), rate.getEffectiveTo()), rate.getRetiredFrom()), rate.getPricingVersion() == null ? null : rate.getPricingVersion().getRetiredFrom());
        if (method == ShipmentMethod.HOME_PICKUP) {
            var rules = pickupOverride == null ? pickups.findEffectiveRules(originId, service.getId(), now) : List.of(pickupOverride);
            // A branch-specific policy wins. An ambiguous branch policy is never hidden by a global one.
            if (rules.isEmpty() && originId != null && pickupOverride == null) { rules = pickups.findEffectiveRules(null, service.getId(), now); }
            if (rules.size() != 1) { throw error("shipmentMethod", "Chưa có đúng một bảng phụ phí lấy hàng đang hiệu lực cho chi nhánh và dịch vụ này."); }
            pickup = rules.getFirst();
            if (pickup.getAmount() == null || pickup.getAmount().signum() <= 0) {
                throw error("shipmentMethod", "Bảng phụ phí lấy hàng không hợp lệ.");
            }
            pickupFee = money(pickup.getAmount());
            expires = earlier(earlier(earlier(expires, pickup.getEffectiveTo()), pickup.getRetiredFrom()), pickup.getPricingVersion() == null ? null : pickup.getPricingVersion().getRetiredFrom());
        }
        if (!expires.isAfter(now)) { throw error("serviceId", "Bảng giá đã hết hiệu lực. Vui lòng báo giá lại."); }
        return new Price(rate, pickup, shipping, pickupFee, expires);
    }
    @PreAuthorize("hasRole('ADMIN')") @Transactional
    public PublicQuoteView simulateDraft(@NotNull @Positive Long id, @NotNull @Valid GuestQuoteForm form) {
        requireRole("ROLE_ADMIN");
        Long serviceId = versions.findServiceId(id).orElseThrow(this::notFound);
        ShippingService service = services.findForPricingUpdate(serviceId).orElseThrow(this::notFound);
        entityManager.refresh(service, LockModeType.PESSIMISTIC_WRITE); requireRole("ROLE_ADMIN");
        PricingVersion version = versions.findById(id).orElseThrow(this::notFound); entityManager.refresh(version);
        if (version.getStatus() != PricingVersionStatus.DRAFT || !serviceId.equals(version.getService().getId()) || !serviceId.equals(form.getServiceId())) {
            throw error("serviceId", "Chỉ mô phỏng bản nháp của đúng dịch vụ; bản đã công bố dùng công cụ giá hiệu lực.");
        }
        var violations = validator.validate(form); if (!violations.isEmpty()) { throw new ConstraintViolationException(violations); }
        var input = form.toShipmentInput(); Route route = form.isNationwide() ? null : route(input); conditions(service, input);
        RateScopeType scope = ProvinceCatalog.classify(form.getSenderProvince(), form.getReceiverProvince());
        var shipping = rates.findByPricingVersionIdAndIsActiveTrue(id).stream().filter(r -> r.getScopeType() == scope).toList();
        Long originId = route == null ? null : route.origin().getId();
        var pickup = pickups.findByPricingVersionIdAndIsActiveTrue(id).stream().filter(r -> r.getBranch() != null && r.getBranch().getId().equals(originId)).toList();
        if (pickup.isEmpty()) { pickup = pickups.findByPricingVersionIdAndIsActiveTrue(id).stream().filter(r -> r.getBranch() == null).toList(); }
        if (shipping.size() > 1 || pickup.size() > 1) { throw error("serviceId", "Bản nháp có quy tắc chồng lấn, cần sửa trước khi mô phỏng."); }
        Price result = price(service, scope, form.getShipmentMethod(), originId, form.getWeightKg(), version.getValidFrom(),
            shipping.isEmpty() ? null : shipping.getFirst(), pickup.isEmpty() ? null : pickup.getFirst());
        return new PublicQuoteView(service.getName(), form.getShipmentMethod(), result.shipping(), result.pickupFee(), new BigDecimal("0.00"),
            money(result.shipping().add(result.pickupFee())), "VND", version.getValidFrom(), earlier(result.expires(), version.getValidTo()),
            PriceBreakdown.of(snapshot(input, result.rate(), result.shipping())));
    }
    @PreAuthorize("permitAll()") @Transactional
    public PublicQuoteView estimate(@NotNull @Valid GuestQuoteForm form) {
        var violations = validator.validate(form); if (!violations.isEmpty()) { throw new ConstraintViolationException(violations); }
        ShipmentCreateForm input = form.toShipmentInput(); Route route = form.isNationwide() ? null : route(input);
        ShippingService service = activeService(form.getServiceId()); conditions(service, input); LocalDateTime time = now();
        RateScopeType scope = ProvinceCatalog.classify(form.getSenderProvince(), form.getReceiverProvince());
        Price price = price(service, scope, form.getShipmentMethod(), route == null ? null : route.origin().getId(), form.getWeightKg(), time);
        return new PublicQuoteView(service.getName(), form.getShipmentMethod(), price.shipping(), price.pickupFee(), new BigDecimal("0.00"),
            money(price.shipping().add(price.pickupFee())), "VND", time, price.expires(), PriceBreakdown.of(snapshot(input, price.rate(), price.shipping())));
    }
    @PreAuthorize("permitAll()")
    public List<ShipmentOptions.ServiceOption> estimateServices() {
        return services.findByIsActiveTrueOrderByNameAsc().stream().map(s -> new ShipmentOptions.ServiceOption(s.getId(), s.getName())).toList();
    }
    @PreAuthorize("permitAll()")
    public BranchChoices estimateBranches(@NotNull GuestQuoteForm form) {
        if (form.getShipmentMethod() == null) { throw error("shipmentMethod", "Vui lòng chọn phương thức gửi."); }
        for (String value : new String[] {form.getSenderProvince(), form.getSenderDistrict(), form.getSenderWard(), form.getReceiverProvince(), form.getReceiverDistrict(), form.getReceiverWard()}) {
            if (value == null || value.isBlank() || value.length() > 100) { throw error("senderProvince", "Cần nhập đủ tỉnh, huyện, xã hợp lệ của cả hai đầu trước khi tìm chi nhánh."); }
        }
        return choices(form.toShipmentInput());
    }
    private void conditions(ShippingService service, ShipmentCreateForm form) {
        limit(service.getMaxWeightKg(), form.getWeightKg(), "weightKg");
        limit(service.getMaxLengthCm(), form.getLengthCm(), "lengthCm");
        limit(service.getMaxWidthCm(), form.getWidthCm(), "widthCm");
        limit(service.getMaxHeightCm(), form.getHeightCm(), "heightCm");
    }
    private void limit(BigDecimal max, BigDecimal value, String field) {
        if (max != null && (value == null || value.compareTo(max) > 0)) { throw error(field, "Vượt giới hạn dịch vụ hoặc chưa nhập số đo bắt buộc. Giới hạn: " + max.toPlainString()); }
    }

    private BranchChoices choices(ShipmentCreateForm form) {
        List<ShipmentOptions.BranchOption> origins;
        List<ShipmentOptions.BranchOption> destinations;
        try { origins = network.quoteServingBranches(ProvinceCatalog.require(form.getSenderProvince(), "senderProvince").name(), form.getSenderDistrict(), form.getSenderWard(), form.getShipmentMethod()); }
        catch (LogisticsRuleException failure) { throw error("originBranchId", failure.getMessage()); }
        try { destinations = network.quoteDeliveryBranches(ProvinceCatalog.require(form.getReceiverProvince(), "receiverProvince").name(), form.getReceiverDistrict(), form.getReceiverWard()); }
        catch (LogisticsRuleException failure) { throw error("destinationBranchId", failure.getMessage()); }
        return new BranchChoices(origins, destinations);
    }
    private void requireChoice(Long id, List<ShipmentOptions.BranchOption> choices, String field, String message) {
        if (id == null || choices.stream().noneMatch(option -> id.equals(option.id()))) { throw error(field, message); }
    }
    private Long hub(Long id, String field) {
        try { return network.quoteConfiguredHub(id); }
        catch (LogisticsRuleException failure) { throw error(field, failure.getMessage()); }
    }
    private Branch activeBranch(Long id, String field) {
        return branches.findByIdAndStatus(id, BranchStatus.ACTIVE)
            .orElseThrow(() -> error(field, "Chi nhánh không tồn tại hoặc đã ngừng hoạt động."));
    }
    private ShippingService activeService(Long id) {
        ShippingService service = services.findByIdAndIsActiveTrue(id)
            .orElseThrow(() -> error("serviceId", "Dịch vụ không tồn tại hoặc đã ngừng hoạt động."));
        entityManager.refresh(service, LockModeType.PESSIMISTIC_WRITE);
        if (!service.isActive()) { throw error("serviceId", "Dịch vụ đã ngừng hoạt động."); }
        return service;
    }
    private BigDecimal shippingFee(ShippingRateRule rule, BigDecimal weight) {
        return ShippingChargeCalculator.calculate(weight, rule.getBaseWeightKg(), rule.getBasePrice(),
            rule.getExtraWeightStepKg(), rule.getExtraPricePerStep()).total();
    }
    private ShippingPriceSnapshot snapshot(ShipmentCreateForm form, ShippingRateRule rate, BigDecimal shipping) {
        var origin = ProvinceCatalog.require(form.getSenderProvince(), "senderProvince");
        var destination = ProvinceCatalog.require(form.getReceiverProvince(), "receiverProvince");
        return new ShippingPriceSnapshot(ProvinceCatalog.classify(origin.code(), destination.code()), origin.code(), destination.code(),
            money(rate.getBasePrice()), money(shipping.subtract(rate.getBasePrice())), rate.getBaseWeightKg(), rate.getExtraWeightStepKg(), money(rate.getExtraPricePerStep()));
    }
    private void verifyStoredPrice(ShipmentQuote quote) {
        var snapshot = quote.getPriceSnapshot();
        // Legacy quotes retain their already-held price; do not infer new zones or rewrite history.
        if (snapshot == null) { return; }
        var charges = ShippingChargeCalculator.calculate(quote.getWeightKg(), snapshot.getBaseWeightKg(), snapshot.getBaseFee(), snapshot.getStepWeightKg(), snapshot.getStepPrice());
        if (snapshot.getScope() != ProvinceCatalog.classify(quote.getSenderProvince(), quote.getReceiverProvince())
                || !snapshot.getOriginProvinceCode().equals(ProvinceCatalog.require(quote.getSenderProvince(), "senderProvince").code())
                || !snapshot.getDestinationProvinceCode().equals(ProvinceCatalog.require(quote.getReceiverProvince(), "receiverProvince").code())
                || charges.extra().compareTo(snapshot.getExtraFee()) != 0 || charges.total().compareTo(quote.getShippingFee()) != 0
                || money(charges.total().add(quote.getPickupFee()).add(quote.getInsuranceFee())).compareTo(quote.getTotalFee()) != 0) {
            throw error("serviceId", "Snapshot báo giá không khớp. Vui lòng lấy báo giá mới.");
        }
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
    private User requireUser() { return requireRole("ROLE_USER"); }
    private User requireRole(String requiredRole) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getAuthorities().stream()
                .noneMatch(role -> requiredRole.equals(role.getAuthority()))) { throw new AccessDeniedException("Forbidden"); }
        User owner = users.findByEmail(authentication.getName()).orElseThrow(() -> new AccessDeniedException("Forbidden"));
        entityManager.refresh(owner);
        if (owner.getStatus() != UserStatus.ACTIVE || !roles.findRoleNamesByUserId(owner.getId()).contains(requiredRole)) {
            throw new AccessDeniedException("Forbidden");
        }
        return owner;
    }
    private ShipmentCreationException error(String field, String message) { return new ShipmentCreationException(field, message); }
    private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy báo giá."); }
}
