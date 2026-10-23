package com.ute.uteexpress.security;

import com.ute.uteexpress.service.notifications.NotificationIdentity;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.server.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.server.HandshakeInterceptor;

/** WebSocket activity never extends the HTTP session lifetime. Close on logout/expiry/account revocation. */
@Component
public class NotificationSocketSessions implements HandshakeInterceptor, HttpSessionListener {
    private static final String SESSION = NotificationSocketSessions.class.getName();
    private final Map<String, WebSocketSession> sockets = new ConcurrentHashMap<>();
    private final NotificationIdentity identity;
    public NotificationSocketSessions(NotificationIdentity identity) { this.identity = identity; }
    @Override public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Map<String, Object> attrs) {
        if (!(request instanceof ServletServerHttpRequest servlet)) { return false; }
        HttpSession session = servlet.getServletRequest().getSession(false);
        if (session == null) { return false; }
        identity.require(request.getPrincipal()); attrs.put(SESSION, session); return true;
    }
    @Override public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Exception ex) { }
    public synchronized void opened(WebSocketSession socket) {
        long tabs = sockets.values().stream().filter(s -> s.getPrincipal() != null && s.getPrincipal().getName().equals(socket.getPrincipal().getName())).count();
        if (sockets.size() >= 1000 || tabs >= 8) {
            try { socket.close(CloseStatus.POLICY_VIOLATION); } catch (IOException ignored) { } return;
        }
        sockets.put(socket.getId(), socket);
    }
    public void closed(String id) { sockets.remove(id); }
    public boolean valid(String id) {
        WebSocketSession socket = sockets.get(id);
        if (socket == null) { return false; }
        try {
            HttpSession session = (HttpSession) socket.getAttributes().get(SESSION);
            Object stored = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            if (!(stored instanceof SecurityContext context) || context.getAuthentication() == null
                    || !context.getAuthentication().getName().equals(socket.getPrincipal().getName())
                    || (session.getMaxInactiveInterval() > 0 && System.currentTimeMillis() - session.getLastAccessedTime() >= session.getMaxInactiveInterval() * 1000L)) { return false; }
            identity.require(context.getAuthentication()); return true;
        } catch (RuntimeException deniedOrUnavailable) { return false; }
    }
    public void close(String id) {
        WebSocketSession socket = sockets.remove(id);
        if (socket != null) { try { socket.close(CloseStatus.POLICY_VIOLATION); } catch (IOException ignored) { } }
    }
    @Scheduled(fixedDelay = 10000) public void revokeExpired() {
        for (String id : sockets.keySet()) { if (!valid(id)) { close(id); } }
    }
    @Override public void sessionDestroyed(HttpSessionEvent event) {
        sockets.forEach((id, socket) -> {
            HttpSession session = (HttpSession) socket.getAttributes().get(SESSION);
            if (session != null && session.getId().equals(event.getSession().getId())) { close(id); }
        });
    }
}
