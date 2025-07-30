package com.wechat.rag.core.agent;

import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

public interface Assistant {
    TokenStream chat(@UserMessage String message);
}
