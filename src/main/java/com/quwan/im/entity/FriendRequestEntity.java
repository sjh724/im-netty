package com.quwan.im.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 好友请求实体类
 * 对应数据库表：im_friend_request
 */
@Data
@TableName("im_friend_request")
public class FriendRequestEntity {
    @TableId(type = IdType.AUTO)
    private Long id;                 // 自增ID
    private String fromUser;         // 发起请求的用户ID
    private String toUser;           // 接收请求的用户ID
    private String remark;           // 请求备注信息
    private String status;           // 状态：PENDING(待处理)、ACCEPTED(已接受)、REJECTED(已拒绝)
    private LocalDateTime createTime; // 创建时间
    private LocalDateTime updateTime; // 更新时间
}
