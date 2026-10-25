package com.ute.uteexpress.controller;

import com.ute.uteexpress.dto.operations.OperationsDashboardViews.*;
import com.ute.uteexpress.enums.ShipmentStatus;
import com.ute.uteexpress.service.operations.*;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller @PreAuthorize("hasRole('MANAGER')")
public class OperationsDashboardController {
    @ModelAttribute("labels") public com.ute.uteexpress.dto.operations.OperationsLabels labels(){return new com.ute.uteexpress.dto.operations.OperationsLabels();}
    private final OperationsAccessService access;private final OperationsDashboardReadService read;
    public OperationsDashboardController(OperationsAccessService access,OperationsDashboardReadService read){this.access=access;this.read=read;}
    @GetMapping("/manager/operations") public String dashboard(@RequestParam(required=false) Long branchId,
            @RequestParam(defaultValue="LAST_7_DAYS") Period period,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,@RequestParam(required=false) ShipmentStatus status,
            @RequestParam(defaultValue="CONFIRM") WorkGroup group,@RequestParam(defaultValue="0") int page,Model m){
        var branches=access.branches();m.addAttribute("branches",branches);m.addAttribute("period",period);m.addAttribute("periods",Period.values());m.addAttribute("statuses",ShipmentStatus.values());
        if(branches.isEmpty() && branchId==null){m.addAttribute("emptyScope",true);return "operations/dashboard";}
        m.addAttribute("dashboard",read.read(branchId,period,from,to,status,group,page));return "operations/dashboard";
    }
}
