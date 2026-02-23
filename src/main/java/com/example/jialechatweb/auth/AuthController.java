package com.example.jialechatweb.auth;

import com.example.jialechatweb.oss.OssService;
import com.example.jialechatweb.security.JwtService;
import com.example.jialechatweb.user.User;
import com.example.jialechatweb.user.UserService;
import com.example.jialechatweb.email.VerificationCodeService;
import com.example.jialechatweb.friend.RateLimitException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@Validated
@Tag(name = "Authentication", description = "User authentication and registration")
public class AuthController {
    private final UserService userService;
    private final JwtService jwtService;
    private final OssService ossService;
    private final VerificationCodeService verificationCodeService;

    public AuthController(UserService userService, JwtService jwtService, OssService ossService, VerificationCodeService verificationCodeService) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.ossService = ossService;
        this.verificationCodeService = verificationCodeService;
    }

    public record RegisterRequest(@NotBlank @Email String email, @NotBlank String password, @NotBlank String displayName) {}
    public record LoginRequest(@NotBlank String account, @NotBlank String password) {}
    public record SendCodeRequest(@NotBlank @Email String email) {}
    public record VerifyCodeRequest(@NotBlank @Email String email, @NotBlank String code) {}
    public record ForgotPasswordRequest(@NotBlank @Email String email, @NotBlank String code, @NotBlank String newPassword) {}

    @PostMapping("/send-email-code")
    @Operation(summary = "Send verification code", description = "Sends a 6-digit verification code to the specified email. Rate limited.")
    @ApiResponse(responseCode = "200", description = "Code sent successfully")
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    public ResponseEntity<?> sendEmailCode(@RequestBody @Validated SendCodeRequest req, HttpServletRequest request) {
        try {
            String ip = request.getRemoteAddr();
            verificationCodeService.sendCode(req.email(), ip);
            return ResponseEntity.ok(Map.of("code", 0, "msg", "发送成功"));
        } catch (RateLimitException e) {
            return ResponseEntity.status(429).body(Map.of("code", 1, "msg", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("code", 1, "msg", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("code", 1, "msg", "发送失败，请稍后重试"));
        }
    }

    @PostMapping("/verify-email-code")
    @Operation(summary = "Verify code", description = "Verifies the email verification code.")
    @ApiResponse(responseCode = "200", description = "Verification successful")
    @ApiResponse(responseCode = "400", description = "Invalid code or expired")
    public ResponseEntity<?> verifyEmailCode(@RequestBody @Validated VerifyCodeRequest req) {
        try {
            verificationCodeService.verifyCode(req.email(), req.code());
            return ResponseEntity.ok(Map.of("code", 0, "msg", "校验通过"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("code", 1, "msg", e.getMessage()));
        }
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Reset password", description = "Reset password using email verification code.")
    public ResponseEntity<?> forgotPassword(@RequestBody @Validated ForgotPasswordRequest req) {
        try {
            // First check if user exists to avoid consuming code if email is wrong
            User user = userService.findByEmail(req.email())
                    .orElseThrow(() -> new IllegalArgumentException("该邮箱未注册"));
            
            verificationCodeService.verifyCode(req.email(), req.code());
            userService.updatePassword(user.getId(), req.newPassword());
            
            return ResponseEntity.ok(Map.of("code", 0, "msg", "密码重置成功，请登录"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/register")
    @Operation(summary = "Register", description = "Register a new user with a random account ID.")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        try {
            User user = userService.registerWithRandomAccount(req.email(), req.password(), req.displayName());
            String token = jwtService.createToken(String.valueOf(user.getId()), Map.of("username", user.getUsername()));
            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "user", Map.of("id", user.getId(), "username", user.getUsername(), "displayName", user.getDisplayName())
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "注册失败: " + e.getMessage()));
        }
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate user and return JWT token. Supports login by Account ID or Email.")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        // Try to find by username (Account ID)
        Optional<User> userOpt = userService.findByUsername(req.account());
        
        // If not found, try to find by email
        if (userOpt.isEmpty()) {
             userOpt = userService.findByEmail(req.account());
        }

        if (userOpt.isEmpty() || !userService.checkPassword(userOpt.get(), req.password())) {
            return ResponseEntity.status(401).body(Map.of("error", "账号/邮箱或密码错误"));
        }
        User user = userOpt.get();
        String token = jwtService.createToken(String.valueOf(user.getId()), Map.of("username", user.getUsername()));
        Map<String, Object> body = new HashMap<>();
        body.put("token", token);
        
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("username", user.getUsername());
        userMap.put("displayName", user.getDisplayName());
        userMap.put("avatarUrl", ossService.generateSignedUrl(user.getAvatarUrl()));
        
        body.put("user", userMap);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestAttribute("currentUserId") Long userId) {
        User user = userService.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("username", user.getUsername());
        userMap.put("displayName", user.getDisplayName());
        userMap.put("avatarUrl", ossService.generateSignedUrl(user.getAvatarUrl()));
        return ResponseEntity.ok(userMap);
    }

    private boolean isValidAccount(String account) {
        // Updated to allow 10 digits
        return account != null && account.matches("\\d{10}");
    }
}
