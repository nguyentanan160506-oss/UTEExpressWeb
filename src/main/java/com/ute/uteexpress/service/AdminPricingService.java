package com.ute.uteexpress.service;

import com.ute.uteexpress.dto.pricing.PricingCommands.*;
import com.ute.uteexpress.dto.pricing.PricingCommands.Period;
import com.ute.uteexpress.dto.pricing.PricingViews.*;
import com.ute.uteexpress.dto.pricing.PricingViews.Catalog;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import jakarta.validation.Valid;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

/** Service row is the serialization gate for catalog edits, publication and all new pricing. */
@Service @Validated @PreAuthorize("hasRole('ADMIN')") @Transactional(readOnly = true)
public class AdminPricingService {
    private final ShippingServiceRepository services;
    private final PricingVersionRepository versions;
    private final ShippingRateRuleRepository rates;
    private final PickupFeeRuleRepository pickups;
    private final BranchRepository branches;
    private final UserRepository users;
    private final UserRoleRepository roles;
    private final AuditLogRepository audits;
    private final Clock clock;
    private final EntityManager entityManager;
    public AdminPricingService(ShippingServiceRepository services, PricingVersionRepository versions, ShippingRateRuleRepository rates,
            PickupFeeRuleRepository pickups, BranchRepository branches, UserRepository users, UserRoleRepository roles, AuditLogRepository audits, Clock clock, EntityManager entityManager) {
        this.services = services; this.versions = versions; this.rates = rates; this.pickups = pickups; this.branches = branches;
        this.users = users; this.roles = roles; this.audits = audits; this.clock = clock; this.entityManager = entityManager;
    }
    public Page<Catalog> catalog(@Size(max = 100) String q, @Min(0) @Max(1000000) int page) {
        actor(); return services.findCatalog(q == null || q.isBlank() ? null : q.strip().toUpperCase(Locale.ROOT), page(page)).map(this::catalog);
    }
    public void authorize() { actor(); }
    public Page<com.ute.uteexpress.dto.shipment.ShipmentOptions.BranchOption> pickupBranches(@Min(0) @Max(1000000) int page) {
        actor(); return branches.findByStatusAndBranchType(BranchStatus.ACTIVE, BranchType.LOCAL_BRANCH, page(page))
            .map(b -> new com.ute.uteexpress.dto.shipment.ShipmentOptions.BranchOption(b.getId(), b.getName(), b.getProvince()));
    }
    public ServiceDetail service(@NotNull @Positive Long id, @Min(0) @Max(1000000) int page) {
        actor(); var s = services.findById(id).orElseThrow(this::missing);
        return new ServiceDetail(catalog(s), rates.findByServiceIdAndPricingVersionIsNull(id, page(page)).map(this::rule),
            pickups.findByServiceIdAndPricingVersionIsNull(id, page(page)).map(this::pickup), history(AuditResourceType.SHIPPING_SERVICE, id, page));
    }
    public Page<Book> books(@Positive Long serviceId, @Min(0) @Max(1000000) int page) {
        actor(); return (serviceId == null ? versions.findAll(page(page)) : versions.findByServiceId(serviceId, page(page))).map(this::book);
    }
    public Detail detail(@NotNull @Positive Long id, @Min(0) @Max(1000000) int page) {
        actor(); var v = versions.findById(id).orElseThrow(this::missing);
        return new Detail(book(v), rates.findByPricingVersionId(id, page(page)).map(this::rule),
            pickups.findByPricingVersionId(id, page(page)).map(this::pickup), history(AuditResourceType.PRICE_VERSION, id, page));
    }
    @Transactional
    public Long createService(@NotNull @Valid com.ute.uteexpress.dto.pricing.PricingCommands.Catalog c) {
        User actor = actor(); String code = code(c.code());
        if (services.existsByCodeIgnoreCase(code)) { throw rule("Mã dịch vụ đã tồn tại."); }
        var s = new ShippingService(); s.setCode(code); apply(s, c); s.setActive(false); s.setCreatedBy(actor); s.setUpdatedBy(actor);
        s.setCreatedAt(now()); s.setUpdatedAt(now()); services.saveAndFlush(s);
        audit(actor, AuditResourceType.SHIPPING_SERVICE, s.getId(), "SERVICE_CREATED", null, catalog(s).toString()); return s.getId();
    }
    @Transactional
    public void editService(@NotNull @Positive Long id, @NotNull @PositiveOrZero Long expected, @NotNull @Valid com.ute.uteexpress.dto.pricing.PricingCommands.Catalog c) {
        ShippingService s = lockedService(id); User actor = actor(); version(s.getVersion(), expected, ShippingService.class, id);
        if (c.code() != null) { throw rule("Mã dịch vụ không được thay đổi; tạo dịch vụ mới nếu cần mã khác."); }
        String before = catalog(s).toString(); apply(s, c); s.setUpdatedBy(actor); touch(s); services.saveAndFlush(s);
        audit(actor, AuditResourceType.SHIPPING_SERVICE, id, "SERVICE_EDITED", before, catalog(s).toString());
    }
    @Transactional
    public void serviceStatus(@NotNull @Positive Long id, @NotNull @PositiveOrZero Long expected, boolean active) {
        ShippingService s = lockedService(id); User actor = actor(); version(s.getVersion(), expected, ShippingService.class, id);
        if (s.isActive() == active) { throw rule("Trạng thái dịch vụ đã được áp dụng."); }
        String before = catalog(s).toString(); s.setActive(active); s.setUpdatedBy(actor); touch(s); services.saveAndFlush(s);
        audit(actor, AuditResourceType.SHIPPING_SERVICE, id, active ? "SERVICE_ENABLED" : "SERVICE_DISABLED", before, catalog(s).toString());
    }
    @Transactional
    public Long createDraft(@NotNull @Valid Draft c) {
        ShippingService s = lockedService(c.serviceId()); User actor = actor(); String code = code(c.code()); period(c.validFrom(), c.validTo());
        if (versions.existsByServiceIdAndCodeIgnoreCase(s.getId(), code)) { throw rule("Mã phiên bản đã tồn tại trong dịch vụ."); }
        PricingVersion v = new PricingVersion(); v.setService(s); v.setCode(code); v.setValidFrom(c.validFrom()); v.setValidTo(c.validTo());
        v.setCreatedBy(actor); v.setUpdatedBy(actor); v.setCreatedAt(now()); v.setUpdatedAt(now()); versions.saveAndFlush(v);
        audit(actor, AuditResourceType.PRICE_VERSION, v.getId(), "PRICE_DRAFT_CREATED", null, book(v).toString()); return v.getId();
    }
    @Transactional
    public void editPeriod(@NotNull @Positive Long id, @NotNull @PositiveOrZero Long expected, @NotNull @Valid Period c) {
        var v = lockedBook(id, expected); User actor = actor(); draft(v); period(c.validFrom(), c.validTo()); String before = book(v).toString();
        v.setValidFrom(c.validFrom()); v.setValidTo(c.validTo());
        // Only unpublished rules can have their planned interval edited.
        for (var r : rates.findByPricingVersionIdAndIsActiveTrue(id)) { r.setEffectiveFrom(c.validFrom()); r.setEffectiveTo(c.validTo()); rates.saveAndFlush(r); }
        for (var r : pickups.findByPricingVersionIdAndIsActiveTrue(id)) { r.setEffectiveFrom(c.validFrom()); r.setEffectiveTo(c.validTo()); pickups.saveAndFlush(r); }
        changed(v, actor); audit(actor, AuditResourceType.PRICE_VERSION, id, "PRICE_DRAFT_PERIOD", before, book(v).toString());
    }
    @Transactional
    public void shippingRule(@NotNull @Positive Long id, @NotNull @PositiveOrZero Long expected, @NotNull @Valid ShippingRule c) {
        var v = lockedBook(id, expected); User actor = actor(); draft(v); scope(c.scope());
        weight(c.baseWeightKg()); weight(c.extraWeightStepKg()); money(c.basePrice(), false); money(c.extraPricePerStep(), false);
        ShippingRateRule r = c.ruleId() == null ? new ShippingRateRule() : rates.findByIdAndPricingVersionId(c.ruleId(), id).orElseThrow(this::missing);
        String before = r.getId() == null ? null : rule(r).toString();
        // All positive weights belong to one base+step rule per scope; never select overlapping arbitrary bands.
        rates.findByPricingVersionIdAndScopeType(id, c.scope()).filter(existing -> !Objects.equals(existing.getId(), r.getId()))
            .ifPresent(existing -> { throw rule("Phạm vi đã có công thức trọng lượng, kể cả dòng đang tắt; sửa dòng hiện có, không thêm bậc chồng lấn."); });
        r.setService(v.getService()); r.setPricingVersion(v); r.setScopeType(c.scope()); r.setBaseWeightKg(c.baseWeightKg()); r.setBasePrice(c.basePrice());
        r.setExtraWeightStepKg(c.extraWeightStepKg()); r.setExtraPricePerStep(c.extraPricePerStep()); r.setActive(c.active());
        r.setEffectiveFrom(v.getValidFrom()); r.setEffectiveTo(v.getValidTo()); rates.saveAndFlush(r); changed(v, actor);
        audit(actor, AuditResourceType.PRICE_VERSION, id, "PRICE_DRAFT_SHIPPING_RULE", before, rule(r).toString());
    }
    @Transactional
    public void pickupRule(@NotNull @Positive Long id, @NotNull @PositiveOrZero Long expected, @NotNull @Valid PickupRule c) {
        var v = lockedBook(id, expected); User actor = actor(); draft(v); money(c.amount(), true); Branch b = branch(c.branchId());
        if (c.ruleId() == null && pickups.countByPricingVersionId(id) >= 500) { throw rule("Mỗi bản tối đa 500 dòng phụ phí; tách phiên bản theo phạm vi khi cần."); }
        PickupFeeRule r = c.ruleId() == null ? new PickupFeeRule() : pickups.findByIdAndPricingVersionId(c.ruleId(), id).orElseThrow(this::missing);
        String before = r.getId() == null ? null : pickup(r).toString();
        pickups.findByPricingVersionIdAndBranchId(id, b.getId()).filter(existing -> !Objects.equals(existing.getId(), r.getId()))
            .ifPresent(existing -> { throw rule("Chi nhánh đã có phụ phí, kể cả dòng đang tắt; sửa dòng hiện có."); });
        r.setService(v.getService()); r.setPricingVersion(v); r.setBranch(b); r.setAmount(c.amount()); r.setActive(c.active());
        r.setEffectiveFrom(v.getValidFrom()); r.setEffectiveTo(v.getValidTo()); pickups.saveAndFlush(r); changed(v, actor);
        audit(actor, AuditResourceType.PRICE_VERSION, id, "PRICE_DRAFT_PICKUP_RULE", before, pickup(r).toString());
    }
    public List<String> check(@NotNull @Positive Long id) {
        actor(); var v = versions.findById(id).orElseThrow(this::missing); return problems(v);
    }
    @Transactional
    public void publish(@NotNull @Positive Long id, @NotNull @PositiveOrZero Long expected, boolean confirmed) {
        var v = lockedBook(id, expected); User actor = actor(); draft(v);
        if (!confirmed) { throw rule("Cần xác nhận đã kiểm tra và công bố phiên bản."); }
        List<String> issues = problems(v); if (!issues.isEmpty()) { throw rule(String.join(" ", issues)); }
        LocalDateTime publishedAt = now();
        if (v.getValidFrom().isBefore(publishedAt)) { throw rule("Đã qua thời điểm bắt đầu trong lúc kiểm tra; sửa thời gian bản nháp rồi công bố lại, không công bố hồi tố."); }
        v.setStatus(PricingVersionStatus.PUBLISHED); v.setPublishedBy(actor); v.setPublishedAt(publishedAt); changed(v, actor);
        audit(actor, AuditResourceType.PRICE_VERSION, id, "PRICE_PUBLISHED", null, book(v).toString());
    }
    @Transactional
    public void retireVersion(@NotNull @Positive Long id, @NotNull @PositiveOrZero Long expected, @NotNull LocalDateTime at, boolean confirmed) {
        var v = lockedBook(id, expected); User actor = actor();
        if (!confirmed || v.getStatus() != PricingVersionStatus.PUBLISHED || v.getRetiredFrom() != null) { throw rule("Chỉ lập một lịch ngừng mới cho bản đã công bố, cần xác nhận."); }
        retirement(at, v.getValidFrom(), v.getValidTo()); String before = book(v).toString(); v.setRetiredFrom(at); changed(v, actor);
        audit(actor, AuditResourceType.PRICE_VERSION, id, "PRICE_RETIREMENT_SCHEDULED", before, book(v).toString());
    }
    @Transactional
    public void retireLegacy(@NotNull @Positive Long ruleId, boolean pickup, @NotNull LocalDateTime at, boolean confirmed) {
        Long serviceId = (pickup ? pickups.findServiceId(ruleId) : rates.findServiceId(ruleId)).orElseThrow(this::missing);
        lockedService(serviceId); User actor = actor(); if (!confirmed) { throw rule("Cần xác nhận lịch ngừng quy tắc legacy."); }
        String before, after;
        if (pickup) {
            var r = pickups.findById(ruleId).orElseThrow(this::missing);
            if (r.getPricingVersion() != null || r.getRetiredFrom() != null || !r.getService().getId().equals(serviceId)) { throw rule("Không phải quy tắc legacy chưa lập lịch ngừng."); }
            retirement(at, r.getEffectiveFrom(), r.getEffectiveTo()); before = pickup(r).toString(); r.setRetiredFrom(at); pickups.saveAndFlush(r); after = pickup(r).toString();
        } else {
            var r = rates.findById(ruleId).orElseThrow(this::missing);
            if (r.getPricingVersion() != null || r.getRetiredFrom() != null || !r.getService().getId().equals(serviceId)) { throw rule("Không phải quy tắc legacy chưa lập lịch ngừng."); }
            retirement(at, r.getEffectiveFrom(), r.getEffectiveTo()); before = rule(r).toString(); r.setRetiredFrom(at); rates.saveAndFlush(r); after = rule(r).toString();
        }
        audit(actor, AuditResourceType.SHIPPING_SERVICE, serviceId, pickup ? "LEGACY_PICKUP_RETIREMENT" : "LEGACY_SHIPPING_RETIREMENT", before, after);
    }
    private List<String> problems(PricingVersion v) {
        List<String> errors = new ArrayList<>(); period(v.getValidFrom(), v.getValidTo());
        if (v.getStatus() != PricingVersionStatus.DRAFT) { errors.add("Phiên bản đã công bố, phần giá bị khóa."); }
        if (v.getValidFrom().isBefore(now())) { errors.add("Thời điểm bắt đầu phải ở hiện tại/tương lai UTC, không công bố hồi tố."); }
        var shipping = rates.findByPricingVersionIdAndIsActiveTrue(v.getId()); var pickup = pickups.findByPricingVersionIdAndIsActiveTrue(v.getId());
        if (shipping.isEmpty() && pickup.isEmpty()) { errors.add("Cần ít nhất một quy tắc đang bật."); }
        if (shipping.size() > 2 || pickup.size() > 500) { errors.add("Vượt phạm vi quy tắc được hỗ trợ."); }
        Set<RateScopeType> scopes = new HashSet<>(); Set<Long> points = new HashSet<>();
        for (var r : shipping) {
            scope(r.getScopeType()); weight(r.getBaseWeightKg()); weight(r.getExtraWeightStepKg()); money(r.getBasePrice(), false); money(r.getExtraPricePerStep(), false);
            if (!scopes.add(r.getScopeType()) || !sameContext(v, r.getService(), r.getEffectiveFrom(), r.getEffectiveTo())) { errors.add("Quy tắc vận chuyển trùng/phạm vi hoặc thời gian không khớp bản nháp."); }
            if (!rates.findConflicts(v.getService().getId(), r.getScopeType(), v.getId(), v.getValidFrom(), v.getValidTo()).isEmpty()) { errors.add("Xung đột thời gian giá vận chuyển " + r.getScopeType() + "; cần lập lịch ngừng bản trước/legacy trước."); }
        }
        for (var r : pickup) {
            branch(r.getBranch().getId()); money(r.getAmount(), true);
            if (!points.add(r.getBranch().getId()) || !sameContext(v, r.getService(), r.getEffectiveFrom(), r.getEffectiveTo())) { errors.add("Phụ phí trùng chi nhánh hoặc thời gian không khớp bản nháp."); }
            if (!pickups.findConflicts(v.getService().getId(), r.getBranch().getId(), v.getId(), v.getValidFrom(), v.getValidTo()).isEmpty()) { errors.add("Xung đột thời gian phụ phí lấy hàng tại chi nhánh " + r.getBranch().getId() + "."); }
        }
        return List.copyOf(errors);
    }
    private boolean sameContext(PricingVersion v, ShippingService service, LocalDateTime from, LocalDateTime to) {
        return v.getService().getId().equals(service.getId()) && v.getValidFrom().equals(from) && Objects.equals(v.getValidTo(), to);
    }
    private PricingVersion lockedBook(Long id, Long expected) {
        Long serviceId = versions.findServiceId(id).orElseThrow(this::missing); lockedService(serviceId);
        PricingVersion v = versions.findById(id).orElseThrow(this::missing);
        entityManager.refresh(v); // Do not accept an entity cached before waiting for the service gate.
        if (!v.getService().getId().equals(serviceId)) { throw rule("Phiên bản không thuộc dịch vụ."); }
        version(v.getVersion(), expected, PricingVersion.class, id); return v;
    }
    private ShippingService lockedService(Long id) {
        actor(); ShippingService s = services.findForPricingUpdate(id).orElseThrow(this::missing);
        entityManager.refresh(s, LockModeType.PESSIMISTIC_WRITE); return s;
    }
    private void draft(PricingVersion v) { if (v.getStatus() != PricingVersionStatus.DRAFT) { throw rule("Không sửa giá của phiên bản đã công bố; tạo bản nháp mới."); } }
    private void changed(PricingVersion v, User actor) { v.setUpdatedBy(actor); v.setUpdatedAt(next(v.getUpdatedAt())); versions.saveAndFlush(v); }
    private void touch(ShippingService s) { s.setUpdatedAt(next(s.getUpdatedAt())); }
    private LocalDateTime next(LocalDateTime old) { return old != null && !now().isAfter(old) ? old.plusNanos(100) : now(); }
    private void version(Long actual, Long expected, Class<?> type, Long id) { if (expected == null || !Objects.equals(actual, expected)) { throw new ObjectOptimisticLockingFailureException(type, id); } }
    private Branch branch(Long id) { return branches.findByIdAndStatus(id, BranchStatus.ACTIVE).filter(b -> b.getBranchType() == BranchType.LOCAL_BRANCH).orElseThrow(() -> rule("Phụ phí cần chi nhánh LOCAL_BRANCH đang hoạt động.")); }
    private User actor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) { throw new AccessDeniedException("Forbidden"); }
        User u = users.findByEmail(auth.getName()).orElseThrow(() -> new AccessDeniedException("Forbidden"));
        entityManager.refresh(u);
        if (u.getStatus() != UserStatus.ACTIVE || !roles.findRoleNamesByUserId(u.getId()).contains("ROLE_ADMIN")) { throw new AccessDeniedException("Forbidden"); } return u;
    }
    private void apply(ShippingService s, com.ute.uteexpress.dto.pricing.PricingCommands.Catalog c) {
        if (c.name() == null || c.name().isBlank() || c.name().length() > 100 || c.estimatedDeliveryHours() == null || c.estimatedDeliveryHours() < 1 || (c.description() != null && c.description().length() > 255)) { throw rule("Tên, mô tả hoặc thời gian giao không hợp lệ."); }
        for (var max : Arrays.asList(c.maxWeightKg(), c.maxLengthCm(), c.maxWidthCm(), c.maxHeightCm())) { if (max != null) { weight(max); } }
        s.setName(c.name().strip()); s.setDescription(c.description() == null ? null : c.description().strip()); s.setEstimatedDeliveryHours(c.estimatedDeliveryHours());
        s.setMaxWeightKg(c.maxWeightKg()); s.setMaxLengthCm(c.maxLengthCm()); s.setMaxWidthCm(c.maxWidthCm()); s.setMaxHeightCm(c.maxHeightCm());
    }
    private String code(String code) { if (code == null || !code.matches("[A-Z0-9][A-Z0-9_-]{0,49}")) { throw rule("Mã cần 1–50 ký tự A–Z, 0–9, _ hoặc -, bắt đầu bằng chữ/số."); } return code; }
    private void scope(RateScopeType scope) { if (scope != RateScopeType.INTRA_PROVINCE && scope != RateScopeType.INTER_PROVINCE) { throw rule("Chưa có dữ liệu phân vùng INTER_REGION; chỉ hỗ trợ nội tỉnh/liên tỉnh."); } }
    private void weight(BigDecimal n) { if (n == null || n.signum() <= 0 || n.scale() > 2 || n.compareTo(new BigDecimal("99999999.99")) > 0) { throw rule("Trọng lượng/kích thước/bước cân phải dương, tối đa 8 chữ số nguyên và 2 số lẻ."); } }
    private void money(BigDecimal n, boolean positive) { if (n == null || n.signum() < (positive ? 1 : 0) || n.scale() > 2 || n.compareTo(new BigDecimal("9999999999999999.99")) > 0) { throw rule("Giá không hợp lệ; phụ phí lấy hàng phải dương, không tạo giá 0 để che thiếu cấu hình."); } }
    private void period(LocalDateTime from, LocalDateTime to) { if (from == null || (to != null && !to.isAfter(from)) || from.getNano() % 100 != 0 || (to != null && to.getNano() % 100 != 0)) { throw rule("Khoảng hiệu lực UTC phải [bắt đầu, kết thúc), đúng DATETIME2(7)."); } }
    private void retirement(LocalDateTime at, LocalDateTime from, LocalDateTime to) { if (at == null || !at.isAfter(now()) || !at.isAfter(from) || (to != null && !at.isBefore(to)) || at.getNano() % 100 != 0) { throw rule("Lịch ngừng phải ở tương lai UTC, sau bắt đầu và trước kết thúc hiện có."); } }
    private void audit(User actor, AuditResourceType resource, Long id, String action, String before, String after) {
        AuditLog a = new AuditLog(); a.setActor(actor); a.setResourceType(resource); a.setResourceId(id.toString()); a.setAction(action);
        a.setOldValue(before); a.setNewValue(after); a.setCreatedAt(now()); audits.saveAndFlush(a);
    }
    private Page<Audit> history(AuditResourceType resource, Long id, int page) { return audits.findByResourceTypeAndResourceId(resource, id.toString(), page(page)).map(a -> new Audit(a.getAction(), a.getActor() == null ? null : a.getActor().getId(), a.getOldValue(), a.getNewValue(), a.getCreatedAt())); }
    private Catalog catalog(ShippingService s) { return new Catalog(s.getId(), s.getCode(), s.getName(), s.getDescription(), s.getEstimatedDeliveryHours(), s.isActive(), s.getVersion(), s.getMaxWeightKg(), s.getMaxLengthCm(), s.getMaxWidthCm(), s.getMaxHeightCm()); }
    private Rule rule(ShippingRateRule r) { return new Rule(r.getId(), r.getScopeType(), r.getBaseWeightKg(), r.getBasePrice(), r.getExtraWeightStepKg(), r.getExtraPricePerStep(), r.isActive(), r.getEffectiveFrom(), r.getEffectiveTo(), r.getRetiredFrom()); }
    private Pickup pickup(PickupFeeRule r) { return new Pickup(r.getId(), r.getBranch().getId(), r.getBranch().getName(), r.getAmount(), r.isActive(), r.getEffectiveFrom(), r.getEffectiveTo(), r.getRetiredFrom()); }
    private Book book(PricingVersion v) {
        LocalDateTime end = v.getRetiredFrom() != null ? v.getRetiredFrom() : v.getValidTo();
        String state = v.getStatus() == PricingVersionStatus.DRAFT ? "Bản nháp" : end != null && !now().isBefore(end) ? "Đã ngừng/hết hạn" : now().isBefore(v.getValidFrom()) ? "Đã công bố, chờ hiệu lực" : "Đang hiệu lực";
        return new Book(v.getId(), v.getService().getId(), v.getService().getName(), v.getCode(), v.getStatus(), v.getCurrency(), v.getValidFrom(), v.getValidTo(), v.getRetiredFrom(), v.getVersion(), state, v.getCreatedAt(), v.getUpdatedAt(), v.getPublishedAt());
    }
    private PageRequest page(int page) { return PageRequest.of(page, 20, Sort.by("id").descending()); }
    private LocalDateTime now() { var t = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); return t.withNano(t.getNano() / 100 * 100); }
    private AdminRuleException rule(String message) { return new AdminRuleException(message); }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy dịch vụ/phiên bản/quy tắc."); }
}
