package com.wechat.rag.datasync.model;

import lombok.Getter;

/**
 * 向量化处理进度状态枚举
 */
@Getter
public enum ProgressStatus {
    /**
     * 正在获取聊天记录数据
     */
    FETCHING("正在获取聊天记录数据"),
    
    /**
     * 正在处理和过滤数据
     */
    PROCESSING("正在处理数据"),
    
    /**
     * 正在存储向量数据
     */
    STORING("正在存储数据"),
    
    /**
     * 处理完成
     */
    COMPLETED("处理完成"),
    
    /**
     * 处理失败
     */
    FAILED("处理失败");
    
    private final String description;
    
    ProgressStatus(String description) {
        this.description = description;
    }
}