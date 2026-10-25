package com.ute.uteexpress.controller;

import com.ute.uteexpress.dto.admin.ReportViews.*;
import com.ute.uteexpress.service.reports.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller @RequestMapping("/admin/reports") @PreAuthorize("hasRole('ADMIN')")
public class AdminReportsController {
    private final AdminReportService reports;private final ReportCsvService csv;
    public AdminReportsController(AdminReportService reports,ReportCsvService csv){this.reports=reports;this.csv=csv;}
    @ModelAttribute void common(Model model,HttpServletResponse response){response.setHeader("Cache-Control","no-store");model.addAttribute("labels",new com.ute.uteexpress.dto.admin.ReportLabels());model.addAttribute("datasets",Dataset.values());model.addAttribute("periods",Period.values());model.addAttribute("trends",Trend.values());}
    @GetMapping public String page(@ModelAttribute("query") Request query,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
            @RequestParam(defaultValue="0") int branchPage,@RequestParam(defaultValue="0") int servicePage,Model model){
        model.addAttribute("report",reports.read(query,page,size));model.addAttribute("branches",reports.branches(branchPage));model.addAttribute("services",reports.services(servicePage));model.addAttribute("branchPage",branchPage);model.addAttribute("servicePage",servicePage);return "reports/index";
    }
    @GetMapping("/export") public ResponseEntity<byte[]> export(@ModelAttribute Request query,@RequestParam(defaultValue="DETAILS") Export kind){
        var file=csv.export(query,kind);return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .contentType(new MediaType("text","csv",java.nio.charset.StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.filename()).build().toString()).body(file.bytes());
    }
}
