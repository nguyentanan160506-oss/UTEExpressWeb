package com.ute.uteexpress.controller;

import com.ute.uteexpress.dto.transport.*;
import com.ute.uteexpress.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequestMapping("/manager/trips") @PreAuthorize("hasRole('MANAGER')")
public class TransportTripController {
    private final TransportTripReadService read;
    private final TransportTripService operations;
    public TransportTripController(TransportTripReadService read, TransportTripService operations) { this.read = read; this.operations = operations; }
    @InitBinder("createForm") void createFields(WebDataBinder b) { b.setAllowedFields("routeId", "requestToken", "_csrf"); }
    @InitBinder("manifestForm") void manifestFields(WebDataBinder b) { b.setAllowedFields("tripId", "expectedVersion", "shipmentIds", "_csrf"); }
    @InitBinder("actionForm") void actionFields(WebDataBinder b) { b.setAllowedFields("tripId", "expectedVersion", "confirmed", "reference", "notes", "_csrf"); }
    @InitBinder("receiveForm") void receiveFields(WebDataBinder b) { b.setAllowedFields("tripId", "expectedVersion", "itemId", "confirmed", "reference", "notes", "resolveDiscrepancy", "_csrf"); }
    @InitBinder("discrepancyForm") void discrepancyFields(WebDataBinder b) { b.setAllowedFields("tripId", "expectedVersion", "itemId", "kind", "reference", "notes", "_csrf"); }
    @InitBinder("resolveForm") void resolveFields(WebDataBinder b) { b.setAllowedFields("tripId", "expectedVersion", "discrepancyId", "confirmed", "reference", "notes", "_csrf"); }
    @InitBinder("localForm") void localFields(WebDataBinder b) { b.setAllowedFields("shipmentId", "expectedVersion", "confirmed", "reference", "notes", "_csrf"); }
    @ModelAttribute void labels(Model m) {
        m.addAttribute("tripLabels", TripLabels.STATUS); m.addAttribute("legLabels", TripLabels.LEG); m.addAttribute("itemLabels", TripLabels.ITEM);
        m.addAttribute("issueLabels", TripLabels.ISSUE); m.addAttribute("eventLabels", TripLabels.EVENT);
    }
    @GetMapping({"", "/"}) String list(@RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page, Model m) {
        m.addAttribute("trips", read.list(page)); return "transport/list";
    }
    @GetMapping("/new") String newTrip(@RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page, Model m) {
        m.addAttribute("routes", read.routes(page)); m.addAttribute("requestToken", UUID.randomUUID().toString().replace("-", "")); return "transport/new";
    }
    @PostMapping String create(@Valid @ModelAttribute("createForm") TripForms.Create form, BindingResult errors, RedirectAttributes flash) {
        return mutate(errors, flash, "/manager/trips/new", () -> "/manager/trips/" + operations.create(new TripCommands.Create(form.getRouteId(), form.getRequestToken())));
    }
    @GetMapping("/{tripId}") String detail(@PathVariable @Positive Long tripId,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int itemPage,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int candidatePage,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int issuePage,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int eventPage, Model m) {
        m.addAttribute("detail", read.detail(tripId, itemPage, candidatePage, issuePage, eventPage)); return "transport/detail";
    }
    @PostMapping("/{tripId}/items") String add(@PathVariable @Positive Long tripId, @Valid @ModelAttribute("manifestForm") TripForms.Manifest f, BindingResult e, RedirectAttributes a) {
        return change(tripId, e, a, () -> operations.addItems(new TripCommands.Manifest(tripId, f.getExpectedVersion(), f.getShipmentIds())));
    }
    @PostMapping("/{tripId}/seal") String seal(@PathVariable @Positive Long tripId, @Valid @ModelAttribute("actionForm") TripForms.Action f, BindingResult e, RedirectAttributes a) {
        return change(tripId, e, a, () -> operations.seal(command(tripId, f)));
    }
    @PostMapping("/{tripId}/depart") String depart(@PathVariable @Positive Long tripId, @Valid @ModelAttribute("actionForm") TripForms.Action f, BindingResult e, RedirectAttributes a) {
        return change(tripId, e, a, () -> operations.depart(command(tripId, f)));
    }
    @PostMapping("/{tripId}/arrive") String arrive(@PathVariable @Positive Long tripId, @Valid @ModelAttribute("actionForm") TripForms.Action f, BindingResult e, RedirectAttributes a) {
        return change(tripId, e, a, () -> operations.arrive(command(tripId, f)));
    }
    @PostMapping("/{tripId}/receipts") String receive(@PathVariable @Positive Long tripId, @Valid @ModelAttribute("receiveForm") TripForms.Receive f, BindingResult e, RedirectAttributes a) {
        return change(tripId, e, a, () -> operations.receive(new TripCommands.Receive(tripId, f.getExpectedVersion(), f.getItemId(), f.isConfirmed(), f.getReference(), f.getNotes(), f.isResolveDiscrepancy())));
    }
    @PostMapping("/{tripId}/discrepancies") String discrepancy(@PathVariable @Positive Long tripId, @Valid @ModelAttribute("discrepancyForm") TripForms.Discrepancy f, BindingResult e, RedirectAttributes a) {
        return change(tripId, e, a, () -> operations.recordDiscrepancy(new TripCommands.Discrepancy(tripId, f.getExpectedVersion(), f.getItemId(), f.getKind(), f.getReference(), f.getNotes())));
    }
    @PostMapping("/{tripId}/discrepancies/resolve") String resolve(@PathVariable @Positive Long tripId, @Valid @ModelAttribute("resolveForm") TripForms.Resolve f, BindingResult e, RedirectAttributes a) {
        return change(tripId, e, a, () -> operations.resolveUnexpected(new TripCommands.Resolve(tripId, f.getExpectedVersion(), f.getDiscrepancyId(), f.isConfirmed(), f.getReference(), f.getNotes())));
    }
    @PostMapping("/{tripId}/close") String close(@PathVariable @Positive Long tripId, @Valid @ModelAttribute("actionForm") TripForms.Action f, BindingResult e, RedirectAttributes a) {
        return change(tripId, e, a, () -> operations.close(command(tripId, f)));
    }
    @GetMapping("/local-ready") String local(@RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page, Model m) {
        m.addAttribute("shipments", read.localReady(page)); return "transport/local";
    }
    @PostMapping("/local-ready") String localReady(@Valid @ModelAttribute("localForm") TripForms.LocalReady f, BindingResult e, RedirectAttributes a) {
        return mutate(e, a, "/manager/trips/local-ready", () -> { operations.readyAtSameBranch(new TripCommands.LocalReady(f.getShipmentId(), f.getExpectedVersion(), f.isConfirmed(), f.getReference(), f.getNotes())); return "/manager/trips/local-ready"; });
    }
    @GetMapping("/shipments/{shipmentId}/history") String history(@PathVariable @Positive Long shipmentId,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page, Model m) {
        m.addAttribute("history", read.history(shipmentId, page)); m.addAttribute("shipmentId", shipmentId); return "transport/history";
    }
    private TripCommands.Change command(Long id, TripForms.Action f) { return new TripCommands.Change(id, f.getExpectedVersion(), f.isConfirmed(), f.getReference(), f.getNotes()); }
    private String change(Long id, BindingResult e, RedirectAttributes a, Runnable operation) {
        String target = "/manager/trips/" + id; return mutate(e, a, target, () -> { operation.run(); return target; });
    }
    private String mutate(BindingResult e, RedirectAttributes a, String target, Supplier<String> operation) {
        if (e.getSuppressedFields().length > 0) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trường không được phép."); }
        if (e.hasErrors()) { a.addFlashAttribute("error", "Thông tin chưa hợp lệ. Vui lòng kiểm tra và gửi lại."); }
        else {
            try { target = operation.get(); a.addFlashAttribute("notice", "Đã ghi nhận thao tác."); }
            catch (OptimisticLockingFailureException conflict) { a.addFlashAttribute("error", "Dữ liệu đã thay đổi. Vui lòng tải lại trước khi thao tác."); }
            catch (LogisticsRuleException rule) { a.addFlashAttribute("error", rule.getMessage()); }
            catch (DataAccessException failure) { a.addFlashAttribute("error", "Không thể ghi nhận; vui lòng tải lại kiểm tra. Chưa xác nhận thành công."); }
        }
        return "redirect:" + target;
    }
}
