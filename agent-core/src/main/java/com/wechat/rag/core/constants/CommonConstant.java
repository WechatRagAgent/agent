package com.wechat.rag.core.constants;

import java.util.regex.Pattern;

public class CommonConstant {
    public static final String OPENROUTER_API_BASE_URL = "https://openrouter.ai/api/v1";

    public static final Pattern CONTEXT_PATTERN = Pattern.compile("\\[CONTEXT\\](.*?)\\[/CONTEXT\\]\\n?", Pattern.DOTALL);

    /**
     * 解析LLM模型 默认参数
     */
    public static final Double DEFAULT_PARSE_TEMPERATURE = 0.2;
    public static final Double DEFAULT_PARSE_TOP_P = 1.0;
    public static final Integer DEFAULT_PARSE_MAX_TOKENS = 8192;
}