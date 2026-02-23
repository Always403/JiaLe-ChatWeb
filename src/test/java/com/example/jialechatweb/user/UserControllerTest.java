package com.example.jialechatweb.user;

import com.example.jialechatweb.oss.OssService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private OssService ossService;

    @InjectMocks
    private UserController userController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void uploadAvatar_Success() {
        // Arrange
        Long userId = 1L;
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getSize()).thenReturn(1024L);

        String objectKey = "avatar/test.jpg";
        when(ossService.uploadAvatar(file, userId)).thenReturn(objectKey);
        when(ossService.generateSignedUrl(objectKey)).thenReturn("http://oss/avatar/test.jpg");
        when(ossService.generateThumbnailUrl(eq(objectKey), anyInt(), anyInt())).thenReturn("http://oss/avatar/test_thumb.jpg");

        // Act
        ResponseEntity<?> response = userController.uploadAvatar(userId, file);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertNotNull(body);
        assertEquals("http://oss/avatar/test.jpg", body.get("avatarUrl"));
        
        verify(userService).updateAvatar(userId, objectKey);
        verify(ossService).uploadAvatar(file, userId);
    }

    @Test
    void uploadAvatar_InvalidFormat() {
        // Arrange
        Long userId = 1L;
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("text/plain");

        // Act
        ResponseEntity<?> response = userController.uploadAvatar(userId, file);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(ossService, never()).uploadAvatar(any(), anyLong());
    }

    @Test
    void getAvatarHistory_Success() {
        // Arrange
        Long userId = 1L;
        List<String> keys = Arrays.asList("key1", "key2");
        when(userService.getRecentAvatars(userId)).thenReturn(keys);
        when(ossService.generateSignedUrl("key1")).thenReturn("url1");
        when(ossService.generateSignedUrl("key2")).thenReturn("url2");

        // Act
        ResponseEntity<List<Map<String, String>>> response = userController.getAvatarHistory(userId);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<Map<String, String>> history = response.getBody();
        assertNotNull(history);
        assertEquals(2, history.size());
        assertEquals("key1", history.get(0).get("key"));
        assertEquals("url1", history.get(0).get("url"));
    }

    @Test
    void rollbackAvatar_Success() {
        // Arrange
        Long userId = 1L;
        String key = "key1";
        Map<String, String> body = Map.of("key", key);
        when(ossService.generateSignedUrl(key)).thenReturn("url1");

        // Act
        ResponseEntity<?> response = userController.rollbackAvatar(userId, body);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userService).updateAvatar(userId, key);
        Map<String, Object> respBody = (Map<String, Object>) response.getBody();
        assertEquals("url1", respBody.get("avatarUrl"));
    }
}