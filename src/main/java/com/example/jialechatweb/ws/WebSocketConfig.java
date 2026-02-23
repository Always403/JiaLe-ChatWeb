package com.example.jialechatweb.ws;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

@Configuration
@EnableWebSocket
@Profile("!test")
public class WebSocketConfig implements WebSocketConfigurer {

    private final ObjectProvider<ChatWebSocketHandler> chatWebSocketHandlerProvider;
    private final AuthHandshakeInterceptor authHandshakeInterceptor;

    public WebSocketConfig(ObjectProvider<ChatWebSocketHandler> chatWebSocketHandlerProvider, AuthHandshakeInterceptor authHandshakeInterceptor) {
        this.chatWebSocketHandlerProvider = chatWebSocketHandlerProvider;
        this.authHandshakeInterceptor = authHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        ChatWebSocketHandler handler = chatWebSocketHandlerProvider.getIfAvailable();
        if (handler != null) {
            registry.addHandler(handler, "/ws")
                    .addInterceptors(authHandshakeInterceptor)
                    .setAllowedOrigins("*");
        }
    }
}
