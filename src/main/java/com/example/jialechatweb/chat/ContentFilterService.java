package com.example.jialechatweb.chat;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class ContentFilterService {

    // Simple list of sensitive words for demonstration
    private static final List<String> SENSITIVE_WORDS = Arrays.asList(
            "暴力", "色情", "赌博", "毒品", "炸弹", "恐怖", "傻逼", "弱智"
    );

    public String filter(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        
        String filtered = content;
        for (String word : SENSITIVE_WORDS) {
            if (filtered.contains(word)) {
                String replacement = "*".repeat(word.length());
                filtered = filtered.replace(word, replacement);
            }
        }
        return filtered;
    }

    public boolean containsSensitive(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        for (String word : SENSITIVE_WORDS) {
            if (content.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
