package com.quwan.im.model;

import lombok.Data;
import java.io.Serializable;

@Data
public class IMMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String from;
    private String to;
    private String content;
    private MessageType type;
    private long timestamp;
    private String groupId; // 群组ID，群消息时使用
    private String extra;   // 额外信息
}
