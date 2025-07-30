package com.wechat.rag.core.config;

import com.wechat.rag.core.constants.CommonConstant;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "rag.parse-model")
@Data
public class RagParseModel {
    private String apiKey;

    private String model;

    @Bean("wechatParseModel")
    public ChatModel createParseModel() {
        return OpenAiChatModel.builder()
                .customHeaders(Map.of(
                        "X-Title", "Wechat Rag Agent",
                        "HTTP-Referer", "https://github.com/kosmosr"))
                .apiKey(apiKey)
                .modelName(model)
                .baseUrl(CommonConstant.OPENROUTER_API_BASE_URL)
                .temperature(CommonConstant.DEFAULT_PARSE_TEMPERATURE)
                .topP(CommonConstant.DEFAULT_PARSE_TOP_P)
                .maxTokens(CommonConstant.DEFAULT_PARSE_MAX_TOKENS)
                .build();
    }
}
