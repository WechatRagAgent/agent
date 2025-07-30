package com.wechat.rag.datasync.chatlog.response;

import lombok.Data;

/**
 * Count-Api-响应
 */
@Data
public class ChatlogCountResponse {
    
    /**
     * 符合条件的记录总数
     */
    private Integer count;
    
    /**
     * 聊天对象
     */
    private String talker;
    
    /**
     * 时间范围
     */
    private String time;
}