package com.ute.uteexpress.controller;

import com.ute.uteexpress.dto.pricing.*;
import com.ute.uteexpress.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.dao.DataAccessException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequestMapping("/admin") @PreAuthorize("hasRole('ADMIN')")
public class AdminPricingController {
    private final AdminPricingService admin;
    public AdminPricingController(AdminPricingService admin) { this.admin = admin; }
    @InitBinder("catalogForm") void catalog(WebDataBinder b) { b.setAllowedFields("code", "name", "description", "estimatedDeliveryHours", "maxWeightKg", "maxLengthCm", "maxWidthCm", "maxHeightCm", "catalogVersion", "id", "_csrf"); }
    @InitBinder("draftForm") void draft(WebDataBinder b) { b.setAllowedFields("serviceId", "code", "validFrom", "validTo", "id", "_csrf"); }
    @InitBinder("periodForm") void period(WebDataBinder b) { b.setAllowedFields("expectedVersion", "validFrom", "validTo", "id", "_csrf"); }
    @InitBinder("shippingForm") void shipping(WebDataBinder b) { b.setAllowedFields("expectedVersion", "ruleId", "scope", "baseWeightKg", "basePrice", "extraWeightStepKg", "extraPricePerStep", "active", "id", "_csrf"); }
    @InitBinder("pickupForm") void pickup(WebDataBinder b) { b.setAllowedFields("expectedVersion", "ruleId", "branchId", "amount", "active", "id", "_csrf"); }
    @InitBinder("confirmForm") void confirm(WebDataBinder b) { b.setAllowedFields("expectedVersion", "confirmed", "id", "_csrf"); }
    @InitBinder("statusForm") void status(WebDataBinder b) { b.setAllowedFields("expectedVersion", "active", "confirmed", "id", "_csrf"); }
    @InitBinder({"retireForm", "legacyForm"}) void retire(WebDataBinder b) { b.setAllowedFields("expectedVersion", "at", "confirmed", "id", "ruleId", "_csrf"); }
    @GetMapping("/services") String services(@RequestParam(defaultValue = "") @Size(max = 100) String q, @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page, Model m) {
        m.addAttribute("services", admin.catalog(q, page)); m.addAttribute("q", q); return "pricing/services";
    }
    @GetMapping("/services/new") String newService(Model m) { admin.authorize(); m.addAttribute("catalogForm", new PricingForms.Catalog()); return "pricing/service-form"; }
    @GetMapping("/services/{id}") String service(@PathVariable @Positive Long id, @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page, Model m) {
        m.addAttribute("detail", admin.service(id, page)); return "pricing/service-detail";
    }
    @GetMapping("/services/{id}/edit") String edit(@PathVariable @Positive Long id, Model m) {
        var c = admin.service(id, 0).service(); var f = new PricingForms.Catalog();
        f.setName(c.name()); f.setDescription(c.description()); f.setEstimatedDeliveryHours(c.hours()); f.setMaxWeightKg(c.maxWeightKg());
        f.setMaxLengthCm(c.maxLengthCm()); f.setMaxWidthCm(c.maxWidthCm()); f.setMaxHeightCm(c.maxHeightCm()); f.setCatalogVersion(c.version());
        m.addAttribute("service", c); m.addAttribute("catalogForm", f); return "pricing/service-form";
    }
    @PostMapping("/services") String create(@Valid @ModelAttribute("catalogForm") PricingForms.Catalog f, BindingResult e, RedirectAttributes a) {
        return mutate("/admin/services", e, a, () -> admin.createService(catalog(f, true)));
    }
    @PostMapping("/services/{id}") String edit(@PathVariable @Positive Long id, @Valid @ModelAttribute("catalogForm") PricingForms.Catalog f, BindingResult e, RedirectAttributes a) {
        if (f.getCode() != null) { throw bad(); }
        if (f.getCatalogVersion() == null) { e.reject("version", "Thiếu version của dịch vụ."); }
        return mutate("/admin/services/" + id, e, a, () -> admin.editService(id, f.getCatalogVersion(), catalog(f, false)));
    }
    @PostMapping("/services/{id}/status") String status(@PathVariable @Positive Long id, @Valid @ModelAttribute("statusForm") PricingForms.Status f, BindingResult e, RedirectAttributes a) {
        if (!f.isConfirmed()) { e.reject("confirmed", "Cần xác nhận thay đổi nhận đơn."); }
        return mutate("/admin/services/" + id, e, a, () -> admin.serviceStatus(id, f.getExpectedVersion(), f.isActive()));
    }
    @GetMapping("/pricing") String books(@RequestParam(required = false) @Positive Long serviceId, @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page, Model m) {
        m.addAttribute("books", admin.books(serviceId, page)); m.addAttribute("serviceId", serviceId); return "pricing/books";
    }
    @GetMapping("/pricing/new") String draft(@RequestParam @Positive Long serviceId, Model m) {
        m.addAttribute("service", admin.service(serviceId, 0).service()); var f = new PricingForms.Draft(); f.setServiceId(serviceId); m.addAttribute("draftForm", f); return "pricing/new";
    }
    @PostMapping("/pricing") String create(@Valid @ModelAttribute("draftForm") PricingForms.Draft f, BindingResult e, RedirectAttributes a) {
        return mutate("/admin/pricing", e, a, () -> admin.createDraft(new PricingCommands.Draft(f.getServiceId(), f.getCode(), f.getValidFrom(), f.getValidTo())));
    }
    @GetMapping("/pricing/{id}") String detail(@PathVariable @Positive Long id, @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page, Model m) {
        m.addAttribute("detail", admin.detail(id, page)); m.addAttribute("branches", admin.pickupBranches(page)); return "pricing/detail";
    }
    @PostMapping("/pricing/{id}") String period(@PathVariable @Positive Long id, @Valid @ModelAttribute("periodForm") PricingForms.Period f, BindingResult e, RedirectAttributes a) {
        return mutate(book(id), e, a, () -> admin.editPeriod(id, f.getExpectedVersion(), new PricingCommands.Period(f.getValidFrom(), f.getValidTo())));
    }
    @PostMapping("/pricing/{id}/rules") String rule(@PathVariable @Positive Long id, @Valid @ModelAttribute("shippingForm") PricingForms.Shipping f, BindingResult e, RedirectAttributes a) {
        return mutate(book(id), e, a, () -> admin.shippingRule(id, f.getExpectedVersion(), new PricingCommands.ShippingRule(f.getRuleId(), f.getScope(), f.getBaseWeightKg(), f.getBasePrice(), f.getExtraWeightStepKg(), f.getExtraPricePerStep(), f.isActive())));
    }
    @PostMapping("/pricing/{id}/pickup-rules") String pickup(@PathVariable @Positive Long id, @Valid @ModelAttribute("pickupForm") PricingForms.Pickup f, BindingResult e, RedirectAttributes a) {
        return mutate(book(id), e, a, () -> admin.pickupRule(id, f.getExpectedVersion(), new PricingCommands.PickupRule(f.getRuleId(), f.getBranchId(), f.getAmount(), f.isActive())));
    }
    @PostMapping("/pricing/{id}/check") String check(@PathVariable @Positive Long id, RedirectAttributes a) {
        var problems = admin.check(id); a.addFlashAttribute(problems.isEmpty() ? "notice" : "error", problems.isEmpty() ? "Hợp lệ tại thời điểm kiểm tra; công bố sẽ kiểm tra lại dưới khóa dịch vụ." : String.join(" ", problems)); return "redirect:" + book(id);
    }
    @PostMapping("/pricing/{id}/publish") String publish(@PathVariable @Positive Long id, @Valid @ModelAttribute("confirmForm") PricingForms.Confirm f, BindingResult e, RedirectAttributes a) {
        return mutate(book(id), e, a, () -> admin.publish(id, f.getExpectedVersion(), f.isConfirmed()));
    }
    @PostMapping("/pricing/{id}/retire") String retire(@PathVariable @Positive Long id, @Valid @ModelAttribute("retireForm") PricingForms.Retire f, BindingResult e, RedirectAttributes a) {
        return mutate(book(id), e, a, () -> admin.retireVersion(id, f.getExpectedVersion(), f.getAt(), f.isConfirmed()));
    }
    @PostMapping("/services/legacy/shipping/{ruleId}/retire") String retireShipping(@PathVariable @Positive Long ruleId, @Valid @ModelAttribute("legacyForm") PricingForms.RetireDate f, BindingResult e, RedirectAttributes a) {
        return mutate("/admin/services", e, a, () -> admin.retireLegacy(ruleId, false, f.getAt(), f.isConfirmed()));
    }
    @PostMapping("/services/legacy/pickup/{ruleId}/retire") String retirePickup(@PathVariable @Positive Long ruleId, @Valid @ModelAttribute("legacyForm") PricingForms.RetireDate f, BindingResult e, RedirectAttributes a) {
        return mutate("/admin/services", e, a, () -> admin.retireLegacy(ruleId, true, f.getAt(), f.isConfirmed()));
    }
    private PricingCommands.Catalog catalog(PricingForms.Catalog f, boolean create) { return new PricingCommands.Catalog(create ? f.getCode() : null, f.getName(), f.getDescription(), f.getEstimatedDeliveryHours(), f.getMaxWeightKg(), f.getMaxLengthCm(), f.getMaxWidthCm(), f.getMaxHeightCm()); }
    private String book(Long id) { return "/admin/pricing/" + id; }
    private ResponseStatusException bad() { return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trường không được phép."); }
    private String mutate(String redirect, BindingResult e, RedirectAttributes a, Runnable operation) {
        if (e.getSuppressedFields().length > 0) { throw bad(); }
        if (e.hasErrors()) { a.addFlashAttribute("error", "Dữ liệu không hợp lệ: " + String.join("; ", PricingForms.errors(e))); }
        else { try { operation.run(); a.addFlashAttribute("notice", "Đã ghi nhận thay đổi và audit."); }
            catch (ObjectOptimisticLockingFailureException conflict) { a.addFlashAttribute("error", "Dữ liệu đã thay đổi. Vui lòng tải lại trước khi thao tác."); }
            catch (AdminRuleException invalid) { a.addFlashAttribute("error", invalid.getMessage()); }
            catch (DataAccessException failure) { a.addFlashAttribute("error", "Chưa thể ghi nhận. Kiểm tra mã/quy tắc trùng và tải lại; chưa xác nhận thành công."); }
        }
        return "redirect:" + redirect;
    }
}
