package com.wechat.rag.datasync.chatlog.response;

import lombok.Data;

import java.util.List;

/**
 * 群聊API-响应
 */
@Data
public class ChatRoomResponse {
    /**
     * 群聊列表
     */
    private List<ChatRoom> items;

    /**
     * 群聊
     */
    @Data
    public static class ChatRoom {
        /**
         * 群聊id
         */
        private String name;
        /**
         * 群主 wxid
         */
        private String owner;
        /**
         * 群成员
         */
        private List<User> users;
        /**
         * 群备注
         */
        private String remark;

        /**
         * 群昵称
         */
        private String nickName;
    }

    /**
     * 群成员
     */
    @Data
    public static class User {
        /**
         * 用户id
         */
        private String userName;
        /**
         * 用户昵称
         */
        private String displayName;
    }
}
