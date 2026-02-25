package com.example.jialechatweb.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
@Profile("!test")
@ConditionalOnProperty(name = "spring.data.redis.enabled", havingValue = "true", matchIfMissing = false)
public class RedisMessaging implements MessageListener {
    private final StringRedisTemplate redisTemplate;
    private final RedisMessageListenerContainer container;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Consumer<String> handler;
    private final ChannelTopic topic = new ChannelTopic("chat-broadcast");

    public RedisMessaging(StringRedisTemplate redisTemplate, RedisMessageListenerContainer container) {
        this.redisTemplate = redisTemplate;
        this.container = container;
        this.container.addMessageListener(this, topic);
    }

    public void setHandler(Consumer<String> handler) {
        this.handler = handler;
    }

    public void publish(Object payload) {
        try {
            redisTemplate.convertAndSend(topic.getTopic(), objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public void addOfflineMessage(Long userId, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            redisTemplate.opsForList().rightPush("offline:msg:" + userId, json);
            // Expire after 7 days
            redisTemplate.expire("offline:msg:" + userId, java.time.Duration.ofDays(7));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public java.util.List<String> getAndClearOfflineMessages(Long userId) {
        String key = "offline:msg:" + userId;
        // Retrieve all messages
        java.util.List<String> messages = redisTemplate.opsForList().range(key, 0, -1);
        if (messages == null || messages.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        // Clear the list safely preventing race condition
        redisTemplate.opsForList().trim(key, messages.size(), -1);
        return messages;
    }

    public void markUserOnline(Long userId) {
        redisTemplate.opsForValue().set("online:" + userId, "1", java.time.Duration.ofMinutes(10)); // Heartbeat ttl
    }

    public void markUserOffline(Long userId) {
        redisTemplate.delete("online:" + userId);
    }

    public boolean isUserOnline(Long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("online:" + userId));
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (handler != null) {
            handler.accept(new String(message.getBody()));
        }
    }
}
