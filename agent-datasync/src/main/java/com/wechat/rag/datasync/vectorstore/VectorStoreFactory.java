package com.wechat.rag.datasync.vectorstore;

import com.wechat.rag.datasync.config.VectorStoreConfig;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * Vector Store工厂类
 * 根据配置创建不同的Vector Store实例
 */
@Component
@Slf4j
public class VectorStoreFactory {

    private final VectorStoreConfig vectorStoreConfig;

    public VectorStoreFactory(VectorStoreConfig vectorStoreConfig) {
        this.vectorStoreConfig = vectorStoreConfig;
    }

    @Bean
    public EmbeddingStore<TextSegment> createVectorStore() {
        String providerValue = vectorStoreConfig.getProvider();
        VectorStoreConfig.Provider provider = VectorStoreConfig.Provider.fromValue(providerValue);

        log.info("创建Vector Store实例: {}", provider.getValue());

        return switch (provider) {
            case CHROMA -> createChromaStore();
            case ELASTICSEARCH -> createElasticsearchStore();
        };
    }

    /**
     * 创建Chroma Vector Store
     */
    private ChromaEmbeddingStore createChromaStore() {
        log.debug("初始化Chroma EmbeddingStore: url={}, collection={}",
                vectorStoreConfig.getUrl(), vectorStoreConfig.getCollectionName());

        return ChromaEmbeddingStore.builder()
                .baseUrl(vectorStoreConfig.getUrl())
                .collectionName(vectorStoreConfig.getCollectionName())
                .build();
    }

    /**
     * 创建Elasticsearch Vector Store
     */
    private ElasticsearchEmbeddingStore createElasticsearchStore() {
        log.debug("初始化Elasticsearch EmbeddingStore: url={}, index={}",
                vectorStoreConfig.getUrl(), vectorStoreConfig.getCollectionName());


        RestClient restClient = RestClient
                .builder(HttpHost.create(vectorStoreConfig.getUrl()))
                .build();
        return ElasticsearchEmbeddingStore.builder()
                .restClient(restClient)
                .indexName(vectorStoreConfig.getCollectionName())
                .build();
    }
}
