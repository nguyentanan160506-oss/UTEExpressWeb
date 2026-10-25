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

@ControllerAdvice(assignableTypes={OperationsDashboardController.class,ManagerTicketController.class})
public class OperationsErrorAdvice {
    @ExceptionHandler(OperationsRuleException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String rule(OperationsRuleException e,Model m){m.addAttribute("message",e.getMessage());return "operations/error";}
    @ExceptionHandler({BindException.class,ConstraintViolationException.class,HandlerMethodValidationException.class,MethodArgumentTypeMismatchException.class}) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String invalid(Exception e,Model m){m.addAttribute("message","Dữ liệu chưa hợp lệ. Kiểm tra các trường bắt buộc, giới hạn độ dài, ngày và bộ lọc.");return "operations/error";}
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class,PessimisticLockingFailureException.class,DataIntegrityViolationException.class}) @ResponseStatus(HttpStatus.CONFLICT)
    public String conflict(Exception e,Model m){m.addAttribute("message","Dữ liệu đã thay đổi hoặc yêu cầu đã được xử lý. Hãy tải lại trang trước khi thao tác.");return "operations/error";}
    @ExceptionHandler(DataAccessException.class) @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public String unavailable(Exception e,Model m){m.addAttribute("message","Dữ liệu vận hành tạm thời chưa sẵn sàng. Vui lòng thử lại sau.");return "operations/error";}
    @ExceptionHandler(QueryTimeoutException.class) @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public String timeout(Exception e,Model m){m.addAttribute("message","Báo cáo tạm thời chưa sẵn sàng. Thu hẹp khoảng ngày và thử lại.");return "operations/error";}
}
