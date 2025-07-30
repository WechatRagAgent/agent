package com.wechat.rag.datasync.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Seq处理结果
 * 用于记录每个seq的处理状态
 */
@Data
@AllArgsConstructor
public class SeqProcessedResult {
    private Long seq;
    private Boolean processed;
}