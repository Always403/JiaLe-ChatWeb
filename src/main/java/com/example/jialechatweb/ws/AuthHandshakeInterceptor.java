package com.example.jialechatweb.ws;

import com.example.jialechatweb.security.JwtService;
import com.example.jialechatweb.user.UserService;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class AuthHandshakeInterceptor implements HandshakeInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(AuthHandshakeInterceptor.class);
    private final JwtService jwtService;
    private final UserService userService;

    public AuthHandshakeInterceptor(JwtService jwtService, UserService userService) {
        this.jwtService = jwtService;
        this.userService = userService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servlet) {
            String auth = servlet.getServletRequest().getParameter("token");
            if (!StringUtils.hasText(auth)) {
                String header = servlet.getServletRequest().getHeader("Authorization");
                if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
                    auth = header.substring(7);
                }
            }
            try {
                if (StringUtils.hasText(auth)) {
                    Claims claims = jwtService.parse(auth);
                    Long userId = Long.valueOf(claims.getSubject());
                    
                    // Verify user exists in DB
                    if (userService.findById(userId).isEmpty()) {
                        logger.warn("WebSocket handshake failed: User ID {} not found in database", userId);
                        response.setStatusCode(HttpStatus.UNAUTHORIZED);
                        return false;
                    }

                    attributes.put("userId", userId);
                    attributes.put("username", claims.get("username", String.class));
                    return true;
                } else {
                    logger.warn("WebSocket handshake failed: No token provided");
                }
            } catch (Exception e) {
                logger.error("WebSocket handshake failed: Invalid token", e);
            }
        }
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
    }
}
