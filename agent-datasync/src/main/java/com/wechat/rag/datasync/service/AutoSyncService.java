package com.wechat.rag.datasync.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

/**
 * 自动同步服务
 */
@Service
@Slf4j
public class AutoSyncService {
    private static final String AUTO_SYNC_CONFIG_KEY = "chatlog:auto_sync_config";

    @Autowired
    private ReactiveRedisTemplate<String, Object> redisTemplate;

    @Autowired
    private IncrementalSyncService incrementalSyncService;

    /**
     * 获取自动同步配置
     *
     * @return 自动同步配置
     */
    public Mono<List<String>> getAutoSyncConfig() {
        return redisTemplate.opsForList()
                .range(AUTO_SYNC_CONFIG_KEY, 0, -1)
                .cast(String.class)
                .collectList();
    }

    /**
     * 添加聊天对象到自动同步列表
     *
     * @param talker  聊天对象标识
     * @param enabled 是否启用自动同步
     * @return 操作结果
     */
    public Mono<Void> addToAutoSync(String talker, Boolean enabled) {
        if (StringUtils.isEmpty(talker) || Objects.isNull(enabled)) {
            log.error("添加自动同步配置失败，聊天对象或启用状态不能为空");
            return Mono.error(new IllegalArgumentException("聊天对象或启用状态不能为空"));
        }

        // 先检查是否已存在，避免重复添加
        return redisTemplate.opsForList()
                .range(AUTO_SYNC_CONFIG_KEY, 0, -1)
                .collectList()
                .flatMap(list -> {
                    if (enabled) {
                        // 检查是否已存在
                        boolean exists = list.stream()
                                .anyMatch(existingTalker -> existingTalker.equals(talker));
                        if (exists) {
                            return Mono.empty();
                        } else {
                            // 添加到列表末尾
                            return redisTemplate.opsForList()
                                    .rightPush(AUTO_SYNC_CONFIG_KEY, talker)
                                    .then();
                        }
                    } else {
                        // 如果禁用，则从列表中移除
                        return redisTemplate.opsForList()
                                .remove(AUTO_SYNC_CONFIG_KEY, 1, talker)
                                .then();
                    }
                });
    }

    /**
     * 定时执行自动增量同步
     * 每10分钟执行一次
     */
    @Scheduled(fixedRate = 600000) // 10分钟 = 600000毫秒
    public void autoIncrementalSync() {
        log.info("开始执行自动增量同步任务");

        getAutoSyncConfig()
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
