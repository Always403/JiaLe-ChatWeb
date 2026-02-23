package com.example.jialechatweb.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/errors")
public class ErrorLogController {
    private static final Logger log = LoggerFactory.getLogger(ErrorLogController.class);
    private final ErrorLogMapper errorLogMapper;

    public ErrorLogController(ErrorLogMapper errorLogMapper) {
        this.errorLogMapper = errorLogMapper;
    }

    @PostMapping("/collect")
    public Map<String, Object> collect(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        ErrorLog errorLog = new ErrorLog();
        errorLog.setUserId(readLong(payload, "userId"));
        errorLog.setUsername(readString(payload, "username"));
        String errorType = defaultIfBlank(readString(payload, "type"), "unknown");
        errorLog.setErrorType(errorType);
        errorLog.setSeverity(defaultIfBlank(readString(payload, "severity"), "error"));
        errorLog.setMessage(defaultIfBlank(readString(payload, "message"), "Unknown error"));
        errorLog.setStack(readString(payload, "stack"));
        errorLog.setUrl(readString(payload, "url"));
        errorLog.setComponent(readString(payload, "component"));
        errorLog.setModule(readString(payload, "module"));
        errorLog.setRoute(readString(payload, "route"));
        String userAgent = defaultIfBlank(readString(payload, "userAgent"), request.getHeader("User-Agent"));
        errorLog.setUserAgent(userAgent);
        errorLog.setVersion(readString(payload, "version"));
        errorLog.setResourceUrl(readString(payload, "resourceUrl"));
        errorLog.setRequestMethod(readString(payload, "requestMethod"));
        errorLog.setStatusCode(readInteger(payload, "statusCode"));
        errorLog.setExtra(readString(payload, "extra"));
        errorLog.setBrowser(defaultIfBlank(readString(payload, "browser"), detectBrowser(userAgent)));
        errorLog.setOs(defaultIfBlank(readString(payload, "os"), detectOs(userAgent)));
        errorLogMapper.insert(errorLog);
        int recent = errorLogMapper.countRecentByType(errorType, Instant.now().minusSeconds(300));
        boolean alert = recent >= 20;
        if (alert) {
            log.warn("Error burst detected: type={}, count={}", errorType, recent);
        }
        Map<String, Object> res = new HashMap<>();
        res.put("ok", true);
        res.put("alert", alert);
        res.put("recent", recent);
        return res;
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics(@RequestParam(required = false) String version,
                                       @RequestParam(required = false) String browser,
                                       @RequestParam(required = false) String os) {
        int total = errorLogMapper.countAll(version, browser, os);
        int users = errorLogMapper.countDistinctUsers(version, browser, os);
        List<ErrorAggregate> byType = errorLogMapper.countByType(version, browser, os);
        Map<String, Object> totals = new HashMap<>();
        totals.put("count", total);
        totals.put("uniqueUsers", users);
        Map<String, Object> res = new HashMap<>();
        res.put("totals", totals);
        res.put("byType", byType);
        return res;
    }

    @GetMapping("/list")
    public Map<String, Object> list(@RequestParam(required = false) String version,
                                    @RequestParam(required = false) String browser,
                                    @RequestParam(required = false) String os,
                                    @RequestParam(required = false, defaultValue = "50") int limit,
                                    @RequestParam(required = false, defaultValue = "0") int offset) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        int safeOffset = Math.max(offset, 0);
        List<ErrorLog> items = errorLogMapper.list(version, browser, os, safeLimit, safeOffset);
        Map<String, Object> res = new HashMap<>();
        res.put("items", items);
        res.put("limit", safeLimit);
        res.put("offset", safeOffset);
        return res;
    }

    private String defaultIfBlank(String value, String fallback) {
        if (value == null) return fallback;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private String readString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) return null;
        return String.valueOf(value);
    }

    private Long readLong(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) return null;
        if (value instanceof Number num) return num.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer readInteger(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) return null;
        if (value instanceof Number num) return num.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String detectBrowser(String userAgent) {
        if (userAgent == null) return null;
        String ua = userAgent.toLowerCase();
        if (ua.contains("edg/")) return "Edge";
        if (ua.contains("opr/") || ua.contains("opera")) return "Opera";
        if (ua.contains("chrome")) return "Chrome";
        if (ua.contains("firefox")) return "Firefox";
        if (ua.contains("safari") && !ua.contains("chrome")) return "Safari";
        return "Other";
    }

    private String detectOs(String userAgent) {
        if (userAgent == null) return null;
        String ua = userAgent.toLowerCase();
        if (ua.contains("windows")) return "Windows";
        if (ua.contains("mac os") || ua.contains("macintosh")) return "Mac";
        if (ua.contains("android")) return "Android";
        if (ua.contains("iphone") || ua.contains("ipad")) return "iOS";
        if (ua.contains("linux")) return "Linux";
        return "Other";
    }
}
