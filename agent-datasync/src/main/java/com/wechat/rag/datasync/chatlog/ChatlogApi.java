package com.wechat.rag.datasync.chatlog;

import com.wechat.rag.datasync.chatlog.config.ChatlogConfig;
import com.wechat.rag.datasync.chatlog.constants.ChatlogConstant;
import com.wechat.rag.datasync.chatlog.response.ChatRoomResponse;
import com.wechat.rag.datasync.chatlog.response.ChatlogCountResponse;
import com.wechat.rag.datasync.chatlog.response.ChatlogResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

/**
 * Chatlog API 客户端
 */
@Service
@Slf4j
public class ChatlogApi {

    private final WebClient webClient;

    private final ChatlogConfig chatlogConfig;

    public ChatlogApi(ChatlogConfig chatlogConfig) {
        this.chatlogConfig = chatlogConfig;
        this.webClient = WebClient.builder()
                .baseUrl(chatlogConfig.getBaseUrl())
                .build();
    }

    /**
     * 获取聊天记录总数
     * 查询指定时间范围内与特定联系人或群聊的聊天记录总数
     *
     * @param talker 聊天对象 wxid, 群id, 备注名, 昵称
     * @param time   时间范围 格式：YYYY-MM-DD 或 YYYY-MM-DD~YYYY-MM-DD
     * @return 聊天记录计数响应
     */
    public Mono<ChatlogCountResponse> getChatlogCount(String talker, String time) {
        // 参数校验
        if (StringUtils.isEmpty(time)) {
            throw new IllegalArgumentException("time参数是必传的，格式：YYYY-MM-DD 或 YYYY-MM-DD~YYYY-MM-DD");
        }
        if (StringUtils.isEmpty(talker)) {
            throw new IllegalArgumentException("talker参数是必传的");
        }
        if (!isValidTimeFormat(time)) {
            throw new IllegalArgumentException("time参数格式不正确，格式：YYYY-MM-DD 或 YYYY-MM-DD~YYYY-MM-DD");
        }

        String path = ChatlogConstant.API_GET_CHATLOG_COUNT_PATH;
        log.debug("获取聊天记录总数: talker={}, time={}", talker, time);

        return webClient.get()
                .uri(uriBuilder ->
                        uriBuilder.path(path)
                                .queryParam("talker", talker)
                                .queryParam("time", time)
                                .build())
                .retrieve()
                .bodyToMono(ChatlogCountResponse.class);
    }

    /**
     * 获取聊天记录
     * 查询指定时间范围内与特定联系人或群聊的聊天记录
     *
     * @param talker 聊天对象 wxid, 群id, 备注名, 昵称
     * @param time   时间范围 格式：YYYY-MM-DD 或 YYYY-MM-DD~YYYY-MM-DD
     * @param limit  每次查询的记录数
     * @param offset 偏移量 默认0
     * @return 聊天记录列表
     */
    public Mono<List<ChatlogResponse>> getChatlog(String talker, String time, Integer limit, Optional<Integer> offset) {
        // 参数校验
        if (StringUtils.isEmpty(time)) {
            throw new IllegalArgumentException("time参数是必传的，格式：YYYY-MM-DD 或 YYYY-MM-DD~YYYY-MM-DD");
        }
        if (StringUtils.isEmpty(talker)) {
            throw new IllegalArgumentException("talker参数是必传的");
        }
        if (!isValidTimeFormat(time)) {
            throw new IllegalArgumentException("time参数格式不正确，格式：YYYY-MM-DD 或 YYYY-MM-DD~YYYY-MM-DD");
        }

        String path = ChatlogConstant.API_GET_CHATLOG_PATH;
        log.debug("获取聊天记录: talker={}, time={}, limit={}, offset={}", talker, time, limit, offset.orElse(0));
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(path)
                        .queryParam("talker", talker)
                        .queryParam("time", time)
                        .queryParam("limit", limit)
                        .queryParam("format", "json")
                        .queryParam("offset", offset.orElse(0))
                        .build())
                .retrieve()
                .bodyToFlux(ChatlogResponse.class)
                .collectList();
    }

    /**
     * 获取群聊
     *
     * @param keyword 群聊名称 可选
     * @return 群聊列表
     */
    public Mono<ChatRoomResponse> getChatRoom(String keyword) {
        String path = ChatlogConstant.API_GET_CHATROOM_PATH;
        log.debug("获取群聊信息: keyword={}", keyword);

        return webClient.get()
                .uri(uriBuilder -> {
                    var uri = uriBuilder.path(path);
                    if (StringUtils.isNotEmpty(keyword)) {
                        uri = uri.queryParam("keyword", keyword);
                    }
                    return uri.queryParam("format", "json")
                            .build();
                })
                .retrieve()
                .bodyToMono(ChatRoomResponse.class);
    }

    /**
     * 验证时间格式
     *
     * @param time 时间字符串
     * @return 是否有效
     */
    private boolean isValidTimeFormat(String time) {
        if (StringUtils.isEmpty(time)) {
            return false;
        }

        // 单日期格式：YYYY-MM-DD
        if (time.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return true;
        }

        // 日期范围格式：YYYY-MM-DD~YYYY-MM-DD
        return time.matches("\\d{4}-\\d{2}-\\d{2}~\\d{4}-\\d{2}-\\d{2}");
    }
}
