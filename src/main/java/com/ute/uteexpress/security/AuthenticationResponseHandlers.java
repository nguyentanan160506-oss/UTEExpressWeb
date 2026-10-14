package com.ute.uteexpress.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

/** Response handling only; Spring Security owns authentication and session lifecycle. */
@Component
public class AuthenticationResponseHandlers implements AuthenticationSuccessHandler,
        AuthenticationFailureHandler, LogoutSuccessHandler {

    private final SecurityErrorHandlers errors;
    private final RedirectStrategy redirects = new DefaultRedirectStrategy();

    public AuthenticationResponseHandlers(SecurityErrorHandlers errors) {
        this.errors = errors;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        var session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
        }
        // Fixed target: never redirect to a client-supplied or undeclared destination.
        success(request, response, "/account");
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        if (errors.prefersJson(request)) {
            errors.commence(request, response, exception);
        } else {
            // No account-specific exception or credentials are stored or rendered.
            redirects.sendRedirect(request, response, "/login?error");
        }
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        success(request, response, "/login?logout");
    }

    private void success(HttpServletRequest request, HttpServletResponse response,
            String target) throws IOException {
        if (errors.prefersJson(request)) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } else {
            redirects.sendRedirect(request, response, target);
        }
    }
}
