package com.wechat.rag.web.controller;

import com.wechat.rag.datasync.model.Progress;
import com.wechat.rag.datasync.model.ProgressStatus;
import com.wechat.rag.datasync.model.SyncIncrementCheckpoint;
import com.wechat.rag.datasync.service.AutoSyncService;
import com.wechat.rag.datasync.service.ChatlogVectorService;
import com.wechat.rag.datasync.service.IncrementalSyncService;
import com.wechat.rag.datasync.service.ProgressService;
import com.wechat.rag.web.dto.ProgressResponse;
import com.wechat.rag.web.dto.VectorizationRequest;
import com.wechat.rag.web.dto.VectorizationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * 向量化处理控制器
 */
@RestController
@RequestMapping("/api/vectorization")
@RequiredArgsConstructor
@Slf4j
public class VectorizationController {

    private final ChatlogVectorService chatlogVectorService;

    private final ProgressService progressService;

    private final IncrementalSyncService incrementalSyncService;

    private final AutoSyncService autoSyncService;

    /**
     * 向量化处理 - 查询已同步的群信息
     *
     * @param talker 群聊ID或用户ID 默认查询所有
     */
    @GetMapping("/chatlog/sync")
    public Flux<SyncIncrementCheckpoint> getSyncedChatlogs(
            @Valid @RequestParam(value = "talker", required = false) String talker) {

        log.info("查询已同步的群信息: talker={}", talker);

        // 查询已同步的群信息
        return chatlogVectorService.getSyncedChatlogs(talker)
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * SSE接口 - 执行向量化并实时推送进度
     *
     * @param request 向量化请求参数
     * @return 进度事件流
     */
    @GetMapping(value = "/chatlog/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ProgressResponse>> vectorizeChatlogStream(
            @Valid VectorizationRequest request) {

        // 生成任务ID用于追踪
        String taskId = UUID.randomUUID().toString();

        log.info("接收到SSE向量化请求: talker={}, time={}, taskId={}",
                request.getTalker(), request.getTime(), taskId);

        // 初始化进度信息
        progressService.initProgress(taskId, request.getTalker(), request.getTime());

        // 异步执行 包含增量数据
        incrementalSyncService.syncIncrementalWithProgress(
                        request.getTalker(),
                        request.getTime(),
                        taskId,
                        (status, percentage, totalCount, processedCount) -> {
                            try {
                                progressService.updateProgress(taskId, status, percentage, totalCount, processedCount);
                                log.debug("SSE进度更新: taskId={}, status={}, percentage={}", taskId, status, percentage);
                            } catch (Exception e) {
                                log.warn("进度更新失败: {}", e.getMessage(), e);
                            }
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(aVoid -> {
                    log.info("SSE向量化任务完成: taskId={}", taskId);
                    progressService.completeProgress(taskId);
                })
                .doOnError(error -> {
                    log.error("SSE向量化任务失败: taskId={}", taskId, error);
                    progressService.failProgress(taskId, error.getMessage());
                })
                .subscribe();
        // 更新 不带增量数据
        /*chatlogVectorService.initVectorizeChatlog(
                        request.getTalker(),
                        request.getTime(),
                        taskId,
                        // 进度回调 - 只更新进度，不直接推送SSE
                        (status, percentage, totalCount, processedCount) -> {
                            try {
                                progressService.updateProgress(taskId, status, percentage, totalCount, processedCount);
                                log.debug("SSE进度更新: taskId={}, status={}, percentage={}", taskId, status, percentage);
                            } catch (Exception e) {
                                log.warn("进度更新失败: {}", e.getMessage(), e);
                            }
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(aVoid -> {
                    log.info("SSE向量化任务完成: taskId={}", taskId);
                    progressService.completeProgress(taskId);
                })
                .doOnError(error -> {
                    log.error("SSE向量化任务失败: taskId={}", taskId, error);
                    progressService.failProgress(taskId, error.getMessage());
                })
                .subscribe();*/

        // 返回轮询进度的SSE流
        return Flux.interval(Duration.ofMillis(500)) // 每500ms推送一次
                .map(tick -> {
                    Optional<Progress> progressOpt = progressService.getProgress(taskId);

                    if (progressOpt.isEmpty()) {
                        // 任务不存在，发送错误事件
                        ProgressResponse errorResponse = new ProgressResponse();
                        errorResponse.setTaskId(taskId);
                        errorResponse.setStatus(ProgressStatus.FAILED);
                        errorResponse.setStatusDescription("任务不存在");
                        errorResponse.setFailed(true);
                        return ServerSentEvent.<ProgressResponse>builder()
                                .id(taskId + "-" + tick)
                                .event("error")
                                .data(errorResponse)
                                .build();
                    }

                    Progress progress = progressOpt.get();
                    ProgressResponse response = convertToProgressResponse(progress);

                    // 构建SSE事件
                    String eventType = switch (progress.getStatus()) {
                        case COMPLETED -> "completed";
                        case FAILED -> "failed";
                        default -> "progress";
                    };

                    return ServerSentEvent.<ProgressResponse>builder()
                            .id(taskId + "-" + tick)
                            .event(eventType)
                            .data(response)
                            .build();
                })
                .takeUntil(sse -> {
                    // 当任务完成或失败时停止推送
                    ProgressResponse data = sse.data();
                    return data != null && (data.getCompleted() || data.getFailed());
                })
                .doOnNext(sse -> log.debug("推送SSE事件: taskId={}, event={}, percentage={}",
                        taskId, sse.event(), sse.data() != null ? sse.data().getPercentage() : null))
                .doOnComplete(() -> log.info("SSE进度推送完成: taskId={}", taskId))
                .doOnError(error -> log.error("SSE进度推送异常: taskId={}", taskId, error))
                .onErrorResume(error -> {
                    // 发送错误事件并结束流
                    ProgressResponse errorResponse = new ProgressResponse();
                    errorResponse.setTaskId(taskId);
                    errorResponse.setStatus(ProgressStatus.FAILED);
                    errorResponse.setStatusDescription("推送异常");
                    errorResponse.setFailed(true);

                    return Flux.just(ServerSentEvent.<ProgressResponse>builder()
                            .id(taskId + "-stream-error")
                            .event("error")
                            .data(errorResponse)
                            .build());
                });
    }

    /**
     * 删除已完成任务的进度信息（清理接口）
     *
     * @param taskId 任务ID
     * @return 删除结果
     */
    @DeleteMapping("/progress/{taskId}")
    public Mono<ResponseEntity<VectorizationResponse>> deleteProgress(@PathVariable String taskId) {
        log.info("删除进度信息: taskId={}", taskId);

        return Mono.fromCallable(() -> {
                    Optional<Progress> progressOpt = progressService.getProgress(taskId);

                    if (progressOpt.isEmpty()) {
                        return ResponseEntity.notFound().<VectorizationResponse>build();
                    }

                    Progress progress = progressOpt.get();

                    // 只允许删除已完成或失败的任务
                    if (progress.getStatus() != ProgressStatus.COMPLETED &&
                            progress.getStatus() != ProgressStatus.FAILED) {
                        VectorizationResponse response = new VectorizationResponse(
                                "ERROR",
                                "只能删除已完成或失败的任务进度信息"
                        );
                        return ResponseEntity.badRequest().body(response);
                    }

                    progressService.removeProgress(taskId);

                    VectorizationResponse response = new VectorizationResponse(
                            "SUCCESS",
                            "进度信息已删除"
                    );
                    return ResponseEntity.ok(response);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 将Progress转换为ProgressResponse
     */
    private ProgressResponse convertToProgressResponse(Progress progress) {
        ProgressResponse response = new ProgressResponse();
        response.setTaskId(progress.getTaskId());
        response.setStatus(progress.getStatus());
        response.setStatusDescription(progress.getStatus().getDescription());
        response.setPercentage(progress.getPercentage());
        response.setTotalCount(progress.getTotalCount());
        response.setProcessedCount(progress.getProcessedCount());
        response.setStartTime(progress.getStartTime());
        response.setUpdateTime(progress.getUpdateTime());
        response.setErrorMessage(progress.getErrorMessage());
        response.setTalker(progress.getTalker());
        response.setTimeRange(progress.getTimeRange());
        response.setCompleted(progress.getStatus() == ProgressStatus.COMPLETED);
        response.setFailed(progress.getStatus() == ProgressStatus.FAILED);
        return response;
    }
}