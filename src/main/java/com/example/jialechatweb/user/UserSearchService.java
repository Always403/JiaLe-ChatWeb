package com.example.jialechatweb.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserSearchService {
    private final UserService userService;
    private final long cacheTtlMillis;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public UserSearchService(UserService userService,
                             @Value("${friends.search.cache-seconds:30}") int cacheSeconds) {
        this.userService = userService;
        this.cacheTtlMillis = Math.max(1, cacheSeconds) * 1000L;
    }

    public SearchResult search(Long currentUserId, String account) {
        if (account == null) {
            return new SearchResult(false, List.of());
        }
        String key = account.trim();
        CacheEntry cached = cache.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAt > now) {
            return cached.result;
        }
        SearchResult result = doSearch(currentUserId, key);
        cache.put(key, new CacheEntry(result, now + cacheTtlMillis));
        return result;
    }

    private SearchResult doSearch(Long currentUserId, String account) {
        if (account.contains("@")) {
            return userService.findByEmail(account)
                    .filter(u -> !u.getId().equals(currentUserId))
                    .map(u -> new SearchResult(true, List.of(new Suggestion(u.getUsername(), u.getDisplayName()))))
                    .orElse(new SearchResult(false, List.of()));
        }

        if (account.length() == 10 && account.matches("\\d{10}")) {
            return userService.findByUsername(account)
                    .filter(u -> !u.getId().equals(currentUserId))
                    .map(u -> new SearchResult(true, List.of(new Suggestion(u.getUsername(), u.getDisplayName()))))
                    .orElse(new SearchResult(false, List.of()));
        }
        if (!account.matches("\\d{1,10}")) {
            return new SearchResult(false, List.of());
        }
        if (account.length() < 3) {
            return new SearchResult(false, List.of());
        }
        List<User> users = userService.listByAccountPrefix(account, 5);
        List<Suggestion> suggestions = users.stream()
                .filter(u -> !u.getId().equals(currentUserId))
                .map(u -> new Suggestion(u.getUsername(), u.getDisplayName()))
                .toList();
        return new SearchResult(false, suggestions);
    }

    private record CacheEntry(SearchResult result, long expiresAt) {}

    public record Suggestion(String account, String displayName) {}
    public record SearchResult(boolean exists, List<Suggestion> suggestions) {}
}
