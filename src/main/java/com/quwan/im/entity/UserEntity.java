package com.quwan.im.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("im_user")
public class UserEntity {
    @TableId(type = IdType.INPUT)
    private String userId;
    private String username;
    private String avatar;
    private String password;
    private String status; // ONLINE, OFFLINE
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
