package com.example.jialechatweb.user;

import com.example.jialechatweb.oss.OssService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user")
@Tag(name = "User", description = "User management")
public class UserController {

    private final UserService userService;
    private final OssService ossService;

    public UserController(UserService userService, OssService ossService) {
        this.userService = userService;
        this.ossService = ossService;
    }

    @PostMapping("/avatar")
    @Operation(summary = "Upload avatar", description = "Uploads a new avatar image (JPG/PNG, max 5MB).")
    public ResponseEntity<?> uploadAvatar(
            @RequestAttribute("currentUserId") Long userId,
            @RequestParam("file") MultipartFile file) {
        
        // Validation
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件不能为空"));
        }
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "仅支持JPG/PNG格式"));
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件大小不能超过5MB"));
        }

        try {
            // Upload to OSS (returns object key)
            String objectKey = ossService.uploadAvatar(file, userId);
            
            // Update DB
            userService.updateAvatar(userId, objectKey);
            
            // Generate signed URLs
            String originalUrl = ossService.generateSignedUrl(objectKey);
            String thumb100 = ossService.generateThumbnailUrl(objectKey, 100, 100);
            String thumb50 = ossService.generateThumbnailUrl(objectKey, 50, 50);
            
            return ResponseEntity.ok(Map.of(
                    "avatarUrl", originalUrl,
                    "avatarUrlSmall", thumb50,
                    "avatarUrlMedium", thumb100
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "上传失败: " + e.getMessage()));
        }
    }
    
    @GetMapping("/avatar/history")
    public ResponseEntity<List<Map<String, String>>> getAvatarHistory(@RequestAttribute("currentUserId") Long userId) {
        List<String> keys = userService.getRecentAvatars(userId);
        List<Map<String, String>> history = keys.stream()
                .map(key -> Map.of(
                    "key", key,
                    "url", ossService.generateSignedUrl(key)
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(history);
    }

    @PostMapping("/avatar/rollback")
    public ResponseEntity<?> rollbackAvatar(
            @RequestAttribute("currentUserId") Long userId,
            @RequestBody Map<String, String> body) {
        String key = body.get("key");
        if (key == null || key.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Key不能为空"));
        }
        userService.updateAvatar(userId, key);
        return ResponseEntity.ok(Map.of(
                "success", true, 
                "avatarUrl", ossService.generateSignedUrl(key),
                "key", key
        ));
    }

    @PostMapping("/profile")
    public ResponseEntity<?> updateProfile(
            @RequestAttribute("currentUserId") Long userId,
            @RequestBody Map<String, String> body) {
        String displayName = body.get("displayName");
        if (displayName == null || displayName.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "昵称不能为空"));
        }
        if (displayName.length() > 50) {
            return ResponseEntity.badRequest().body(Map.of("error", "昵称过长"));
        }
        userService.updateDisplayName(userId, displayName.trim());
        return ResponseEntity.ok(Map.of("success", true, "displayName", displayName.trim()));
    }
}
