package com.ute.uteexpress.controller;

import com.ute.uteexpress.dto.operations.TicketCommands.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.operations.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequestMapping("/manager/tickets") @PreAuthorize("hasRole('MANAGER')")
public class ManagerTicketController {
    @ModelAttribute("labels") public com.ute.uteexpress.dto.operations.OperationsLabels labels(){return new com.ute.uteexpress.dto.operations.OperationsLabels();}
    private final TicketService write;private final TicketReadService read;private final OperationsAccessService access;
    public ManagerTicketController(TicketService write,TicketReadService read,OperationsAccessService access){this.write=write;this.read=read;this.access=access;}
    @ModelAttribute void options(Model m){m.addAttribute("ticketStatuses",TicketStatus.values());m.addAttribute("priorities",TicketPriority.values());m.addAttribute("categories",TicketCategory.values());m.addAttribute("sources",new TicketSource[]{TicketSource.MANAGER_PHONE,TicketSource.MANAGER_EMAIL,TicketSource.MANAGER_COUNTER});}
    @GetMapping public String list(@RequestParam(required=false) Long branchId,@RequestParam(required=false) TicketStatus status,@RequestParam(required=false) TicketPriority priority,
            @RequestParam(required=false) TicketCategory category,@RequestParam(required=false) Long assignee,@RequestParam(defaultValue="false") boolean unassigned,
            @RequestParam(defaultValue="false") boolean overdue,@RequestParam(defaultValue="false") boolean mine,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="createdAt") String sort,@RequestParam(defaultValue="0") int candidatePage,Model m){
        var branches=access.branches();m.addAttribute("branches",branches);if(branches.isEmpty() && branchId==null){m.addAttribute("emptyScope",true);return "tickets/list";}
        var scope=access.scope(branchId);Long target=mine?scope.actor().getId():assignee;
        m.addAttribute("tickets",read.list(scope.branch().getId(),status,priority,category,target,unassigned,overdue,page,size,sort));
        m.addAttribute("candidates",read.candidates(scope.branch().getId(),candidatePage));m.addAttribute("branchId",scope.branch().getId());m.addAttribute("selectedStatus",status);m.addAttribute("selectedPriority",priority);m.addAttribute("selectedCategory",category);m.addAttribute("assignee",target);m.addAttribute("unassigned",unassigned);m.addAttribute("overdue",overdue);m.addAttribute("mine",mine);m.addAttribute("sort",sort);return "tickets/list";
    }
    @GetMapping("/new") public String create(@RequestParam(required=false) Long branchId,Model m){m.addAttribute("branches",access.branches());m.addAttribute("selectedBranch",branchId);if(branchId!=null)access.scope(branchId);m.addAttribute("token",UUID.randomUUID().toString().replace("-",""));return "tickets/new";}
    @PostMapping public String create(@Valid @ModelAttribute Create c,RedirectAttributes flash){Long id=write.create(c);flash.addFlashAttribute("notice","Đã ghi nhận ticket do Manager tiếp nhận; không giả mạo khách hàng gửi yêu cầu.");return "redirect:/manager/tickets/"+id;}
    @GetMapping("/{id}") public String detail(@PathVariable Long id,@RequestParam(defaultValue="0") int messagePage,@RequestParam(defaultValue="0") int eventPage,@RequestParam(defaultValue="0") int candidatePage,Model m){m.addAttribute("detail",read.detail(id,messagePage,eventPage,candidatePage));return "tickets/detail";}
    @PostMapping("/{id}/assign") public String assign(@PathVariable Long id,@Valid @ModelAttribute Assign c,RedirectAttributes f){write.assign(id,c);return done(id,f);}
    @PostMapping("/{id}/status") public String status(@PathVariable Long id,@Valid @ModelAttribute ChangeStatus c,RedirectAttributes f){write.changeStatus(id,c);return done(id,f);}
    @PostMapping("/{id}/classify") public String classify(@PathVariable Long id,@Valid @ModelAttribute Classify c,RedirectAttributes f){write.classify(id,c);return done(id,f);}
    @PostMapping("/{id}/notes") public String notes(@PathVariable Long id,@Valid @ModelAttribute Message c,RedirectAttributes f){write.note(id,c);return done(id,f);}
    @PostMapping("/{id}/reply") public String reply(@PathVariable Long id,@Valid @ModelAttribute Message c,RedirectAttributes f){write.reply(id,c);return done(id,f);}
    @PostMapping("/{id}/resolve") public String resolve(@PathVariable Long id,@Valid @ModelAttribute Decision c,RedirectAttributes f){write.resolve(id,c);return done(id,f);}
    @PostMapping("/{id}/close") public String close(@PathVariable Long id,@Valid @ModelAttribute Decision c,RedirectAttributes f){write.close(id,c);return done(id,f);}
    @PostMapping("/{id}/reopen") public String reopen(@PathVariable Long id,@Valid @ModelAttribute Decision c,RedirectAttributes f){write.reopen(id,c);return done(id,f);}
    private String done(Long id,RedirectAttributes flash){flash.addFlashAttribute("notice","Đã lưu thao tác và lịch sử xử lý.");return "redirect:/manager/tickets/"+id;}
}
