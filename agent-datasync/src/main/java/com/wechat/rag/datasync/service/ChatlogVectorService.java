package com.wechat.rag.datasync.service;

import com.wechat.rag.datasync.chatlog.ChatlogApi;
import com.wechat.rag.datasync.chatlog.response.ChatlogResponse;
import com.wechat.rag.datasync.model.ProgressStatus;
import com.wechat.rag.datasync.model.SyncIncrementCheckpoint;
import com.wechat.rag.datasync.vectorstore.VectorStoreService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

/**
 * 聊天记录向量化存储服务
 */
@Service
@Slf4j
public class ChatlogVectorService {

    /**
     * 进度回调接口
     */
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(ProgressStatus stage, int percentage, Integer totalCount, Integer processedCount);
    }

    // 分批拉取数据 - 配置常量
    private static final int DEFAULT_PAGE_SIZE = 200;

    private static final int DEFAULT_BATCH_SIZE = 200;

    private static final int DEFAULT_CONCURRENCY = 4;

    // 时间格式化器
    private static final DateTimeFormatter OUTPUT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private ChatlogApi chatlogApi;

    @Autowired
    private VectorStoreService vectorStoreService;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private RedisSyncStateService redisSyncStateService;

    @Autowired
    private AutoSyncService autoSyncService;

    /**
     * 获取已同步的聊天记录检查点
     *
     * @param talker 聊天对象 wxid, 群id, 备注名, 昵称
     */
    public Flux<SyncIncrementCheckpoint> getSyncedChatlogs(String talker) {
        if (StringUtils.isEmpty(talker)) {
            return redisSyncStateService.getAllCheckpoints();
        }
        return Flux.from(redisSyncStateService.getCheckpoint(talker));
    }

    public Mono<Void> deleteSyncedChatlogs(String talker) {
        if (StringUtils.isEmpty(talker)) {
            return Mono.error(new IllegalArgumentException("Talker不能为空"));
        }
        return Mono.when(
                // 删除向量数据库中的记录
                vectorStoreService.deleteByTalker(talker),
                // 删除Redis中的同步状态
                redisSyncStateService.deleteTalker(talker),
                // 从自动同步列表中移除
                autoSyncService.removeFromAutoSync(talker)
        );
    }

    /**
     * 初始化
     */
    public Mono<Void> initVectorizeChatlog(String talker, String time, String taskId,
                                           ProgressCallback progressCallback) {
        return vectorizeChatlog(talker, time, taskId, progressCallback, null, null);
    }

    /**
     * 增量更新
     */
    public Mono<Void> incrementalVectorizeChatlog(String talker, String time, String taskId,
                                                  ProgressCallback progressCallback, Long lastProcessedSeq,
                                                  BiFunction<String, Long, Mono<Void>> checkpointCallback) {
        return vectorizeChatlog(talker, time, taskId, progressCallback, lastProcessedSeq, checkpointCallback);
    }

    /**
     * 向量化存储聊天记录 - 带进度追踪的异步处理
     *
     * @param talker             聊天对象 wxid, 群id, 备注名, 昵称
     * @param time               时间范围 格式：YYYY-MM-DD 或 YYYY-MM-DD~YYYY-MM-DD
     * @param taskId             任务ID，用于进度追踪
     * @param progressCallback   进度回调函数
     * @param lastProcessedSeq   上次处理的最大Seq，用于增量更新
     * @param checkpointCallback 检查点回调函数，用于更新处理状态
     * @return 异步处理结果
     */
    public Mono<Void> vectorizeChatlog(String talker, String time, String taskId,
                                       ProgressCallback progressCallback, Long lastProcessedSeq, BiFunction<String, Long, Mono<Void>> checkpointCallback) {
        if (StringUtils.isAnyEmpty(talker, time)) {
            log.error("向量化存储聊天记录参数不完整: talker={}, time={}", talker, time);
            return Mono.error(new IllegalArgumentException("talker和time不能为空"));
        }

        log.info("开始向量化存储聊天记录: talker={}, time={}, taskId={}", talker, time, taskId);
        long startTime = System.currentTimeMillis();

        // 安全的进度回调调用
        safeProgressCallback(progressCallback, ProgressStatus.FETCHING, 5, null, null);

        // 用于追踪处理进度的原子计数器
        AtomicInteger processedCount = new AtomicInteger(0);
        return chatlogApi.getChatlogCount(talker, time)
                .flatMap(countResponse -> {
                    int totalCount = countResponse.getCount();
                    log.info("聊天记录总数: {} 条, talker={}, time={}", totalCount, talker, time);

                    if (totalCount == 0) {
                        log.info("无聊天记录需要处理");
                        safeProgressCallback(progressCallback, ProgressStatus.COMPLETED, 100, 0, 0);
                        return Mono.empty();
                    }

                    int totalPages = (totalCount + DEFAULT_PAGE_SIZE - 1) / DEFAULT_PAGE_SIZE;
                    log.info("开始分页并发处理，总页数: {}, 页面大小: {}", totalPages, DEFAULT_PAGE_SIZE);

                    return Flux.range(0, totalPages)
                            .parallel(DEFAULT_CONCURRENCY)
                            .runOn(Schedulers.boundedElastic())
                            // 多线程获取每一页的聊天记录
                            .flatMap(page -> fetchChatlogPage(talker, time, page)
                                    .doOnNext(records -> {
                                        safeProgressCallback(progressCallback, ProgressStatus.FETCHING, 50, totalCount, null);
                                    })
                            )
                            // 合并为单流
                            .sequential()
                            .flatMap(Flux::fromIterable)
                            .filterWhen(chatlog -> {
                                // 使用Redis检查去重
                                if (Objects.nonNull(lastProcessedSeq)) {
                                    return redisSyncStateService.isSeqProcessed(talker, chatlog.getSeq())
                                            .map(processed -> !processed);
                                }
                                return Mono.just(true);
                            })
                            .filter(chatlog -> {
                                // 如果有lastProcessedSeq，过滤掉已处理的记录
                                if (Objects.nonNull(lastProcessedSeq)) {
                                    return chatlog.getSeq() > lastProcessedSeq;
                                }
                                return true;
                            })
                            // 过滤无效的聊天记录
                            .filter(this::isValidChatlog)
                            .collectList()
                            .flatMapMany(filteredList -> {
                                if (Objects.nonNull(lastProcessedSeq) && filteredList.isEmpty()) {
                                    log.info("增量数据大小为0: talker={}, time={}", talker, time);
                                    safeProgressCallback(progressCallback, ProgressStatus.COMPLETED, 100, totalCount, null);
                                    return Flux.empty();
                                }
                                return Flux.fromIterable(filteredList);
                            })
                            // 收集TextSegment
                            .map(this::toTextSegment)
                            .buffer(DEFAULT_BATCH_SIZE)
                            // 批量处理嵌入向量并存储
                            .flatMap(batch -> processEmbeddingBatch(batch, talker, checkpointCallback)
                                    .doOnSuccess(count -> {
                                                int currentProcessed = processedCount.addAndGet(count);
                                                // 进度从60%开始，处理完成时达到100%
                                                int percentage = 60 + (int) ((double) currentProcessed / totalCount * 40);
                                                safeProgressCallback(progressCallback, ProgressStatus.PROCESSING, percentage, totalCount, currentProcessed);
                                            }
                                    ))
                            .doOnError(e -> {
                                log.error("向量化处理失败: talker={}, time={}", talker, time, e);
                                safeProgressCallback(progressCallback, ProgressStatus.FAILED, 0, totalCount, null);
                            })
                            .then();
                })
                .doOnSuccess(v -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("向量化存储完成: talker={}, time={}, taskId={}, 耗时={}ms",
                            talker, time, taskId, duration);
                    safeProgressCallback(progressCallback, ProgressStatus.COMPLETED, 100, null, processedCount.get());
                })
                .doOnError(e -> {
                    log.error("向量化存储失败: talker={}, time={}, taskId={}", talker, time, taskId, e);
                    safeProgressCallback(progressCallback, ProgressStatus.FAILED, 0, null, processedCount.get());
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 安全的进度回调调用，避免空指针异常
     */
    private void safeProgressCallback(ProgressCallback callback, ProgressStatus stage, int percentage, Integer totalCount, Integer processedCount) {
        if (callback != null) {
            try {
                callback.onProgress(stage, percentage, totalCount, processedCount);
            } catch (Exception e) {
                log.warn("进度回调执行失败: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 获取单页聊天记录 - 带重试机制
     */
    private Mono<List<ChatlogResponse>> fetchChatlogPage(String talker, String time, int page) {
        int offset = page * DEFAULT_PAGE_SIZE;
        return chatlogApi.getChatlog(talker, time, DEFAULT_PAGE_SIZE, Optional.of(offset))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .filter(e -> !(e instanceof IllegalArgumentException))
                )
                .onErrorResume(e -> {
                    log.error("获取聊天记录页面失败，跳过该页: page={}, offset={}", page, offset, e);
                    return Mono.just(List.of()); // 返回空列表，继续处理其他页面
                })
                .doOnSuccess(records -> log.debug("成功获取页面: page={}, 记录数={}", page, records.size()));
    }

    /**
     * 校验聊天记录是否有效
     */
    private boolean isValidChatlog(ChatlogResponse chatlog) {
        return chatlog != null
                && StringUtils.isNotEmpty(chatlog.getContent())
                && chatlog.getType() != null
                // 文本消息
                && (chatlog.getType() == 1
                // 引用文本消息
                || (chatlog.getType() == 49 && chatlog.getSubType() == 57)
        );
    }

    /**
     * 将聊天记录转换为TextSegment
     */
    private TextSegment toTextSegment(ChatlogResponse chatlog) {
        // 构建元数据
        Map<String, Object> metadataMap = new HashMap<>(16);
        metadataMap.put("seq", chatlog.getSeq());
        metadataMap.put("time", formatTime(chatlog.getTime()));
        metadataMap.put("talker", chatlog.getTalker());
        metadataMap.put("talkerName", chatlog.getTalkerName());
        metadataMap.put("sender", chatlog.getSender());
        metadataMap.put("senderName", chatlog.getSenderName());
        metadataMap.put("isChatRoom", chatlog.getIsChatRoom() ? 1 : 0);
        metadataMap.put("isSelf", chatlog.getIsSelf() ? 1 : 0);
        metadataMap.put("type", chatlog.getType());
        metadataMap.put("subType", chatlog.getSubType());
        if (chatlog.getType() == 49 && chatlog.getSubType() == 57 && Objects.nonNull(chatlog.getContents())) {
            // 加入引用消息
            Optional.ofNullable(chatlog.getContents().getRefer())
                    .map(refer -> {
                        if (refer.getType() == 1 || refer.getType() == 49) {
                            return refer;
                        } else {
                            return null;
                        }
                    })
                    .ifPresent(refer -> {
                        metadataMap.put("refer", String.format("sender:%s, senderName: %s, content: %s",
                                refer.getSender(), refer.getSenderName(), StringUtils.deleteWhitespace(refer.getContent())));
                    });
        }
        // refer sender/senderName/content
        Metadata metadata = Metadata.from(metadataMap);
        // 创建TextSegment
        return TextSegment.from(chatlog.getContent().trim(), metadata);
    }

    /**
     * 批量处理嵌入向量并存储
     */
    private Mono<Integer> processEmbeddingBatch(List<TextSegment> textSegments, String talker, BiFunction<String, Long, Mono<Void>> checkpointCallback) {
        if (textSegments.isEmpty()) {
            return Mono.just(0);
        }

        log.debug("开始处理嵌入向量批次，批次大小: {}", textSegments.size());

        return Mono.fromCallable(() -> {
                    // 生成嵌入向量
                    Response<List<Embedding>> listResponse = embeddingModel.embedAll(textSegments);
                    return listResponse.content();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(embeddings -> vectorStoreService.addDocuments(embeddings, textSegments)
                        .collectList()
                        .flatMap(documentIds -> {
                            // 提取序列号并计算最大值
                            List<Long> seqs = textSegments.stream()
                                    .map(segment -> segment.metadata().getLong("seq"))
                                    .toList();
                            Long maxSeq = seqs.stream().max(Long::compareTo).orElse(0L);

                            // 标记为已处理，然后更新检查点
                            return redisSyncStateService.markSeqsAsProcessed(talker, seqs)
                                    .then(updateCheckpoint(talker, maxSeq, checkpointCallback))
                                    .thenReturn(documentIds.size());
                        })
                )
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(2)))
                .onErrorResume(e -> {
                    log.error("嵌入向量处理失败，跳过该批次: 批次大小={}", textSegments.size(), e);
                    return Mono.just(0); // 返回0表示该批次失败，但继续处理其他批次
                })
                .doOnSuccess(count -> log.debug("成功处理嵌入向量批次: 处理数量={}", count));
    }

    /**
     * 更新检查点统一方法
     *
     * @param talker             聊天对象
     * @param maxSeq             最大序列号
     * @param checkpointCallback 检查点回调函数，如果为null则直接更新检查点
     * @return 异步更新结果
     */
    private Mono<Void> updateCheckpoint(String talker, Long maxSeq, BiFunction<String, Long, Mono<Void>> checkpointCallback) {
        if (checkpointCallback != null) {
            return checkpointCallback.apply(talker, maxSeq);
        }

        return redisSyncStateService.getCheckpoint(talker)
                .flatMap(checkpoint -> {
                    if (checkpoint.getLastSeq() == 0L || maxSeq > checkpoint.getLastSeq()) {
                        checkpoint.setLastSeq(maxSeq);
                        return redisSyncStateService.updateCheckpoint(talker, checkpoint);
                    }
                    return Mono.empty();
                });
    }

    /**
     * 格式化时间字符串
     * 将ISO 8601格式转换为 yyyy-MM-dd HH:mm:ss 格式
     *
     * @param timeStr ISO 8601格式时间字符串
     * @return 格式化后的时间字符串
     */
    private String formatTime(String timeStr) {
        if (StringUtils.isBlank(timeStr)) {
            return "";
        }

        try {
            // 解析ISO 8601格式时间
            OffsetDateTime offsetDateTime = OffsetDateTime.parse(timeStr);
            // 转换为本地时间并格式化
            return offsetDateTime.toLocalDateTime().format(OUTPUT_TIME_FORMATTER);
        } catch (Exception e) {
            log.warn("时间格式转换失败: {}, 原始值: {}", e.getMessage(), timeStr);
            return timeStr; // 转换失败时返回原始值
        }
    }
}
