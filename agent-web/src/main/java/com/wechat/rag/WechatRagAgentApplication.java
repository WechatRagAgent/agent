package com.wechat.rag;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WechatRagAgentApplication {
    public static void main(String[] args) {
        org.springframework.boot.SpringApplication.run(WechatRagAgentApplication.class, args);
    }
}
