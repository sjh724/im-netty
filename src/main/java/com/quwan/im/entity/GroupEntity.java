package com.quwan.im.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("im_group")
public class GroupEntity {
    @TableId(type = IdType.INPUT)
    private String groupId;
    private String groupName;
    private String avatar;
    private String ownerId;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
