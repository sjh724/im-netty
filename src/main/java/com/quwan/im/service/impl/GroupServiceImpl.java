package com.quwan.im.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.quwan.im.entity.GroupEntity;
import com.quwan.im.entity.GroupMemberEntity;
import com.quwan.im.mapper.GroupMapper;
import com.quwan.im.mapper.GroupMemberMapper;
import com.quwan.im.service.GroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 群组服务实现类
 */
@Service
public class GroupServiceImpl extends ServiceImpl<GroupMapper, GroupEntity> implements GroupService {

    @Autowired
    private GroupMapper groupMapper;

    @Autowired
    private GroupMemberMapper groupMemberMapper;

    /**
     * 创建群组
     * 同时添加创建者为群成员（群主）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createGroup(String groupName, String ownerId, String description) {
        // 生成群组ID
        String groupId = "group_" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 16);

        // 创建群组
        GroupEntity group = new GroupEntity();
        group.setGroupId(groupId);
        group.setGroupName(groupName);
        group.setOwnerId(ownerId);
        group.setDescription(description);
        group.setCreateTime(LocalDateTime.now());
        group.setUpdateTime(LocalDateTime.now());

        groupMapper.insert(group);

        // 添加创建者为群成员（群主）
        GroupMemberEntity owner = new GroupMemberEntity();
        owner.setGroupId(groupId);
        owner.setUserId(ownerId);
        owner.setNickname("群主");
        owner.setRole("OWNER");
        owner.setJoinTime(LocalDateTime.now());

        groupMemberMapper.insert(owner);

        return groupId;
    }

    /**
     * 添加群成员
     */
    @Override
    public boolean addGroupMember(String groupId, String userId, String nickname) {
        // 检查是否已为群成员
        if (isGroupMember(groupId, userId)) {
            return false;
        }

        GroupMemberEntity member = new GroupMemberEntity();
        member.setGroupId(groupId);
        member.setUserId(userId);
        member.setNickname(nickname);
        member.setRole("MEMBER"); // 默认普通成员
        member.setJoinTime(LocalDateTime.now());

        return groupMemberMapper.insert(member) > 0;
    }

    /**
     * 移除群成员
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeGroupMember(String groupId, String userId, String operatorId) {
        // 检查操作人是否有权限（群主或管理员）
        GroupMemberEntity operator = groupMemberMapper.selectOne(
                new QueryWrapper<GroupMemberEntity>()
                        .eq("group_id", groupId)
                        .eq("user_id", operatorId)
        );

        if (operator == null || (!"OWNER".equals(operator.getRole()) && !"ADMIN".equals(operator.getRole()))) {
            return false;
        }

        // 群主不能被移除
        GroupEntity group = getById(groupId);
        if (group != null && group.getOwnerId().equals(userId)) {
            return false;
        }

        // 执行移除
        int rows = groupMemberMapper.delete(
                new QueryWrapper<GroupMemberEntity>()
                        .eq("group_id", groupId)
                        .eq("user_id", userId)
        );

        return rows > 0;
    }

    /**
     * 用户退出群组
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean quitGroup(String groupId, String userId) {
        // 群主不能退出群组，只能解散
        GroupEntity group = getById(groupId);
        if (group != null && group.getOwnerId().equals(userId)) {
            return false;
        }

        int rows = groupMemberMapper.delete(
                new QueryWrapper<GroupMemberEntity>()
                        .eq("group_id", groupId)
                        .eq("user_id", userId)
        );

        return rows > 0;
    }

    /**
     * 获取群成员列表
     */
    @Override
    public List<GroupMemberEntity> getGroupMembers(String groupId) {
        return groupMemberMapper.selectMembersByGroupId(groupId);
    }

    /**
     * 获取用户加入的群组
     */
    @Override
    public List<GroupEntity> getUserGroups(String userId) {
        // 实现逻辑：先查询用户加入的群成员记录，再关联查询群组信息
        // 这里简化实现，实际项目中可能需要更复杂的关联查询
        return groupMapper.selectUserGroups(userId);
    }

    /**
     * 检查是否为群成员
     */
    @Override
    public boolean isGroupMember(String groupId, String userId) {
        return groupMemberMapper.isGroupMember(groupId, userId);
    }

    /**
     * 检查是否为群主
     */
    @Override
    public boolean isGroupOwner(String groupId, String userId) {
        GroupEntity group = getById(groupId);
        return group != null && group.getOwnerId().equals(userId);
    }
}
