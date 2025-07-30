package com.wechat.rag.web.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wechat.rag.core.agent.Assistant;
import com.wechat.rag.core.agent.injector.MetadataContentInjector;
import com.wechat.rag.core.agent.retriever.EnhancedContentRetriever;
import com.wechat.rag.core.constants.CommonConstant;
import com.wechat.rag.web.dto.ChatCompletionResponse;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@Slf4j
public class ChatController {

    @Autowired
    private EnhancedContentRetriever enhancedContentRetriever;

    @Autowired
    private MetadataContentInjector contentInjector;

    @Autowired
    private ScoringModel scoringModel;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(String question, String modelName, String apiKey) {
        log.info("用户提问： {}", question);

        String requestId = UUID.randomUUID().toString();
        long created = Instant.now().getEpochSecond();

        return Mono.fromCallable(() -> {
                    // 在弹性线程池中执行所有同步操作
                    // 重排器
                    ReRankingContentAggregator contentAggregator = ReRankingContentAggregator.builder()
                            .scoringModel(scoringModel)
                            .minScore(0.001)
                            .build();
                    // 创建检索增强器，包含元数据注入
                    DefaultRetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                            .contentAggregator(contentAggregator)
                            .contentRetriever(enhancedContentRetriever)
                            .contentInjector(contentInjector)
                            .build();

                    Assistant assistant = AiServices.builder(Assistant.class)
                            .streamingChatModel(createChatModel(modelName, apiKey))
                            .retrievalAugmentor(retrievalAugmentor)
                            .build();
                    return assistant.chat(question);
                })
                .subscribeOn(Schedulers.boundedElastic()) // 关键：整个调用链在弹性线程池执行
                .flatMapMany(tokenStream ->
                        Flux.create(sink -> {
                            tokenStream
                                    .onPartialResponse(partialResponse -> {
                                        log.debug("Partial response: {}", partialResponse);
                                        try {
                                            ChatCompletionResponse response = createStreamingResponse(
                                                    requestId, modelName, created, partialResponse, null);
                                            String jsonData = objectMapper.writeValueAsString(response);
                                            sink.next(jsonData);
                                        } catch (JsonProcessingException e) {
                                            log.error("Error serializing response", e);
                                            sink.error(e);
                                        }
                                    })
                                    .onCompleteResponse(response -> {
                                        log.info("Complete response received");
                                        try {
                                            // 检查是否为null（内容过滤时会返回null）
                                            String finishReason = "stop";
                                            if (response == null) {
                                                finishReason = "content_filter";
                                                log.warn("Response is null - likely content filtered by LLM provider");
                                            } else if (response.finishReason() != null && 
                                                     response.finishReason().name().equals("CONTENT_FILTER")) {
                                                finishReason = "content_filter";
                                                log.warn("Content filtered by LLM provider");
                                            }
                                            
                                            ChatCompletionResponse finalResponse = createStreamingResponse(
                                                    requestId, modelName, created, null, finishReason);
                                            String jsonData = objectMapper.writeValueAsString(finalResponse);
                                            sink.next(jsonData);
                                            sink.complete();
                                        } catch (JsonProcessingException e) {
                                            log.error("Error serializing final response", e);
                                            sink.error(e);
                                        }
                                    })
                                    .onError(error -> {
                                        log.error("Error in token stream", error);
                                        sink.error(error);
                                    })
                                    .start();
                        })
                );
    }

    private StreamingChatModel createChatModel(String modelName, String apiKey) {
        return OpenAiStreamingChatModel.builder()
                .customHeaders(Map.of(
                        "X-Title", "Wechat Rag Agent",
                        "HTTP-Referer", "https://github.com/kosmosr"))
                .apiKey(apiKey)
                .modelName(modelName)
                .baseUrl(CommonConstant.OPENROUTER_API_BASE_URL)
                .logResponses(true)
//                .temperature(CommonConstant.DEFAULT_TEMPERATURE)
//                .topP(CommonConstant.DEFAULT_TOP_P)
                .build();
    }

    private ChatCompletionResponse createStreamingResponse(String requestId, String modelName,
                                                           long created, String content, String finishReason) {
        ChatCompletionResponse.Delta delta = null;
        if (content != null) {
            delta = ChatCompletionResponse.Delta.builder()
                    .role("assistant")
                    .content(content)
                    .build();
        }

        ChatCompletionResponse.Choice choice = ChatCompletionResponse.Choice.builder()
                .index(0)
                .delta(delta)
                .finishReason(finishReason)
                .build();

        return ChatCompletionResponse.builder()
                .id(requestId)
                .object("chat.completion.chunk")
                .created(created)
                .model(modelName != null ? modelName : "wechat-rag-agent")
                .choices(List.of(choice))
                .build();
    }
}
