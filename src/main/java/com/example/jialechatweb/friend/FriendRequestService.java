package com.example.jialechatweb.friend;

import com.example.jialechatweb.user.User;
import com.example.jialechatweb.user.UserService;
import com.example.jialechatweb.ws.ChatWebSocketHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
public class FriendRequestService {
    private final FriendMapper friendMapper;
    private final UserService userService;
    private final FriendRequestLogMapper logMapper;
    private final FriendRequestLimiter limiter;
    private final ObjectProvider<ChatWebSocketHandler> chatWebSocketHandlerProvider;
    private final int maxPerDay;
    private final int maxFriends;

    public FriendRequestService(
            FriendMapper friendMapper,
            UserService userService,
            FriendRequestLogMapper logMapper,
            FriendRequestLimiter limiter,
            ObjectProvider<ChatWebSocketHandler> chatWebSocketHandlerProvider,
            @Value("${friends.request.max-per-day:20}") int maxPerDay,
            @Value("${friends.max-count:500}") int maxFriends) {
        this.friendMapper = friendMapper;
        this.userService = userService;
        this.logMapper = logMapper;
        this.limiter = limiter;
        this.chatWebSocketHandlerProvider = chatWebSocketHandlerProvider;
        this.maxPerDay = maxPerDay;
        this.maxFriends = maxFriends;
    }

    @Transactional
    public SendResult sendRequest(Long userId, String account) {
        if (!isValidAccount(account)) {
            throw new IllegalArgumentException("账号必须为10位数字");
        }
        User target = userService.findByUsername(account).orElseThrow(() -> new IllegalArgumentException("账号不存在"));
        if (target.getId().equals(userId)) {
            throw new IllegalArgumentException("不能添加自己");
        }
        if (!limiter.allowPerMinute(userId)) {
            throw new RateLimitException("请求过于频繁，请稍后再试");
        }
        if (!limiter.allowMinInterval(userId, target.getId())) {
            throw new RateLimitException("请求过于频繁，请稍后再试");
        }
        int acceptedCount = friendMapper.countAccepted(userId);
        if (acceptedCount >= maxFriends) {
            throw new IllegalArgumentException("已达好友上限");
        }
        int dailyCount = logMapper.countByUserSince(userId, "SEND", Instant.now().minus(1, ChronoUnit.DAYS));
        if (dailyCount >= maxPerDay) {
            throw new IllegalArgumentException("今日好友请求次数已达上限");
        }
        List<Friend> relations = friendMapper.listBetween(userId, target.getId());
        for (Friend r : relations) {
            if ("ACCEPTED".equals(r.getStatus())) {
                throw new IllegalArgumentException("已是好友");
            }
            if ("PENDING".equals(r.getStatus())) {
                if (r.getUserId().equals(userId)) {
                    throw new IllegalArgumentException("请求已发送");
                } else {
                    throw new IllegalArgumentException("对方已向你发出请求");
                }
            }
        }

        Friend req = new Friend();
        req.setUserId(userId);
        req.setFriendId(target.getId());
        req.setStatus("PENDING");
        friendMapper.insert(req);

        FriendRequestLog log = new FriendRequestLog();
        log.setUserId(userId);
        log.setTargetId(target.getId());
        log.setAction("SEND");
        logMapper.insert(log);

        User requester = userService.findById(userId).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        notifyFriendRequest(target.getId(), userId, req.getId(), requester.getUsername(), requester.getDisplayName());

        return new SendResult(req.getId(), target.getUsername(), target.getDisplayName(), 5);
    }

    @Transactional
    public void cancelRequest(Long userId, String account) {
        if (!isValidAccount(account)) {
            throw new IllegalArgumentException("账号必须为10位数字");
        }
        User target = userService.findByUsername(account).orElseThrow(() -> new IllegalArgumentException("账号不存在"));
        int removed = friendMapper.deletePending(userId, target.getId());
        if (removed == 0) {
            throw new IllegalArgumentException("无可取消的请求");
        }
        FriendRequestLog log = new FriendRequestLog();
        log.setUserId(userId);
        log.setTargetId(target.getId());
        log.setAction("CANCEL");
        logMapper.insert(log);
    }

    public List<FriendRequestView> listIncomingRequests(Long userId) {
        return friendMapper.listIncomingPending(userId);
    }

    @Transactional
    public void acceptRequest(Long userId, Long requesterId) {
        if (requesterId == null) {
            throw new IllegalArgumentException("请求参数不完整");
        }
        List<Friend> relations = friendMapper.listBetween(userId, requesterId);
        boolean hasPendingFromRequester = false;
        boolean hasRowFromMe = false;
        boolean hasAccepted = false;
        for (Friend r : relations) {
            if ("ACCEPTED".equals(r.getStatus())) {
                hasAccepted = true;
            }
            if ("PENDING".equals(r.getStatus())) {
                if (r.getUserId().equals(requesterId) && r.getFriendId().equals(userId)) {
                    hasPendingFromRequester = true;
                }
                if (r.getUserId().equals(userId) && r.getFriendId().equals(requesterId)) {
                    hasRowFromMe = true;
                }
            }
            if (r.getUserId().equals(userId) && r.getFriendId().equals(requesterId)) {
                hasRowFromMe = true;
            }
        }
        if (hasAccepted) {
            throw new IllegalArgumentException("已是好友");
        }
        if (!hasPendingFromRequester) {
            throw new IllegalArgumentException("无可处理的请求");
        }
        friendMapper.updateStatus(requesterId, userId, "ACCEPTED");
        if (hasRowFromMe) {
            friendMapper.updateStatus(userId, requesterId, "ACCEPTED");
        } else {
            User requester = userService.findById(requesterId).orElseThrow(() -> new IllegalArgumentException("账号不存在"));
            Friend reciprocal = new Friend();
            reciprocal.setUserId(userId);
            reciprocal.setFriendId(requesterId);
            reciprocal.setRemark(requester.getDisplayName());
            reciprocal.setStatus("ACCEPTED");
            friendMapper.insert(reciprocal);
        }
        FriendRequestLog log = new FriendRequestLog();
        log.setUserId(userId);
        log.setTargetId(requesterId);
        log.setAction("ACCEPT");
        logMapper.insert(log);
    }

    @Transactional
    public void rejectRequest(Long userId, Long requesterId) {
        if (requesterId == null) {
            throw new IllegalArgumentException("请求参数不完整");
        }
        int removed = friendMapper.deletePending(requesterId, userId);
        if (removed == 0) {
            throw new IllegalArgumentException("无可处理的请求");
        }
        FriendRequestLog log = new FriendRequestLog();
        log.setUserId(userId);
        log.setTargetId(requesterId);
        log.setAction("REJECT");
        logMapper.insert(log);
    }

    private boolean isValidAccount(String account) {
        return account != null && account.matches("\\d{10}");
    }

    private void notifyFriendRequest(Long receiverId, Long requesterId, Long requestId, String account, String displayName) {
        ChatWebSocketHandler handler = chatWebSocketHandlerProvider.getIfAvailable();
        if (handler == null) {
            return;
        }
        handler.broadcast(new ChatWebSocketHandler.Event("friend_request", Map.of(
                "receiverId", receiverId,
                "requesterId", requesterId,
                "requestId", requestId,
                "account", account,
                "displayName", displayName
        )));
    }

    public record SendResult(Long requestId, String account, String displayName, int estimatedMinutes) {}
}
