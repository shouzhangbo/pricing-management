package com.pricing.management.admin;

import com.pricing.management.application.scheme.BusinessException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> business(BusinessException e) {
        HttpStatus status = "NOT_FOUND".equals(e.getCode()) ? HttpStatus.NOT_FOUND : "CONFLICT".equals(e.getCode()) ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new ApiResponse<>(e.getCode(), e.getMessage(), Map.of()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> validation(MethodArgumentNotValidException e) {
        String message=e.getBindingResult().getFieldErrors().isEmpty()?"请求参数不合法":e.getBindingResult().getFieldErrors().getFirst().getDefaultMessage();
        return ResponseEntity.badRequest().body(new ApiResponse<>("PARAM_ERROR",message,Map.of()));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> unexpected(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>("SYSTEM_ERROR", "系统繁忙，请稍后重试", Map.of()));
    }
}
