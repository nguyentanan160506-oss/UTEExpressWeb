package com.ute.uteexpress.controller;

import com.ute.uteexpress.dto.operations.CustomerTicketCommands.*;
import com.ute.uteexpress.dto.operations.CustomerTicketViews.Filter;
import com.ute.uteexpress.dto.operations.CustomerTicketLabels;
import com.ute.uteexpress.enums.TicketCategory;
import com.ute.uteexpress.service.operations.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequestMapping("/account/support") @PreAuthorize("hasRole('USER')")
public class CustomerSupportController {
    private final TicketService write;
    private final TicketCustomerReadService read;
    public CustomerSupportController(TicketService write,TicketCustomerReadService read){this.write=write;this.read=read;}
    @ModelAttribute void common(Model model,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");model.addAttribute("labels",new CustomerTicketLabels());
        model.addAttribute("categories",TicketCategory.values());model.addAttribute("filters",Filter.values());
    }
    @GetMapping
    public String list(@RequestParam(defaultValue="ALL") Filter filter,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size,Model model){
        model.addAttribute("tickets",read.list(filter,page,size));model.addAttribute("filter",filter);return "support/list";
    }
    @GetMapping("/new")
    public String create(@RequestParam(required=false) Long shipmentId,@RequestParam(defaultValue="0") int shipmentPage,Model model){
        model.addAttribute("form",new Create(shipmentId,null,"","",token()));options(shipmentId,shipmentPage,model);return "support/new";
    }
    @PostMapping
    public String create(@Valid @ModelAttribute("form") Create form,BindingResult errors,Model model,
            HttpServletResponse response,RedirectAttributes flash){
        if(errors.hasErrors()) {options(form.shipmentId(),0,model);response.setStatus(HttpStatus.BAD_REQUEST.value());return "support/new";}
        Long id=write.createCustomer(form);flash.addFlashAttribute("notice","Đã tiếp nhận yêu cầu hỗ trợ của bạn.");return "redirect:/account/support/"+id;
    }
    @GetMapping("/{ticketId}")
    public String detail(@PathVariable Long ticketId,@RequestParam(defaultValue="0") int page,Model model){
        model.addAttribute("detail",read.portalDetail(ticketId,page));model.addAttribute("messageToken",token());model.addAttribute("reopenToken",token());return "support/detail";
    }
    @PostMapping("/{ticketId}/messages")
    public String message(@PathVariable Long ticketId,@Valid @ModelAttribute Message form,RedirectAttributes flash){
        write.customerMessage(ticketId,form);flash.addFlashAttribute("notice","Đã gửi thông tin bổ sung. Chi nhánh sẽ tiếp tục xem xét.");return "redirect:/account/support/"+ticketId;
    }
    @PostMapping("/{ticketId}/reopen")
    public String reopen(@PathVariable Long ticketId,@Valid @ModelAttribute Message form,RedirectAttributes flash){
        write.requestCustomerReopen(ticketId,form);flash.addFlashAttribute("notice","Đã gửi yêu cầu mở lại để Manager xem xét. Trạng thái chưa thay đổi.");return "redirect:/account/support/"+ticketId;
    }
    private void options(Long shipmentId,int page,Model model){
        model.addAttribute("shipments",read.shipmentOptions(page));
        if(shipmentId!=null)model.addAttribute("selectedShipment",read.shipment(shipmentId));
    }
    private String token(){return UUID.randomUUID().toString().replace("-","");}
}
