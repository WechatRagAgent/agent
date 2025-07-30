package com.wechat.rag.core.agent.query;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wechat.rag.datasync.chatlog.ChatlogApi;
import com.wechat.rag.datasync.chatlog.response.ChatRoomResponse;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * 查询解析器
 * 解析结构化查询并生成过滤条件和搜索查询
 */
@Component
@Slf4j
public class QueryParser {
    private final ChatlogApi chatlogApi;

    private final TimeParser timeParser;

    private final ObjectMapper objectMapper = new ObjectMapper();


    @Qualifier("wechatParseModel")
    private ChatModel wechatParseModel;

    public QueryParser(ChatlogApi chatlogApi, TimeParser timeParser, ChatModel wechatParseModel) {
        this.chatlogApi = chatlogApi;
        this.timeParser = timeParser;
        this.wechatParseModel = wechatParseModel;
    }

    // 用于匹配前端上下文的正则表达式
    private static final Pattern CONTEXT_PATTERN = Pattern.compile("\\[CONTEXT\\](.*?)\\[/CONTEXT\\]\\n?", Pattern.DOTALL);

    private final String PARSING_PROMPT_TEMPLATE = """
            ### 角色
            你是一个智能查询分析器。你的任务是将用户的自然语言问题转换成一个结构化的JSON对象，用于后续的数据库检索。
            
            ### 指令
            1.  识别问题中提到的过滤条件，特别是人名(senderName)、群聊名(talkerName)和时间表达(timeExpression)。
            2.  提取用户真正关心的核心问题(search_query)。
            3.  严格按照下面的JSON格式输出，不要添加任何额外的解释。
            4.  如果某个字段没有识别到，请将其值设为null。
            5.  时间表达需要识别：
                - 相对时间：昨天、今天、上周等
                - 绝对时间：2024-01-15, "上个月10号" 等
                - 时间段：上午、下午、晚上等
                - 最近表达：最近、近期、最近3天、最近一周、这几天、这段时间等
            
            {
              "search_query": "用户关心的核心内容",
              "filters": {
                "senderName": "识别出的发送者姓名",
                "talkerName": "识别出的群聊名称",
                "timeExpression": "识别出的时间表达"
              }
            }
            
            ### 用户问题
            {user_query}
            """;

    /**
     * 解析查询字符串
     *
     * @param query 原始查询字符串 可能包含 [CONTEXT]...[/CONTEXT] 块
     * @return 解析结果
     */
    public QueryParseResult parseQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("查询字符串不能为空");
        }
        Filter contextFilter = null;
        String userQuery = query;

        // 1. 解析前端传来的结构化上下文
        Matcher contextMatcher = CONTEXT_PATTERN.matcher(query);
        if (contextMatcher.find()) {
            String contextJson = contextMatcher.group(1);
            // 从查询中移除上下文，得到干净的用户问题
            userQuery = contextMatcher.replaceFirst("");
            try {
                JsonNode contextNode = objectMapper.readTree(contextJson);
                contextFilter = buildFilterFromContext(contextNode); // 从上下文中构建过滤器
            } catch (JsonProcessingException e) {
                // 即使解析失败，也继续执行，只是不使用上下文信息
                log.error("解析前端CONTEXT失败: {}", e.getMessage());
            }
        }

        // 2. 使用LLM解析剩余的自然语言查询
        String prompt = PARSING_PROMPT_TEMPLATE.replace("{user_query}", userQuery);
        String jsonResponse = wechatParseModel.chat(prompt).replaceAll("(?s)^```json\n|\\n```$", "");


        JsonNode llmParseNode;
        try {
            llmParseNode = objectMapper.readTree(jsonResponse);
            log.debug("LLM解析查询结果: {}", llmParseNode);
        } catch (JsonProcessingException e) {
            log.error("LLM解析查询结果失败: {}. 将使用原始查询。", e.getMessage());
            // 如果LLM解析失败，则将整个用户问题作为搜索词
            return QueryParseResult.builder()
                    .originalQuery(query)
                    .searchQuery(userQuery) // 使用清理后的查询
                    .filter(contextFilter)  // 仍然使用从上下文中解析出的过滤器
                    .hasFilter(contextFilter != null)
                    .build();
        }

        // 3. 从LLM的解析结果中构建过滤器
        Filter llmFilter = buildFilterFromLLM(llmParseNode, contextFilter != null);

        // 4. 合并上下文过滤器和LLM过滤器 (上下文过滤器优先)
        Filter finalFilter = contextFilter;
        if (Objects.nonNull(llmFilter)) {
            if (Objects.nonNull(finalFilter)) {
                finalFilter = finalFilter.and(llmFilter);
            } else {
                finalFilter = llmFilter;
            }
        }

        // 5. 获取最终的搜索查询
        String searchQuery = query; // 默认值
        JsonNode searchQueryNode = llmParseNode.get("search_query");
        if (searchQueryNode != null && !searchQueryNode.isNull()) {
            searchQuery = searchQueryNode.asText();
        }

        QueryParseResult result = QueryParseResult.builder()
                .originalQuery(userQuery)
                .searchQuery(searchQuery)
                .filter(finalFilter)
                .hasFilter(finalFilter != null)
                .build();
        log.debug("查询解析结果 - 搜索查询: '{}', 有过滤条件: {}", result.getSearchQuery(), result.isHasFilter());
        return result;
    }

    /**
     * 从前端传入的CONTEXT JSON中构建过滤器
     */
    private Filter buildFilterFromContext(JsonNode contextNode) {
        Filter filter = null;

        // 解析群聊 (groups)
        if (contextNode.has("groups")) {
            List<String> groups = new ArrayList<>();
            for (JsonNode groupNode : contextNode.get("groups")) {
                groups.add(groupNode.asText());
            }
            if (!groups.isEmpty()) {
                filter = metadataKey("talker").isIn(groups);
                log.info("从CONTEXT应用群聊过滤: {}", groups);
            }
        }

        // 解析发送者 (members)
        if (contextNode.has("members")) {
            List<String> members = new ArrayList<>();
            for (JsonNode memberNode : contextNode.get("members")) {
                members.add(memberNode.asText());
            }
            if (!members.isEmpty()) {
                Filter senderFilter = metadataKey("sender").isIn(members);
                if (Objects.nonNull(filter)) {
                    filter = filter.and(senderFilter);
                } else {
                    filter = senderFilter;
                }
                log.info("从CONTEXT应用成员过滤: {}", members);
            }
        }
        return filter;
    }

    /**
     * 从LLM返回的JSON中构建过滤器
     * 优先使用上下文提供的过滤条件
     * 如果上下文未提供，则使用LLM解析的结果
     */
    private Filter buildFilterFromLLM(JsonNode llmParseNode, boolean hasContextFilter) {
        Filter filter = null;
        JsonNode filtersNode = llmParseNode.get("filters");
        if (filtersNode == null || filtersNode.isNull()) {
            return null;
        }

        // 注意：这里的 senderName 和 talkerName 仅在前端上下文未提供时作为备用
        // 解析 senderName
        String senderName = filtersNode.path("senderName").asText(null);
        if (!ObjectUtils.isEmpty(senderName) && !hasContextFilter) {
            List<String> senderIds = findSenders(senderName);
            if (!senderIds.isEmpty()) {
                filter = metadataKey("sender").isIn(senderIds);
            } else {
                log.warn("LLM解析到发送者, 但未找到ID: {}", senderName);
            }
        }

        // 解析 talkerName
        String talkerName = filtersNode.path("talkerName").asText(null);
        if (!ObjectUtils.isEmpty(talkerName) && !hasContextFilter) {
            List<String> talkerIds = findTalkers(talkerName);
            if (!talkerIds.isEmpty()) {
                Filter talkerFilter = metadataKey("talker").isIn(talkerIds);
                if (Objects.nonNull(filter)) {
                    filter = filter.and(talkerFilter);
                } else {
                    filter = talkerFilter;
                }
            } else {
                log.warn("LLM解析到群聊, 但未找到ID: {}", talkerName);
            }
        }

        // 解析时间过滤条件
        String timeExpression = filtersNode.path("timeExpression").asText(null);
        if (!ObjectUtils.isEmpty(timeExpression)) {
            TimeParser.TimeRange timeRange = timeParser.parseTimeExpression(timeExpression);
            if (timeRange != null) {
                Filter timeFilter = metadataKey("seq")
                        .isGreaterThanOrEqualTo(timeRange.getStartTimestamp())
                        .and(metadataKey("seq").isLessThanOrEqualTo(timeRange.getEndTimestamp()));

                if (Objects.nonNull(filter)) {
                    filter = filter.and(timeFilter);
                } else {
                    filter = timeFilter;
                }
                log.info("LLM解析并应用时间过滤: {} -> {}", timeExpression, timeRange);
            } else {
                log.warn("无法解析LLM提取的时间表达: {}", timeExpression);
            }
        }

        return filter;
    }

    private List<String> findTalkers(String talkerName) {
        return chatlogApi.getChatRoom(talkerName)
                .subscribeOn(Schedulers.boundedElastic()) // 使用弹性调度器，避免阻塞主线程
                .map(response -> {
                    if (Objects.isNull(response) || CollectionUtils.isEmpty(response.getItems())) {
                        return List.<String>of();
                    }
                    return response.getItems().stream()
                            .filter(chatRoom -> chatRoom.getNickName().contains(talkerName))
                            .map(ChatRoomResponse.ChatRoom::getName)
                            .collect(Collectors.toList());
                })
                .onErrorReturn(List.of())
                .block(); // 阻塞等待结果
    }

    /**
     * 根据发送者名称获取发送者wxid
     * 遍历所有群聊，筛选出包含发送者名称的群聊，然后获取群聊中的所有用户，筛选出包含发送者名称的用户，然后获取用户wxid
     *
     * @param senderName 发送者名称
     * @return 发送者wxid列表
     */
    private List<String> findSenders(String senderName) {
        return chatlogApi.getChatRoom(null)
                .subscribeOn(Schedulers.boundedElastic()) // 使用弹性调度器，避免阻塞主线程
                .map(chatRoomResponse -> {
                    if (Objects.isNull(chatRoomResponse) || CollectionUtils.isEmpty(chatRoomResponse.getItems())) {
                        return List.<String>of();
                    }
                    return chatRoomResponse.getItems().stream()
                            .flatMap(chatRoom -> chatRoom.getUsers().stream())
                            .filter(user -> user.getDisplayName().contains(senderName))
                            .map(ChatRoomResponse.User::getUserName)
                            .collect(Collectors.toList());
                })
                .onErrorReturn(List.of())
                .block(); // 阻塞等待结果
    }

    /**
     * 查询解析结果
     */
    @Data
    @Builder
    public static class QueryParseResult {
        private String originalQuery;
        private String searchQuery;
        private Filter filter;
        private boolean hasFilter;
    }

}
