package com.wechat.rag.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.wechat.rag.datasync.model.ProgressStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 进度查询响应DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProgressResponse {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 当前状态
     */
    private ProgressStatus status;

    /**
     * 状态描述
     */
    private String statusDescription;

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
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTime;

    /**
     * 更新时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
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

    /**
     * 任务是否完成
     */
    private Boolean completed;

    /**
     * 任务是否失败
     */
    private Boolean failed;
}