package com.wechat.rag.datasync.chatlog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "wechat.chatlog")
@Data
public class ChatlogConfig {
    /**
     * chatlog baseUrl
     */
    private String baseUrl;
}
