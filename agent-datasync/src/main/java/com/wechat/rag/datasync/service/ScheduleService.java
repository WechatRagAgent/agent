package com.wechat.rag.datasync.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.datasync.schedule.enabled", havingValue = "true")
public class ScheduleService {
    private final AutoSyncService autoSyncService;

    private final IncrementalSyncService incrementalSyncService;

    /**
     * 定时执行自动增量同步
     * 每10分钟执行一次
     */
    @Scheduled(fixedRate = 600000) // 10分钟 = 600000毫秒
    public void autoIncrementalSync() {
        log.info("开始执行自动增量同步任务");

        autoSyncService.getAutoSyncConfig()
                .flatMapIterable(talkers -> talkers)
                .flatMap(talker -> {
                    log.info("正在为聊天对象 {} 执行增量同步", talker);
                    return incrementalSyncService.syncIncremental(talker)
                            .doOnSuccess(v -> log.info("聊天对象 {} 增量同步完成", talker))
                            .doOnError(error -> log.error("聊天对象 {} 增量同步失败: {}", talker, error.getMessage()))
                            .onErrorResume(error -> {
                                // 单个聊天对象同步失败不影响其他对象的同步
                                log.warn("跳过聊天对象 {} 的同步，继续处理下一个", talker);
                                return Mono.empty();
                            });
                })
                .then()
                .doOnSuccess(v -> log.info("自动增量同步任务执行完成"))
                .doOnError(error -> log.error("自动增量同步任务执行失败", error))
                .subscribe();
    }
}
