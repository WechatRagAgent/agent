package com.wechat.rag.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 重排器配置
 */
@Data
@Configuration
@ConfigurationProperties("rag.rerank")
public class RerankConfig {
    /**
     * 提供商
     */
    private String provider;

    /**
     * 模型名称
     */
    private String model;

    /**
     * 基础URL
     */
    private String baseUrl;

    /**
     * API密钥
     */
    private String apiKey;

    public enum Provider {
        LOCAL("local"),
        SILICON_FLOW("siliconflow"),
        ;

        private final String value;

        Provider(String value) {
            this.value = value;
        }

        public static RerankConfig.Provider fromValue(String value) {
            for (RerankConfig.Provider provider : values()) {
                if (provider.value.equalsIgnoreCase(value)) {
                    return provider;
                }
            }
            throw new IllegalArgumentException("不支持的重拍器提供商: " + value);
        }
    }
}
