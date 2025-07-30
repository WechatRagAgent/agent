package com.wechat.rag.datasync.vectorstore;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

/**
 * Vector Store服务类
 * 负责处理与Vector Store相关的业务逻辑
 */
@Service
@Slf4j
public class VectorStoreService {
    private final EmbeddingStore<TextSegment> embeddingStore;

    public VectorStoreService(EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingStore = embeddingStore;
    }

    /**
     * 添加文档到Vector Store
     *
     * @param embedding   嵌入向量
     * @param textSegment 文本片段
     * @return 文档ID
     */
    public Mono<String> addDocument(Embedding embedding, TextSegment textSegment) {
        if (ObjectUtils.anyNull(embedding, textSegment)) {
            return Mono.error(new IllegalArgumentException("Embedding和TextSegment不能为空"));
        }

        return Mono.fromCallable(() -> embeddingStore.add(embedding, textSegment))
                .onErrorResume(e -> {
                    log.error("添加文档失败", e);
                    return Mono.error(new RuntimeException("添加文档失败", e));
                });
    }

    /**
     * 批量添加文档到向量存储
     *
     * @param embeddings   嵌入向量列表
     * @param textSegments 文本片段列表
     * @return 文档ID列表
     */
    public Flux<String> addDocuments(List<Embedding> embeddings, List<TextSegment> textSegments) {
        if (CollectionUtils.isEmpty(embeddings) || CollectionUtils.isEmpty(textSegments)) {
            return Flux.error(new IllegalArgumentException("Embeddings和TextSegments不能为空"));
        }

        if (embeddings.size() != textSegments.size()) {
            return Flux.error(new IllegalArgumentException("Embeddings和TextSegments数量不匹配"));
        }

        return Mono.fromCallable(() -> embeddingStore.addAll(embeddings, textSegments))
                .onErrorResume(e -> {
                    log.error("添加文档列表失败", e);
                    return Mono.error(new RuntimeException("添加文档列表失败", e));
                })
                .flatMapMany(Flux::fromIterable);
    }
}
