package com.wechat.rag.datasync.config;

import lombok.Data;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Vector Store配置
 */
@Configuration
@ConfigurationProperties(prefix = "rag.vector-store")
@Data
public class VectorStoreConfig {
    /**
     * Vector Store提供商
     */
    private String provider;

    /**
     * Vector Store URL
     */
    private String url;

    /**
     * Vector Store集合名称
     */
    private String collectionName;

    @Getter
    public enum Provider {
        CHROMA("chroma"),
        ELASTICSEARCH("elasticsearch");

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
            throw new IllegalArgumentException("不支持的Vector Store提供商: " + value);
        }
    }
}
