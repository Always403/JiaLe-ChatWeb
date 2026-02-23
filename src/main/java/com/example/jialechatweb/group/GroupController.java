package com.example.jialechatweb.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/groups")
public class GroupController {
    private final GroupMapper groupMapper;

    public GroupController(GroupMapper groupMapper) {
        this.groupMapper = groupMapper;
    }

    public record CreateReq(@NotBlank String name) {}
    public record MemberReq(@NotNull Long userId) {}

    @PostMapping
    public ResponseEntity<?> create(@RequestAttribute("currentUserId") Long userId, @RequestBody CreateReq req) {
        Group g = new Group();
        g.setName(req.name());
        g.setOwnerId(userId);
        groupMapper.insert(g);
        groupMapper.addMember(g.getId(), userId);
        return ResponseEntity.ok(Map.of("id", g.getId(), "name", g.getName()));
    }

    @PostMapping("/{groupId}/members")
    public ResponseEntity<?> add(@PathVariable Long groupId, @RequestBody MemberReq req) {
        groupMapper.addMember(groupId, req.userId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    public ResponseEntity<?> remove(@PathVariable Long groupId, @PathVariable Long userId) {
        groupMapper.removeMember(groupId, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<?> myGroups(@RequestAttribute("currentUserId") Long userId) {
        return ResponseEntity.ok(groupMapper.listByUser(userId));
    }
}
