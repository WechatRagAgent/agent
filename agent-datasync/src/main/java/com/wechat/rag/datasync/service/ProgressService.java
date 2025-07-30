package com.wechat.rag.datasync.service;

import com.wechat.rag.datasync.model.Progress;
import com.wechat.rag.datasync.model.ProgressStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进度管理服务 - 线程安全的内存存储
 */
@Service
@Slf4j
public class ProgressService {
    
    private final ConcurrentHashMap<String, Progress> progressMap = new ConcurrentHashMap<>();
    
    /**
     * 初始化进度信息
     */
    public Progress initProgress(String taskId, String talker, String timeRange) {
        Progress progress = Progress.builder()
                .taskId(taskId)
                .status(ProgressStatus.FETCHING)
                .percentage(0)
                .totalCount(0)
                .processedCount(0)
                .startTime(new Date())
                .updateTime(new Date())
                .talker(talker)
                .timeRange(timeRange)
                .build();
        
        progressMap.put(taskId, progress);
        log.info("初始化进度信息: taskId={}, talker={}, timeRange={}", taskId, talker, timeRange);
        return progress;
    }
    
    /**
     * 更新进度状态
     */
    public void updateProgress(String taskId, ProgressStatus status) {
        updateProgress(taskId, status, null, null, null);
    }
    
    /**
     * 更新进度百分比
     */
    public void updateProgress(String taskId, ProgressStatus status, Integer percentage) {
        updateProgress(taskId, status, percentage, null, null);
    }
    
    /**
     * 更新进度详细信息
     */
    public void updateProgress(String taskId, ProgressStatus status, Integer percentage, Integer totalCount, Integer processedCount) {
        Progress progress = progressMap.get(taskId);
        if (progress == null) {
            log.warn("未找到进度信息: taskId={}", taskId);
            return;
        }
        
        progress.setStatus(status);
        progress.setUpdateTime(new Date());
        
        if (percentage != null) {
            progress.setPercentage(Math.max(0, Math.min(100, percentage)));
        }
        
        if (totalCount != null) {
            progress.setTotalCount(totalCount);
        }

        if (processedCount != null) {
            progress.setProcessedCount(processedCount);
        }

//        log.debug("更新进度: taskId={}, status={}, percentage={}",
//                taskId, status, progress.getPercentage());
    }
    
    /**
     * 标记任务完成
     */
    public void completeProgress(String taskId) {
        updateProgress(taskId, ProgressStatus.COMPLETED, 100);
        log.info("任务完成: taskId={}", taskId);
    }
    
    /**
     * 标记任务失败
     */
    public void failProgress(String taskId, String errorMessage) {
        Progress progress = progressMap.get(taskId);
        if (progress != null) {
            progress.setStatus(ProgressStatus.FAILED);
            progress.setErrorMessage(errorMessage);
            progress.setUpdateTime(new Date());
            log.error("任务失败: taskId={}, error={}", taskId, errorMessage);
        }
    }
    
    /**
     * 获取进度信息
     */
    public Optional<Progress> getProgress(String taskId) {
        return Optional.ofNullable(progressMap.get(taskId));
    }
    
    /**
     * 删除进度信息（任务完成后清理）
     */
    public void removeProgress(String taskId) {
        Progress removed = progressMap.remove(taskId);
        if (removed != null) {
            log.info("清理进度信息: taskId={}", taskId);
        }
    }
    
    /**
     * 获取当前存储的任务数量（用于监控）
     */
    public int getActiveTaskCount() {
        return progressMap.size();
    }
}