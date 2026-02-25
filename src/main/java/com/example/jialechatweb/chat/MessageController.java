package com.example.jialechatweb.chat;

import com.example.jialechatweb.oss.OssService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
    private final MessageMapper messageMapper;
    private final OssService ossService;

    public MessageController(MessageMapper messageMapper, OssService ossService) {
        this.messageMapper = messageMapper;
        this.ossService = ossService;
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) Long friendId,
                                  @RequestParam(required = false) Long groupId,
                                  @RequestParam(defaultValue = "50") int limit,
                                  @RequestParam(defaultValue = "0") int offset,
                                  @RequestAttribute("currentUserId") Long userId) {
        List<ChatMessage> messages;
        if (groupId != null) {
            messages = messageMapper.listGroupMessages(groupId, limit, offset);
            messages.forEach(msg -> {
                if (msg.getSenderAvatar() != null) {
                    msg.setSenderAvatar(ossService.generateSignedUrl(msg.getSenderAvatar()));
                }
            });
        } else if (friendId != null) {
            // Mark messages as read
            messageMapper.markReadP2P(userId, friendId);
            messages = messageMapper.listP2P(userId, friendId, limit, offset);
        } else {
            return ResponseEntity.badRequest().body("friendId or groupId is required");
        }
        
        // Reverse the list so the oldest messages are first (top), newest last (bottom)
        Collections.reverse(messages);
        return ResponseEntity.ok(messages);
    }
}
