package com.ute.uteexpress.controller;

import com.ute.uteexpress.service.AdminRuleException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@ControllerAdvice(assignableTypes={AdminReportsController.class,AdminAuditController.class})
public class AdminReportingErrorAdvice {
    @ExceptionHandler(AdminRuleException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String rule(AdminRuleException e,Model model){model.addAttribute("message",e.getMessage());return "reports/error";}
    @ExceptionHandler({BindException.class,MethodArgumentTypeMismatchException.class}) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String invalid(Exception e,Model model){model.addAttribute("message","Bộ lọc chưa hợp lệ. Kiểm tra ngày UTC, nhóm báo cáo, trạng thái và ID.");return "reports/error";}
    @ExceptionHandler(DataAccessException.class) @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public String unavailable(Exception e,Model model){model.addAttribute("message","Báo cáo tạm thời chưa sẵn sàng. Thu hẹp bộ lọc hoặc thử lại sau.");return "reports/error";}
}
