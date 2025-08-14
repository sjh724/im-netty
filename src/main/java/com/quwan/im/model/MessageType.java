package com.quwan.im.model;

public enum MessageType {
    // 系统基础消息（0-9）
    LOGIN((byte) 0, "客户端登录请求"),
    LOGIN_RESPONSE((byte) 1, "服务端登录响应"),
    LOGOUT((byte) 2, "客户端登出请求"),
    LOGOUT_RESPONSE((byte) 3, "服务端登出响应"),
    PING((byte) 4, "客户端心跳包"),
    PONG((byte) 5, "服务端心跳响应"),
    SYSTEM_NOTIFY((byte) 6, "系统通知消息"),
    ERROR_RESPONSE((byte) 7, "错误响应消息"),

    // 单聊消息（10-19）
    SINGLE_CHAT((byte) 10, "单聊文本消息"),
    SINGLE_CHAT_ACK((byte) 11, "单聊消息确认（已送达）"),
    SINGLE_CHAT_READ((byte) 12, "单聊消息已读回执"),
    SINGLE_CHAT_RECALL((byte) 13, "单聊消息撤回"),

    // 群聊消息（20-29）
    GROUP_CHAT((byte) 20, "群聊文本消息"),
    GROUP_CHAT_ACK((byte) 21, "群聊消息确认（已送达）"),
    GROUP_CHAT_READ((byte) 22, "群聊消息已读回执"),
    GROUP_CHAT_RECALL((byte) 23, "群聊消息撤回"),

    // 好友关系管理（30-39）
    FRIEND_REQUEST_SEND((byte) 30, "发送好友请求"),
    FRIEND_REQUEST_RECV((byte) 31, "接收好友请求（通知）"),
    FRIEND_REQUEST_RESPONSE((byte) 32, "好友请求响应（接受/拒绝）"),
    FRIEND_REQUEST_RESULT((byte) 33, "好友请求处理结果通知"),
    FRIEND_LIST_QUERY((byte) 34, "查询好友列表"),
    FRIEND_LIST_RESPONSE((byte) 35, "好友列表响应"),
    FRIEND_DELETE((byte) 36, "删除好友"),
    FRIEND_DELETE_RESPONSE((byte) 37, "删除好友结果"),

    // 群组管理（40-49）
    GROUP_CREATE((byte) 40, "创建群组"),
    GROUP_CREATE_RESPONSE((byte) 41, "创建群组结果"),
    GROUP_JOIN((byte) 42, "加入群组请求"),
    GROUP_JOIN_RESPONSE((byte) 43, "加入群组结果"),
    GROUP_QUIT((byte) 44, "退出群组"),
    GROUP_QUIT_RESPONSE((byte) 45, "退出群组结果"),
    GROUP_MEMBER_QUERY((byte) 46, "查询群成员列表"),
    GROUP_MEMBER_RESPONSE((byte) 47, "群成员列表响应"),
    GROUP_LIST_QUERY((byte) 48, "查询加入的群组列表"),
    GROUP_LIST_RESPONSE((byte) 49, "群组列表响应");  // 补充缺失的code

    private final byte code;       // 消息类型编码（byte类型，范围-128~127）
    private final String desc;     // 消息类型描述

    MessageType(byte code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 获取消息类型编码
     * @return 编码值
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取消息类型描述
     * @return 描述信息
     */
    public String getDesc() {
        return desc;
    }

    /**
     * 根据编码获取消息类型（核心方法，确保与消息处理器匹配）
     * @param code 消息编码
     * @return 对应的MessageType，若编码无效则抛出IllegalArgumentException
     */
    public static MessageType fromCode(byte code) {
        for (MessageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("无效的消息类型编码: " + code);
    }
}
