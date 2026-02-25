package com.example.jialechatweb.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final com.example.jialechatweb.group.GroupMapper groupMapper;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder, com.example.jialechatweb.group.GroupMapper groupMapper) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.groupMapper = groupMapper;
    }

    public Optional<User> findByUsername(String username) {
        return userMapper.findByUsername(username);
    }

    public Optional<User> findByEmail(String email) {
        return userMapper.findByEmail(email);
    }

    public Optional<User> findById(Long id) {
        return userMapper.findById(id);
    }

    public java.util.List<User> listByAccountPrefix(String prefix, int limit) {
        return userMapper.listByAccountPrefix(prefix, limit);
    }

    @Transactional
    public User register(String username, String email, String rawPassword, String displayName) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setDisplayName(displayName);
        userMapper.insert(user);
        
        if (groupMapper != null) {
            try {
                groupMapper.addMember(1L, user.getId());
            } catch (Exception ignored) {
            }
        }
        
        return user;
    }

    @Transactional
    public User registerWithRandomAccount(String email, String rawPassword, String displayName) {
        // Check if email already exists
        if (userMapper.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("该邮箱已被注册");
        }

        int maxRetries = 5;
        for (int i = 0; i < maxRetries; i++) {
            String candidate = generateRandomAccount();
            if (userMapper.findByUsername(candidate).isEmpty()) {
                return register(candidate, email, rawPassword, displayName);
            }
        }
        throw new RuntimeException("无法生成唯一账号，请稍后重试");
    }

    private String generateRandomAccount() {
        // Generate 10-digit random number string
        // Using SecureRandom for better randomness
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder();
        // Use pure digits as requested
        String chars = "0123456789";
        for (int i = 0; i < 10; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    public boolean checkPassword(User user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    @Transactional
    public void updateAvatar(Long userId, String avatarKey) {
        userMapper.updateAvatar(userId, avatarKey);
        userMapper.insertAvatarHistory(userId, avatarKey);
    }

    @Transactional
    public void updateDisplayName(Long userId, String displayName) {
        userMapper.updateDisplayName(userId, displayName);
    }

    @Transactional
    public void updatePassword(Long userId, String newPassword) {
        userMapper.updatePassword(userId, passwordEncoder.encode(newPassword));
    }

    public java.util.List<String> getRecentAvatars(Long userId) {
        return userMapper.findRecentAvatars(userId, 5);
    }
}
