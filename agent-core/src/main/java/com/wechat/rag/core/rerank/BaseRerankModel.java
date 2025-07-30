package com.wechat.rag.core.rerank;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BaseRerankModel {
    private static final Pattern CONTEXT_PATTERN = Pattern.compile("\\[CONTEXT\\](.*?)\\[/CONTEXT\\]\\n?", Pattern.DOTALL);

    protected String processQueryText(String queryText) {
        Matcher contextMatcher = CONTEXT_PATTERN.matcher(queryText);
        if (contextMatcher.find()) {
            // 过滤掉 [CONTEXT] 标签及其内容
            return contextMatcher.replaceFirst("");
        }
        // 直接返回原始查询文本
        return queryText;
    }
}
