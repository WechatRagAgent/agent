package com.wechat.rag.web.chatlog;

import com.wechat.rag.datasync.chatlog.ChatlogApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

//@SpringBootTest
public class ChatlogApiTest {

//    @Autowired
    private ChatlogApi chatlogApi;

//    @Test
    void testGetChatlog_Success() {
        // 执行测试
        StepVerifier.create(chatlogApi.getChatlog("狠狠的羁绊", "2025-07-01~2025-07-22", -1, Optional.of(0)))
                .expectNextMatches(chatlogList -> {
                    return !chatlogList.isEmpty();
                })
                .verifyComplete();
    }

    public static void main(String[] args) {
        System.out.println(ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }
}