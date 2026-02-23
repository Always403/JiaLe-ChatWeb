package com.example.jialechatweb.friend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FriendRequestLimiter {
    private final int maxPerMinute;
    private final long minIntervalMillis;
    private final Map<Long, Deque<Long>> userMinuteBuckets = new ConcurrentHashMap<>();
    private final Map<String, Long> pairLastSentAt = new ConcurrentHashMap<>();

    public FriendRequestLimiter(
            @Value("${friends.request.max-per-minute:5}") int maxPerMinute,
            @Value("${friends.request.min-interval-seconds:30}") int minIntervalSeconds) {
        this.maxPerMinute = maxPerMinute;
        this.minIntervalMillis = Math.max(1, minIntervalSeconds) * 1000L;
    }

    public boolean allowPerMinute(Long userId) {
        long now = System.currentTimeMillis();
        Deque<Long> bucket = userMinuteBuckets.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (bucket) {
            while (!bucket.isEmpty() && now - bucket.peekFirst() > 60_000) {
                bucket.pollFirst();
            }
            if (bucket.size() >= maxPerMinute) {
                return false;
            }
            bucket.addLast(now);
            return true;
        }
    }

    public boolean allowMinInterval(Long userId, Long targetId) {
        long now = System.currentTimeMillis();
        String key = userId + ":" + targetId;
        Long last = pairLastSentAt.get(key);
        if (last != null && now - last < minIntervalMillis) {
            return false;
        }
        pairLastSentAt.put(key, now);
        return true;
    }
}
