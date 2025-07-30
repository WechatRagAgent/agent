package com.wechat.rag.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 向量化请求响应DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VectorizationResponse {
    /**
     * 状态码
     */
    private String status;
    
    /**
     * 响应消息
     */
    private String message;
    
    /**
     * 任务ID（可选）
     */
    private String taskId;
    
    public VectorizationResponse(String status, String message) {
        this.status = status;
        this.message = message;
    }
}