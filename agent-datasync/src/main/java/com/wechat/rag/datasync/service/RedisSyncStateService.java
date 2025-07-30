package com.wechat.rag.datasync.service;

import com.wechat.rag.datasync.chatlog.ChatlogApi;
import com.wechat.rag.datasync.model.SeqProcessedResult;
import com.wechat.rag.datasync.model.SyncIncrementCheckpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RedisSyncStateService {
    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    private final ChatlogApi chatlogApi;

    protected static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    // Redis键前缀
    private static final String CHECKPOINT_KEY_PREFIX = "sync:checkpoint:";

    private static final String PROCESSED_KEY_PREFIX = "sync:processed:";

    /**
     * 已处理的seq缓存过期时间
     * 7天
     */
    private static final Duration PROCESSED_CACHE_TTL = Duration.ofDays(1);

    public RedisSyncStateService(ReactiveRedisTemplate<String, Object> redisTemplate, ChatlogApi chatlogApi) {
        this.redisTemplate = redisTemplate;
        this.chatlogApi = chatlogApi;
    }

    /**
     * 获取同步增量检查点
     *
     * @param talker 聊天对象 wxid, 群id, 备注名, 昵称
     */
    public Mono<SyncIncrementCheckpoint> getCheckpoint(String talker) {
        String key = CHECKPOINT_KEY_PREFIX + talker;
        return redisTemplate.opsForHash().entries(key)
                .collectMap(
                        entry -> entry.getKey().toString(),
                        entry -> entry.getValue().toString()
                )
                .flatMap(this::mapToCheckpointMono)
                .switchIfEmpty(createInitialCheckpoint(talker));
    }

    /**
     * 获取所有同步增量检查点
     */
    public Flux<SyncIncrementCheckpoint> getAllCheckpoints() {
        return redisTemplate.keys(CHECKPOINT_KEY_PREFIX + "*")
                .flatMap(key -> redisTemplate.opsForHash().entries(key)
                        .collectMap(
                                entry -> entry.getKey().toString(),
                                entry -> entry.getValue().toString()
                        )
                        .flatMap(this::mapToCheckpointMono)
                );
    }

    /**
     * 更新同步增量检查点
     *
     * @param talker     聊天对象 wxid, 群id, 备注名, 昵称
     * @param checkpoint 同步增量检查点
     */
    public Mono<Void> updateCheckpoint(String talker, SyncIncrementCheckpoint checkpoint) {
        String key = CHECKPOINT_KEY_PREFIX + talker;
        Map<String, Object> checkpointMap = mapFromCheckpoint(checkpoint);

        return redisTemplate.opsForHash().putAll(key, checkpointMap)
                .doOnSuccess(v -> log.debug("更新检查点成功: talker={}, checkpoint={}", talker, checkpoint))
                .then();
    }

    /**
     * 检查seq是否已处理
     */
    public Mono<Boolean> isSeqProcessed(String talker, Long seq) {
        String key = PROCESSED_KEY_PREFIX + talker;
        return redisTemplate.opsForZSet().rank(key, seq.toString())
                .map(Objects::nonNull)
                .defaultIfEmpty(false);
    }

    /**
     * 批量检查seq是否已处理
     */
    public Flux<SeqProcessedResult> checkSeqsProcessed(String talker, List<Long> seqs) {
        String key = PROCESSED_KEY_PREFIX + talker;

        return Flux.fromIterable(seqs)
                .flatMap(seq ->
                        redisTemplate.opsForZSet().rank(key, seq.toString())
                                .map(rank -> new SeqProcessedResult(seq, rank != null))
                                .defaultIfEmpty(new SeqProcessedResult(seq, false))
                );
    }

    /**
     * 标记seq为已处理
     */
    public Mono<Void> markSeqAsProcessed(String talker, Long seq) {
        String key = PROCESSED_KEY_PREFIX + talker;
        double timestamp = System.currentTimeMillis();

        return redisTemplate.opsForZSet().add(key, seq.toString(), timestamp)
                .then(redisTemplate.expire(key, PROCESSED_CACHE_TTL))
                .then();
    }


    /**
     * 批量标记seq为已处理
     */
    public Mono<Void> markSeqsAsProcessed(String talker, List<Long> seqs) {
        String key = PROCESSED_KEY_PREFIX + talker;
        double timestamp = System.currentTimeMillis();

        Set<DefaultTypedTuple<Object>> tuples = seqs.stream()
                .map(seq -> new DefaultTypedTuple<Object>(seq.toString(), timestamp))
                .collect(Collectors.toSet());

        return redisTemplate.opsForZSet().addAll(key, tuples)
                .then(redisTemplate.expire(key, PROCESSED_CACHE_TTL))
                .then();
    }


    // 辅助方法
    private Mono<SyncIncrementCheckpoint> mapToCheckpointMono(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return Mono.empty();
        }

        String lastSyncTimeStr = map.get("lastSyncTime");
        if (lastSyncTimeStr == null) {
            return Mono.empty();
        }

        try {
            LocalDateTime localDateTime = LocalDateTime.parse(lastSyncTimeStr, DateTimeFormatter.ofPattern(DATE_TIME_FORMAT));
            SyncIncrementCheckpoint checkpoint = SyncIncrementCheckpoint.builder()
                    .talker(map.get("talker"))
                    .talkerName(map.getOrDefault("talkerName", "未找到"))
                    .lastSeq(Long.valueOf(map.getOrDefault("lastSeq", "0")))
                    .lastSyncTime(localDateTime.format(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT)))
                    .build();
            return Mono.just(checkpoint);
        } catch (Exception e) {
            log.warn("解析检查点数据失败: {}", e.getMessage(), e);
            return Mono.empty();
        }
    }

    private Map<String, Object> mapFromCheckpoint(SyncIncrementCheckpoint checkpoint) {
        Map<String, Object> map = new HashMap<>();
        map.put("talker", checkpoint.getTalker());
        map.put("talkerName", checkpoint.getTalkerName());
        map.put("lastSeq", checkpoint.getLastSeq().toString());
        map.put("lastSyncTime", checkpoint.getLastSyncTime());
        return map;
    }

    // 创建初始检查点
    private Mono<SyncIncrementCheckpoint> createInitialCheckpoint(String talker) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT));
        return chatlogApi.getChatRoom(talker)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("未找到聊天对象: " + talker)))
                .flatMap(chatRoom -> {
                    String talkerName = CollectionUtils.isEmpty(chatRoom.getItems()) ? "未找到" : chatRoom.getItems().getFirst().getNickName();
                    return Mono.just(SyncIncrementCheckpoint.builder()
                            .talker(talker)
                            .talkerName(talkerName)
                            .lastSeq(0L)
                            .lastSyncTime(now)
                            .build());
                });
    }
}
