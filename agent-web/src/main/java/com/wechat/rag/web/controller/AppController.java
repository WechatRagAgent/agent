package com.wechat.rag.web.controller;

import com.wechat.rag.datasync.service.AutoSyncService;
import com.wechat.rag.web.dto.AutoSyncRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AppController {

    @Autowired
    private final AutoSyncService autoSyncService;

    /**
     * 首页
     *
     * @return 欢迎信息
     */
    @GetMapping
    public Mono<String> index() {
        return Mono.just("欢迎使用 Wechat RAG Agent！");
    }


    /**
     * 获取自动同步配置
     */
    @GetMapping("/chatlog/autosync")
    public Mono<List<String>> getAutoSyncConfig() {
        return autoSyncService.getAutoSyncConfig()
                .switchIfEmpty(Mono.just(Collections.emptyList()));
    }

    /**
     * 设置自动同步配置
     *
     * @param request 自动同步请求
     *                包含聊天对象标识和启用状态
     */
    @PutMapping("/chatlog/autosync")
    public Mono<Void> autoSyncConfig(@RequestBody AutoSyncRequest request) {
        // 设置自动同步配置
        return autoSyncService.addToAutoSync(request.getTalker(), request.getEnabled())
                .subscribeOn(Schedulers.boundedElastic());
    }
}
