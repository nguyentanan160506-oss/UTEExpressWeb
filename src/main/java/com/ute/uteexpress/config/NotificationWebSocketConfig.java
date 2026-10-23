package com.ute.uteexpress.config;

import com.ute.uteexpress.security.NotificationSocketSessions;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.*;
import org.springframework.messaging.simp.config.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

@Configuration @EnableWebSocketMessageBroker @EnableWebSocketSecurity @EnableScheduling
public class NotificationWebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final NotificationSocketSessions sessions;
    public NotificationWebSocketConfig(NotificationSocketSessions sessions) { this.sessions = sessions; }
    @Bean ThreadPoolTaskScheduler notificationHeartbeatScheduler() {
        var scheduler = new ThreadPoolTaskScheduler(); scheduler.setPoolSize(1); scheduler.setThreadNamePrefix("notification-heartbeat-"); return scheduler;
    }
    @Bean ThreadPoolTaskScheduler taskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler(); scheduler.setPoolSize(2); scheduler.setThreadNamePrefix("notification-worker-"); return scheduler;
    }
    @Override public void registerStompEndpoints(StompEndpointRegistry endpoints) {
        // Empty allowed origins means same origin only. No SockJS, wildcard origins, or CSRF exceptions.
        endpoints.addEndpoint("/ws/notifications").addInterceptors(sessions);
    }
    @Override public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue").setHeartbeatValue(new long[]{10000, 10000}).setTaskScheduler(notificationHeartbeatScheduler());
        registry.setUserDestinationPrefix("/user"); registry.setApplicationDestinationPrefixes("/app");
        registry.setPreservePublishOrder(true);
    }
    @Bean AuthorizationManager<Message<?>> notificationMessageAuthorization(MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        return messages.simpTypeMatchers(SimpMessageType.CONNECT, SimpMessageType.UNSUBSCRIBE, SimpMessageType.DISCONNECT, SimpMessageType.HEARTBEAT).authenticated()
            .simpSubscribeDestMatchers("/user/queue/notifications").authenticated()
            .anyMessage().denyAll().build();
    }
    @Override public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.taskExecutor().corePoolSize(2).maxPoolSize(4).queueCapacity(200);
        registration.interceptors(new ChannelInterceptor() {
            @Override public Message<?> preSend(Message<?> message, MessageChannel channel) {
                var headers = StompHeaderAccessor.wrap(message);
                if (headers.getCommand() != StompCommand.DISCONNECT && !sessions.valid(headers.getSessionId())) {
                    sessions.close(headers.getSessionId()); throw new org.springframework.security.access.AccessDeniedException("Forbidden");
                }
                return message;
            }
        });
    }
    @Override public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor().corePoolSize(2).maxPoolSize(4).queueCapacity(200);
        registration.interceptors(new ChannelInterceptor() {
            @Override public Message<?> preSend(Message<?> message, MessageChannel channel) {
                String id = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
                if (id != null && SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) == SimpMessageType.MESSAGE && !sessions.valid(id)) {
                    sessions.close(id); return null;
                }
                return message;
            }
        });
    }
    @Override public void configureWebSocketTransport(WebSocketTransportRegistration transport) {
        transport.setMessageSizeLimit(4096).setSendBufferSizeLimit(16384).setSendTimeLimit(10000).setTimeToFirstMessage(10000);
        transport.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {
            @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception { sessions.opened(session); super.afterConnectionEstablished(session); }
            @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception { sessions.closed(session.getId()); super.afterConnectionClosed(session, status); }
        });
    }
    @Bean ServletListenerRegistrationBean<HttpSessionListener> notificationSessionListener() { return new ServletListenerRegistrationBean<>(sessions); }
}
