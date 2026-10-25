package com.ute.uteexpress.controller;

import com.ute.uteexpress.dto.admin.AuditViews.*;
import com.ute.uteexpress.service.reports.AdminAuditService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller @RequestMapping("/admin/audit") @PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {
    private final AdminAuditService service;
    public AdminAuditController(AdminAuditService service){this.service=service;}
    @GetMapping public String page(@ModelAttribute("query") Request query,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,Model model,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");model.addAttribute("labels",new com.ute.uteexpress.dto.admin.ReportLabels());model.addAttribute("audit",service.read(query,page,size));model.addAttribute("sources",Source.values());model.addAttribute("entityTypes",AdminAuditService.entityTypes());return "reports/audit";
    }
}
