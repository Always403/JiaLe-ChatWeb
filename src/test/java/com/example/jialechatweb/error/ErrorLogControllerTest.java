package com.example.jialechatweb.error;

import com.example.jialechatweb.security.JwtService;
import com.example.jialechatweb.user.User;
import com.example.jialechatweb.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class ErrorLogControllerTest {
    private MockMvc mockMvc;
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private UserService userService;
    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void collectAndQueryMetrics() throws Exception {
        String account = String.format("%010d", System.currentTimeMillis() % 10000000000L);
        User me = userService.findByUsername(account).orElseGet(() -> userService.register(account, "err@example.com", "pwd", "ErrUser"));
        String token = jwtService.createToken(String.valueOf(me.getId()), Map.of("username", me.getUsername()));
        String payload = """
            {
              "userId": %d,
              "username": "%s",
              "type": "script",
              "severity": "error",
              "message": "Test error",
              "url": "http://localhost/test",
              "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36",
              "version": "test-1"
            }
            """.formatted(me.getId(), me.getUsername());

        mockMvc.perform(post("/api/errors/collect")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        mockMvc.perform(get("/api/errors/metrics")
                .header("Authorization", "Bearer " + token)
                .param("version", "test-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.count").value(1))
                .andExpect(jsonPath("$.totals.uniqueUsers").value(1));

        mockMvc.perform(get("/api/errors/list")
                .header("Authorization", "Bearer " + token)
                .param("version", "test-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].message").value("Test error"));
    }
}
