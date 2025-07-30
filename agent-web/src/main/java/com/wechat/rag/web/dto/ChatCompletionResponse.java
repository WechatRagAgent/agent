package com.wechat.rag.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatCompletionResponse {
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("object")
    private String object;
    
    @JsonProperty("created")
    private Long created;
    
    @JsonProperty("model")
    private String model;
    
    @JsonProperty("choices")
    private List<Choice> choices;
    
    @JsonProperty("usage")
    private Usage usage;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Choice {
        @JsonProperty("index")
        private Integer index;
        
        @JsonProperty("delta")
        private Delta delta;
        
        @JsonProperty("finish_reason")
        private String finishReason;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Delta {
        @JsonProperty("role")
        private String role;
        
        @JsonProperty("content")
        private String content;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;
        
        @JsonProperty("completion_tokens")
        private Integer completionTokens;
        
        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }
}