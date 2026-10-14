package com.ute.uteexpress.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthController {

    @GetMapping("/login")
    public String login(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/account";
        }
        return "auth/login";
    }

    @GetMapping("/account")
    @PreAuthorize("isAuthenticated()")
    public String account(Authentication authentication, Model model) {
        model.addAttribute("email", authentication.getName());
        model.addAttribute("roles", authentication.getAuthorities().stream()
            .map(authority -> authority.getAuthority())
            .filter(authority -> authority.startsWith("ROLE_"))
            .sorted()
            .toList());
        return "auth/account";
    }
}
