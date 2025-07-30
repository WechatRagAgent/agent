package com.wechat.rag.core.embedding;

import com.wechat.rag.core.config.EmbeddingConfig;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.huggingface.HuggingFaceEmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Embedding模型工厂类
 * 用于创建和管理Embedding模型实例
 */
@Component
@Slf4j
public class EmbeddingModelFactory {

    @Autowired
    private EmbeddingConfig embeddingConfig;

    @Bean
    public EmbeddingModel createEmbeddingModel() {
        String providerValue = embeddingConfig.getProvider();
        EmbeddingConfig.Provider provider = EmbeddingConfig.Provider.fromValue(providerValue);

        log.info("创建EmbeddingModel: {}", provider);

        return switch (provider) {
            case HUGGINGFACE -> createHuggingFaceModel();
            case LOCAL -> null;
            case SILICON_FLOW -> createSiliconFlowModel();
        };
    }

    /**
     * 创建HuggingFace Embedding模型
     *
     * @return HuggingFaceEmbeddingModel实例
     */
    private EmbeddingModel createHuggingFaceModel() {
        HuggingFaceEmbeddingModel.HuggingFaceEmbeddingModelBuilder builder = HuggingFaceEmbeddingModel.builder()
                .baseUrl(embeddingConfig.getBaseUrl())
                .accessToken(embeddingConfig.getApiKey())
                .modelId(embeddingConfig.getModel())
                .timeout(Duration.ofSeconds(60));
        log.info("创建HuggingFace Embedding模型: modelName={}", embeddingConfig.getModel());
        return builder.build();
    }

    /**
     * 创建SiliconFlow Embedding模型
     *
     * @return SiliconflowEmbeddingModel实例
     */
    private EmbeddingModel createSiliconFlowModel() {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                // 设置最大内存大小为1MB
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
        WebClient webClient = WebClient.builder()
                .baseUrl(embeddingConfig.getBaseUrl())
                .exchangeStrategies(strategies)
                .defaultHeader("Authorization", "Bearer " + embeddingConfig.getApiKey())
                .build();
        return new SiliconflowEmbeddingModel(embeddingConfig.getModel(), webClient);
    }

}
