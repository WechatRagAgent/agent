package com.wechat.rag.core.agent.retriever;

import com.wechat.rag.core.agent.query.QueryParser;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 增强的内容检索器
 * 支持元数据过滤的高级RAG检索
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EnhancedContentRetriever implements ContentRetriever {

    private final EmbeddingModel embeddingModel;

    private final EmbeddingStore<TextSegment> embeddingStore;

    private final QueryParser queryParser;

    // 默认配置
    private static final int DEFAULT_MAX_RESULTS = 500;

    private static final double DEFAULT_MIN_SCORE = 0.7;

    @Override
    public List<Content> retrieve(Query query) {
        log.info("开始检索内容，查询文本: {}", query.text());
        String queryText = query.text();

        // 解析查询
        QueryParser.QueryParseResult queryParseResult = queryParser.parseQuery(queryText);
        log.info("解析查询结果: {}", queryParseResult);

        log.info("执行向量相似度搜索");
        return performVectorSearch(queryParseResult);
    }

    /**
     * 执行向量搜索
     */
    private List<Content> performVectorSearch(QueryParser.QueryParseResult queryParseResult) {
        log.info("生成查询向量");
        Embedding queryEmbedding = embeddingModel.embed(queryParseResult.getSearchQuery()).content();

        log.info("构建搜索请求");
        EmbeddingSearchRequest.EmbeddingSearchRequestBuilder requestBuilder = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(DEFAULT_MAX_RESULTS)
                .minScore(DEFAULT_MIN_SCORE);
        if (queryParseResult.isHasFilter()) {
            requestBuilder.filter(queryParseResult.getFilter());
        }

        EmbeddingSearchRequest request = requestBuilder.build();
        EmbeddingSearchResult<TextSegment> ebdStoreSearchResult = embeddingStore.search(request);
        log.info("向量搜索完成，返回{}个结果", ebdStoreSearchResult.matches().size());
        return convertToContent(ebdStoreSearchResult.matches());
    }

    /**
     * 将搜索结果转换为Content
     */
    private List<Content> convertToContent(List<EmbeddingMatch<TextSegment>> matches) {
        return matches.stream()
                .map(match -> {
                    TextSegment segment = match.embedded();

                    // 构建增强的内容，包含元数据信息
                    return Content.from(segment, Map.of(ContentMetadata.SCORE, match.score()));
                })
                .collect(Collectors.toList());
    }
}
