package com.ute.uteexpress.controller;

import com.ute.uteexpress.service.operations.OperationsRuleException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.*;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@ControllerAdvice(assignableTypes=CustomerSupportController.class)
public class CustomerSupportErrorAdvice {
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public String response(org.springframework.web.server.ResponseStatusException e,Model m,jakarta.servlet.http.HttpServletResponse response){
        response.setStatus(e.getStatusCode().value());response.setHeader("Cache-Control","no-store");
        e.getHeaders().forEach((name,values)->response.setHeader(name,String.join(",",values)));
        m.addAttribute("message",e.getReason());return "support/error";
    }
    @ExceptionHandler(OperationsRuleException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String rule(OperationsRuleException e,Model m){m.addAttribute("message",e.getMessage());return "support/error";}
    @ExceptionHandler({BindException.class,ConstraintViolationException.class,HandlerMethodValidationException.class,MethodArgumentTypeMismatchException.class}) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String invalid(Exception e,Model m){m.addAttribute("message","Kiểm tra vận đơn, loại vấn đề và các trường bắt buộc. Tiêu đề tối đa 200 ký tự; nội dung/lý do tối đa 2.000 ký tự. Vui lòng tải lại form nếu dữ liệu chưa hợp lệ.");return "support/error";}
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class,PessimisticLockingFailureException.class,DataIntegrityViolationException.class}) @ResponseStatus(HttpStatus.CONFLICT)
    public String conflict(Exception e,Model m){m.addAttribute("message","Yêu cầu đã được cập nhật hoặc xử lý. Hãy tải lại trang chi tiết trước khi thao tác tiếp.");return "support/error";}
    @ExceptionHandler(DataAccessException.class) @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public String unavailable(Exception e,Model m){m.addAttribute("message","Dữ liệu hỗ trợ tạm thời chưa sẵn sàng. Vui lòng thử lại sau.");return "support/error";}
}
