package com.wechat.rag.core.rerank;

import com.wechat.rag.core.config.RerankConfig;
import dev.langchain4j.model.scoring.ScoringModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@Slf4j
public class RerankModelFactory {

    @Autowired
    private RerankConfig rerankConfig;

    @Bean
    public ScoringModel createScoringModel() {
        String providerValue = rerankConfig.getProvider();
        RerankConfig.Provider provider = RerankConfig.Provider.fromValue(providerValue);

        log.info("创建RerankModel: {}", provider);

        return switch (provider) {
            case LOCAL -> createLocalModel();
            case SILICON_FLOW -> createSiliconFlowModel();
            default -> throw new IllegalArgumentException("不支持的Rerank模型提供者: " + provider);
        };
    }

    private ScoringModel createSiliconFlowModel() {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                // 设置最大内存大小为1MB
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
        WebClient webClient = WebClient.builder()
                .baseUrl(rerankConfig.getBaseUrl())
                .exchangeStrategies(strategies)
                .defaultHeader("Authorization", "Bearer " + rerankConfig.getApiKey())
                .build();
        return new SiliconflowRerankModel(rerankConfig.getModel(), webClient);
    }

    private ScoringModel createLocalModel() {
        throw new IllegalArgumentException("本地Rerank模型未实现");
    }
}
