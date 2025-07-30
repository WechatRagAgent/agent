package com.wechat.rag.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;


/**
 * 向量化请求DTO
 */
@Data
public class VectorizationRequest {
    
    /**
     * 聊天对象 wxid, 群id, 备注名, 昵称
     */
    @NotBlank(message = "talker不能为空")
    private String talker;
    
    /**
     * 时间范围 格式：YYYY-MM-DD 或 YYYY-MM-DD~YYYY-MM-DD
     */
    @NotBlank(message = "time不能为空")
    private String time;
}