package com.example.jialechatweb.auth;

import com.example.jialechatweb.user.UserService;
import com.example.jialechatweb.security.JwtService;
import com.example.jialechatweb.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AuthControllerTest {
    private MockMvc mockMvc;
    @Autowired
    private UserService userService;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private WebApplicationContext context;

    @BeforeEach
    void setup() {
        if (userService.findByUsername("1234567890").isEmpty()) {
            userService.register("1234567890", "test@example.com", "pwd", "Mock");
        }
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void loginOk() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"1234567890\",\"password\":\"pwd\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void friendRequestFlowOk() throws Exception {
        User me = userService.register("1111111111", "me@example.com", "pwd", "UserA");
        userService.register("2222222222", "other@example.com", "pwd", "UserB");
        String token = jwtService.createToken(String.valueOf(me.getId()), Map.of("username", me.getUsername()));

        mockMvc.perform(get("/api/users/search")
                .header("Authorization", "Bearer " + token)
                .requestAttr("currentUserId", me.getId())
                .param("account", "222"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/friends/request")
                .header("Authorization", "Bearer " + token)
                .requestAttr("currentUserId", me.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"2222222222\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/friends/request")
                .header("Authorization", "Bearer " + token)
                .requestAttr("currentUserId", me.getId())
                .param("account", "2222222222"))
                .andExpect(status().isOk());
    }
}
