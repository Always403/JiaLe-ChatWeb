package com.example.jialechatweb.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceUnitTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void registerWithRandomAccount_Success() {
        // Arrange
        String email = "test@example.com";
        String rawPassword = "password";
        String displayName = "TestUser";
        when(userMapper.findByEmail(email)).thenReturn(Optional.empty());
        when(userMapper.findByUsername(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(rawPassword)).thenReturn("encodedPassword");

        // Act
        User result = userService.registerWithRandomAccount(email, rawPassword, displayName);

        // Assert
        assertNotNull(result);
        assertEquals(displayName, result.getDisplayName());
        assertEquals(email, result.getEmail());
        assertEquals("encodedPassword", result.getPasswordHash());
        
        // Verify username format
        String username = result.getUsername();
        assertNotNull(username);
        assertEquals(10, username.length());
        assertTrue(Pattern.matches("^\\d+$", username), "Username should be numeric");
        
        verify(userMapper, times(1)).findByEmail(email);
        verify(userMapper, times(1)).insert(any(User.class));
    }

    @Test
    void registerWithRandomAccount_EmailExists() {
        // Arrange
        String email = "existing@example.com";
        when(userMapper.findByEmail(email)).thenReturn(Optional.of(new User()));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            userService.registerWithRandomAccount(email, "pwd", "name");
        });
        
        assertEquals("该邮箱已被注册", exception.getMessage());
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void registerWithRandomAccount_RetrySuccess() {
        // Arrange
        String email = "test@example.com";
        String rawPassword = "password";
        String displayName = "TestUser";
        
        when(userMapper.findByEmail(email)).thenReturn(Optional.empty());
        // Simulate collision twice, then success
        when(userMapper.findByUsername(anyString()))
                .thenReturn(Optional.of(new User())) // 1st try collision
                .thenReturn(Optional.of(new User())) // 2nd try collision
                .thenReturn(Optional.empty());       // 3rd try success

        when(passwordEncoder.encode(rawPassword)).thenReturn("encodedPassword");

        // Act
        User result = userService.registerWithRandomAccount(email, rawPassword, displayName);

        // Assert
        assertNotNull(result);
        verify(userMapper, times(3)).findByUsername(anyString());
        verify(userMapper, times(1)).insert(any(User.class));
    }

    @Test
    void registerWithRandomAccount_MaxRetriesExceeded() {
        // Arrange
        String email = "test@example.com";
        String rawPassword = "password";
        String displayName = "TestUser";
        
        when(userMapper.findByEmail(email)).thenReturn(Optional.empty());
        // Always collision
        when(userMapper.findByUsername(anyString())).thenReturn(Optional.of(new User()));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.registerWithRandomAccount(email, rawPassword, displayName);
        });
        
        assertEquals("无法生成唯一账号，请稍后重试", exception.getMessage());
        verify(userMapper, times(5)).findByUsername(anyString()); // Max retries is 5
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void generateRandomAccount_DistributionCheck() {
        // This test indirectly checks the randomness by calling the method multiple times
        // and ensuring we don't get immediate duplicates (though collision is possible, it's unlikely)
        // Since we can't access generateRandomAccount directly, we use the fact that 
        // registerWithRandomAccount calls it.
        // However, we need to mock findByUsername to always return empty to let it proceed.
        
        when(userMapper.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userMapper.findByUsername(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("pwd");

        java.util.Set<String> accounts = new java.util.HashSet<>();
        for (int i = 0; i < 100; i++) {
            User u = userService.registerWithRandomAccount("test" + i + "@example.com", "p", "d");
            assertTrue(accounts.add(u.getUsername()), "Should generate unique accounts in small batch");
        }
    }
}
