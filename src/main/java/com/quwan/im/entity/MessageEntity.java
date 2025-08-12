package com.quwan.im.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("im_message")
public class MessageEntity {
    @TableId(type = IdType.INPUT)
    private String messageId;
    private String fromUser;
    private String toUser;
    private String content;
    private String type;
    private String groupId;
    private String status; // SENT, DELIVERED, READ
    private LocalDateTime timestamp;
    private LocalDateTime createTime;
}
