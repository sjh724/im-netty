package com.quwan.im.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("im_group_member")
public class GroupMemberEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String groupId;
    private String userId;
    private String nickname;
    private String role; // OWNER, ADMIN, MEMBER
    private LocalDateTime joinTime;
}
