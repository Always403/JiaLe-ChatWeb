package com.example.jialechatweb.friend;

import com.example.jialechatweb.oss.OssService;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/friends")
public class FriendController {
    private final FriendMapper friendMapper;
    private final OssService ossService;

    public FriendController(FriendMapper friendMapper, OssService ossService) {
        this.friendMapper = friendMapper;
        this.ossService = ossService;
    }

    public record AddRequest(@NotNull Long friendId, String remark) {}
    public record UpdateReq(@NotNull Long friendId, String remark) {}

    @PostMapping
    public ResponseEntity<?> add(@RequestAttribute("currentUserId") Long userId, @RequestBody AddRequest req) {
        Friend f = new Friend();
        f.setUserId(userId);
        f.setFriendId(req.friendId());
        f.setRemark(req.remark());
        f.setStatus("ACCEPTED");
        friendMapper.insert(f);
        return ResponseEntity.ok(Map.of("id", f.getId()));
    }

    @DeleteMapping("/{friendId}")
    public ResponseEntity<?> remove(@RequestAttribute("currentUserId") Long userId, @PathVariable Long friendId) {
        friendMapper.delete(userId, friendId);
        return ResponseEntity.ok().build();
    }

    @PutMapping
    public ResponseEntity<?> update(@RequestAttribute("currentUserId") Long userId, @RequestBody UpdateReq req) {
        friendMapper.updateRemark(userId, req.friendId(), req.remark());
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestAttribute("currentUserId") Long userId) {
        List<FriendDTO> friends = friendMapper.list(userId);
        friends.forEach(f -> {
            if (f.getAvatarUrl() != null) {
                f.setAvatarUrl(ossService.generateSignedUrl(f.getAvatarUrl()));
            }
        });
        return ResponseEntity.ok(friends);
    }
}
