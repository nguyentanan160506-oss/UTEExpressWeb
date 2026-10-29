package com.ute.uteexpress.controller;

import com.ute.uteexpress.dto.pricing.GuestQuoteForm;
import com.ute.uteexpress.service.*;
import jakarta.validation.Valid;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class PublicQuoteController {
    private final QuoteService quotes;
    private final AdminPricingService admin;
    public PublicQuoteController(QuoteService quotes, AdminPricingService admin) { this.quotes = quotes; this.admin = admin; }
    @InitBinder("estimateForm") void fields(WebDataBinder b) {
        b.setAllowedFields("nationwide", "shipmentMethod", "serviceId", "originBranchId", "destinationBranchId", "senderProvince", "senderDistrict", "senderWard", "receiverProvince", "receiverDistrict", "receiverWard", "weightKg", "lengthCm", "widthCm", "heightCm", "id", "_csrf");
    }
    @GetMapping("/quote") String form(@RequestParam(defaultValue="true") boolean nationwide, Model m) {
        var form = new GuestQuoteForm(); form.setNationwide(nationwide); m.addAttribute("estimateForm", form); return page(m, false);
    }
    @PostMapping("/quote") String estimate(@Valid @ModelAttribute("estimateForm") GuestQuoteForm f, BindingResult e, Model m) { return calculate(f, e, m, false); }
    @PostMapping("/quote/branches") String branches(@ModelAttribute("estimateForm") GuestQuoteForm f, BindingResult e, Model m) { return branches(f, e, m, false); }
    @GetMapping("/admin/pricing/preview") @PreAuthorize("hasRole('ADMIN')") String preview(Model m) { admin.authorize(); m.addAttribute("estimateForm", new GuestQuoteForm()); return page(m, true); }
    @PostMapping("/admin/pricing/preview") @PreAuthorize("hasRole('ADMIN')") String preview(@Valid @ModelAttribute("estimateForm") GuestQuoteForm f, BindingResult e, Model m) { admin.authorize(); return calculate(f, e, m, true); }
    @PostMapping("/admin/pricing/preview/branches") @PreAuthorize("hasRole('ADMIN')") String previewBranches(@ModelAttribute("estimateForm") GuestQuoteForm f, BindingResult e, Model m) { admin.authorize(); return branches(f, e, m, true); }
    @GetMapping("/admin/pricing/{id}/preview") @PreAuthorize("hasRole('ADMIN')") String draft(@PathVariable @jakarta.validation.constraints.Positive Long id, Model m) {
        var f = new GuestQuoteForm(); draftContext(id, m); f.setServiceId(admin.detail(id, 0).book().serviceId()); m.addAttribute("estimateForm", f); return page(m, true);
    }
    @PostMapping("/admin/pricing/{id}/preview") @PreAuthorize("hasRole('ADMIN')") String draft(@PathVariable @jakarta.validation.constraints.Positive Long id,
            @Valid @ModelAttribute("estimateForm") GuestQuoteForm f, BindingResult e, Model m) { draftContext(id, m); return calculate(f, e, m, true); }
    @PostMapping("/admin/pricing/{id}/preview/branches") @PreAuthorize("hasRole('ADMIN')") String draftBranches(@PathVariable @jakarta.validation.constraints.Positive Long id,
            @ModelAttribute("estimateForm") GuestQuoteForm f, BindingResult e, Model m) { draftContext(id, m); return branches(f, e, m, true); }
    private void draftContext(Long id, Model m) {
        var book = admin.detail(id, 0).book(); m.addAttribute("previewVersion", id); m.addAttribute("previewCode", book.code());
        m.addAttribute("services", java.util.List.of(new com.ute.uteexpress.dto.shipment.ShipmentOptions.ServiceOption(book.serviceId(), book.service())));
    }
    private String calculate(GuestQuoteForm f, BindingResult e, Model m, boolean preview) {
        allowed(e);
        if (!e.hasErrors()) { try { m.addAttribute("estimate", m.containsAttribute("previewVersion") ? quotes.simulateDraft((Long) m.getAttribute("previewVersion"), f) : quotes.estimate(f)); }
            catch (ShipmentCreationException failure) { e.rejectValue(failure.getField(), "quote.configuration", failure.getMessage()); }
            catch (DataAccessException failure) { e.reject("quote.database", "Chưa thể báo giá. Vui lòng thử lại."); }
        }
        m.addAttribute("validationMessages", com.ute.uteexpress.dto.pricing.PricingForms.errors(e));
        return page(m, preview);
    }
    private String branches(GuestQuoteForm f, BindingResult e, Model m, boolean preview) {
        allowed(e);
        if (!e.hasErrors()) { try { m.addAttribute("choices", quotes.estimateBranches(f)); }
            catch (ShipmentCreationException failure) { e.rejectValue(failure.getField(), "quote.coverage", failure.getMessage()); }
        }
        m.addAttribute("validationMessages", com.ute.uteexpress.dto.pricing.PricingForms.errors(e));
        return page(m, preview);
    }
    private void allowed(BindingResult e) { if (e.getSuppressedFields().length > 0) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trường không được phép."); } }
    private String page(Model m, boolean preview) {
        m.addAttribute("provinces", ProvinceCatalog.all());
        if (!m.containsAttribute("validationMessages")) { m.addAttribute("validationMessages", java.util.List.of()); }
        m.addAttribute("adminPreview", preview); if (!m.containsAttribute("services")) { m.addAttribute("services", quotes.estimateServices()); }
        if (!m.containsAttribute("choices")) {
            try { m.addAttribute("choices", quotes.estimateBranches((GuestQuoteForm) m.getAttribute("estimateForm"))); }
            catch (ShipmentCreationException invalid) { m.addAttribute("choices", new QuoteService.BranchChoices(java.util.List.of(), java.util.List.of())); }
        }
        return "pricing/estimate";
    }
}
