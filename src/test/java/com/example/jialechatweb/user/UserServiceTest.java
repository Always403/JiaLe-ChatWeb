package com.example.jialechatweb.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class UserServiceTest {
    @Autowired
    private UserService userService;

    @Test
    @Transactional
    void registerAndFind() {
        User u = userService.register("0987654321", "user@example.com", "password123", "Tester");
        assertNotNull(u.getId());
        assertTrue(userService.findByUsername("0987654321").isPresent());
        assertTrue(userService.checkPassword(u, "password123"));
    }
}
