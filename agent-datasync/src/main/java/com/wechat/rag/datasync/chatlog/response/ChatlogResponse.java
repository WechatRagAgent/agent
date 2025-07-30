package com.wechat.rag.datasync.chatlog.response;

import lombok.Data;

/**
 * 聊天记录API-响应
 */
@Data
public class ChatlogResponse {
    private Long seq;

    private String time;

    private String talker;

    private String talkerName;

    private Boolean isChatRoom;

    private String sender;

    private String senderName;

    private Boolean isSelf;

    private Integer type;

    private Integer subType;

    private String content;

    private Contents contents;

    @Data
    public static class Contents {
        private Refer refer;
    }

    @Data
    public static class Refer {
        private String sender;

        private String senderName;

        private Integer type;

        private Integer subType;

        private String content;
    }


}
