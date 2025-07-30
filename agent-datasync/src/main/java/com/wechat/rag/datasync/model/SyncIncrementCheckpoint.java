package com.wechat.rag.datasync.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * 同步增量检查点
 * 用于记录同步状态
 */
@Data
@Builder
@AllArgsConstructor
public class SyncIncrementCheckpoint {
    private String talker;

    private String talkerName;

    private Long lastSeq;

    private String lastSyncTime;

    public SyncIncrementCheckpointBuilder toBuilder() {
        return SyncIncrementCheckpoint.builder()
                .talker(this.talker)
                .talkerName(this.talkerName)
                .lastSeq(this.lastSeq)
                .lastSyncTime(this.lastSyncTime);
    }
}
