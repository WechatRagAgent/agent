package com.wechat.rag.core.rerank;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Slf4j
@Builder
public class SiliconflowRerankModel extends BaseRerankModel implements ScoringModel {
    private final String model;

    private final WebClient webClient;

    @Builder
    private record Request(String model, String query, List<String> documents) {
    }

    private record RerankResponse(List<Result> results) {
    }

    private record Result(@JsonProperty("relevance_score") Double relevanceScore) {
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> textSegments, String query) {
        log.info("开始Rerank，查询文本: {}, 文档数量: {}", processQueryText(query), textSegments.size());
        List<String> texts = textSegments.stream().map(TextSegment::text).toList();
        Request request = Request.builder()
                .model(this.model)
                .query(query)
                .documents(texts)
                .build();
        RerankResponse rerankResponse = webClient.post()
                .uri(uriBuilder -> uriBuilder.path("/rerank").build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(RerankResponse.class)
                .block();
        if (rerankResponse == null) {
            log.error("Rerank响应为空");
            throw new RuntimeException("Rerank响应为空");
        }
        List<Double> scores = rerankResponse.results().stream()
                .map(result -> result.relevanceScore())
                .toList();
        return Response.from(scores);
    }
}
