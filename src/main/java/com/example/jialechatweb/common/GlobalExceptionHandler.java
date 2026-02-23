package com.example.jialechatweb.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.jialechatweb.error.ErrorLog;
import com.example.jialechatweb.error.ErrorLogMapper;
import com.example.jialechatweb.friend.RateLimitException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ErrorLogMapper errorLogMapper;

    public GlobalExceptionHandler(ErrorLogMapper errorLogMapper) {
        this.errorLogMapper = errorLogMapper;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "参数校验失败"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<?> handleRateLimit(RateLimitException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    public ResponseEntity<?> handleDuplicateKey(org.springframework.dao.DuplicateKeyException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "数据已存在，请勿重复操作"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception", ex);
        recordServerError(ex, request);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "服务器内部错误"));
    }

    private void recordServerError(Exception ex, HttpServletRequest request) {
        if (request == null) return;
        ErrorLog errorLog = new ErrorLog();
        errorLog.setErrorType("server");
        errorLog.setSeverity("error");
        errorLog.setMessage(ex.getMessage() == null ? "Internal error" : ex.getMessage());
        errorLog.setStack(stackTrace(ex));
        errorLog.setUrl(request.getRequestURI());
        errorLog.setComponent("backend");
        errorLog.setModule("api");
        errorLog.setRoute(request.getRequestURI());
        errorLog.setUserAgent(request.getHeader("User-Agent"));
        errorLog.setRequestMethod(request.getMethod());
        Object userId = request.getAttribute("currentUserId");
        if (userId instanceof Number num) {
            errorLog.setUserId(num.longValue());
        } else if (userId != null) {
            try {
                errorLog.setUserId(Long.parseLong(String.valueOf(userId)));
            } catch (NumberFormatException ignored) {
            }
        }
        try {
            errorLogMapper.insert(errorLog);
        } catch (Exception ignored) {
        }
    }

    private String stackTrace(Exception ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
