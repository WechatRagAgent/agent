package com.wechat.rag.web.dto;

import lombok.Data;

@Data
public class AutoSyncRequest {
    /**
     * 聊天对象标识（wxid, 群id, 备注名, 昵称）
     */
    private String talker;

    /**
     * 是否启用自动同步
     */
    private Boolean enabled;
}
