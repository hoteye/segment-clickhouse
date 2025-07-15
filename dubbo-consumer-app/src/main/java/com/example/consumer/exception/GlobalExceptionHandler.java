package com.example.consumer.exception;

import org.apache.skywalking.apm.toolkit.trace.ActiveSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import com.example.consumer.util.ErrorLogUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGlobalException(Exception ex, WebRequest request) {
        String requestInfo = String.format("URI: %s, 方法: %s",
                request.getDescription(false),
                request.getHeader("User-Agent"));

        ErrorLogUtil.logError(logger, "全局异常处理器捕获到异常", ex, requestInfo);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Internal Server Error");
        errorResponse.put("message", ex.getMessage());
        errorResponse.put("timestamp", System.currentTimeMillis());

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex, WebRequest request) {
        String requestInfo = String.format("URI: %s", request.getDescription(false));

        ErrorLogUtil.logError(logger, "运行时异常", ex, requestInfo);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Runtime Error");
        errorResponse.put("message", ex.getMessage());
        errorResponse.put("timestamp", System.currentTimeMillis());

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * 处理算术异常（如除零异常）
     */
    @ExceptionHandler(ArithmeticException.class)
    public ResponseEntity<Map<String, Object>> handleArithmeticException(ArithmeticException ex, WebRequest request) {
        String requestInfo = String.format("URI: %s", request.getDescription(false));

        ErrorLogUtil.logError(logger, "算术异常", ex, requestInfo);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Arithmetic Error");
        errorResponse.put("message", "计算错误: " + ex.getMessage());
        errorResponse.put("timestamp", System.currentTimeMillis());

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * 处理空指针异常
     */
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<Map<String, Object>> handleNullPointerException(NullPointerException ex, WebRequest request) {
        String requestInfo = String.format("URI: %s", request.getDescription(false));

        ErrorLogUtil.logError(logger, "空指针异常", ex, requestInfo);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Null Pointer Error");
        errorResponse.put("message", "空指针错误: " + ex.getMessage());
        errorResponse.put("timestamp", System.currentTimeMillis());

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}