package com.wechat.rag.datasync.service;

import com.wechat.rag.datasync.model.SyncIncrementCheckpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class IncrementalSyncService {
    private final RedisSyncStateService redisSyncStateService;

    private final ChatlogVectorService chatlogVectorService;

    public IncrementalSyncService(RedisSyncStateService redisSyncStateService, ChatlogVectorService chatlogVectorService) {
        this.redisSyncStateService = redisSyncStateService;
        this.chatlogVectorService = chatlogVectorService;
    }

    public Mono<Void> syncIncremental(String talker) {
        // 获取当前检查点
        return redisSyncStateService.getCheckpoint(talker)
                .flatMap(checkpoint -> {
                    if (checkpoint.getLastSeq() == 0L) {
                        return Mono.error(new RuntimeException("首次同步未完成，请先执行初始化同步"));
                    }
                    String timeWindow = calculateTimeWindow(checkpoint);
                    log.info("开始增量同步: talker={}, timeWindow={}", talker, timeWindow);
                    // 执行增量同步逻辑
                    return chatlogVectorService.incrementalVectorizeChatlog(talker, timeWindow, null, null, checkpoint.getLastSeq(), this::updateCheckpointCallback);
                });
    }

    public Mono<Void> syncIncrementalWithProgress(String talker, String time, String taskId, ChatlogVectorService.ProgressCallback progressCallback) {
        // 获取当前检查点
        return redisSyncStateService.getCheckpoint(talker)
                .flatMap(checkpoint -> {
                    if (checkpoint.getLastSeq() == 0L) {
                        log.info("首次同步: talker={}, time={}", talker, time);
                        return chatlogVectorService.initVectorizeChatlog(talker, time, taskId, progressCallback);
                    }
                    String timeWindow = calculateTimeWindow(checkpoint);
                    log.info("开始增量同步: talker={}, timeWindow={}", talker, timeWindow);
                    // 执行增量同步逻辑
                    return chatlogVectorService.incrementalVectorizeChatlog(talker, timeWindow, taskId, progressCallback, checkpoint.getLastSeq(), this::updateCheckpointCallback);
                });
    }

    /**
     * 计算时间窗口
     */
    private String calculateTimeWindow(SyncIncrementCheckpoint checkpoint) {
        LocalDateTime lastSyncTime;
        try {
            lastSyncTime = LocalDateTime.parse(checkpoint.getLastSyncTime(), DateTimeFormatter.ofPattern(RedisSyncStateService.DATE_TIME_FORMAT));
        } catch (Exception e) {
            // 如果解析失败，使用昨天作为起始时间
            lastSyncTime = LocalDateTime.now().minusDays(1);
        }

        LocalDate startDate = lastSyncTime.toLocalDate();
        LocalDate endDate = LocalDate.now();

        if (startDate.equals(endDate)) {
            return startDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } else {
            return startDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "~" +
                    endDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
    }

    /**
     * 更新检查点回调
     *
     * @param talker 聊天对象 wxid, 群id, 备注名, 昵称
     * @param maxSeq 最大Seq
     */
    private Mono<Void> updateCheckpointCallback(String talker, Long maxSeq) {
        return redisSyncStateService.getCheckpoint(talker)
                .flatMap(checkpoint -> {
                    if (maxSeq > checkpoint.getLastSeq()) {
                        SyncIncrementCheckpoint updatedCheckpoint = checkpoint.toBuilder()
                                .lastSeq(maxSeq)
                                .lastSyncTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern(RedisSyncStateService.DATE_TIME_FORMAT)))
                                .build();
                        return redisSyncStateService.updateCheckpoint(talker, updatedCheckpoint);
                    }
                    return Mono.empty();
                });
    }
}
