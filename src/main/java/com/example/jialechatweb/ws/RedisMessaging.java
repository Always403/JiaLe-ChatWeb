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

    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (handler != null) {
            handler.accept(new String(message.getBody()));
        }
    }
}
