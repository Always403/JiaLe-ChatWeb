package com.example.jialechatweb.friend;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/friends")
public class FriendRequestController {
    private final FriendRequestService friendRequestService;

    public FriendRequestController(FriendRequestService friendRequestService) {
        this.friendRequestService = friendRequestService;
    }

    public record FriendRequestBody(@NotBlank String account) {}
    public record FriendRequestAction(@NotNull Long requesterId) {}

    @PostMapping("/request")
    public ResponseEntity<?> send(@RequestAttribute("currentUserId") Long userId, @RequestBody FriendRequestBody body) {
        try {
            FriendRequestService.SendResult result = friendRequestService.sendRequest(userId, body.account());
            return ResponseEntity.ok(Map.of(
                    "status", "PENDING",
                    "requestId", result.requestId(),
                    "account", result.account(),
                    "displayName", result.displayName(),
                    "estimatedMinutes", result.estimatedMinutes()
            ));
        } catch (RateLimitException ex) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/request")
    public ResponseEntity<?> cancel(@RequestAttribute("currentUserId") Long userId, @RequestParam String account) {
        try {
            friendRequestService.cancelRequest(userId, account);
            return ResponseEntity.ok(Map.of("status", "CANCELED"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/requests/incoming")
    public ResponseEntity<?> incoming(@RequestAttribute("currentUserId") Long userId) {
        return ResponseEntity.ok(Map.of("items", friendRequestService.listIncomingRequests(userId)));
    }

    @PostMapping("/request/accept")
    public ResponseEntity<?> accept(@RequestAttribute("currentUserId") Long userId, @RequestBody FriendRequestAction body) {
        try {
            friendRequestService.acceptRequest(userId, body.requesterId());
            return ResponseEntity.ok(Map.of("status", "ACCEPTED"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/request/reject")
    public ResponseEntity<?> reject(@RequestAttribute("currentUserId") Long userId, @RequestBody FriendRequestAction body) {
        try {
            friendRequestService.rejectRequest(userId, body.requesterId());
            return ResponseEntity.ok(Map.of("status", "REJECTED"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }
}
