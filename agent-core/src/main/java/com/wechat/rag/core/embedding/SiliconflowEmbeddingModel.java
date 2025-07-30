package com.wechat.rag.core.embedding;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wechat.rag.core.config.EmbeddingConfig;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Silicon_flow(硅基流动) Embedding模型实现
 */
@Slf4j
@Builder
public class SiliconflowEmbeddingModel implements EmbeddingModel {
    /**
     * API批次大小限制（默认32）
     */
    private final int maxBatchSize = 32;

    /**
     * 单个文本最大token数
     */
    private final int maxTokensPerText = 8192;

    /**
     * 字符到token的估算比例（中文约4个字符=1个token）
     */
    private final double tokenEstimationRatio = 4.0;

    private final String model;

    private final WebClient webClient;
    
    public SiliconflowEmbeddingModel(String model, WebClient webClient) {
        this.model = model;
        this.webClient = webClient;
    }

    @Builder
    @Data
    private static class EmbedRequest {
        private final String model;

        private final List<String> input;

        private EmbedRequest(String model, List<String> input) {
            this.model = model;
            this.input = input;
        }
    }

    @Data
    private static class EmbedResponse {
        /**
         * The name of the model used to generate the embedding.
         */
        private String model;

        private List<EmbeddingData> data;

        private Usage usage;
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        if (textSegments == null || textSegments.isEmpty()) {
            return Response.from(new ArrayList<>());
        }

        List<String> texts = textSegments.stream().map(TextSegment::text).toList();
        
        // 1. 验证文本长度
//        validateTextLengths(texts);
        
        // 2. 分割为子批次
        List<List<String>> subBatches = partitionTexts(texts);
        
        // 3. 处理每个子批次并聚合结果
        List<Embedding> allEmbeddings = new ArrayList<>();
        for (int i = 0; i < subBatches.size(); i++) {
            List<String> batch = subBatches.get(i);
            log.debug("处理子批次 {}/{}, 大小: {}", i + 1, subBatches.size(), batch.size());
            
            List<Embedding> batchEmbeddings = processSingleBatch(batch);
            allEmbeddings.addAll(batchEmbeddings);
        }
        
        // 5. 验证结果完整性
        if (allEmbeddings.size() != texts.size()) {
            throw new RuntimeException(String.format("嵌入结果数量不匹配: 输入%d个文本，生成%d个嵌入", 
                texts.size(), allEmbeddings.size()));
        }
        
        log.debug("成功处理 {} 个文本段，分 {} 个子批次", texts.size(), subBatches.size());
        return Response.from(allEmbeddings);
    }

    /**
     * 验证文本长度不超过token限制
     */
    private void validateTextLengths(List<String> texts) {
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            if (text == null || text.isEmpty()) {
                continue;
            }
            
            int estimatedTokens = (int) Math.ceil(text.length() / tokenEstimationRatio);
            if (estimatedTokens > maxTokensPerText) {
                log.warn("文本段 {} 超过token限制: 文本长度={}, 估算token数={}, 限制={}", 
                    i, text.length(), estimatedTokens, maxTokensPerText);
                // 对于超长文本，可以选择截断或抛出异常
                // 这里选择抛出异常，让调用方处理
                throw new IllegalArgumentException(String.format(
                    "文本段 %d 超过token限制: 估算token数=%d, 限制=%d", 
                    i, estimatedTokens, maxTokensPerText));
            }
        }
    }

    /**
     * 将文本列表分割为指定大小的子批次
     */
    private List<List<String>> partitionTexts(List<String> texts) {
        List<List<String>> partitions = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += maxBatchSize) {
            partitions.add(texts.subList(i, Math.min(i + maxBatchSize, texts.size())));
        }
        return partitions;
    }

    /**
     * 处理单个批次的API请求
     */
    private List<Embedding> processSingleBatch(List<String> texts) {
        EmbedRequest requestBody = EmbedRequest.builder()
                .model(this.model)
                .input(texts)
                .build();

        EmbedResponse embedResponse = webClient.post()
                .uri(uriBuilder ->
                        uriBuilder.path("/embeddings")
                                .build()
                )
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(EmbedResponse.class)
                .block();

        if (embedResponse == null || embedResponse.getData() == null) {
            log.error("Embedding响应为空");
            throw new RuntimeException("Embedding响应为空");
        }

        List<Embedding> embeddings = embedResponse.getData().stream()
                .map(e -> Embedding.from(e.getEmbedding()))
                .toList();

        log.debug("成功生成 {} 个嵌入向量", embeddings.size());
        return embeddings;
    }

    /**
     * The usage information for the request.
     */
    @Data
    private static class Usage {
        /**
         * The number of tokens used by the prompt.
         */
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        /**
         * The number of tokens used by the completion.
         */
        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        /**
         * The total number of tokens used in the request.
         */
        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }

    /**
     * The list of embeddings generated by the model.
     */
    @Data
    private static class EmbeddingData {
        private List<Float> embedding;

        private Integer index;

        private String object;
    }
}
