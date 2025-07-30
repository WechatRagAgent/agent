package com.wechat.rag.datasync.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 向量化处理进度信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Progress {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 当前状态
     */
    private ProgressStatus status;

    /**
     * 完成百分比 (0-100)
     */
    private Integer percentage;

    /**
     * 总记录数
     */
    private Integer totalCount;

    /**
     * 已处理记录数
     */
    private Integer processedCount;

    /**
     * 开始时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    /**
     * 更新时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /**
     * 错误信息（失败时）
     */
    private String errorMessage;

    /**
     * 聊天对象
     */
    private String talker;

    /**
     * 时间范围
     */
    private String timeRange;
}