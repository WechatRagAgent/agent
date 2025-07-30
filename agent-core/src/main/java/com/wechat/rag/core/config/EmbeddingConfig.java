package com.wechat.rag.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 嵌入模型配置类
 */
@Configuration
@ConfigurationProperties(prefix = "rag.embedding")
@Data
public class EmbeddingConfig {
    /**
     * 提供商
     */
    private String provider;

    /**
     * 嵌入模型
     */
    private String model;

    /**
     * API密钥
     */
    private String apiKey;

    /**
     * 基础URL
     */
    private String baseUrl;

    public enum Provider {
        HUGGINGFACE("huggingface"),
        LOCAL("local"),
        SILICON_FLOW("siliconflow"),
        ;

        private final String value;

        Provider(String value) {
            this.value = value;
        }

        public static Provider fromValue(String value) {
            for (Provider provider : values()) {
                if (provider.value.equalsIgnoreCase(value)) {
                    return provider;
                }
            }
            throw new IllegalArgumentException("不支持的Embedding Model提供商: " + value);
        }
    }
}
