package com.example.jialechatweb.ws;

import com.example.jialechatweb.chat.ChatMessage;
import com.example.jialechatweb.chat.MessageMapper;
import com.example.jialechatweb.oss.OssService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("!test")
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final MessageMapper messageMapper;
    private final com.example.jialechatweb.user.UserMapper userMapper;
    private final com.example.jialechatweb.chat.ContentFilterService contentFilterService;
    private final RedisMessaging redisMessaging;
    private final OssService ossService;

    public ChatWebSocketHandler(MessageMapper messageMapper, 
                                com.example.jialechatweb.user.UserMapper userMapper,
                                com.example.jialechatweb.chat.ContentFilterService contentFilterService,
                                ObjectProvider<RedisMessaging> redisMessagingProvider,
                                OssService ossService) {
        this.messageMapper = messageMapper;
        this.userMapper = userMapper;
        this.contentFilterService = contentFilterService;
        this.redisMessaging = redisMessagingProvider.getIfAvailable();
        this.ossService = ossService;
        if (this.redisMessaging != null) {
            this.redisMessaging.setHandler(this::handleBroadcast);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node = mapper.readTree(message.getPayload());
        String type = node.get("type").asText();
        JsonNode data = node.get("data");
        Long senderId = (Long) session.getAttributes().get("userId");
        switch (type) {
            case "send" -> handleSend(senderId, data);
            case "typing" -> handleTyping(senderId, data);
            case "read" -> handleRead(senderId, data);
            default -> { }
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            sessions.put(userId, session);
            System.out.println("WS Connected: User " + userId);
            broadcastOnlineCount();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {

        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            sessions.remove(userId);
            System.out.println("WS Closed: User " + userId + " Status: " + status);
            broadcastOnlineCount();
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Long userId = (Long) session.getAttributes().get("userId");
        System.err.println("WS Error: User " + userId + " - " + exception.getMessage());
        exception.printStackTrace();
    }

    private void handleSend(Long senderId, JsonNode data) {
        Long receiverId = data.hasNonNull("receiverId") ? data.get("receiverId").asLong() : null;
        Long groupId = data.hasNonNull("groupId") ? data.get("groupId").asLong() : null;

        if (receiverId == null && groupId == null) return;

        String content = data.get("content").asText();
        if (content == null || content.trim().isEmpty() || content.length() > 1000) {
            return;
        }

        ChatMessage msg = new ChatMessage();
        msg.setSenderId(senderId);
        msg.setContent(contentFilterService.filter(content));
        msg.setContentType(data.hasNonNull("contentType") ? data.get("contentType").asText() : "text");
        msg.setIsRead(false);
        msg.setReceiverId(receiverId);
        msg.setGroupId(groupId);

        Long conversationId = null;
        if (groupId != null) {
            // Group chat
            // conversationId can be left null or set to something specific if needed
        } else {
            // P2P chat
            long min = Math.min(senderId, receiverId);
            long max = Math.max(senderId, receiverId);
            conversationId = (min << 32) | (max & 0xFFFFFFFFL);
            msg.setConversationId(conversationId);
        }

        try {
            messageMapper.insert(msg);
            
            // Use String for IDs to avoid JavaScript precision loss with large integers (Long)
            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("id", String.valueOf(msg.getId()));
            payload.put("senderId", String.valueOf(senderId));
            payload.put("content", msg.getContent());
            payload.put("contentType", msg.getContentType());
            
            if (conversationId != null) payload.put("conversationId", String.valueOf(conversationId));
            if (receiverId != null) payload.put("receiverId", String.valueOf(receiverId));
            if (groupId != null) {
                payload.put("groupId", String.valueOf(groupId));
                userMapper.findById(senderId).ifPresent(u -> {
                    payload.put("senderName", u.getDisplayName());
                    payload.put("senderAvatar", ossService.generateSignedUrl(u.getAvatarUrl()));
                });
            }

            broadcast(new Event("message", payload));
        } catch (Exception e) {
            System.err.println("Error saving/broadcasting message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleTyping(Long senderId, JsonNode data) {
        Long toUserId = data.get("toUserId").asLong();
        // Generate conversationId for compatibility
        long min = Math.min(senderId, toUserId);
        long max = Math.max(senderId, toUserId);
        Long conversationId = (min << 32) | (max & 0xFFFFFFFFL);

        broadcast(new Event("typing", Map.of(
                "conversationId", String.valueOf(conversationId),
                "from", String.valueOf(senderId),
                "to", String.valueOf(toUserId)
        )));
    }

    private void handleRead(Long userId, JsonNode data) {
        // Read status is usually just local, or we can notify sender. 
        // For simplicity, skip broadcast or implement later.
    }

    public void broadcast(Event event) {
        if (redisMessaging != null) {
            redisMessaging.publish(event);
        } else {
            // deliver locally only if redis is not enabled
            deliver(event);
        }
    }

    private void handleBroadcast(String json) {
        try {
            Event event = mapper.readValue(json, Event.class);
            deliver(event);
        } catch (IOException ignored) {
        }
    }

    private void deliver(Event event) {
        // Security fix: Only deliver to relevant users
        Long senderId = null;
        Long receiverId = null;
        Long groupId = null;
        
        try {
            if ("message".equals(event.type)) {
                senderId = parseLong(event.data.get("senderId"));
                receiverId = parseLong(event.data.get("receiverId"));
                groupId = parseLong(event.data.get("groupId"));
            } else if ("typing".equals(event.type)) {
                senderId = parseLong(event.data.get("from"));
                receiverId = parseLong(event.data.get("to"));
            } else if ("friend_request".equals(event.type)) {
                receiverId = parseLong(event.data.get("receiverId"));
            } else if ("online_count".equals(event.type)) {
                // Broadcast to everyone
                broadcastToAll(event);
                return;
            }
        } catch (Exception e) {
            System.err.println("Error parsing IDs in deliver: " + e.getMessage());
            return;
        }

        if (groupId != null) {
            if (groupId == 1L) { // Public Channel
                broadcastToAll(event);
            } else {
                // TODO: Fetch group members and send
            }
        } else {
            if (senderId != null) sendToUser(senderId, event);
            if (receiverId != null) sendToUser(receiverId, event);
        }
    }

    private void broadcastToAll(Event event) {
        try {
            TextMessage msg = new TextMessage(mapper.writeValueAsString(event));
            for (WebSocketSession session : sessions.values()) {
                if (session.isOpen()) {
                    try {
                        synchronized (session) {
                            session.sendMessage(msg);
                        }
                    } catch (IOException ignored) {}
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void broadcastOnlineCount() {
        broadcast(new Event("online_count", Map.of("count", sessions.size())));
    }

    private Long parseLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).longValue();
        if (obj instanceof String) return Long.parseLong((String) obj);
        return null;
    }

    private void sendToUser(Long userId, Event event) {
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(mapper.writeValueAsString(event)));
                }
            } catch (IOException ignored) {
            }
        }
    }

    public static class Event {
        public String type;
        public Map<String, Object> data;
        public Event() {}
        public Event(String type, Map<String, Object> data) {
            this.type = type;
            this.data = data;
        }
    }
}
