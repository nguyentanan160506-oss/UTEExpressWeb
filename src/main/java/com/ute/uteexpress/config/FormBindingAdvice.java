package com.ute.uteexpress.config;

import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.servlet.support.ExtendedServletRequestDataBinder;

/** Business forms bind request parameters and URI variables, never transport headers. */
@ControllerAdvice(basePackages = "com.ute.uteexpress.controller")
public class FormBindingAdvice {
    @InitBinder
    void excludeRequestHeaders(WebDataBinder binder) {
        if (binder instanceof ExtendedServletRequestDataBinder servletBinder) {
            // Keep each controller's allowed-field and suppressed-field checks intact.
            servletBinder.addHeaderPredicate(header -> false);
        }
    }
}
