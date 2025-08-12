package com.quwan.im.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("im_friend")
public class FriendEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private String friendId;
    private String status; // PENDING, ACCEPTED, REJECTED
    private String remark; // 备注信息
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
