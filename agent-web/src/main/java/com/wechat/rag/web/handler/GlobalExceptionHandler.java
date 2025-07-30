package com.wechat.rag.web.handler;

import com.wechat.rag.web.dto.VectorizationResponse;
import com.wechat.rag.web.dto.ChatCompletionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<VectorizationResponse>> handleValidationException(
            WebExchangeBindException ex) {
        
        String errorMessage = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        
        log.warn("参数校验失败: {}", errorMessage);
        
        VectorizationResponse response = new VectorizationResponse(
                "VALIDATION_ERROR", 
                "参数校验失败: " + errorMessage
        );
        
        return Mono.just(ResponseEntity.badRequest().body(response));
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<VectorizationResponse>> handleIllegalArgumentException(
            IllegalArgumentException ex) {
        
        log.warn("非法参数异常: {}", ex.getMessage());
        
        VectorizationResponse response = new VectorizationResponse(
                "INVALID_PARAMETER", 
                ex.getMessage()
        );
        
        return Mono.just(ResponseEntity.badRequest().body(response));
    }

    /**
     * 处理通用异常
     */
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<VectorizationResponse>> handleGeneralException(
            Exception ex) {
        
        log.error("未预期的异常", ex);
        
        VectorizationResponse response = new VectorizationResponse(
                "INTERNAL_ERROR", 
                "服务内部错误"
        );
        
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response));
    }
}