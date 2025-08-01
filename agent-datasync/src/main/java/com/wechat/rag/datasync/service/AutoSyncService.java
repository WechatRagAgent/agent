package com.wechat.rag.datasync.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
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

    public Mono<Void> removeFromAutoSync(String talker) {
        if (StringUtils.isEmpty(talker)) {
            log.error("移除自动同步配置失败，聊天对象不能为空");
            return Mono.error(new IllegalArgumentException("聊天对象不能为空"));
        }

        // 从列表中移除
        return redisTemplate.opsForList()
                .remove(AUTO_SYNC_CONFIG_KEY, 1, talker)
                .then();
    }
}
