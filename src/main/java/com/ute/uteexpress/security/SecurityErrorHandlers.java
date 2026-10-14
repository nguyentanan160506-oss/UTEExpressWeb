package com.ute.uteexpress.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class SecurityErrorHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        respond(request, response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException, ServletException {
        respond(request, response, HttpServletResponse.SC_FORBIDDEN, "Forbidden");
    }

    private void respond(HttpServletRequest request, HttpServletResponse response,
            int status, String title) throws IOException {
        if (prefersJson(request)) {
            response.setStatus(status);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            // Fixed values only: no exception, principal, request body or URL is exposed.
            response.getWriter().write(
                "{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status + "}");
        } else {
            // Let the servlet error page / Spring Boot render HTML for browser requests.
            response.sendError(status, title);
        }
    }

    private boolean prefersJson(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept == null || accept.isBlank()) {
            return false;
        }
        try {
            var accepted = new ArrayList<>(MediaType.parseMediaTypes(accept));
            accepted.sort(Comparator.comparingDouble(MediaType::getQualityValue).reversed());
            for (MediaType mediaType : accepted) {
                if (mediaType.getQualityValue() == 0) {
                    continue;
                }
                if (mediaType.includes(MediaType.TEXT_HTML)) {
                    return false;
                }
                if (mediaType.getType().equals("application")
                        && (mediaType.getSubtype().equals("json")
                            || mediaType.getSubtype().endsWith("+json"))) {
                    return true;
                }
            }
        } catch (IllegalArgumentException ignored) {
            // An invalid Accept header must not turn an authentication error into a 500.
        }
        return false;
    }
}
