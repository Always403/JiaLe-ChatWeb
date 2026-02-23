package com.example.jialechatweb.user;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserSearchController {
    private final UserSearchService userSearchService;

    public UserSearchController(UserSearchService userSearchService) {
        this.userSearchService = userSearchService;
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestAttribute("currentUserId") Long userId, @RequestParam String account) {
        UserSearchService.SearchResult result = userSearchService.search(userId, account);
        return ResponseEntity.ok(Map.of(
                "exists", result.exists(),
                "suggestions", result.suggestions()
        ));
    }
}
