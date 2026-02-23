package com.example.jialechatweb.email;

import com.example.jialechatweb.friend.RateLimitException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;

@Service
public class VerificationCodeService {

    private final StringRedisTemplate redisTemplate;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    public VerificationCodeService(StringRedisTemplate redisTemplate, EmailService emailService) {
        this.redisTemplate = redisTemplate;
        this.emailService = emailService;
    }

    public void sendCode(String email, String ip) {
        validateEmail(email);
        checkRateLimit(email, ip);

        String code = generateCode();
        String codeKey = "email:" + email + ":code";
        String countKey = "email:" + email + ":count:" + LocalDate.now();

        // Store code with 5 min expiration
        redisTemplate.opsForValue().set(codeKey, code, Duration.ofMinutes(5));

        // Set cooldown
        String cooldownKey = "email:" + email + ":cooldown";
        redisTemplate.opsForValue().set(cooldownKey, "1", Duration.ofSeconds(60));

        // Increment daily count
        redisTemplate.opsForValue().increment(countKey);
        redisTemplate.expire(countKey, Duration.ofDays(1));

        try {
            emailService.sendVerificationCode(email, code);
        } catch (Exception e) {
            throw new RuntimeException("邮件发送失败", e);
        }
    }

    public void verifyCode(String email, String inputCode) {
        String codeKey = "email:" + email + ":code";
        String storedCode = redisTemplate.opsForValue().get(codeKey);

        if (storedCode == null) {
            throw new IllegalArgumentException("验证码不存在或已过期");
        }

        if (!storedCode.equalsIgnoreCase(inputCode)) {
            handleFailedAttempt(email);
            throw new IllegalArgumentException("验证码错误");
        }

        // Success: delete code
        redisTemplate.delete(codeKey);
    }

    private void handleFailedAttempt(String email) {
        String attemptKey = "email:" + email + ":attempts";
        Long attempts = redisTemplate.opsForValue().increment(attemptKey);
        if (attempts == 1) {
            redisTemplate.expire(attemptKey, Duration.ofMinutes(5));
        }
        if (attempts != null && attempts >= 3) {
            redisTemplate.delete("email:" + email + ":code");
            redisTemplate.delete(attemptKey);
            throw new IllegalArgumentException("错误次数过多，验证码已失效");
        }
    }

    private void checkRateLimit(String email, String ip) {
        String cooldownKey = "email:" + email + ":cooldown";
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            throw new RateLimitException("请求过于频繁，请稍后再试");
        }

        String countKey = "email:" + email + ":count:" + LocalDate.now();
        String countStr = redisTemplate.opsForValue().get(countKey);
        if (countStr != null && Integer.parseInt(countStr) >= 10) {
            throw new RateLimitException("今日发送次数已达上限");
        }
    }

    private String generateCode() {
        int code = 100000 + secureRandom.nextInt(900000);
        return String.valueOf(code);
    }

    private void validateEmail(String email) {
        if (email == null || !email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new IllegalArgumentException("邮箱格式不正确");
        }
        // Simple whitelist check example
        // if (!email.endsWith("@example.com") && !email.endsWith("@gmail.com")) {
        //     throw new IllegalArgumentException("不支持该邮箱域名");
        // }
    }
}
